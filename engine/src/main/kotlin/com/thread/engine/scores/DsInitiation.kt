package com.thread.engine.scores

import com.thread.engine.Weights
import com.thread.engine.model.Factor
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind

/**
 * Disorientation: task initiation / scatter.
 *
 * Twenty-five fields, seven dropdowns, four mandatory sections - and no idea
 * where to start.
 *
 * This is a different failure from choice freeze. Freeze is "I cannot pick
 * between these options". This is "I cannot begin" - task initiation, the
 * defining executive-function difficulty in ADHD. The signature is scatter:
 * focusing fields out of order, editing none, scrolling without committing.
 *
 * It is also the score that finally gives Screen Memory Load something to do.
 * SML is otherwise only ever a multiplier, which means a genuinely overwhelming
 * screen could score 85 and trigger nothing at all.
 *
 * The response is sequencing mode: one section at a time, with progress visible.
 * A lens over the form, never a deletion - every field stays reachable.
 */
object DsInitiation {

    private const val FOCUS_WITHOUT_EDIT_SATURATION = 5.0
    private const val SCROLL_REVERSAL_SATURATION = 6.0

    data class InitiationSignals(
        val fieldsCommitted: Int = 0,
        val focusWithoutEdit: Int = 0,
        /** Order in which fields were focused, by their position on screen. */
        val fieldFocusOrder: List<Int> = emptyList(),
        val scrollReversals: Int = 0,
        val secondsOnScreen: Double = 0.0,
    )

    /** Below this, the user has barely arrived; do not judge them yet. */
    private const val MIN_SECONDS_BEFORE_SCORING = 15.0

    fun score(
        signals: InitiationSignals,
        smlForScreen: Double = Sml.NEUTRAL,
        w: Weights = Weights.DEFAULT,
    ): Score {
        if (signals.secondsOnScreen < MIN_SECONDS_BEFORE_SCORING) {
            return Score.of(ScoreKind.DS_INITIATION, emptyList())
        }

        // The core signal: time spent, nothing committed. Any commit clears it.
        val zeroCommitRaw = if (signals.fieldsCommitted > 0) 0.0 else 1.0

        val focusRaw = Normalise.ratio(signals.focusWithoutEdit, FOCUS_WITHOUT_EDIT_SATURATION)
        val scatterRaw = Normalise.scatter(signals.fieldFocusOrder)
        val smlRaw = Normalise.clamp01(smlForScreen / 100.0)
        val scrollRaw = Normalise.ratio(signals.scrollReversals, SCROLL_REVERSAL_SATURATION)

        return Score.of(
            ScoreKind.DS_INITIATION,
            listOf(
                Factor("nothing started yet", zeroCommitRaw, zeroCommitRaw * w.initiationZeroCommits),
                Factor("opening fields without filling them", focusRaw, focusRaw * w.initiationFocusWithoutEdit),
                Factor("jumping between fields", scatterRaw, scatterRaw * w.initiationScatter),
                Factor("screen memory load", smlRaw, smlRaw * w.initiationScreenMemoryLoad),
                Factor("scrolling back and forth", scrollRaw, scrollRaw * w.initiationScrollReversal),
            ),
        )
    }
}
