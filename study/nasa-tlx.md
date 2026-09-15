# NASA-TLX (task load index)

Administered after each task, in both arms. Six sub-scales, each 0–100 on a
21-point scale.

Read aloud, verbatim, in the same order every time. Varying the phrasing between
arms is how you accidentally measure your own delivery.

---

**1. Mental demand**
How much mental and perceptual activity was required? Was the task easy or
demanding, simple or complex?

`Very low ——————————————————————— Very high`

**2. Physical demand**
How much physical activity was required?

`Very low ——————————————————————— Very high`

**3. Temporal demand**
How much time pressure did you feel due to the pace of the task?

`Very low ——————————————————————— Very high`

**4. Performance**
How successful were you in accomplishing what you were asked to do?

`Perfect ———————————————————————— Failure`

> Note the reversed anchors. This one is scored in the opposite direction to the
> rest and is the most common place TLX data gets silently corrupted.

**5. Effort**
How hard did you have to work to accomplish your level of performance?

`Very low ——————————————————————— Very high`

**6. Frustration**
How insecure, discouraged, irritated, stressed and annoyed were you?

`Very low ——————————————————————— Very high`

---

## Scoring

Use **Raw TLX**: the unweighted mean of the six sub-scales. Skip the pairwise
weighting procedure — it adds fifteen comparisons per task, and with n≈5 the
weights add nothing but participant fatigue, which is a poor thing to add to a
study about cognitive load.

Report sub-scales individually as well as the mean. The mean can stay flat while
frustration halves, and with this audience frustration is the one that moves.

## What to expect

**Mental demand** is the target sub-scale — it should drop in the Thread arm.

**Frustration** usually moves most, and moves earliest.

**Physical demand** should be flat. If it is not, something is wrong with the task
design, not the product.

**Performance** is unreliable at this sample size. People with cognitive fatigue
frequently under-rate their own performance, so treat it as commentary rather than
a measure.

## Honest limitations

TLX is a self-report instrument taken *after* the fact, by someone who has just
been through the task. It is a reasonable proxy and a poor ground truth.

It is used here because the alternative — inferring load from behaviour and then
validating that inference against itself — is circular. TLX at least asks the
person.

State this when presenting the numbers. Somebody in the room will know it, and it
is much better coming from you.
