# Privacy model

Write this first, not last. Thread infers struggle from behaviour, and behaviour
that correlates with cognitive disability edges toward sensitive-data territory the
moment it is transmitted or stored. The design answer is to do neither.

## The claims

| Claim | How it is enforced |
|---|---|
| Behavioural signals never leave the device | `engine/` is pure Kotlin with no network dependency |
| Nothing is persisted | No database in the repository. `TaskState` is in memory and dropped when the task ends |
| No user identity | `taskId` is a random UUID per task. No account, no device ID, no profile |
| No third-party visibility | Nothing is exported. No admin console, no manager view, no telemetry endpoint |
| Opt-in only | Enforced by Android — the user enables it in Settings → Accessibility |

## Thread never diagnoses

This is the load-bearing one.

Screen readers do not detect blindness. Captions do not detect deafness. Assistive
technology is **declared**, not diagnosed. The moment a system infers "this person
appears cognitively impaired," it becomes something creepy, legally fraught, and
unlike how assistive technology actually works.

So: the user turns support on. Behavioural signals decide **when** support appears.
They play no part in deciding **whether** somebody needs it.

This is also the cleanest possible answer to "isn't this surveillance?" — there is
nothing to surveil, because there is no inference about the person being made.

## The honest crack

**If the optional model-assisted phrasing is enabled, task content leaves the device.**

To generate a warmer resumption line, `TaskState` would be sent to a model. So
"nothing leaves the device" is not strictly true in that configuration, and it
should never be claimed as though it were.

Mitigations, in order of preference:

1. **The template path is the default, not a fallback.** `OfferComposer` renders a
   complete resumption card with no model call at all. The feature works fully
   offline; the model only makes the phrasing warmer.
2. Where a model is used, send **field labels and structure, not values** —
   `amount: filled`, not `amount: ₹42,000`.
3. State it precisely: *"behavioural signals never leave the device; task content
   goes to a model only when support mode is on, only at resumption, and only if
   model-assisted phrasing is enabled."*

Side benefit: the demo survives a dead network or a flaky API key.

## What may be logged, if anything

Only **counts, never content** — `Arbiter.outcomeCounts()` returns offers shown,
accepted, dismissed. That is enough to report offer precision without transmitting
anything about the user's work. Even this should be opt-in and aggregate.

## Questions to expect

**"Could an employer use this to monitor productivity?"**
No. Nothing is exported and nothing is stored. There is no endpoint to collect
from and no identifier to join on. This is an architectural property, not a policy.

**"What about GDPR special category data?"**
Data that is never transmitted or stored does not create retention obligations,
subject access requests, or residency questions. Not collecting is the strongest
available control.

**"Isn't behavioural analysis inherently invasive?"**
The analysis happens where the behaviour happens and is discarded within seconds.
Nothing is built up about the person over time — there is deliberately no
cross-session profile, which is also why cross-device resumption is out of scope.

## Known limits

- **No cross-device resumption.** Start on desktop, resume on laptop would require
  sync, which would require a server. Accepted limitation.
- **No learning loop.** Improving the model from real usage would need aggregate
  data, which fights this architecture. If pursued: opt-in, counts only, never
  content.
