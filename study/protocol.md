# Validation protocol

The claim being tested is **not** "the score is accurate". It is:

> When Thread offers something, was it the right thing at the right moment?

An engine that is right about a person's internal state and unhelpful about it has
failed. So the measure is offer quality, not score fidelity.

---

## Primary measure: offer precision

For every offer shown, the participant answers one question:

> Was that useful, just then?    **Yes / No / I didn't notice it**

```
precision = useful / (useful + not useful)
```

`Arbiter.precision()` computes this from recorded outcomes.

**Target: ≥ 0.7.** Below that, Thread is interrupting people who were fine, which
for this audience is not a neutral error — it is the exact harm the product exists
to prevent. A false positive costs more than a false negative here, and the
thresholds in `config/weights.json` are set conservatively for that reason.

## Secondary: recall on resumption

After an interruption, before showing anything, ask:

> Without looking — what were you in the middle of?

Score 0–3: intent, decisions made, next action, blockers hit.

Then show the card, and ask what it added. The delta is the product.

## Tertiary: NASA-TLX

Administered per task, both arms. See `nasa-tlx.md`.

The sub-scale that matters is **mental demand**; **frustration** is the one that
moves most visibly after an interruption. Expect effort and temporal demand to be
noisy with small n — do not over-read them.

---

## Design

Within-subjects, counterbalanced. Each participant does two equivalent tasks, one
with Thread and one without, order alternated across participants.

Interruptions are **scripted**, not natural: a message arrives at a fixed point
mid-task requiring a reply in another app. Waiting for a natural interruption
makes the sessions unequal and the data incomparable.

The interruption lands mid-task deliberately. Interrupted at 5% you have lost
nothing; at 95% the remaining step is obvious. At 50% you have the most state in
your head — and that is where abandonment happens.

## Participants

4–6 people who self-identify as being affected by interruption: ADHD, cognitive
fatigue (long COVID, MS, post-treatment), TBI or post-concussion, anxiety-driven
task freeze.

Recruit through a disability ERG, not a general usability pool.

**This is not a statistical sample and must never be presented as one.** With n=5
you are looking for whether the thing helps at all and where it misfires. Any
percentage quoted from this is directional. Say so out loud before anyone asks.

## Consent and data

- Opt-in, written, and revocable during the session without explanation.
- Nobody is asked to disclose a diagnosis. Self-identification is sufficient and
  is not recorded against their responses.
- No screen recording of task content. Notes only.
- Raw notes stay in `study/results/`, which is gitignored.

The protocol has to hold the same line the product does: **declared, not
diagnosed.** Asking a participant to prove they qualify would contradict the
principle the whole design rests on.

---

## Recording

One row per offer:

| field | meaning |
|---|---|
| `participant` | pseudonymous id |
| `arm` | with / without |
| `offer_kind` | resumption, pin, error, sequencing, reassurance, hint |
| `score_at_trigger` | the score and its factor breakdown |
| `useful` | yes / no / unnoticed |
| `note` | verbatim, if they said anything |

Keep `score_at_trigger` with the factor breakdown attached. When an offer is
judged useless, the breakdown tells you which factor over-fired — which is how the
weights get retuned rather than guessed at again.

## What would falsify this

Worth stating before running it, so the result means something either way:

- Precision below ~0.5 — detection is not carrying its weight and the product
  should be pull-only.
- Participants report the card told them what they already knew — the resumption
  content is too shallow; the decision line is doing no work.
- Participants notice the dot and never tap it — the way in is not discoverable,
  and every push-based claim rests on sand.
