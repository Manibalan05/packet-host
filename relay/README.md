# Alan VPS relay — whole-internet hosting on your own server

The phone keeps serving locally through its reverse proxy (`ProxyServer` on
`0.0.0.0:<proxyPort>`). For the whole internet, the app opens an **outbound**
SSH connection to **your own VPS** and requests **SSH remote port forwarding**:

```
visitor ──https──> VPS:443 (Caddy, auto-HTTPS) ──> 127.0.0.1:REMOTE (sshd)
      ──ssh -R──> phone 127.0.0.1:LOCAL (Alan reverse proxy) ──> your site
```

Because the connection is *outbound* from the phone, home routers, hotel
Wi-Fi, NAT and carrier CGNAT cannot block it — nothing inbound needs to reach
the phone. This is the same "own infrastructure + reverse proxy" pattern
Coolify uses; there is no third-party relay service involved — just a VPS
you rent. The SSH client is pure-Java (sshj), so no native binaries are
needed on the phone for this path (the separate free quick-tunnel path
ships its own cloudflared binary; see below).

## VPS requirements

- Any VPS with a **public IP** and **SSH access** (~$4–6/mo is plenty).
- A domain/subdomain whose DNS you control (for automatic HTTPS).
- ~10 minutes. No secrets live in these files.

## The 3 commands (on the VPS)

```bash
# 1. Allow SSH remote forwarding (needs sudo; see sshd_note.txt)
sudo ./setup.sh --remote-port 8080 --domain sites.example.com

# 2. Point your domain at the VPS, then start Caddy with the example file:
sudo cp Caddyfile.example /etc/caddy/Caddyfile   # edit the domain + port first
sudo systemctl reload caddy

# 3. In the Alan app: Relay screen → enter VPS host/user/credentials,
#    approve the host key, tap Connect. Open https://sites.example.com/
```

`setup.sh` installs Caddy, opens the firewall, enables `GatewayPorts` for
remote forwarding, and prints a checklist. Read it before running.

## Three public paths — when to use each

| Path | Cost | Address | When to use |
|---|---|---|---|
| **LAN** (direct) | free | `http://<phone-wifi-ip>:8080/` | Same Wi-Fi/hotspot, nothing to configure. |
| **Quick tunnel** (Cloudflare, in-app Host button) | free | random `https://*.trycloudflare.com` | Demos & quick shares; anonymous URL, changes every Host. |
| **VPS relay** (this folder) | ~$4–6/mo | your own domain, stable | Production; stable address + auto-HTTPS via Caddy. |

Use the quick tunnel when you need a public link in seconds with no
server of your own; use this VPS relay when you need a stable domain
and real auto-HTTPS.

## How it maps to the app

| Piece | Where |
|---|---|
| Relay settings + connect UI | Alan app → *VPS relay* screen (`RelayScreen`) |
| Outbound SSH + remote forward | `net/RelayManager.kt` (sshj), held by `engine/RelayService.kt` |
| Host-key approval | First connect shows the VPS fingerprint; approve once, it is stored in `filesDir/ssh/known_hosts` |
| Status + third access option | Alan app → *Public access* screen |

## Notes & limits

- TLS terminates at **Caddy on the VPS** (real auto-HTTPS). The phone itself
  still serves plain HTTP locally — by design, and stated honestly in the UI.
- VPS public ports below 1024 need the SSH user to be **root** (or extra
  capabilities); otherwise use 1024+ (e.g. 8080 behind Caddy on 443).
- `GatewayPorts yes` is required on the VPS or the forwarded port only binds
  to loopback and the world cannot reach it.
- Keepalives are enabled both ends (phone every 30s, `ClientAliveInterval`
  server-side), but mobile radios still drop idle sockets — the app
  reconnects with exponential backoff when `autoReconnect` is on.
- One relay serves the whole proxy (all sites), keyed by Host header /
  path prefix exactly like LAN access.
