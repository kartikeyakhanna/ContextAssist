# Validation

Do not skip this. It is what separates the project from every other hackathon
entry, and it takes one morning.

## Co-design first

Recruit **3–4 people with lived experience** through a disability ERG — ADHD,
cognitive fatigue, post-treatment, TBI. Not colleagues role-playing.

*"Nothing about us without us"* is the standard in accessibility work. Any
accessibility judge knows the phrase and will look for whether you did it. **Four
real users outweigh fifty synthetic ones**, and a direct quote on the slide
outweighs the whole scoreboard.

## Protocol

Per participant, ~10 minutes:

1. Task: submit an expense claim in the demo app (3 steps, step 2 deliberately heavy).
2. **Two scripted interruptions** at fixed points — at ~30% and ~55% progress, so
   the mid-task peak is actually exercised. Same timing for every participant.
3. Run A: Thread off. Run B: Thread on. **Alternate the order between participants**
   so learning effects don't all land the same way.
4. After each run: NASA-TLX, six questions.
5. Open question: *"What happened when you came back?"* Record the exact words.

## Metrics

**Primary — the demo scoreboard**

| Metric | Why |
|---|---|
| **Task completion rate** under interruption, on vs. off | Abandonment is the harm. This is the headline. |
| **Time-to-resume** — return → first productive action | Directly measures the thing being fixed |

**Secondary**

- **Offer precision** (accepted ÷ shown) — target > 70%. `Arbiter.precision()`.
- Interruptions survived before abandonment
- Post-return error rate
- Correlation between CLS and NASA-TLX self-report

## Report honestly

With n = 4–10, you have a **directional signal, not a result**. Say so.

> "r = 0.6, n = 10 — directional, not conclusive."

reads as rigour. Overclaiming gets you dismantled in Q&A, and the honest version is
genuinely more persuasive because almost nobody else will have measured anything
at all.

**Report precision, not recall.** Thread is deliberately tuned to miss rather than
misfire, so a low recall number is the design working, not failing.

## NASA-TLX (six questions, 0–100 each)

1. **Mental demand** — how mentally demanding was the task?
2. **Physical demand** — how physically demanding?
3. **Temporal demand** — how hurried or rushed?
4. **Performance** — how successful were you? *(reverse-scored)*
5. **Effort** — how hard did you have to work?
6. **Frustration** — how insecure, irritated, stressed or annoyed?

For this product, **effort** and **frustration** are the ones to watch: resumption
support should move those two without much changing mental demand, because the task
itself is not getting easier — only the re-entry is.

## Store results here

`study/results/` — one file per participant. Quotes verbatim. No names, no
identifying detail, and confirm consent to quote before using anything on a slide.
