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
| No third-party visibility into behavioural signals | Scores, accessibility events, and captured field values are not exported |
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

**If an optional model-assisted feature is enabled, submitted task content leaves the device.**

For `@breakdown`, the Android app sends the task the user explicitly submitted,
the active Office app, a bounded preview of visible labels, and any selected text
exposed through Android accessibility APIs. Password fields, blocked sensitive
apps, decisions, behavioural scores, package history, identifiers, and the full
`TaskState` are never sent. The current Firebase AI Logic configuration uses the
Gemini Developer API. On its free tier, submitted content may be used by Google to
improve its products; do not submit sensitive task or screen text in that
configuration.

When the user explicitly attaches a `.docx`, Thread reads the Android content URI
locally and extracts up to 20,000 characters from `word/document.xml`. The file
and extracted text are not persisted, but the bounded text is included in
subsequent Word breakdown requests until the in-memory session ends or another
document replaces it.

The dedicated `com.thread.worddemo` package is the only exception that permits
automatic reading of editable document text. It contains synthetic demo content
and intentionally exposes its editor through Android accessibility semantics.
This exception does not apply to Microsoft Word, Excel, PowerPoint, or arbitrary
third-party applications.

Within that exception, `PlaceCapture` holds one further copy of document text:
the last reading of it, kept so the next reading can be compared against it to
locate where the user was writing. This is not an optimisation that could be
dropped — a Compose text field reports its contents but reports its cursor as
-1, so the comparison is the only way to answer the question at all. The copy is
in memory, is replaced on every reading rather than accumulated, and is dropped
with the session. It never leaves the device; the resumption card is rendered
locally and the retained text is not sent to any model.

The line and snippet that reach the card are narrower still: one line of the
document, windowed to roughly 80 characters around the edit. That fragment is
shown on the user's own screen and goes nowhere else.

Mitigations, in order of preference:

1. **Core resumption remains local.** `OfferComposer` renders the complete
   resumption card with no model call.
2. **The user initiates every model request.** Typing and submitting `@breakdown`
   is the explicit action that sends the task.
3. **Screen context is separately consented.** The checkbox is off by default and
   previews the labels that would be included. The exact snapshot is kept only in
   the in-memory tool request so retrying cannot silently expand its scope.
4. **Send the minimum.** Without that checkbox, only the submitted task and generic
   intent label are sent. With it, at most 20 deduplicated labels and 1,500
   characters are added. Empty editable fields may contribute their visible label
   or hint, but fields containing entered text and all password fields are
   excluded before the network boundary.
5. State it precisely: *"behavioural signals never leave the device; text submitted
   to an optional AI tool is sent to its configured model only when invoked."*

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
