package com.blockbet;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView status;
    private MaterialButton toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.toggleButton);
        updateState();

        toggle.setOnClickListener(v -> {
            if (BetBlockVpnService.isRunning()) {
                stopService(new Intent(this, BetBlockVpnService.class));
                updateState();
            } else {
                Intent permission = VpnService.prepare(this);
                if (permission != null) startActivityForResult(permission, 100);
                else startProtection();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) startProtection();
    }

    private void startProtection() {
        startService(new Intent(this, BetBlockVpnService.class));
        status.postDelayed(this::updateState, 350);
    }

    private void updateState() {
        boolean running = BetBlockVpnService.isRunning();
        status.setText(running ? "Proteção ativada" : "Proteção desligada");
        status.setTextColor(getColor(running ? android.R.color.holo_green_light : android.R.color.holo_red_light));
        toggle.setText(running ? "DESATIVAR BLOQUEIO" : "ATIVAR BLOQUEIO");
    }
}
