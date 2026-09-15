package com.thread.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the real config/weights.json, not a fixture.
 *
 * The "these weights are tunable, not claims of truth" defence only holds if the
 * file is genuinely the source of truth and genuinely loads. This test is what
 * makes that statement true rather than aspirational.
 */
class WeightsConfigTest {

    private val configFile = File("../config/weights.json")

    private fun loaded(): Weights {
        assertTrue(configFile.exists(), "config/weights.json missing at ${configFile.absolutePath}")
        return Weights.fromJson(configFile.readText())
    }

    @Test
    fun `the shipped config parses and matches the documented defaults`() {
        assertEquals(Weights.DEFAULT, loaded())
    }

    @Test
    fun `weights within each score sum to one hundred`() {
        val w = loaded()

        val groups = mapOf(
            "SML" to listOf(
                w.smlItemsToHold, w.smlDecisionDensity, w.smlProgressInvisibility,
                w.smlIrreversibility, w.smlCrossReference, w.smlLanguageComplexity,
            ),
            "CLS" to listOf(
                w.clsInterruptionDuration, w.clsProgressAtExit, w.clsCumulativeInterruptions,
                w.clsPostReturnDisorientation, w.clsScreenMemoryLoad,
            ),
            "orbit" to listOf(
                w.orbitHubRevisits, w.orbitRepeatedError, w.orbitZeroProgressTime,
                w.orbitPathEntropy, w.orbitDeadEndReturns,
            ),
            "freeze" to listOf(
                w.freezeDwellRatio, w.freezeScanWithoutCommit, w.freezeOpenCloseLoops,
                w.freezeDecisionDensity, w.freezeIrreversiblePresent,
            ),
            "initiation" to listOf(
                w.initiationZeroCommits, w.initiationFocusWithoutEdit, w.initiationScatter,
                w.initiationScreenMemoryLoad, w.initiationScrollReversal,
            ),
        )

        groups.forEach { (name, values) ->
            assertEquals(100.0, values.sum(), 0.001, "$name weights must sum to 100")
        }
    }

    @Test
    fun `retuning a weight actually moves the score`() {
        // The live tuning panel is only honest if nothing is hard-coded in a scorer.
        val retuned = Weights.fromJson("""{ "cls.progressAtExit": 60 }""")
        assertEquals(60.0, retuned.clsProgressAtExit)
        assertEquals(
            Weights.DEFAULT.clsInterruptionDuration,
            retuned.clsInterruptionDuration,
            "unspecified keys must keep their defaults",
        )
    }

    @Test
    fun `thresholds stay conservative - a false offer costs more than a missed one`() {
        val w = loaded()
        assertTrue(w.passiveThreshold < w.offerThreshold)
        assertTrue(w.offerThreshold >= 50.0, "offering below 50 would chase recall over precision")
        assertTrue(w.offerCooldownSeconds > 0, "offers must never be able to stack")
        assertTrue(w.dismissalsBeforeSilence in 1..3, "must learn quickly that it is wrong")
    }
}
