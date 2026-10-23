# Packet Guard

Packet Guard is an Android DPI-bypass app built on [ByeByeDPI](https://github.com/romanvht/ByeDPIAndroid)
(GPL-3.0). It keeps upstream's native engine (ByeDPI + hev-socks5-tunnel) and service layer, and
replaces the whole interface with a single Jetpack Compose app.

## Version 2.0.0

- **Rebrand:** the app is named Packet Guard, with application id `com.packetguard.app`, and has a new shield launcher, notification and tile icon.
  The Kotlin namespace stays `io.github.romanvht.byedpi` because the native JNI symbols are bound to it.
- **Connect:** the behavior is unchanged. The connect control is now a shield-shaped button with animated states.
- **Benchmark:** the screen now reads top to bottom as: winning profile, Start Optimization, best profile per domain,
  link lists, profiles to test, and all results.
- **Settings:** a tabbed screen (General, Strategy, Engine, Benchmark, About) that replaces both the
  old Advanced screen and upstream's preference screens ("Full settings").
- **Removed:** the classic interface and all legacy View code (activities, fragments, adapters, layouts,
  menus, preference XML) and the unused storage permissions.
- **Motion:** tab and screen transitions, staggered card entry, press feedback, animated progress,
  rolling metrics, expand/collapse for conditional settings.

## Architecture

```
app/src/main/java/io/github/romanvht/byedpi/
├── core/        native bridges (ByeDpiProxy, TProxyService) – upstream
├── services/    ByeDpiVpnService, ByeDpiProxyService, ServiceManager, TestService, QuickTile – upstream
├── data/        settings models, TestState/TestResult (latency + HTTP code added)
├── utility/     preferences, history, domain lists, site checks, settings backup – upstream
├── receiver/    boot + notification actions – upstream
├── activities/  ToggleActivity (home-screen shortcuts) – upstream
└── ui/          Packet Guard (Compose)
    ├── ModernActivity      single activity; handles "open_tab" intents
    ├── AppRoot             scaffold, bottom navigation, permissions, auto-connect
    ├── Theme / Motion / Components   palette, typography, animation helpers, shared controls
    ├── ShieldButton        shield shape + animated connect control
    ├── ConnectScreen + ConnectViewModel
    ├── BenchmarkScreen + BenchmarkViewModel + BenchModel (ranking, per-domain best)
    ├── SettingsScreen      tab host + reconnect banner
    │   ├── SettingsGeneral    mode, behavior, DNS, IPv6, per-app routing, proxy address, system
    │   ├── SettingsStrategy   command-line strategy editor + saved strategy history
    │   ├── SettingsEngine     visual desync editor (upstream "UI settings")
    │   ├── SettingsBenchmark  test parameters + custom profiles
    │   └── SettingsAbout      backup/restore/reset, diagnostics, credits
    ├── AppPicker           per-app routing selector
    └── StrategyApplier     saves + applies a strategy, restarts the service if running
```

All settings use upstream's SharedPreferences keys and value types, so the engine reads them unchanged.

## Building

Every push to `main` runs `.github/workflows/build-apk.yml`. The workflow builds debug-signed APKs and publishes
them on the repository's **Releases** page as `PacketGuard-v<version>-universal.apk` (plus per-ABI builds).

## License

GPL-3.0, inherited from ByeByeDPI and ByeDPI. hev-socks5-tunnel is MIT.
