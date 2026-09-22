# 📱 Packet Host — Your Phone Is the Server

**Host any git repo straight from your Android phone and share it with the whole internet — no VPS, no signup, no port forwarding.**

Clone a repo → tap **Run** → tap **Host** → get a free public `https://` link. That's it.

> **Turn any Android phone into a public web server with free HTTPS.** Packet Host (a.k.a. Pocket Host — the server in your pocket) is a free Android web server app (no root, no port forwarding) that hosts static sites, Node.js, Python, and PHP apps from your phone. Share over LAN with QR, get an instant `https://*.trycloudflare.com` link via Cloudflare Tunnel, or use your own VPS relay with Caddy auto-HTTPS for a stable custom domain. If you searched for *android web server no root*, *host website from android phone*, *pocket host android*, *git clone to hosting*, *cloudflare tunnel android app*, *termux alternative web hosting*, or *localhost to public URL free HTTPS* — this is it.

![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)
![Build](https://img.shields.io/badge/build-assembleDebug-brightgreen)
![Tests](https://img.shields.io/badge/tests-36%20JUnit4-blue)

## Table of Contents

- [Features](#-features)
- [Demo & Screenshots](#-demo--screenshots)
- [Quick start](#-quick-start)
- [Three public paths](#-three-public-paths)
- [Packet Host vs alternatives](#-packet-host-vs-alternatives)
- [Build from source](#️-build-from-source)
- [Architecture](#️-architecture)
- [Tech stack](#-tech-stack)
- [Project structure](#-project-structure)
- [Honest limitations](#️-honest-limitations)
- [FAQ](#-faq)
- [Contributing](#-contributing)
- [License](#-license)

## ✨ Features

| Feature | Details |
|---|---|
| 📦 **Git clone import** | Public repos, private repos (token), plus `#branch` suffix (e.g. `…/mysite#live`) |
| 🔍 **Auto-detect runtime** | Static, Node, Python, PHP (static fully works on-device today) |
| 🔀 **Reverse proxy** | One entrypoint (`:8080`) routing by Host header / path prefix — Coolify-style |
| 🌍 **Free public links** | Cloudflare quick tunnels → instant `https://*.trycloudflare.com` |
| 🖥️ **Your own VPS relay** | SSH reverse-forward + Caddy auto-HTTPS for custom domains |
| 🏠 **LAN sharing** | Same Wi-Fi/hotspot access + QR code, zero config |
| 💚 **Health checks** | Per-site `/__alan_health` polling with live status |
| 🎨 **Modern UI** | Colorful Material3, light + dark mode |

## 📸 Demo & Screenshots

> Add a 30-sec demo GIF here — repos with visuals get far more stars and Google image traffic.

```
docs/
  social-preview-1280x640.png   # GitHub → Settings → Social preview (1280×640)
  demo.gif                       # clone → Run → Host → open trycloudflare URL on laptop
  screenshots/home.png
  screenshots/detail.png
  screenshots/public-access.png
```

**Demo flow:** install APK → **+** → paste repo link → Clone → **Run** → **Host** → share the green `https://*.trycloudflare.com` link → open on another device.

## 🚀 Quick start

1. Install the APK (`app/build/outputs/apk/debug/app-debug.apk`) on your phone.
2. Tap **+** → paste a repo link → **Clone**.
3. Open the site → **Run** → **Host**.
4. Share the green public link. 🌍

> Tip: host the *built* version of a site (the folder with `index.html`), e.g. `…/myrepo#live`.

## 🌍 Three public paths

| Path | Cost | Address | When to use |
|---|---|---|---|
| **LAN** (direct) | free | `http://<phone-wifi-ip>:8080/` | Same Wi-Fi/hotspot, nothing to configure. |
| **Quick tunnel** (Cloudflare, in-app Host button) | free | random `https://*.trycloudflare.com` | Demos & quick shares; anonymous URL, changes every Host. |
| **VPS relay** (`relay/`) | ~$4–6/mo | your own domain, stable | Production; stable address + auto-HTTPS via Caddy. See [relay/README.md](relay/README.md). |

## 🆚 Packet Host vs alternatives

| | **Packet Host** | Termux + cloudflared | Ksweb / Bit Web Server |
|---|---|---|---|
| Git clone → Run → Host in one UI | ✅ | ❌ manual CLI | ❌ no git |
| No root / no port forwarding | ✅ | ✅ | ✅ localhost only |
| Free `trycloudflare.com` URL in-app | ✅ | ✅ manual | ❌ |
| Coolify-style reverse proxy (`:8080`) | ✅ | manual | ❌ |
| Own VPS relay + custom domain + auto-HTTPS | ✅ (`relay/`) | manual | ❌ |
| LAN QR + health checks + Material3 UI | ✅ | ❌ | partial |

*Keywords: termux alternative, ksweb alternative, localhost to internet android, expose localhost android free.*

## 🛠️ Build from source

```bash
./gradlew :app:testDebugUnitTest   # 36 unit tests (JUnit4)
./gradlew assembleDebug            # APK at app/build/outputs/apk/debug/
```

Requires Android SDK (platforms 34–36) — set `sdk.dir` in `local.properties`.
`minSdk 26`, `targetSdk/compileSdk 36`, Java 17, debug signing only.

## 🏗️ Architecture

```
Phone
├── ProxyServer (:8080, Ktor) ──routes by Host/path──▶ per-site servers (127.0.0.1:port)
├── TunnelService ──cloudflared quick tunnel──▶ https://*.trycloudflare.com
├── RelayService ──SSH reverse-forward──▶ your VPS ──Caddy──▶ your domain
├── Room DB (sites, status, health) + WorkManager keep-alive
└── Compose UI (Home / Import / Detail / Logs / Settings / Relay / Public access)
```

Key files: `engine/ReverseProxy.kt`, `engine/StaticServer.kt`, `engine/RuntimeManager.kt`,
`tunnel/CloudflaredManager.kt`, `net/RelayManager.kt`, `engine/HealthChecker.kt`.

## 🧰 Tech stack

- **Language:** Kotlin (Java 17) · **UI:** Jetpack Compose Material 3, Navigation Compose
- **Server:** Ktor (proxy + static) · **Data:** Room + KSP, Coroutines, WorkManager
- **Tunnel:** cloudflared (`arm64-v8a` + `armeabi-v7a`) · **Relay:** sshj + Caddy
- **Git:** JGit · **QR:** ZXing · **Tests:** 36× JUnit4 (`SiteDetector`, `ProxyRouter`, `RelayConfig`, `GitImportHelper`, `CloudflaredParser`)

## 📁 Project structure

```
app/src/main/java/com/alan/app/
  engine/   ReverseProxy, ProxyService/Manager, StaticServer(+Manager), RuntimeManager,
            SiteDetector, HealthChecker, ProcessLogger, ServerService, RelayService, TunnelService
  tunnel/   CloudflaredManager, TunnelState
  net/      RelayManager, RelayConfig, PublicAccess, TlsPolicy, NetworkUtils
  ui/       theme, navigation, screens (Home, Import, Detail, Logs, Settings, Relay, PublicAccess)
  util/     GitImportHelper, FileUtils, PortUtils, Prefs, QrGenerator
relay/      Caddyfile.example, setup.sh, sshd_note.txt, README.md (VPS relay)
```

## ⚠️ Honest limitations

- Free public links are random and change on every Host.
- The phone must stay on + connected; battery/Doze constrain uptime (foreground services mitigate).
- Node/Python/PHP need interpreter binaries at `filesDir/bin/`; static sites always work.
- No ports below 1024 without root → proxy defaults to `8080`.

## ❓ FAQ

**Packet Host or Pocket Host — which is it?**
Both. The repo is named Packet Host; many people search for it as Pocket Host (the server in your pocket). Same app — this FAQ entry exists so either spelling finds it.

**Do I need root or port forwarding?**
No. LAN uses your Wi-Fi IP, quick tunnel and VPS relay both dial *out* — nothing inbound reaches the phone.

**Is the `trycloudflare.com` URL free?**
Yes — free, anonymous, instant. It rotates every Host. Need stable? Use the VPS relay + your own domain ([relay/README.md](relay/README.md)).

**Can I host Node.js / Python / PHP from my phone?**
Static works out of the box. Node/Python/PHP auto-detect and run once interpreter binaries are at `filesDir/bin/` — see limitations above.

**How is this different from Termux?**
Termux is a full terminal — powerful but manual. Packet Host is one-tap: clone → Run → Host → QR/health in one Material3 UI, plus built-in proxy + relay.

**How do custom domains work?**
VPS relay: phone SSH reverse-forwards to your VPS, Caddy terminates real auto-HTTPS. ~$4–6/mo VPS, ~10 min setup, no third-party relay service.

**Is exposing my phone safe?**
Only what you Host becomes public. Binds are local, projects sandboxed — read [SECURITY.md](SECURITY.md) before hosting sensitive content.

## 🤝 Contributing

PRs and issues welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Run `./gradlew :app:testDebugUnitTest` before pushing. Good first issues: FAQ copy, demo GIF/screenshots, reconnect edge cases.

See [CHANGELOG.md](CHANGELOG.md) for version history.

## 📄 License

MIT — see [LICENSE](LICENSE). Bundled extras: `cloudflared` (Cloudflare, Apache-2.0-style terms) and a Mozilla CA bundle (`assets/cacert.pem`, MPL).
