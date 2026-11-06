# Changelog

Every version is built from `main` and published as a signed release on the
[Releases page](https://github.com/S1rHades/Packet-Gaurd-1.0/releases). The app can update itself
from 2.5.0 on (**Settings → About → Updates**); earlier versions are installed by hand.

The first lines under each version are what the app shows as "What's new", so keep them short.

## 2.6.0 (November 2026)

- Undo: after you use a profile, one tap puts the old setting back
- Pasted links and command lines are tidied up, and you see what will be saved
- When a test finds nothing, one button starts a search made just for you
- "About 3 minutes left" while testing, and a "What's new" card after updating

The rest, in more detail:

- **Undo.** Every "Applied" message now has an **Undo** button, from the Benchmark, the command line generator and Settings → Strategy.
- **Forgiving input.** Site lists accept full links, `www.`, ports, `*.` wildcards, quotes and commas, and show how many sites will be tested. Command lines lose a leading `ciadpi`, curly quotes and the long dashes phone keyboards put in place of `--`; Settings shows "Will be saved as" first.
- **Better endings.** A Benchmark that opens nothing offers **Make one just for me**; a quick search that finds nothing offers **Search longer**. After an update, the Connect screen shows the new version's notes once.
- **Clearer progress.** The generator shows its stages as a row of bars (check, each round, re-test), and both tests estimate the minutes left.
- "Use this" changes to "In use" straight away.

## 2.5.1 (November 2026)

- A new README with a tour of the app and screenshots
- A download link that always gets the newest version
- The changelog now has its own page

No changes to the app itself; this release also tries out in-app updates from 2.5.0.

## 2.5.0 (November 2026)

- In-app updates: Packet Guard finds, downloads and installs new versions itself
- A banner on the Connect screen when an update is out, with one Update button
- Settings → About → Updates shows your version, what's new, and an automatic-check switch

Details: the app reads this repository's latest GitHub release (`update/Updater.kt`), downloads its
`PacketGuard-v<version>-universal.apk` and hands it to Android's package installer, which asks you to
confirm; the system install screen is the fallback. Releases are signed with the same key, so updates
install in place and keep your settings. It checks shortly after the app opens and every 6 hours while
it's open, unless the switch is off. CI now copies each version's section of this changelog into its
release description, which is where "What's new" comes from.

## 2.4.0 (November 2026)

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

## 2.3.0 (November 2026)

- **One look everywhere:** every screen now uses the dashboard's glass design: translucent panels over soft blue
  glows (`Glass.kt`), accent-gradient buttons, pills and tabs, glass inputs and a glass navigation bar.
  Text is slightly larger throughout.
- **Simpler Benchmark:** the screen is now three numbered steps in plain words, with big touch targets:
  1. *Pick what you want to open*: link lists as large on/off tiles;
  2. *Start the test*: one big button, then a large percentage and "Trying profile 3 of 9";
  3. *Use the best one*: the winner with "Opens 11 of 12 sites", a Fast/OK/Slow speed rating and one big button.

  The results dashboard follows, and profile packs, list editing and per-site results are in a collapsed
  *Advanced options* section.

## 2.2.0 (November 2026)

- **Results dashboard:** the Benchmark screen's "All results" leaderboard is replaced by a glass-style dashboard,
  shown right under Start Optimization so results are the first thing after a run:
  - headline cards for the fastest profile, sites unlocked, the most reliable profile and how many profiles work,
    each with a trend sparkline;
  - a chart comparing the 8 best profiles by latency, reach or success rate; tap a bar to see that profile's
    numbers, how many sites of each link list it opens, and apply it;
  - a link list breakdown showing how much of each list could be opened, its average latency and best profile,
    with "Most open" and "Fastest" badges.
- Dashboard numbers come from `DashboardModel.kt` and are covered by unit tests.

## 2.1.0 (November 2026)

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

## 2.0.0 (October 2026)

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
