# Block Bet

Aplicativo Android de bloqueio por DNS para domínios de apostas e publicidade.

## Como funciona

Ao ativar a proteção, o Android mostra o aviso padrão de VPN. O app usa uma VPN **local**, sem servidor externo e sem coletar ou enviar dados. Ele responde com NXDOMAIN para domínios presentes na lista de bloqueio; consultas permitidas são encaminhadas a um resolvedor DNS público.

A proteção abrange apps que usam o DNS padrão do sistema. Ela não consegue bloquear tráfego que use DNS-over-HTTPS/DoT próprio, endereços IP diretos, ou conteúdo já carregado em um aplicativo.

## APK

Cada atualização na branch `main` inicia o fluxo **Build Android APK**. Baixe o arquivo `block-bet-debug-apk` na seção **Actions** do GitHub.
