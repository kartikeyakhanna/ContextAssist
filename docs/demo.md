# Demo

One continuous story, one persona, one task. **Not a feature tour.**

Ruthless rule: if a beat does not advance Maya's task, cut it.

---

## Setup

Four APKs, all built from this repo:

| App | Package | Role |
|---|---|---|
| **Thread** | `com.thread.app` | the accessibility service and overlay |
| **Expense Portal** | `com.thread.demo` | the app under observation |
| **Finance Portal** | `com.thread.lookup` | the other app, for the pin beat |
| **Document Editor Demo** | `com.thread.worddemo` | Word-like editor with exposed document text |

```powershell
.\.tooling\gradle-9.1.0\bin\gradle.bat assembleDebug

$adb = ".\.tooling\android-sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb install -r demo\build\outputs\apk\debug\demo-debug.apk
& $adb install -r lookup\build\outputs\apk\debug\lookup-debug.apk
& $adb install -r worddemo\build\outputs\apk\debug\worddemo-debug.apk
```

Then **Settings → Accessibility → Thread → On**.

Do this on stage if there is time. The OS enforcing the opt-in — Thread cannot
enable itself — makes the "declared, not diagnosed" principle something the
audience watches happen rather than something they are told.

Have Teams (or any messaging app) installed for the interruption. Turn off
notifications for everything else.

Expense Portal resets its state each time it is launched from the home screen, so
the demo is repeatable without uninstalling anything.

### Word-like document context demo

Open **Document Editor Demo**. It contains a realistic proposal and mobile
document controls for Back, Save status, Undo, Redo, Find, Share, Cut, Copy,
Paste, Select All, Bold, Italic, Underline, Highlight, Font Color, New Comment,
and Home/Insert/Draw/Layout/Review/View tabs.

Long-press in the document and select the final paragraph, then tap Thread and
enter:

> `@breakdown Finish this proposal with a measurable example`

Unlike Word for Android, the demo exposes the editable document body through the
accessibility tree. Thread sends the selected paragraph as primary context, the
complete bounded document as supporting context, and visible toolbar labels last.
No document attachment or URL is required.

---

## Run sheet (~3 minutes)

**0:00 — Maya.** One slide, one sentence.

> Maya, 34. Returned to work after treatment. Cognitive fatigue — her attention
> holds for six minutes, not sixteen.

No bullet points. Move on fast.

**0:20 — Attempt 1, Thread off.** Maya starts an expense claim on her phone. A
Teams ping arrives. She switches, handles it, comes back.

> **Let this sit. Do not narrate.** She scrolls up. Scrolls down. Re-reads what she
> typed. Opens a field, closes it. Abandons.

On screen: `Attempt 1 — abandoned. 2 interruptions.`

The silence does the work here. The instinct to talk over it is wrong.

**1:00 — Attempt 2, Thread on.** Same task, same interruption. She returns. The dot
is there where it has been all along; she touches it.

> Q3 travel claim · Done: dates, amount · You chose: Delhi cost centre ·
> Next: attach receipt

She continues.

One line, if anyone is counting the taps:

> "It waited to be asked. It has a threshold for speaking first, and this wasn't
> bad enough to cross it."

**1:40 — The pin.** Mid-task she leaves to the Finance Portal to fetch the budget
code. Comes back. Leaves again. On the third time, the chip appears: `BX-4417`.

One line only:

> "On a phone you can't see two things at once. So we hold it for her."

Then stop talking.

**2:10 — Real Excel.** Same phone, stock Excel from the Play Store, zero
integration. App switch, return, resumption card.

> "No changes to Excel. This works on anything on the device."

**2:40 — Numbers, then the human line.**

`Completed: 0/3 → 3/3` · `Time-to-resume: 34s → 4s`

Then one sentence from a co-designer with lived experience.
**End on the human line, not the metric.**

## The close

> "Maya isn't less capable. She just can't afford to rebuild context eleven times
> a day. We gave her back the thread — and we did it without changing a single
> line of Excel."

## Word breakdown scenario

Maya is finishing a six-page return-to-work proposal in Microsoft Word. Her
manager has left comments throughout the document and asked her to strengthen the
selected paragraph with evidence before sending it at the end of the day. The
request is important but open-ended: review comments, find supporting numbers,
rewrite the paragraph, check formatting, proofread, and send.

She selects the paragraph she is currently stuck on, opens Thread, and enters:

> `@breakdown Finish this proposal for my manager`

Thread sends the active app, visible Word context, and selected paragraph with her
request. A representative result is:

| Step | Estimate |
|---|---:|
| Read the manager's comments once without editing | 3 min |
| List the evidence missing from the selected paragraph | 4 min |
| Add one concrete result or number | 6 min |
| Rewrite the selected paragraph in plain language | 8 min |
| Check headings and document formatting | 4 min |
| Read the final section aloud and fix errors | 5 min |
| Save and share the document | 2 min |

Each estimate appears as a countdown beside its step. Maya starts the first timer,
pauses it when interrupted, and resumes without recalculating where she was. Only
one timer runs at a time, so the aid creates one current focus rather than another
set of competing demands.

