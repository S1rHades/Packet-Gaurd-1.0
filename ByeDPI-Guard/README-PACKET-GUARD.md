# Packet Guard

Packet Guard is an Android DPI-bypass app built on [ByeByeDPI](https://github.com/romanvht/ByeDPIAndroid)
(GPL-3.0). It keeps upstream's native engine (ByeDPI + hev-socks5-tunnel) and service layer, and
replaces the whole interface with a single Jetpack Compose app.

## Version 2.4.0

- **Command line generator:** Benchmark step 2 now offers *Make one just for me* next to the ready-made
  profiles. It builds a ciadpi command line for your connection:
  1. a no-bypass check finds which of your sites are actually blocked (only those are searched);
  2. a first round tries each basic technique (split, disorder, OOB, disordered OOB, fake packet with TTL,
     TLS record split) at the positions that usually matter;
  3. later rounds try small variations of the best three (positions, techniques, fake TTL, adding or
     removing a TLS record split or a second step);
  4. the top three are re-tested so a lucky run can't win.

  *Quick* tests about 30 command lines (a few minutes), *Thorough* about 50. The result shows the command
  line, how many blocked sites it opens, a speed rating and two runner-ups, each one tap to apply.
  Candidates are built only from options the bundled ByeDPI 17.3 supports (`CommandSearch.kt`, unit-tested);
  `CommandGenerator.kt` runs the rounds through the existing test service.

## Version 2.3.0

- **One look everywhere:** every screen now uses the dashboard's glass design: translucent panels over soft blue
  glows (`Glass.kt`), accent-gradient buttons, pills and tabs, glass inputs and a glass navigation bar.
  Text is slightly larger throughout.
- **Simpler Benchmark:** the screen is now three numbered steps in plain words, with big touch targets:
  1. *Pick what you want to open*: link lists as large on/off tiles;
  2. *Start the test*: one big button, then a large percentage and "Trying profile 3 of 9";
  3. *Use the best one*: the winner with "Opens 11 of 12 sites", a Fast/OK/Slow speed rating and one big button.

  The results dashboard follows, and profile packs, list editing and per-site results are in a collapsed
  *Advanced options* section.

## Version 2.2.0

- **Results dashboard:** the Benchmark screen's "All results" leaderboard is replaced by a glass-style dashboard,
  shown right under Start Optimization so results are the first thing after a run:
  - headline cards for the fastest profile, sites unlocked, the most reliable profile and how many profiles work,
    each with a trend sparkline;
  - a chart comparing the 8 best profiles by latency, reach or success rate; tap a bar to see that profile's
    numbers, how many sites of each link list it opens, and apply it;
  - a link list breakdown showing how much of each list could be opened, its average latency and best profile,
    with "Most open" and "Fastest" badges.
- Dashboard numbers come from `DashboardModel.kt` and are covered by unit tests.

## Version 2.1.0

- **Signed releases:** release APKs are signed with a fixed key from repository secrets, so they update in place.
  Releases are published only from `main`, once per version. See [Release signing](#release-signing).
- **Translations:** every screen is translated into English, Russian, Turkish, Kazakh and Vietnamese.
  English is now the fallback for other phone languages (it was Russian).
- **Android 15:** targets API 35. Proxy mode now runs as a `specialUse` foreground service, because Android 15
  stops `dataSync` services after 6 hours a day and won't start them on boot.
- **Tests:** unit tests for benchmark ranking, per-domain results, link and profile parsing, command-line
  splitting, input validation and settings backup round-trips. CI runs them and lint on every push.
- **Clean-up:** removed resources, dependencies and placeholder tests that only the old View interface used,
  plus upstream's nested `.github` folder. Bug and feature templates now live in the repository's `.github`.

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
    ├── Theme / Glass / Motion / Components   palette, glass surfaces, typography, animation, shared controls
    ├── ShieldButton        shield shape + animated connect control
    ├── ConnectScreen + ConnectViewModel
    ├── BenchmarkScreen + BenchmarkViewModel + BenchModel (ranking, per-domain best)
    ├── Dashboard + DashboardModel   results dashboard (KPI cards, profile chart, list breakdown)
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

## Languages

The interface ships in English (the default), Russian, Turkish, Kazakh and Vietnamese, following the
phone's language. Compose screens read their text from `res/values*/strings_ui.xml` (keys start with
`pg_`); notifications, the tile and the diagnostic report use the remaining upstream strings in
`strings.xml`. Add a key to every locale when you add one to `values/strings_ui.xml`.

## Building and releases

`.github/workflows/build-apk.yml` runs on every push and pull request. It runs the unit tests and lint,
builds the APKs and attaches them to the run as an artifact.

A GitHub release is published only from `main`, and only once per `versionName`: bump `versionName`
and `versionCode` in `app/build.gradle.kts` to ship a new one (tag `v<versionName>`). Releases contain
`PacketGuard-v<version>-universal.apk` plus per-ABI builds.

### Release signing

Android installs an update only when it has the same signature as the installed app, so every release
must be signed with the same key. The workflow reads it from these repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore file, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

Create the keystore once and keep a backup: losing it means users have to uninstall to update.

```
keytool -genkeypair -v -keystore release.jks -alias packetguard -keyalg RSA -keysize 4096 -validity 10000
```

Without these secrets the workflow builds debug-signed APKs and does not publish a release. Builds
released before signing was set up (`v2.0.0-buildN`) are debug-signed, so moving to the first signed
release needs one uninstall; settings can be carried over with Settings → About → Export settings.

To build locally: `./gradlew testDebugUnitTest lintDebug assembleDebug`. For a signed release, set
`PG_KEYSTORE_FILE`, `PG_KEYSTORE_PASSWORD`, `PG_KEY_ALIAS` and `PG_KEY_PASSWORD`, then run
`./gradlew assembleRelease`.

## License

GPL-3.0, inherited from ByeByeDPI and ByeDPI. hev-socks5-tunnel is MIT.
