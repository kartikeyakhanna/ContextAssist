# Scoring model

Five scores. Every one carries its factor breakdown, so *"why did it fire?"* is
always answerable — that powers the tuning view and it is how an offer explains
itself.

All weights live in `config/weights.json` and are tunable at runtime. They are a
calibratable prior, not a claim of truth.

## Anchors

- **Hick's Law** — choice time grows with `log2(n + 1)`. Option counts are never
  treated linearly: twenty options is not twice as hard as ten.
- **Miller's 7±2** — the items-to-hold factor saturates around seven.
- **NASA-TLX** — the six-question self-report used for validation (`study/`).

---

## A — Screen Memory Load (SML)

Design-time. Produced offline by `tools/complexity-agent`, cached by screen
fingerprint, looked up at runtime — never computed there.

| Factor | Weight |
|---|---|
| Items to hold in memory | 30% |
| Decision density (Hick-scaled) | 20% |
| Progress invisibility | 15% |
| Irreversibility | 15% |
| Cross-reference burden | 10% |
| Language complexity | 10% |

Measures how much you must hold in your head to finish the screen — not how busy
it looks.

**Limitation, and a real one:** SML is a *population-level prior*. A screen that is
overwhelming to a new user is trivial to a daily expert. That is exactly why it is
only ever a multiplier on an observed behavioural signal, and never a trigger by
itself.

Unseen screens fall back to neutral (45) rather than guessing, and are queued for
offline scoring — so the cache self-warms.

## B — Context Loss Score (CLS)

Runtime, computed on return. **The trigger for the hero scenario.**

| Factor | Weight | Shape |
|---|---|---|
| Interruption duration | 25% | Log-scaled; 30s low, 5min high, plateaus at 30min |
| Progress at exit | 20% | **Peaks mid-task** (trapezoid, full across 30–70%) |
| Cumulative interruptions | 20% | Compounding; the first scores 0 |
| Post-return disorientation | 20% | Scroll reversals, focus-without-edit, dead time |
| Screen memory load | 15% | Score A |

The non-obvious one is progress-at-exit. Interrupted at 5% you have lost nothing;
at 95% the remaining step is obvious. At 50% you have the most state in your head —
and that is exactly where abandonment happens.

## C1 — Orbit (search loop)

| Factor | Weight |
|---|---|
| Going back for the same thing (screen revisits **or** repeated fetches) | 25% |
| Same error repeating | 20% |
| Zero-progress time | 20% |
| Path entropy (screens touched ÷ actions committed) | 20% |
| Dead-end visits | 15% |

**Framing decides the intervention.** Someone with a working-memory limitation is
not looping because they are lost — they know exactly what they want. They go to
screen B to read a value, and by the time they are back the value is gone, so they
go again. *The loop is the working-memory failure*, not evidence of confusion.

Which is why the response is to **carry the value** (pin it), not to show a summary.
`Triggers` explicitly refuses to offer a resumption card while orbit is elevated —
handing "here's where you were" to someone on their fifth orbit is patronising, and
it is the fastest way to lose trust in the whole feature. There is a test for this.

## C2 — Freeze (choice paralysis)

| Factor | Weight |
|---|---|
| Dwell vs. screen baseline | 30% |
| Scanning options without selecting | 25% |
| Open–close loops | 20% |
| Decision density (Hick-scaled) | 15% |
| Irreversible action present | 10% |

Response lowers the stakes — a defensible default plus plain-language
reversibility. **It never hides options.** Options that vanish are worse than
options that are merely numerous, especially for this audience.

## C3 — Initiation (scatter)

| Factor | Weight |
|---|---|
| Nothing committed yet | 30% |
| Focus without edit | 25% |
| Scatter (non-sequential field jumps) | 20% |
| Screen memory load | 15% |
| Scroll reversals | 10% |

Distinct from freeze. Freeze is *"I can't pick."* This is *"I can't begin"* — task
initiation, the defining executive-function difficulty in ADHD.

This is also the score that finally gives SML something to do. Without it, a
genuinely overwhelming screen could score 85 and trigger nothing at all, because
SML is otherwise only ever a multiplier.

The user is not scored in the first 15 seconds on a screen — they have barely
arrived.

---

## Screens nobody scored offline

The cache only covers screens someone thought to score in advance, which is fine
for a product you own and useless for whatever the user actually opens. So an
unknown screen is measured live from the accessibility tree — `LiveFacts.facts()`
counts options, editable fields, irreversible-looking buttons and cross-references,
then runs them through the same maths as the offline agent.

