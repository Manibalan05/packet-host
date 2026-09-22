# Contributing to Packet Host

Thanks for helping turn Android phones into public web servers! 🌍📱

## Quick start

```bash
git clone https://github.com/Manibalan05/packet-host.git
cd packet-host
./gradlew :app:testDebugUnitTest   # 36 unit tests, must stay green
./gradlew assembleDebug
```

Android Studio Ladybug+, JDK 17, SDK 34–36.

## Good first issues

- README/FAQ copy, demo GIF, screenshots
- Reconnect edge cases (quick-tunnel drops, Doze, hotspot changes)
- Runtime binary downloaders (Node/Python/PHP to `filesDir/bin/`)
- Proxy routing + health-check polish

## Pull requests

1. Fork → feature branch (`feat/<short-name>`).
2. One feature per PR, scope tight.
3. Tests green: `./gradlew :app:testDebugUnitTest`.
4. Update `README.md` / `CHANGELOG.md` if user-facing.
5. Describe test: device + Android version + APK result.

## Bugs

Use the Bug report template: device, Android version, app version (0.1.0), steps, expected vs actual, logs (redact public URLs).
