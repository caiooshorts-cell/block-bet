package com.blockbet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BetBlockVpnService extends VpnService {
    private static volatile boolean running = false;
    private volatile boolean stopped = false;
    private ParcelFileDescriptor tunnel;

    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
        "bet365.com", "betano.com", "betway.com", "betfair.com", "sportingbet.com",
        "pixbet.com", "blaze.com", "esportesdasaorte.com", "stake.com", "1xbet.com",
        "betsson.com", "bwin.com", "kto.com", "superbet.com", "betnacional.com",
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "adnxs.com", "taboola.com", "outbrain.com"
    ));

    public static boolean isRunning() { return running; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            showNotification();
            new Thread(this::runVpn, "BlockBetDns").start();
        }
        return START_STICKY;
    }

    private void showNotification() {
        String channelId = "block_bet";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Proteção Block Bet", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Block Bet está ativo")
            .setContentText("Bloqueando domínios de apostas e anúncios")
            .setOngoing(true).build();
        startForeground(1, notification);
    }

    private void runVpn() {
        try {
            tunnel = new Builder().setSession("Block Bet").addAddress("10.0.0.1", 32)
                .addDnsServer("10.0.0.2").addRoute("10.0.0.2", 32).setMtu(1500).establish();
            if (tunnel == null) { stopSelf(); return; }
            running = true;
            FileInputStream input = new FileInputStream(tunnel.getFileDescriptor());
            FileOutputStream output = new FileOutputStream(tunnel.getFileDescriptor());
            byte[] packet = new byte[32767];
            while (!stopped) {
                int length = input.read(packet);
                if (length > 0) handlePacket(packet, length, output);
            }
        } catch (Exception ignored) {
        } finally {
            running = false;
            closeTunnel();
        }
    }

    private void handlePacket(byte[] data, int length, FileOutputStream output) {
        try {
            int ihl = (data[0] & 0x0F) * 4;
            if (length < ihl + 8 || (data[0] >> 4) != 4 || (data[9] & 0xFF) != 17) return;
            int sourcePort = u16(data, ihl);
            if (u16(data, ihl + 2) != 53) return;
            byte[] query = Arrays.copyOfRange(data, ihl + 8, length);
            String host = readQuestionHost(query);
            byte[] response = isBlocked(host) ? nxdomain(query) : askCloudflare(query);
            if (response == null) return;

            byte[] reply = new byte[28 + response.length];
            reply[0] = 0x45;
            put16(reply, 2, reply.length);
            reply[8] = 64;
            reply[9] = 17;
            System.arraycopy(data, 16, reply, 12, 4);
            System.arraycopy(data, 12, reply, 16, 4);
            put16(reply, 10, checksum(reply, 0, 20));
            put16(reply, 20, 53);
            put16(reply, 22, sourcePort);
            put16(reply, 24, 8 + response.length);
            System.arraycopy(response, 0, reply, 28, response.length);
            output.write(reply);
        } catch (Exception ignored) {
        }
    }

    private byte[] askCloudflare(byte[] query) {
        try (DatagramSocket socket = new DatagramSocket()) {
            protect(socket);
            socket.setSoTimeout(3500);
            socket.connect(InetAddress.getByName("1.1.1.1"), 53);
            socket.send(new DatagramPacket(query, query.length));
            byte[] reply = new byte[4096];
            DatagramPacket received = new DatagramPacket(reply, reply.length);
            socket.receive(received);
            return Arrays.copyOf(reply, received.getLength());
        } catch (Exception e) { return null; }
    }

    private boolean isBlocked(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String domain : BLOCKED)
            if (normalized.equals(domain) || normalized.endsWith("." + domain)) return true;
        return false;
    }

    private String readQuestionHost(byte[] dns) {
        if (dns.length < 13) return null;
        StringBuilder host = new StringBuilder();
        int at = 12;
        while (at < dns.length) {
            int part = dns[at++] & 0xFF;
            if (part == 0) return host.toString();
            if (part > 63 || at + part > dns.length) return null;
            if (host.length() > 0) host.append('.');
            for (int i = 0; i < part; i++) host.append((char) (dns[at++] & 0xFF));
        }
        return null;
    }

    private byte[] nxdomain(byte[] query) {
        if (query.length < 12) return null;
        byte[] response = Arrays.copyOf(query, query.length);
        response[2] = (byte) 0x81; response[3] = (byte) 0x83;
        response[6] = response[7] = response[8] = response[9] = response[10] = response[11] = 0;
        return response;
    }

    private static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
    private static void put16(byte[] b, int o, int v) { b[o] = (byte)(v >>> 8); b[o + 1] = (byte)v; }
    private static int checksum(byte[] b, int o, int size) {
        long sum = 0;
        for (int i = o; i < o + size; i += 2) {
            if (i == o + 10) continue;
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            while ((sum & 0xFFFF0000L) != 0) sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        return (int)(~sum) & 0xFFFF;
    }

    @Override public void onDestroy() { stopped = true; running = false; closeTunnel(); super.onDestroy(); }
    private void closeTunnel() { try { if (tunnel != null) tunnel.close(); } catch (Exception ignored) {} tunnel = null; }
    @Override public IBinder onBind(Intent intent) { return super.onBind(intent); }
}