This demonstrates the neurodiversity value clearly: an ambiguous, emotionally
heavy task becomes a finite sequence with visible effort, a starting point, and
permission to work one bounded interval at a time.

---

## Deliberately not demoed

Built, but held for Q&A: sequencing mode, default hints, error explanation, the
weights tuning panel, the complexity report.

"We also built X," answered on demand, is far stronger than a feature tour that
dilutes the story.

## Instrumentation stays off the user's screen

Scores, timelines and the completed-vs-abandoned scoreboard go on a **second
screen** — laptop mirroring or a separate device.

The user sees a dot, a card, and a pin. That is it. Resisting the urge to put a
load score in front of the user is itself part of the pitch: *we measure a lot, and
we show almost none of it.*

---

## Q&A: the four questions that will come

**"Why not just open Copilot and ask where you were?"**

Copilot sees the *document*. It has no record of your intent, your progress, your
decisions, or the fact that you left — that data does not exist anywhere today.

More importantly, "just ask" **is** the barrier. To ask, you must remember the
feature exists, decide to invoke it, and formulate the question — which requires
already knowing what you were doing. That is circular. And those steps are task
initiation, precisely the executive function that is impaired here.

Do not position against Copilot. Position underneath it:

> "Copilot can answer anything you ask. This is the session memory it doesn't have,
> and the trigger that means you don't have to ask."

**"How do you know the score is right?"**

We don't claim it is. The weights are a calibratable prior anchored to Hick's Law
and Miller's 7±2, they live in a config file, and they can be retuned live. What we
report is not score accuracy but **offer precision** — accepted ÷ shown — plus
completion rate under interruption. See `docs/validation.md`.

**"Isn't this just good UX for everyone?"**

Yes. Curb cuts. But it is an accommodation first and universal second, and that
ordering matters: it is designed with people for whom interruption is disabling,
not retrofitted to them.

**"Complex for whom?"**

Screen Memory Load is a population-level prior — a heavy screen is trivial to a
daily expert. That is exactly why it is only ever a multiplier on observed
behaviour, never a trigger on its own.

**"Did the card appear on its own, or did she tap for it?"**

Ask this one yourself before a judge does, because the answer is *both, depending
on how bad it got* — and that is the point.

Offers are pushed at CLS ≥ 60. Measured on device, same task, same heavy screen:

| Interruption | Away | CLS |
|---|---|---|
| 1st | 40s | 39 |
| 2nd | 100s | 52 |
| 3rd | 150s | 58 |
| 4th | 180s | **62 — card offered unprompted** |

So one bad moment gets a dot and nothing else. Four, across eight minutes, earns
an interruption. In the run sheet above Maya is at her first, which is why she
taps — and the honest line is:

> "It waited to be asked, because one interruption isn't enough to justify
> interrupting her back. Ask me what happens on the fourth."

No weight was changed to make this fire. The third interruption sat at 58, two
points under, and we kept interrupting rather than moving the line.

---

## What has actually been seen running

On an API 35 emulator, end to end:

- the dot appears when `startTask` arrives from the SDK
- leaving to the Finance Portal logs `away -> com.thread.lookup`
- returning 40 seconds later resolves `Step2Allocation` → SML 83.1 from cache and
  produces a full CLS factor breakdown
- tapping the dot draws the resumption card with *Done: Entered Destination,
  Purpose* and *You chose: Destination: Delhi*
- after a fourth interruption CLS reaches 62 and the same card is offered
  **without being asked for** (`selected=RESUMPTION`)

Screenshots of both paths: `docs/images/resumption-card.png` (pulled) and
`docs/images/pushed-card.png` (pushed).

Three failure modes worth knowing on stage:

- **Reinstalling the Thread APK unbinds the service** and drops task state. Re-enable
  it and restart the scenario; do not try to rescue a half-run.
- **Launching Expense Portal from the launcher icon resets the demo** —
  `Step1Details.onCreate` calls `DemoState.reset()`. Deliberate, for repeatability,
  but it means you cannot use the icon to get back after the interruption. Use the
  back gesture, the way a person actually would.
- **Never run `uiautomator dump` during a live scenario.** It connects its own
  UiAutomation and the platform stops delivering events to Thread — silently. The
  service stays bound, the dot stays on screen, and nothing is recorded. This cost
  an hour of confusion during testing. Collect any coordinates you need *before*
  starting the task.

---

## Before you build the Excel beat

Run the day-zero spike: `NodeTreeProbe` dumps the accessibility tree. Two hours
with it tells you what Excel actually exposes rather than finding out on day two.

Priority order:
1. Do window-state changes report the Excel package reliably? *(If yes, the
   resumption beat is safe regardless of everything below.)*
2. Is cell or formula-bar text readable as node text? *(Decides the pin beat.)*
3. Are there stable view IDs to fingerprint a screen with?

Excel renders its grid on a custom surface, so expect a sparse tree there and a
richer one in dialogs and the ribbon.
