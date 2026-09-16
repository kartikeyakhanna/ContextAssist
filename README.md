# Thread

**Keeps your place, so you don't have to.**

An opt-in accommodation layer that preserves and restores task context across
interruptions. When you are interrupted mid-task, Thread hands back what you were
doing, what you had decided, and what comes next — so you don't have to rebuild it
from scratch.

It does not diagnose anyone, does not change the app you are using, and does not
send anything anywhere.

---

## The problem

You're mid-task. Someone pings you. Four minutes later you come back, and the
screen is exactly as you left it — with zero help. Same twelve fields, half filled,
no indication of what you were doing or what's next.

For most people that's mildly annoying. For someone with ADHD, cognitive fatigue,
MS, a TBI or post-concussion symptoms, that is *the* disabling moment. The task
isn't hard. It's that every interruption costs the whole task, so they abandon —
not from confusion, but from having to rebuild the entire context, again.

There is no "where was I?" button in any enterprise app.

## Who it is for

**Primary** — people for whom interruption is disabling: ADHD, cognitive fatigue
(long COVID, MS, post-treatment), TBI, anxiety-driven task freeze, early cognitive
decline.

**Secondary** — anyone in a high-interruption job. The curb cut.

---

## What it does

| Scenario | Detection | What the user sees |
|---|---|---|
| Interrupted mid-task | Context Loss Score | **Resumption card** — intent, done, decided, next |
| Keeps leaving to fetch a value | Orbit | **Pin chip** — the value, held for them |
| Same error again and again | Orbit | **Error explanation** — the error, not the screen |
| Can't start a heavy form | Initiation | **Sequencing mode** — one section at a time |
| Can't choose between options | Freeze | **Default hint** — consequence + reversibility |
| Frozen at an irreversible action | Hesitation | **Reassurance** — what happens, and the undo window |

One offer at a time. Never stacked. Never auto-dismissed. Always dismissible for good.

**Context is held per app, not one app at a time.** A phone is not used one task at
a time: you are mid-claim, you check a budget code, you search for something, you
answer a message. Each of those is a separate thread to lose, so each gets its own
session. Five apps are held at once — enough to cover a realistic interruption
chain, and small enough that the answer to *"what are you keeping?"* stays short.

**Some apps are watched but never read.** Interruption tracking needs only a
package name and betrays nothing, so it runs everywhere. Reading screen contents is
what makes a memory aid into a record of somebody's bank balance or their
diagnosis, so banking, health, messaging and identity apps are content-gated by
`SensitiveApps`. Thread will note that you stepped away to your banking app; it
will not note what it said.

---

## Two principles that shape everything

**1. It is declared, not diagnosed.**

Screen readers do not detect blindness; captions do not detect deafness. Real
assistive technology is opted into. Thread never infers that somebody has a
cognitive disability — the user switches support on, and behavioural signals only
decide *when* help appears, never *whether* the person needs it.

On Android this is enforced by the platform: the user must enable Thread in
*Settings → Accessibility* themselves. The app cannot do it for them.

**2. Pull first, push second.**

Tapping the dot always works — no threshold, no cooldown, no suppression. Detection
is an assist, not a gatekeeper. Detection will sometimes be wrong, and for someone
already depleted a mistimed interruption is the exact harm Thread exists to prevent.

This is also why it degrades well: one undifferentiated tap requires no recall, no
phrasing, and no deciding what to ask for.

---

## Architecture

```
Android AccessibilityService  (Tier 1: any app, zero integration)
        │ events
        ▼
  TaskStateBuilder ──────► TaskState        in memory, never persisted
        │
        ▼
   Scores:  CLS · Orbit · Freeze · Initiation       (× SML, cached design-time)
        │
        ▼
   Triggers ──► candidates ──► Arbiter ──► at most one Offer
        │
        ▼
  Overlay  (non-focusable — never takes input from the app beneath)
```

**Everything runs on device.** The engine is a pure-Kotlin module with no network
dependency, and there is **no database anywhere in this repository** — the privacy
claim is meant to be verifiable by reading the code, not taken on trust.

### Why the accessibility layer, not an Office add-in

Office Add-ins (Office.js) do not run on Excel for Android, so there is no in-app
surface to inject into. `AccessibilityService` turns that constraint into the
stronger position: Thread works on Excel, Teams, Outlook and everything else
**without any of them changing a line of code**.

Interruption and return detection needs only the package name from a window-state
change — no node tree at all. That is why the hero scenario is also the lowest-risk
one, and why it is unaffected by Excel rendering its grid on a custom surface.

### Two tiers, same engine

| Tier | Integration | Fidelity | Coverage |
|---|---|---|---|
| **1 — Accessibility layer** | none | inferred | every app on the device |
| **2 — SDK** | app emits ~8 events | semantic | apps that opt in |

---

## Repository layout

```
engine/     pure Kotlin/JVM. All scoring. No Android, no network, no deps. Unit tested.
app/        Android: AccessibilityService (collector) + Compose overlay (UI).
sdk/        Tier 2. Six reporting calls an app can make. No keys, nothing returned.
demo/       "Expense Portal" — the app under observation in the demo.
lookup/     "Finance Portal" — the other app, so the pin scenario is real.
config/     weights.json  — every weight, tunable live
            complexity-cache.json — design-time Screen Memory Load per screen
tools/      complexity-agent — offline screen scorer + ranked report
study/      validation protocol and NASA-TLX instrument
docs/       privacy model, demo script, validation protocol
```

`demo/` and `lookup/` are two separate applications on purpose. The pin scenario
depends on the user *genuinely* leaving the first app — a different package is
what the accessibility service actually sees, and the failure being demonstrated
is specific to a phone: there is no second window, so a value read elsewhere is
gone by the time you are back.

