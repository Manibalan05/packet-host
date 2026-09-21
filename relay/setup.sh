#!/usr/bin/env bash
# Alan VPS relay setup — run ON THE VPS (needs sudo). No secrets in this file.
# Installs Caddy, opens the firewall, enables sshd GatewayPorts for the
# phone's SSH remote forwarding, and prints a go-live checklist.
set -euo pipefail

REMOTE_PORT=8080
DOMAIN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --remote-port) REMOTE_PORT="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    *) echo "Usage: sudo ./setup.sh --remote-port 8080 --domain sites.example.com"; exit 1 ;;
  esac
done

if [[ "$(id -u)" -ne 0 ]]; then echo "Run as root/sudo."; exit 1; fi
if ! [[ "$REMOTE_PORT" =~ ^[0-9]+$ ]] || (( REMOTE_PORT < 1 || REMOTE_PORT > 65535 )); then
  echo "Bad --remote-port: $REMOTE_PORT"; exit 1
fi

echo "==> Installing Caddy (https://caddyserver.com/docs/install) ..."
if ! command -v caddy >/dev/null 2>&1; then
  apt-get update
  apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
  apt-get update
  apt-get install -y caddy
fi

echo "==> Enabling sshd remote forwarding (GatewayPorts) ..."
SSHD_DROPIN=/etc/ssh/sshd_config.d/99-alan-relay.conf
mkdir -p /etc/ssh/sshd_config.d
printf 'AllowTcpForwarding yes\nGatewayPorts yes\nClientAliveInterval 30\nClientAliveCountMax 3\n' > "$SSHD_DROPIN"
if sshd -t; then
  systemctl reload ssh || systemctl reload sshd || true
  echo "sshd reloaded."
else
  echo "WARNING: sshd config test failed — fix $SSHD_DROPIN then reload ssh."
fi

echo "==> Opening firewall for 80/443 (Caddy) + $REMOTE_PORT (forward) ..."
if command -v ufw >/dev/null 2>&1; then
  ufw allow 80/tcp || true
  ufw allow 443/tcp || true
  ufw allow "$REMOTE_PORT/tcp" || true
elif command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-service=http || true
  firewall-cmd --permanent --add-service=https || true
  firewall-cmd --permanent --add-port="$REMOTE_PORT/tcp" || true
  firewall-cmd --reload || true
else
  echo "No ufw/firewalld found — open 80, 443 and $REMOTE_PORT in your cloud firewall."
fi

echo
echo "================ CHECKLIST ================"
echo "1. DNS: point ${DOMAIN:-<your-domain>} A-record at this VPS public IP."
echo "2. Caddy: edit Caddyfile.example (domain + $REMOTE_PORT), copy to /etc/caddy/Caddyfile, then: systemctl reload caddy"
echo "3. Alan app: Relay screen -> VPS host/user/credentials, approve host key, Connect."
if [[ -z "$DOMAIN" ]]; then
  echo "4. (Optional) plain http://<vps-ip>:$REMOTE_PORT works without a domain."
else
  echo "4. Open https://$DOMAIN/ from anywhere on the internet."
fi
echo "==========================================="
