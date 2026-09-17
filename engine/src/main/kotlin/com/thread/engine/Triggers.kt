package com.thread.engine

import com.thread.engine.model.Offer
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState

/**
 * Maps scores to candidate offers. Chooses nothing on its own - [Arbiter] does that.
 *
 * Band structure, applied per score:
 *   0..passive   -> silence
 *   passive..offer -> a passive marker only (no card, no interruption)
 *   offer..100   -> an offer may be surfaced
 */
object Triggers {

    /** Context in which the user is currently sitting, beyond the scores. */
    data class CommitContext(
        val actionId: String,
        val consequence: String,
        val undoWindowSeconds: Int,
        val hesitationSeconds: Double,
    )

    data class Evaluation(
        val candidates: List<Offer>,
        /** True when something is elevated but not enough to earn a card. */
        val passiveOnly: Boolean,
    )

    fun evaluate(
        state: TaskState,
        cls: Score? = null,
        orbit: Score? = null,
        freeze: Score? = null,
        initiation: Score? = null,
        commitContext: CommitContext? = null,
        justReturned: Boolean = false,
        /** What the screen is still asking for, when it has a recoverable order. */
        plan: Sequencer.Plan? = null,
        w: Weights = Weights.DEFAULT,
    ): Evaluation {
        val candidates = mutableListOf<Offer>()

        // Ordered by how concrete the evidence is. The most specific evidence wins,
        // because a specific offer that is wrong is recoverable; a vague one is just noise.

        // 1. Hesitating on something irreversible. Unambiguous context.
        if (commitContext != null && commitContext.hesitationSeconds >= w.commitHesitationSeconds) {
            candidates += Offer.Reassurance(
                consequence = commitContext.consequence,
                undoWindowSeconds = commitContext.undoWindowSeconds,
                triggeredBy = null,
            )
        }

        // 2. The same error, repeatedly. Explain the error, not the screen.
        if (orbit.fires(w) && state.repeatedError() != null) {
            OfferComposer.errorExplanation(state, orbit)?.let { candidates += it }
        }

        // 3. Fetching the same value again and again. Carry it for them.
        if (orbit.fires(w)) {
            OfferComposer.pin(state, orbit)?.let { candidates += it }
        }

        // 4. Returned from an interruption and lost the thread.
        //
        // Guarded: never hand a resumption card to someone who is orbiting. They
        // know perfectly well what they were doing - they cannot find the thing.
        // Telling them "here is where you were" is patronising, and it is the
        // single easiest way to lose a user's trust in the whole feature.
        if (justReturned && cls.fires(w) && !orbit.fires(w)) {
            candidates += OfferComposer.resumption(state, cls)
        }

        // 5. Cannot begin. Offer to sequence it.
        if (initiation.fires(w)) {
            candidates += Offer.SequencingMode(
                sectionCount = 4,
                estimateMinutes = 3,
                triggeredBy = initiation,
            )
        }

        // 6. Cannot choose. Lower the stakes - never remove the options.
        //
        // This used to fabricate a default ("Standard is fine for most claims"),
        // which was fine on the one demo screen it was written for and nonsense
        // everywhere else. A defensible default is knowledge about a specific
        // decision; nothing here has it for an arbitrary app. What Thread can do
        // honestly is read what the screen is still asking for and name the next
        // one. When there is no recoverable order, it says nothing at all.
        if (freeze.fires(w)) {
            plan?.let { OfferComposer.nextStep(it, freeze)?.let { offer -> candidates += offer } }
        }

        val anyPassive = listOf(cls, orbit, freeze, initiation)
            .any { it != null && it.value >= w.passiveThreshold && it.value < w.offerThreshold }

        return Evaluation(candidates, passiveOnly = candidates.isEmpty() && anyPassive)
    }

    private fun Score?.fires(w: Weights): Boolean =
        this != null && value >= w.offerThreshold

    /** Which scores are in the passive band - used to place a quiet marker. */
    fun passiveBands(
        scores: List<Score?>,
        w: Weights = Weights.DEFAULT,
    ): List<ScoreKind> = scores
        .filterNotNull()
        .filter { it.value >= w.passiveThreshold && it.value < w.offerThreshold }
        .map { it.kind }
}