## Build

The Android SDK for this project lives inside the repo at `.tooling/android-sdk`
and nothing was installed system-wide.

```powershell
# Engine only - no Android SDK required
.\.tooling\gradle-9.1.0\bin\gradle.bat :engine:test

# All four Android modules
.\.tooling\gradle-9.1.0\bin\gradle.bat assembleDebug
```

`settings.gradle.kts` only includes the Android modules when an SDK is present, so
the engine stays testable on any machine.

**Current state:** engine **39/39 tests passing**; all four Android modules compile
and produce debug APKs, and the whole flow has been run on an API 35 emulator —
see *Known gaps* below for what that run did and did not prove.

## Run the demo

```powershell
$adb = ".\.tooling\android-sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb install -r demo\build\outputs\apk\debug\demo-debug.apk
& $adb install -r lookup\build\outputs\apk\debug\lookup-debug.apk
```

Then enable Thread in **Settings → Accessibility → Thread**. The OS enforces this
step; the app cannot enable itself, which is the opt-in principle made literal.

Full run sheet in `docs/demo.md`.

## Scoring

Five scores, all explained in `docs/scoring.md`. Every score carries its factor
breakdown, so *"why did it fire?"* is always answerable.

Weights are anchored to **Hick's Law** (choice time grows with `log2(n+1)`, so
option counts are never linear) and **Miller's 7±2** (the items-to-hold factor
saturates around seven). They live in `config/weights.json`, tunable at runtime —
they are a calibratable prior, not a claim of truth.

The non-obvious one: **context loss peaks mid-task**. Interrupted at 5% you have
lost nothing; at 95% the remaining step is obvious. At 50% you have the most state
in your head — and that is exactly where abandonment happens.

---

## What this is not

- It does not change the UI. That is a product decision, not an engineering one.
  The complexity agent instead gives the people who own the UI the evidence.
- It does not replace Copilot. Copilot knows the spreadsheet; Thread knows what you
  were doing, and notices you stopped. It is the session memory Copilot doesn't
  have, and the trigger that means you don't have to ask.
- It does not yet handle reading tasks (long documents). That is a different task
  model — no progress, no decisions, no next action. See `docs/scoring.md`.

---

## Known gaps

Stated plainly, because a demo that overclaims gets taken apart in Q&A.

- **Verified on an emulator, not on physical hardware.** All four modules build and
  run on an API 35 emulator: the overlay renders, interruption and return are
  detected, the cache lookup resolves, and the resumption card draws with the
  correct restored context. The Compose-outside-an-Activity plumbing in
  `OverlayController` therefore works, but a real handset has not been tried.
- **Compose surfaces emit no `TYPE_VIEW_SCROLLED`.** Confirmed by logging: swiping a
  Compose list produced zero scroll events, so scroll reversals — one of the three
  inputs to the disorientation signal — never register on a Compose UI. Return
  disorientation currently rests on `secondsBeforeFirstAction` and
  `focusWithoutEdit` alone. Excel's behaviour here is untested. This weakens the
  signal; it does not break it, because the signal was designed to degrade rather
  than guess.
- **The push path fires, but only under genuine severity.** Offers are made at
  CLS ≥ 60. Measured on device, on an 83.1-complexity screen: one 40-second
  interruption reaches **39**; a second (100s) reaches **52**; a third (150s)
  reaches **58**; a fourth (180s) reaches **62**, at which point the resumption
  card is offered without being asked for. So the threshold is reachable, and it
  takes four interruptions and roughly eight minutes of cumulative absence to get
  there. No weight was changed to produce this. Whether four is the right number
  is a question for the co-design sessions, not for us.
- **Intent requires Tier 2, and only intent.** On a non-integrated app Thread now
  runs a Tier 1 session: it anchors to whatever the user opened, detects leaving
  and returning, scores the screen live, and tracks orbiting. Verified on stock
  Android Settings and Clock, neither of which knows Thread exists. What it
  cannot know without integration is *what the user was trying to achieve* and
  which fields they had filled — so a Tier 1 card describes only observed
  behaviour, and never invents a goal. That last gap is not closable by cleverness:
  an app has to say it.
- **Text capture cannot rely on text-change events.** The obvious implementation
  listens for `TYPE_VIEW_TEXT_CHANGED`. Measured on device, Chrome's omnibox emits
  **none at all** while you type — only `TYPE_WINDOW_CONTENT_CHANGED`. An
  implementation built on text-change events therefore works on well-behaved apps
  and silently captures nothing on the rest, which is worse than failing outright
  because it looks like it works. Thread instead reads the input-focused node from
  the tree, throttled to one read per 350 ms, and commits the value once typing
  stops. Verified capturing a Chrome search across two intervening apps.
- **An empty field reports its own hint as its text.** Before this was caught, the
  card read *"Entered Search or type URL"* for a box the user had never typed in —
  a confident, plausible, invented memory handed to the person least able to
  contradict it. Values matching the field's hint are now discarded.
- **Excel's node tree is unverified.** `NodeTreeProbe` exists to answer this in a
  couple of hours. Interruption and return detection need only the package name,
  so the hero scenario stands either way.
- **Scroll direction needs API 28+.** Below that, and on views reporting no usable
  delta, reversals are dropped rather than inferred — a false reversal inflates
  the orbit score, and an inflated score interrupts someone who was fine.
- **`tools/complexity-agent` has not been executed** (no Python on the build
  machine). Its output is committed to `config/`, so nothing depends on running it.
- **Gradle warns that the Kotlin plugin is loaded in multiple subprojects.** Fixing
  it properly means declaring plugins in the root build with `apply false`, which
  would make `:engine` resolve the Android plugin and break SDK-free engine builds.
  The warning is the better trade.
