# Usability test: "make YouTube open"

A 15-minute test to run with five people after each release that changes the interface. Five testers usually find most of an interface's problems (Nielsen and Landauer, 1993). Try to include one older person and one child. Packet Guard collects no analytics, so this test is how we find out what works.

## Before you start

- A phone with the release installed and **fresh settings** (*Settings → About → Reset*, or a clean install).
- A network where YouTube is blocked, or any site that is blocked for that tester.
- A stopwatch, and this page to write on.

Say this, word for word:

> "I'm testing the app, not you. There are no wrong answers. Please think out loud: say what you're looking at and what you expect to happen. I won't help unless you're completely stuck."

## Tasks

Give one task at a time. Write down the time taken, where they hesitated, and anything they said.

| # | Task (read it out) | Success looks like | Laws it checks |
|---|---|---|---|
| 1 | "Make YouTube open on this phone." | Taps the shield, accepts the VPN prompt, YouTube opens | Fitts, Doherty, feedback |
| 2 | "YouTube still doesn't open well. Find the best setting for it." | Opens Benchmark, keeps YouTube on, taps Start, then *Use this* | Hick, goal-gradient, visual hierarchy |
| 3 | "You changed your mind. Go back to the setting you had before." | Taps **Undo** in the message, or picks it in *Settings → Strategy* | Feedback, recognition over recall |
| 4 | "Add the site *example.com* to what gets tested." | Edits a list in *Advanced options*; the preview shows the site | Postel, progressive disclosure |
| 5 | "What does the setting you're using actually do?" | Finds the plain-language lines under the command | Tesler |
| 6 | "Is there a newer version of the app?" | *Settings → About → Updates → Check for updates* | Jakob, serial position |

## After the tasks

Ask:

1. "What was the most annoying moment?" (peak)
2. "How did you feel at the very end?" (end)
3. "On a scale of 1 to 5, how easy was it?"

## Scoring

For each task: **done alone**, **done with a hint**, or **not done**. If a task gets two or more "not done" or "hint" marks across the five testers, it is a problem to fix in the next release. Write it up as an issue with the tester's own words.

## Reference

Nielsen, J. and Landauer, T.K. (1993) 'A mathematical model of the finding of usability problems', in *Proceedings of INTERCHI '93*. New York: ACM, pp. 206–213.
