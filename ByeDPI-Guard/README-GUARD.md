# ByeDPI Guard

A fork of [ByeByeDPI](https://github.com/romanvht/ByeDPIAndroid) (GPL-3.0) with a new Jetpack Compose front-end and a
link-list benchmarker. The engine is unchanged: ByeDPI as a local SOCKS5 proxy, hev-socks5-tunnel as the VPN
tun2socks layer, and upstream's service manager for start/stop/restart.

## What is new

| Area | Change |
|---|---|
| UI | Compose app, launcher is `ui/ModernActivity`: Connect, Benchmark, Advanced tabs. Dark navy `#0F172A`, blue `#1E40AF` accent, animated power button. Upstream's screens stay reachable under Advanced. |
| Benchmark | Reuses upstream's `TestService`. Link lists (YouTube, Google video, Social, Telegram, Discord, Cloudflare, Streaming, News, ...) are toggled with checkboxes; add, edit, import and delete your own. Profile packs: Basic (9 named), Community (upstream's 60), Custom (your own). Live ranking, best profile per domain, "Apply Winning Profile". |
| Latency | `SiteCheckUtils` now records time-to-headers and HTTP status per site (`SiteResult.latencyMs`, `httpCode`). |
| Defaults | YouTube, Google video and Social lists are active on first run. |
| Identity | `applicationId` is `io.github.romanvht.byedpi.guard`, so it installs next to the original app. |

## Getting the APK (no Android Studio needed)

1. Create an empty repository on GitHub.
2. From this folder: `git init && git add . && git commit -m "ByeDPI Guard" && git branch -M main`
   `git remote add origin <your repo URL> && git push -u origin main`
3. Open the repository's **Actions** tab, then the latest **Build APK** run. When it finishes (about 10 to 15 minutes), download
   the `ByeDPI-Guard-apk` artifact. `app-universal-debug.apk` works on any phone.

Local build: Android Studio (or JDK 17 plus the Android SDK, NDK 27 and CMake 3.22.1), then `./gradlew assembleDebug`.

## Notes

- A benchmark pauses the bypass, because only one native engine runs at a time (same as upstream's test screen).
- A site counts as reachable when its full response body arrives, as in upstream. HTTP status is displayed next to latency.
- The Compose UI strings are English only. Upstream's classic screens keep their translations.
- GPL-3.0: if you distribute the APK, you must also offer this source.
