# Thread SDK (Tier 2)

Six calls. No initialisation, no keys, no callbacks, nothing returned.

```kotlin
ThreadSdk.startTask(context, "Submitting your Q3 travel request", screen)
ThreadSdk.fieldCommitted(context, screen, "budgetCode", "Budget code", "BX-4417")
ThreadSdk.decisionMade(context, screen, "Destination", "Delhi")
ThreadSdk.validationFailed(context, screen, "budgetCode", "Budget code is required.")
ThreadSdk.irreversibleAction(context, screen, "submit", "Goes to your manager", 600)
ThreadSdk.taskCompleted(context, screen)
```

## Why an app would bother

Tier 1 — the accessibility service — already works on any app with no integration
at all. But it has to *infer* from the node tree what a screen meant: which text
was a label, whether a value was committed or merely typed, whether a tap was a
settled decision or a stray one.

Those distinctions are what a resumption card is made of. A card built on a wrong
inference is worse than no card, because the person cannot easily tell it is
wrong — checking it costs them the very context they came back to recover.

So an app that can simply *state* these things produces a better card than one
that has to be guessed at.

## What it deliberately does not do

**It returns nothing.** An integrating app cannot read the user's task state,
cannot query scores, and cannot ask whether Thread is even installed. The
asymmetry is the point: integrating should be a five-minute decision with no
ongoing obligation and no new data flowing back into the app.

**It has no lifecycle.** No init, no shutdown, no registration. If Thread is not
installed the broadcasts go nowhere and cost nothing.

**It stores nothing.** `taskCompleted` is the call that matters most here —
everything Thread was holding is dropped the moment it arrives. Not archived, not
summarised. Dropped.

## The two calls that earn their place

`decisionMade` is reported separately from `fieldCommitted` because restoring
*what you filled in* is useful, but restoring *what you decided* is what stops
somebody reopening a question they had already closed before the interruption.

`irreversibleAction` exists because hesitating in front of a button you cannot
take back is not indecision — it is the rational response to an unstated
consequence. It is far more costly for someone who cannot cheaply rebuild the
context needed to re-check their own work.

## Screen ids

`screen` is `"package/ClassName"`, matching what the accessibility service derives
from a window-state change. These strings are the keys in
`config/complexity-cache.json`, so renaming an activity orphans its design-time
Screen Memory Load score.
