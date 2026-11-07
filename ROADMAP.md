# Roadmap

Packet Guard 3.x is feature-complete apart from changes asked for directly (3.1.0: the generator
learns from lines tested before). Releases otherwise fix bugs and polish what exists.
The ideas below are planned but **not started**. Each is a separate release, and none should
be added to a bug-fix release.

Suggested order: 1 and 3 first (they help new and older users most, and need no new
permissions), then 4 (so later work can't quietly break screens), then 2, then 5.

## 1. First-run guide that picks a setting for you

- Three short screens on first launch: what the app does, the VPN permission, then
  "Let's find what works on your network".
- The last screen runs a Quick Benchmark of YouTube and a few common sites, applies the
  recommendation and connects.
- **Skip** is always visible; the guide never shows again.
- Done when: in the usability test (`docs/usability-test.md`), task 1 is done without a hint
  by at least 4 of 5 people.

## 2. Remember what works on each network, and recover when it stops

- Keep the best strategy per network (each Wi-Fi network, each mobile operator), stored on the
  phone only, with Wi-Fi names kept as an anonymous hash.
- On a network change, switch to that network's strategy and show a message with **Undo**.
- Use the live success rate the Connect screen already measures: if it stays low, offer
  "Sites stopped opening. Find a new setting?", which runs a Quick test in one tap.
- Limit: reading the Wi-Fi name needs location permission on Android 10+, so this is off by
  default and explains why it asks.

## 3. Accessibility pass

- TalkBack labels on every icon and on the shield; announce progress changes
  ("37 percent, testing 4 of 8").
- Check every screen at 200% font size; cards wrap rather than cut text.
- All text at a contrast ratio of at least 4.5:1 (the grey captions on dark glass are the
  likely failures).
- Optional light theme.

## 4. Automated quality checks

- Committed screenshot tests (Paparazzi) for each main screen, in English and Russian, at
  normal and large font sizes; CI fails on unexpected changes.
- Emulator smoke test in CI: open the app, tap the shield, accept the VPN prompt, reach
  "Protected".
- Native-speaker review of the ru, tr, kk and vi translations (several were written without
  one).

## 5. Share a working setting with a QR code

- **Share** in Saved strategies shows a QR code and a link containing the command line itself;
  no server.
- Scanning it on another phone shows the plain-language explanation and an **Add** button.
- Import accepts only known engine options.

## Known issues (bugs to fix next, not features)

- **Generator in the background (Android 12+).** Each round of "Make one just for me" stops the
  test service and starts it again. If you leave the app during a long search, Android may
  refuse the restart and the search ends with "The test could not start". Fix: keep one test
  service in the foreground for the whole search. It changes how every test runs, so it needs
  testing on a phone first.
- **Always-on VPN at boot (Android 13 and older).** The engine waits 10 s for its settings from
  the main app; 3.0.1 gives that message priority, but a busy boot could still exceed it. Fix:
  let the engine read its settings itself as a fallback.

## Smaller ideas

- Test the combined "Use the best for each group" command line automatically after building
  it, instead of relying on the Connect screen's live numbers.
- Export Benchmark results as a shareable text report for bug reports.
