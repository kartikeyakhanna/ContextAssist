package com.thread.engine

/**
 * Every weight in the system lives here. Nothing is hard-coded in a scorer.
 *
 * Two reasons this matters:
 *  1. "Where did 30% come from?" has an honest answer - it is a tunable prior,
 *     not a claim of truth. An expert or a study can retune it without a rebuild.
 *  2. It can be adjusted live, on stage, and the scores move.
 *
 * Anchors used when choosing the defaults:
 *  - Hick's Law: decision time grows with log2(options + 1), so option counts are
 *    log-scaled rather than counted linearly.
 *  - Miller's 7 +/- 2: the items-to-hold factor saturates around seven.
 *
 * Weights within each score are expected to sum to 100.
 */
data class Weights(
    // --- Screen Memory Load (design time) ---
    val smlItemsToHold: Double = 30.0,
    val smlDecisionDensity: Double = 20.0,
    val smlProgressInvisibility: Double = 15.0,
    val smlIrreversibility: Double = 15.0,
    val smlCrossReference: Double = 10.0,
    val smlLanguageComplexity: Double = 10.0,

    // --- Context Loss (on return) ---
    val clsInterruptionDuration: Double = 25.0,
    val clsProgressAtExit: Double = 20.0,
    val clsCumulativeInterruptions: Double = 20.0,
    val clsPostReturnDisorientation: Double = 20.0,
    val clsScreenMemoryLoad: Double = 15.0,

    // --- Disorientation: orbit / search loop ---
    val orbitHubRevisits: Double = 25.0,
    val orbitRepeatedError: Double = 20.0,
    val orbitZeroProgressTime: Double = 20.0,
    val orbitPathEntropy: Double = 20.0,
    val orbitDeadEndReturns: Double = 15.0,

    // --- Disorientation: choice freeze ---
    val freezeDwellRatio: Double = 30.0,
    val freezeScanWithoutCommit: Double = 25.0,
    val freezeOpenCloseLoops: Double = 20.0,
    val freezeDecisionDensity: Double = 15.0,
    val freezeIrreversiblePresent: Double = 10.0,

    // --- Disorientation: initiation / scatter ---
    val initiationZeroCommits: Double = 30.0,
    val initiationFocusWithoutEdit: Double = 25.0,
    val initiationScatter: Double = 20.0,
    val initiationScreenMemoryLoad: Double = 15.0,
    val initiationScrollReversal: Double = 10.0,

    // --- Thresholds ---
    /** Below this, stay silent. A missed offer is cheaper than an unwanted one. */
    val passiveThreshold: Double = 30.0,
    /** Above this, surface the card. */
    val offerThreshold: Double = 60.0,
    /** Seconds of hesitation on an irreversible action before reassuring. */
    val commitHesitationSeconds: Double = 8.0,
    /** Seconds before any second offer may appear. */
    val offerCooldownSeconds: Double = 45.0,
    /** Dismissals of one kind before it goes quiet for the rest of the task. */
    val dismissalsBeforeSilence: Int = 2,
) {
    companion object {
        val DEFAULT = Weights()

        /**
         * Reads the flat "section.factor": number form used by config/weights.json.
         * Deliberately dependency-free: the engine ships with zero third-party
         * libraries so it can be audited in one sitting.
         */
        fun fromJson(json: String): Weights {
            val values = Regex("\"([A-Za-z.]+)\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
                .findAll(json)
                .associate { it.groupValues[1] to it.groupValues[2].toDouble() }

            val d = DEFAULT
            fun g(key: String, fallback: Double) = values[key] ?: fallback

            return Weights(
                smlItemsToHold = g("sml.itemsToHold", d.smlItemsToHold),
                smlDecisionDensity = g("sml.decisionDensity", d.smlDecisionDensity),
                smlProgressInvisibility = g("sml.progressInvisibility", d.smlProgressInvisibility),
                smlIrreversibility = g("sml.irreversibility", d.smlIrreversibility),
                smlCrossReference = g("sml.crossReference", d.smlCrossReference),
                smlLanguageComplexity = g("sml.languageComplexity", d.smlLanguageComplexity),
                clsInterruptionDuration = g("cls.interruptionDuration", d.clsInterruptionDuration),
                clsProgressAtExit = g("cls.progressAtExit", d.clsProgressAtExit),
                clsCumulativeInterruptions = g("cls.cumulativeInterruptions", d.clsCumulativeInterruptions),
                clsPostReturnDisorientation = g("cls.postReturnDisorientation", d.clsPostReturnDisorientation),
                clsScreenMemoryLoad = g("cls.screenMemoryLoad", d.clsScreenMemoryLoad),
                orbitHubRevisits = g("orbit.hubRevisits", d.orbitHubRevisits),
                orbitRepeatedError = g("orbit.repeatedError", d.orbitRepeatedError),
                orbitZeroProgressTime = g("orbit.zeroProgressTime", d.orbitZeroProgressTime),
                orbitPathEntropy = g("orbit.pathEntropy", d.orbitPathEntropy),
                orbitDeadEndReturns = g("orbit.deadEndReturns", d.orbitDeadEndReturns),
                freezeDwellRatio = g("freeze.dwellRatio", d.freezeDwellRatio),
                freezeScanWithoutCommit = g("freeze.scanWithoutCommit", d.freezeScanWithoutCommit),
                freezeOpenCloseLoops = g("freeze.openCloseLoops", d.freezeOpenCloseLoops),
                freezeDecisionDensity = g("freeze.decisionDensity", d.freezeDecisionDensity),
                freezeIrreversiblePresent = g("freeze.irreversiblePresent", d.freezeIrreversiblePresent),
                initiationZeroCommits = g("initiation.zeroCommits", d.initiationZeroCommits),
                initiationFocusWithoutEdit = g("initiation.focusWithoutEdit", d.initiationFocusWithoutEdit),
                initiationScatter = g("initiation.scatter", d.initiationScatter),
                initiationScreenMemoryLoad = g("initiation.screenMemoryLoad", d.initiationScreenMemoryLoad),
                initiationScrollReversal = g("initiation.scrollReversal", d.initiationScrollReversal),
                passiveThreshold = g("thresholds.passive", d.passiveThreshold),
                offerThreshold = g("thresholds.offer", d.offerThreshold),
                commitHesitationSeconds = g("thresholds.commitHesitationSeconds", d.commitHesitationSeconds),
                offerCooldownSeconds = g("thresholds.offerCooldownSeconds", d.offerCooldownSeconds),
                dismissalsBeforeSilence = g("thresholds.dismissalsBeforeSilence", d.dismissalsBeforeSilence.toDouble()).toInt(),
            )
        }
    }
}
