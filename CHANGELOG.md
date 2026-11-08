# Changelog

Every version is built from `main` and published as a signed release on the
[Releases page](https://github.com/S1rHades/Packet-Gaurd-1.0/releases). The app can update itself
from 2.5.0 on (**Settings → About → Updates**); earlier versions are installed by hand.

The first lines under each version are what the app shows as "What's new", so keep them short.

## 3.9.2 (November 2026)

- Fixes several visual glitches found in a full review of every screen
- Tabs and chips no longer get cut off at the edge of the screen
- The comparison list shows each line's full name

The rest, in more detail:

- **Cut-off tabs and chips.** The Settings tabs, the Basic / Community / Custom chips and the desync method chips scrolled sideways and showed the last one cut off ("Benchmarl"). They now wrap onto a second line.
- **Cramped checkboxes.** In "Command lines to test" the checkbox touched the row's edge and the text. The rows are roomier now, and so are the choices in the backup dialog and the DNS server dialog.
- **Comparison list.** Names such as "Community #12" were cut to "Commun…" next to their tag. They now have a line of their own.
- **"Made for you" when it wasn't.** When no new line beat one tested before, the result was still headed "Made for you". It now says "Nothing new beat it" with the line's name.
- **Locked choices hard to read.** While a test runs, the chosen Quick / Thorough option (and other locked choices) faded to grey on blue. It now stays readable, and the two options read "Quick / about 3 min" on two even lines.
- **Smaller fixes.** "96 ms" no longer splits across lines. A start time that wasn't recorded no longer shows "about 42716748 minutes left". The update status no longer looks like a second button. The Custom profiles hint names the current section ("Command lines to test"). "Sends 1 fake packets" is reworded. The Russian speed chip starts with a capital letter.

## 3.9.1 (November 2026)

- Make one just for me now aims to beat your best line, not just match it
- The line that scores best on all your sites is always the one it builds on
- If nothing new beats it, one more round builds on it for the sites it missed
- Its line now joins every Test, scored the same way as the community lines, with a side-by-side card

The rest, in more detail:

- **One score for all.** A Test no longer erases the generator's answer. Its line is added to your own lines as "Made for you" and switched on, so every Test runs it on the same sites as the community lines and scores it on the same 0–100 scale. A new card, "Made for you vs the best here", shows its score and place next to the best line of that Test, with how many points apart they are. Switch it off under Custom to leave it out.

- **The right line to beat.** The search picked which lines tested before to build on by how they did on the blocked sites alone. A community line that scores 86 in the Benchmark, because it opens the blocked sites without breaking the rest, could lose that pick to lines that open the blocked sites but break others, so the search never built on it. Now the two lines with the best score on every site, the way the Benchmark scores them, always take part and are kept whole.
- **One more round to beat it.** If the all-sites test at the end is still won by a line known before, the search builds on that line once more, adding one or two groups (`-At,s`) for the exact sites it missed in that test. It then tests them against it on every site. The winner is only replaced if a new line actually scores higher in the same test; a line tested twice counts its average.

## 3.9.0 (November 2026)

- Make one just for me now builds on the best community lines instead of replacing them
- New: "Compared on all your sites" ranks the new lines next to community, ready-made and your own
- Its result is scored on all your sites, on the same scale as the Benchmark
- The answer is never worse than the best line tested before

The rest, in more detail:

- **Compare them yourself.** The search's last run now tests its finalists together with the best community, ready-made and own lines it learned from, on every site. The result card lists them all by score, each marked New, Community (with its number), Ready-made or Yours, with sites opened, speed, success rate and an Apply button. Because they ran together on the same sites, the scores compare directly; the winner is the one at the top.

- **Why it looked worse.** A search tests only the sites that are blocked without a bypass, and its score was out of those alone, while the Benchmark scores every site, most of which open anyway. A community line scoring 86 in the Benchmark could score 46 on the blocked sites alone, which is what the search was compared against.
- **Building on community lines.** Long community lines were simplified to a few steps before the search built on them, which threw away much of what made them work. Now the best lines tested before are kept exactly as written, and the search adds one more group (`-At,s`) aimed at the sites they still miss.
- **Scored like the Benchmark.** The finalists, and the best line known before, are re-tested on every site at the end. The result shows "Opens X of Y sites" with a score on the same scale as the Benchmark's, plus how many of the blocked sites it opened. This also catches a line that breaks a site that opened without a bypass.

## 3.8.0 (November 2026)

- Share a working setting with a QR code or a link
- Scan it with another phone's camera, or open the link, and Packet Guard shows what it does
- Add it to Saved strategies, or add it and switch it on, in one tap

The rest, in more detail:

- **Share** is in each saved strategy's menu (Settings → Strategy). It shows a QR code and **Share as text**, which sends the setting and its link through any app.
- The link (`packetguard://add?…`) holds the command line itself: there's no server, and nothing is uploaded.
- **Opening a shared setting** shows its name, the command line and what each part does, with **Add to saved** and **Add and use**. A link pasted into **Add a shared setting** (under the command line field) works the same way.
- **Only bypass options are accepted.** A shared line can't change the proxy's address or port, run it differently, or read or write files on the phone; anything else, or an option the engine doesn't know, is refused.

## 3.7.0 (November 2026)

- Packet Guard remembers which setting works on each network
- Back on a network it knows, it switches to that setting by itself, with Undo
- If sites stop opening while you're connected, it offers to find a new setting

The rest, in more detail:

- **Per network.** Whenever you switch on a setting (from the Benchmark, a search, Saved strategies or Undo), it's remembered for the network you're on. When you move to another Wi-Fi network or to mobile data while the app is open, or open it on a network it knows, it switches to that network's setting and says so, with Undo.
- **No location permission.** Android needs location permission to read Wi-Fi names, so networks are recognised another way: Wi-Fi by its router and DNS addresses, mobile data by the operator's code. Only a short anonymous code is stored, on the phone. It isn't part of exported settings, and Reset settings clears it.
- **Sites stopped opening?** When most of the Connect screen's checks fail over a minute or so, a card offers **Find a new setting**: the same quick test as the first-run guide, which switches on the best setting. **Not now** hides it until you next connect.

## 3.6.0 (November 2026)

- Connect fits large text: labels keep their room and the two numbers stack
- Every release is now checked automatically before it ships

The rest, in more detail:

- **Screenshot tests.** The Connect, Benchmark and first-run screens are rendered in English and Russian and compared with saved images on every build; a change that wasn't meant fails the build.
- **Phone test.** Every build also starts an Android emulator, opens Packet Guard, taps the shield, waits for "Protected" with the real engine and VPN, and disconnects.
- **Translations.** [docs/translations.md](docs/translations.md) marks which languages still need a native speaker's review, and how to do one.

## 3.5.0 (November 2026)

- Works better with TalkBack: switches, checkboxes, tabs and the shield say what they are and whether they're on
- Test progress and connection changes are read out as they happen
- Larger text no longer gets cut off on buttons and results
- Easier-to-read colours: grey text, red warnings and blue buttons all meet the WCAG contrast level

The rest, in more detail:

- **TalkBack.** Each switch, checkbox and radio row is one item with its state ("Autostart, switch, on"), not a row plus an unnamed control. Site tiles and command lines say whether they're selected; pills and Quick/Thorough say which is chosen; Advanced options, All results and Command lines to test say whether they're open; headings can be jumped between. The shield says the action and the state ("Connect, Disconnected"). The bottom tabs are no longer read twice.
- **Announcements.** "Protected", "Connecting" and failures on Connect, and "Trying profile 4 of 8" or the search's round, are announced when they change; the ticking percentage isn't, but progress bars report their value.
- **Large text (up to 200%).** Buttons grow instead of clipping their label, score labels widen, names in results wrap to two lines, and small badges are 12 sp.
- **Contrast.** Secondary text is slightly lighter, red is lighter so it reads on cards, the blue button gradient is deeper so white text on it passes 4.5:1, and unchecked boxes have a visible border.

## 3.4.0 (November 2026)

- New: a short guide the first time you open Packet Guard
- It explains the app, asks for the VPN permission, then finds a setting for you
- "Find it for me" runs a quick test, switches on the best setting and connects
- Skip is always there, and the guide never comes back

The rest, in more detail:

- **Three screens.** "Open blocked sites" (what the app does, with no server and no account), "Allow the VPN" (Android's one-time question, explained before it appears) and "Let's find what works on your network".
- **Find it for me** tests the basic profiles on the switched-on lists (YouTube and common sites on a new install), switches on the recommended setting with Undo, and connects. If nothing works, it suggests Make one just for me.
- The notification and battery questions now come after the guide, not over it.
- Anyone who used Packet Guard before this version doesn't see the guide.

## 3.3.2 (November 2026)

- Make one just for me keeps going when you leave the app during a long search
- Always-on VPN no longer gives up when the phone is slow to start
- Stop in the search's notification now stops the whole search

The rest, in more detail:

- **Searches in the background.** Each round used to stop the test service and start it again. Android 12 and later can refuse that start while the app is in the background, which ended the search with "The test could not start". The service now stays in the foreground for the whole search and runs each round itself.
- **Always-on VPN at boot.** When Android starts the VPN by itself, the engine now waits up to 45 seconds (was 10) for the app to send its settings, since at boot that message can queue behind every other app's.

## 3.3.1 (November 2026)

- Settings → About now starts by saying where Packet Guard comes from
- Packet Guard is a modified version of ByeByeDPI, which runs the ByeDPI engine
- The ByeByeDPI link in the credits points to the project's current page

The rest, in more detail:

- The same statement opens the README and the store description: all the bypass technology comes from ByeDPI and ByeByeDPI, and Packet Guard's aim is to make it easier to understand and use.

## 3.3.0 (November 2026)

- Benchmark is now two cards: the answer first, then the test
- Recommended for you also holds the other goals, site groups and the combined line
- The test card holds the sites, the command lines it tests and the Test button
- Make one just for me sits under the Test button, in the same card

The rest, in more detail:

- **Recommended for you** keeps the setting, its score, the reason and **Use this setting** at the top. Under them, in the same card: **Care about something else?**, **By site group**, and **Use the best for each group** when it opens more. While a test runs, only the best so far is shown.
- **The test card**: **Sites to open** (the list tiles), **Command lines to test** with how many are selected per pack and **Choose** to pick them (moved here from Advanced options), the **Test** button (**Test again** after a test) with its progress, and then **Make one just for me** with Quick or Thorough and its own button and progress.
- The two-way "ready-made or make one" choice and the folded "Tested … · Test again" line are gone: each kind of test has its own button. The answer shown is from whichever ran last.
- While a test runs and its card is scrolled away, **Stop** stays pinned at the bottom.
- **All results** follows the test card; **Advanced options** keeps editing site lists and the best setting per site.

## 3.2.0 (November 2026)

- Notifications now look like the app: the shield in its blue, the same words and buttons
- On Android 16, protection and tests show as Live Updates with a status bar chip
- While protected, the notification shows how long for and which setting is in use
- Tests and searches show one progress bar with the percentage, round and Stop

The rest, in more detail:

- **Connection.** "Protected", where traffic goes (your apps, or the proxy's address), the setting in use (its saved name, or the command line), a running timer since it started, and **Pause** and **Disconnect**. Tapping it opens Connect.
- **Paused.** "Paused" with **Resume**, and a reminder that sites may be blocked again.
- **Benchmark and Make one just for me.** A progress bar with the percentage (also in the status bar chip on Android 16), "Setting 4 of 12" or "Round 2 of 3", and **Stop**. A search shows its whole progress rather than restarting for each round. Tapping it opens Benchmark.
- **Live Updates** need Android 16 and can be turned off for Packet Guard in the system's notification settings; older versions show the same notifications without the chip.

## 3.1.0 (November 2026)

- "Make one just for me" now learns from every line already tested on your phone
- The best earlier lines, community ones included, are tested again and built on
- New lines combine the two or three lines that open the most sites between them
- The result says whether it beat the best line you'd tested before, and by how much

The rest, in more detail:

- **Memory of tested lines.** Every finished Benchmark and search adds each command line's results, site by site, to a memory on the phone (up to 300 lines, recent results weighted more). It's never sent anywhere, isn't included in exported settings, and **Reset settings** clears it.
- **Starting from it.** A search reads any line it knows (even long community lines with several groups) into the ingredients it builds with, credits those ingredients for the sites they opened, and tests the three best earlier lines for your blocked sites in round 1, as written. Older results lose half their weight every two weeks.
- **Cover.** Each round also joins the two or three lines that between them open the most blocked sites into one line, with a fallback group where needed, instead of merging two at a time over several rounds.
- In the simulated network used by the tests, a Quick search with earlier results to learn from opened every blocked site in 20 of 20 runs, against 9 of 20 starting from nothing.

## 3.0.1 (November 2026)

- Fixes that could lose your own site lists or crash the app after importing settings
- Auto-start now works when you unlock the phone long after it restarted
- An update you started now shows Android's install screen when you come back to the app
- Custom DNS and IP addresses are checked properly on Android 5 to 9

The rest, in more detail. This is a stability release with no new features:

- **Site lists.** Opening the app while connected could make two parts of the app rewrite the lists file at once and leave only the built-in lists. The file is now written safely and one change at a time, and a file that can't be read is never replaced.
- **Importing settings.** A hand-edited backup, or one from another version, could store a value of the wrong type (a number where text was expected, "true" as text) and crash the app at every start until its data was cleared. Each value is now checked and anything unexpected is skipped. Broken site lists and saved commands in a backup are repaired or dropped.
- **Only selected apps** with no app selected (or only uninstalled ones) no longer sends Packet Guard's own traffic into its tunnel.
- **Updates.** If you left the app while an update downloaded, Android blocked the install screen and the update stayed on "Installing". It now opens when you return.
- **Auto-start.** On Android 10 and later the start-up signal only arrives after the first unlock, so a phone unlocked more than 5 minutes after restarting didn't start protection. It now starts once per boot, whenever that is.
- **Command line generator.** Its last result is saved in a form future versions can read. Results saved by 3.0.0 can't be read back and are cleared once.
- **Settings → About → Export** no longer says "exported" when it failed.
- Smaller fixes: the test notification no longer changes where the VPN notification opens; the VPN start signal gets priority at boot; Android 15's background time limit for tests is handled; phones without a VPN consent screen show a message instead of crashing; other apps can't change the proxy's address or port through the shortcut activity; and an error message that was only in English is translated.
- Store listing, screenshots and a privacy policy ([PRIVACY.md](PRIVACY.md)) now describe Packet Guard itself.

## 3.0.0 (November 2026)

- Benchmark results now give one recommendation, with a reason and one button
- Every command line gets a score out of 100 for sites, success and speed
- Choose the fastest or most reliable instead and see what you gain and give up
- "Use the best for each group" combines settings that suit different lists

The rest, in more detail:

- **Scores.** Each command line is scored out of 100: sites opened 50%, successful requests 30%, speed 20% (full marks at 100 ms or less, none at 1.5 s). Bars show the three parts. The generator's results use the same score.
- **Recommended for you.** The best score among the settings no other one beats on sites, success and speed all at once, with a one-line reason.
- **Care about something else?** Fastest, Most reliable and one choice per site list, each compared with the recommendation in green (gains) and red (losses), with its own button.
- **Use the best for each group.** When lists prefer different settings, one command line applies each to its own sites (`-H … -An`). It's built from the results and offered with Undo.
- **Results first.** After a test the setup folds into "Tested … · Test again". The Step 1, 2 and 3 headings are gone.
- The old dashboard (headline cards, bar chart and list table) is replaced by these cards and an **All results** list sorted by score.

## 2.9.0 (November 2026)

- "Make one just for me" now learns from the test and builds new command lines
- It combines what opened different sites, and adds fallbacks for the rest
- The result says whether it is new and how it was made
- The search keeps learning for longer before it settles

The rest, in more detail:

- **Probes, then learning.** Round 1 is a set of probes. Every result is now kept site by site, and each ingredient (technique, position, fake TTL, TLS record split, fake packet type) is credited with the sites it opened.
- **New lines each round.** Targeted additions for the sites still missing (including untried ideas such as a new TTL range), combinations of lines that open different sites, fallbacks with `-At,s`, and tuning with fresh positions and TTLs instead of a fixed menu. Every line is new and uses only options the engine accepts.
- **No early exit on a probe.** Even if a basic technique works at once, the search runs at least two rounds of new lines.
- **Honest results.** A "New · built from your test" badge and a line on how it was made, or, when a probe was still best, how many new lines were tried.
- In a simulated network that no probe could fully get past, the Thorough search built a line opening every site in 19 of 20 runs (unit test).

## 2.8.0 (November 2026)

- Colours mean the same everywhere: green works, amber partly, red failed, blue is best
- Back goes to the Connect screen instead of closing the app
- Pull down on the Connect screen to check the connection now
- YouTube and the other popular site lists come first in the Benchmark

The rest, in more detail:

- **One meaning per colour.** The winner in step 3 and the generator's result use the accent blue rather than amber. "Nothing worked" is red. The dashboard's headline cards no longer use status colours as decoration, so only the fastest profile carries the accent.
- **No false buttons.** The small arrow on the dashboard's cards suggested they could be tapped; it's gone.
- **Back works the Android way.** From Benchmark or Settings it returns to Connect; inside Settings it returns to General first.
- **List order.** Built-in lists were shown alphabetically, which put YouTube last. Now the most used come first (Türkiye first in Turkish), and your own lists come last.
- **A usability test script** for five people, in [docs/usability-test.md](docs/usability-test.md).

## 2.7.0 (November 2026)

- Every command line is explained in plain words, one line per option
- Strategy and Engine are now one tab: type a command line, or use switches
- Expert settings stay hidden until you turn on "Show advanced settings"
- Bigger buttons, a recommended choice in the Benchmark, and shorter charts

The rest, in more detail:

- **Explained command lines.** Under the active strategy, the generator's result and the Benchmark's winner, each option gets a sentence, such as `-d1`: "Splits the first packet at position 1 and sends the first part out of order".
- **Four Settings tabs instead of five.** Strategy has a **Type it / Use switches** choice; saved strategies show their last test result next to their name.
- **Show advanced settings** (Settings → General, off at first) reveals the proxy address, engine connection limits and buffers, and the Benchmark's request counts and timeouts.
- **Benchmark.** Ready-made profiles are marked **Recommended**, and the Start button stays pinned at the bottom of the screen while step 2 is out of view.
- **Results dashboard.** The chart shows the top 5 profiles (tap **Show all** for up to 12), and link lists start with the 3 weakest.
- **Bigger touch targets.** Tabs, segment buttons and the update banner's **Later** are at least 48 dp tall.

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
