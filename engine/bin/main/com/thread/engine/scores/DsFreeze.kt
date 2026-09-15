package com.thread.engine.scores

import com.thread.engine.Weights
import com.thread.engine.model.Factor
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind

/**
 * Disorientation: choice freeze.
 *
 * Dwelling on a screen, scanning options, committing to none.
 *
 * The intervention is to lower the stakes, never to reduce the options. Hiding
 * choices from someone who is struggling to choose removes agency and erodes
 * trust - and for this audience, options that vanish are worse than options that
 * are merely numerous. So the response is a defensible default plus a plain
 * statement of reversibility.
 */
object DsFreeze {

    private const val SCAN_SATURATION = 6.0
    private const val OPEN_CLOSE_SATURATION = 3.0
    private const val DWELL_SATURATION_MULTIPLE = 3.0

    data class FreezeSignals(
        val secondsOnScreen: Double = 0.0,
        /** Median seconds this screen template normally takes. */
        val baselineSecondsForScreen: Double = 30.0,
        /** Distinct options hovered or focused without selecting. */
        val optionsScanned: Int = 0,
        /** Dropdowns or dialogs opened and dismissed with no selection. */
        val openCloseLoops: Int = 0,
        val anythingSelected: Boolean = false,
        /** Options available at this decision point. Log-scaled via Hick's Law. */
        val optionCount: Int = 0,
        val irreversibleActionPresent: Boolean = false,
    )

    fun score(signals: FreezeSignals, w: Weights = Weights.DEFAULT): Score {
        val baseline = signals.baselineSecondsForScreen.coerceAtLeast(1.0)
        val dwellMultiple = (signals.secondsOnScreen / baseline) - 1.0
        val dwellRaw = Normalise.clamp01(dwellMultiple / DWELL_SATURATION_MULTIPLE)

        // Scanning only counts as freeze while nothing has been selected.
        val scanRaw = if (signals.anythingSelected) {
            0.0
        } else {
            Normalise.ratio(signals.optionsScanned, SCAN_SATURATION)
        }

        val loopRaw = Normalise.ratio(signals.openCloseLoops, OPEN_CLOSE_SATURATION)
        val densityRaw = Normalise.hick(signals.optionCount)
        val irreversibleRaw = if (signals.irreversibleActionPresent) 1.0 else 0.0

        return Score.of(
            ScoreKind.DS_FREEZE,
            listOf(
                Factor("dwelling on this screen", dwellRaw, dwellRaw * w.freezeDwellRatio),
                Factor("looking without choosing", scanRaw, scanRaw * w.freezeScanWithoutCommit),
                Factor("opening and closing options", loopRaw, loopRaw * w.freezeOpenCloseLoops),
                Factor("number of choices", densityRaw, densityRaw * w.freezeDecisionDensity),
                Factor("choice cannot be undone", irreversibleRaw, irreversibleRaw * w.freezeIrreversiblePresent),
            ),
        )
    }
}
