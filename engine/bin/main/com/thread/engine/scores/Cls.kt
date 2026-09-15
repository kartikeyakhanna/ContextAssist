package com.thread.engine.scores

import com.thread.engine.Weights
import com.thread.engine.model.Factor
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState

/**
 * Context Loss Score - computed the moment the user comes back.
 *
 * This is the score the whole product is named for. It answers: how much of the
 * thread did this interruption cost, and is it worth handing back?
 *
 * Note what it does *not* do. It makes no claim about the user's cognitive state
 * and it does not infer disability. It measures a property of the interruption.
 * Support mode is switched on by the user, the way a screen reader is; these
 * signals only decide *when* support appears, never *whether* the person needs it.
 *
 * Deliberately conservative. A false offer costs more trust than a missed one,
 * so this is tuned for precision and reported as precision.
 */
object Cls {

    /** Signals observed in the window just after the user returns. */
    data class PostReturnSignals(
        val scrollReversals: Int = 0,
        /** Focused a field and left without editing - re-reading, not working. */
        val focusWithoutEdit: Int = 0,
        /** Re-reading dwell: seconds on screen before the first productive action. */
        val secondsBeforeFirstAction: Double = 0.0,
        val navBacks: Int = 0,
    ) {
        fun count(): Int = scrollReversals + focusWithoutEdit + navBacks
    }

    /** Saturation point for post-return disorientation signals. */
    private const val DISORIENTATION_SATURATION = 6.0

    /** Seconds of aimless time after return that counts as fully disoriented. */
    private const val REORIENT_SATURATION_SECONDS = 20.0

    fun score(
        state: TaskState,
        signals: PostReturnSignals = PostReturnSignals(),
        smlForScreen: Double = Sml.NEUTRAL,
        w: Weights = Weights.DEFAULT,
    ): Score {
        val lastInterruption = state.interruptions.lastOrNull()

        val durationRaw = Normalise.logDuration(lastInterruption?.durationSeconds ?: 0.0)
        val progressRaw = Normalise.midTaskPeak(lastInterruption?.progressAtExit ?: state.progress)
        val cumulativeRaw = Normalise.compounding(state.interruptionCount)

        // Two independent reads of disorientation: discrete actions, and dead time.
        val signalRaw = Normalise.ratio(signals.count(), DISORIENTATION_SATURATION)
        val dwellRaw = Normalise.ratio(signals.secondsBeforeFirstAction, REORIENT_SATURATION_SECONDS)
        val disorientationRaw = maxOf(signalRaw, dwellRaw)

        val smlRaw = Normalise.clamp01(smlForScreen / 100.0)

        return Score.of(
            ScoreKind.CLS,
            listOf(
                Factor("time away", durationRaw, durationRaw * w.clsInterruptionDuration),
                Factor("interrupted mid-task", progressRaw, progressRaw * w.clsProgressAtExit),
                Factor("repeated interruptions", cumulativeRaw, cumulativeRaw * w.clsCumulativeInterruptions),
                Factor("disoriented on return", disorientationRaw, disorientationRaw * w.clsPostReturnDisorientation),
                Factor("screen memory load", smlRaw, smlRaw * w.clsScreenMemoryLoad),
            ),
        )
    }
}
