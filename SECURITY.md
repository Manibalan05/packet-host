# Security Policy

Packet Host exposes phone content to the public internet (LAN, Cloudflare quick tunnel, VPS relay). Security is critical.

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | ✅ |
| < 0.1 | ❌ please upgrade |

## Defaults

- Proxy + per-site servers bind locally; phone never opens unsolicited inbound ports.
- Quick tunnel and VPS relay are outbound-only (Cloudflare / your VPS).
- First-connect VPS host-key approval stored in `filesDir/ssh/known_hosts`.
- Explicit UI states which path (LAN / quick tunnel / relay) is live.

## Reporting

**Do not open a public issue for sensitive reports.** Open a `[SECURITY]` issue with minimal details or contact the maintainer via the repo profile. Include: app version, device/Android, repro, impact, whether public URLs/logs leak paths. Expect acknowledgment within 72h; we coordinate fix + CHANGELOG entry before disclosure.

In scope: proxy Host-header confusion, path traversal outside site root, log/URL leakage, relay host-key bypass. Out of scope: Cloudflare-side `trycloudflare.com` abuse, spam without repro.
