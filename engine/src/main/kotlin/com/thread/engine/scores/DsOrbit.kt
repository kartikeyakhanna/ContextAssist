package com.thread.engine.scores

import com.thread.engine.Weights
import com.thread.engine.model.Factor
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState

/**
 * Disorientation: search loop / orbit.
 *
 * A -> B -> A -> C -> A, with nothing committed in between.
 *
 * The framing matters, because it decides the intervention. Someone with a
 * working-memory limitation is not looping because they are lost - they know
 * exactly what they want. They go to B to read a value, and by the time they are
 * back the value is gone, so they go again. *The loop is the working-memory
 * failure.* It is not evidence of confusion.
 *
 * Which is why the response is to carry the value for them (pin it), not to show
 * them a summary of where they are. They already know where they are. Offering a
 * resumption card to someone on their fifth orbit is patronising and will read as
 * such - the engine explicitly must not do it.
 *
 * Unlike CLS this is not interruption-gated; it runs continuously.
 */
object DsOrbit {

    private const val REVISIT_SATURATION = 3.0
    private const val DEAD_END_SATURATION = 3.0
    private const val ERROR_SATURATION = 5.0
    private const val ZERO_PROGRESS_SATURATION_SECONDS = 480.0

    data class OrbitSignals(
        /** Entries into a screen with no interaction, exited within a few seconds. */
        val deadEndReturns: Int = 0,
        /** Seconds elapsed with nothing committed. */
        val zeroProgressSeconds: Double = 0.0,
    )

    fun score(
        state: TaskState,
        signals: OrbitSignals = OrbitSignals(),
        w: Weights = Weights.DEFAULT,
    ): Score {
        // "Going back for the same thing." Two shapes of the same behaviour:
        // returning to the same screen, and fetching the same value again. The
        // second is the more diagnostic of the two on mobile, where there is no
        // way to keep the value in view, so both feed this factor.
        val screenRevisits = ((state.visitCounts.values.maxOrNull() ?: 0) - 2).coerceAtLeast(0)
        val repeatedFetches = (state.lookups.groupBy { it.label }
            .values.maxOfOrNull { it.size } ?: 0) - 1

        val revisitRaw = maxOf(
            Normalise.ratio(screenRevisits, REVISIT_SATURATION),
            Normalise.ratio(repeatedFetches.coerceAtLeast(0), REVISIT_SATURATION),
        )

        val repeatedErrorCount = state.errorCounts.values.maxOrNull() ?: 0
        val errorRaw = Normalise.ratio((repeatedErrorCount - 1).coerceAtLeast(0), ERROR_SATURATION)

        val zeroProgressRaw = Normalise.logDuration(
            signals.zeroProgressSeconds,
            plateauSeconds = ZERO_PROGRESS_SATURATION_SECONDS,
            kneeSeconds = 60.0,
        )

        val entropyRaw = Normalise.pathEntropy(state.screensVisited(), state.actionsCommitted())
        val deadEndRaw = Normalise.ratio(signals.deadEndReturns, DEAD_END_SATURATION)

        return Score.of(
            ScoreKind.DS_ORBIT,
            listOf(
                Factor("returning to the same screen", revisitRaw, revisitRaw * w.orbitHubRevisits),
                Factor("same error repeating", errorRaw, errorRaw * w.orbitRepeatedError),
                Factor("no progress", zeroProgressRaw, zeroProgressRaw * w.orbitZeroProgressTime),
                Factor("wandering between screens", entropyRaw, entropyRaw * w.orbitPathEntropy),
                Factor("dead-end visits", deadEndRaw, deadEndRaw * w.orbitDeadEndReturns),
            ),
        )
    }
}
