# 📱 Packet Host — Your Phone Is the Server

**Host any git repo straight from your Android phone and share it with the whole internet — no VPS, no signup, no port forwarding.**

Clone a repo → tap **Run** → tap **Host** → get a free public `https://` link. That's it.

![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)
[![Download APK](https://img.shields.io/badge/Download-APK-green?style=for-the-badge&logo=android)](https://github.com/Manibalan05/packet-host/releases/latest/download/packet-host.apk)

### ⬇️ [Download APK — tap on your phone](https://github.com/Manibalan05/packet-host/releases/latest/download/packet-host.apk)

> `Releases → Latest → packet-host.apk` → tap file → Allow `Install unknown apps` → Install. To update, download again.

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

## 🚀 Quick start

1. Install the APK from **Releases** ([direct link](https://github.com/Manibalan05/packet-host/releases/latest/download/packet-host.apk)) on your phone.
2. Tap **+** → paste a repo link → **Clone**.
3. Open the site → **Run** → **Host**.
4. Share the green public link. 🌍

> Tip: host the *built* version of a site (the folder with `index.html`), e.g. `…/myrepo#live`.

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

## ⚠️ Honest limitations

- Free public links are random and change on every Host.
- The phone must stay on + connected; battery/Doze constrain uptime (foreground services mitigate).
- Node/Python/PHP need interpreter binaries at `filesDir/bin/`; static sites always work.
- No ports below 1024 without root → proxy defaults to `8080`.

## 📄 License

MIT — see [LICENSE](LICENSE). Bundled extras: `cloudflared` (Cloudflare, Apache-2.0-style terms) and a Mozilla CA bundle (`assets/cacert.pem`, MPL).