Order of trust, in `ThreadAccessibilityService.smlFor`:

1. the design-time cache, where a model read the whole template
2. a live measurement of the tree in front of us, computed once per screen
3. `Sml.NEUTRAL`, only when the app exposes nothing readable

Measured on stock apps with no integration: Android Settings home **20.2**, Clock
**32.3**, against the hand-scored Expense Portal's **87.5**. The ordering is the
point — it separates a calm screen from a punishing one with nobody having scored
either in advance.

This is the weaker measurement and is treated as such. The offline agent reads a
template and can reason about meaning; this one counts what is on screen. Where
they disagree, the cache wins.

One heuristic bug is worth recording, because it shows the shape these bugs take.
The first live run reported *progress visible* on the Settings home screen — the
battery reads "85%". A bare percentage was matching as a progress indicator, which
would have quietly told the engine that a screen offering no orientation at all
was helping the user keep their place. Percentages now count only when a word like
"complete" sits next to them.

---

## Thresholds

| Band | Action |
|---|---|
| 0–30 | Silence |
| 30–60 | Passive marker only |
| 60–100 | An offer may be surfaced |
| Hesitation > 8s on an irreversible action | Reassurance chip |

Deliberately conservative. **A false offer costs more trust than a missed one**, and
trust is not recoverable within a session. Tuned for precision, and reported as
precision — `Arbiter.precision()` is accepted ÷ shown.

### What these thresholds do in practice

Measured on a live emulator run, on `Step2Allocation` (SML 83.1 as the form then
stood, 25 cost centres in a radio group), one continuous task interrupted
repeatedly. The form has since been rebuilt and scores 87.5; this run has not
been repeated, and the numbers below are left as they were measured:

| Interruption | Away | CLS | Outcome |
|---|---|---|---|
| 1st | 40s | 33 → 39 | passive marker |
| 2nd | 100s | 52 | passive marker |
| 3rd | 150s | 58 | passive marker |
| 4th | 180s | **62** | **resumption card offered** |

Two things are worth reading off that table.

**The threshold is reachable, and it is not cheap.** Crossing 60 took four
interruptions and about eight minutes of cumulative absence from a genuinely
heavy screen. A single bad moment — even a two-and-a-half minute one — stays in
the passive band and the dot remains the way in. That is the intended shape: the
engine earns the right to speak first.

**No weight was moved to produce this.** The 60 line was set before any of these
numbers were measured. The third interruption landing at 58 was uncomfortably
close to a demo that did not work, and the correct response to that was to keep
interrupting rather than to lower the bar.

Note also what does *not* raise CLS: the run above included three validation
failures, and they contributed nothing. Errors are task friction, scored
separately. Being stuck and having lost your place are different problems, and
conflating them would make the resumption card fire at people who know exactly
where they are.

### A signal that is currently weaker than it looks

`Scroll reversals` (10% of disorientation) **does not fire on Jetpack Compose
surfaces** — Compose emits no `TYPE_VIEW_SCROLLED`, confirmed by logging during
device testing. On a Compose UI, post-return disorientation rests on
`secondsBeforeFirstAction` and `focusWithoutEdit` alone. Views-based apps are
unaffected; Excel is untested.

The engine degrades rather than guesses here, which is the same choice made for
pre-API-28 scroll deltas: a fabricated reversal would inflate the score and
interrupt someone who was coping.

The cost is measurable. Scroll reversals are worth up to 2 CLS points, and in the
run above the third interruption landed at **58** against a threshold of 60. The
missing signal is very close to being the difference between speaking and staying
quiet — so this is a gap to close, not one to live with.

## Arbitration

The most important restraint in the engine:

- one offer at a time, never stacked
- a cooldown between offers
- two dismissals of a kind and it goes quiet for the rest of the task
- "don't show again" is permanent, immediately, with no confirmation step
- **`userRequested()` bypasses all of the above** — if someone asks where they were,
  they get an answer

---

## Not covered: reading tasks

Long documents are a **different task model**, not a missing feature. Everything
here assumes an intent, fields, progress, decisions and a next action. Reading a
5,000-word document has none of them — `TaskState` would be structurally empty, and
orbit would half-fire and then offer to pin a value, which is nonsense for a reader.

The right interventions there are place-marking and progressive summary: different
state, different surfaces. Same engine. Scoped out deliberately.
