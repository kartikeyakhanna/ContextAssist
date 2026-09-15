package com.thread.engine.scores

import com.thread.engine.Weights
import com.thread.engine.model.Factor
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState

/**
 * Screen Memory Load.
 *
 * Design-time only. Produced offline by tools/complexity-agent against a screen
 * template and cached by fingerprint; the engine never computes it at runtime,
 * it looks it up. That is not a shortcut - calling a model on every screen view
 * would be slow, costly and non-deterministic. Unseen screens fall back to a
 * neutral score and are queued for offline scoring, so the cache self-warms.
 *
 * What it measures: how much you must hold in your head to finish this screen.
 * Not how many pixels are on it.
 *
 * Important limitation, and it is a real one: SML is a population-level prior.
 * A screen that is "complex" is trivial to a daily power user. That is precisely
 * why it is only ever a multiplier on an observed behavioural signal, never a
 * trigger on its own.
 */
object Sml {

    /** Raw observations for one screen template, as produced by the agent. */
    data class ScreenFacts(
        val screenId: String,
        /** Values needed at some point but not visible where they are needed. */
        val itemsToHold: Int,
        /** Total selectable options across the screen. Log-scaled via Hick's Law. */
        val optionCount: Int,
        /** Is progress through the task visible on screen? */
        val progressVisible: Boolean,
        /** Actions on this screen that cannot be undone. */
        val irreversibleActions: Int,
        /** Pieces of information that must be fetched from outside this screen. */
        val crossReferences: Int,
        /** 0..1. Jargon density and reading difficulty. */
        val languageComplexity: Double,
    )

    const val NEUTRAL: Double = 45.0

    fun score(facts: ScreenFacts, w: Weights = Weights.DEFAULT): Score {
        // Miller's 7 +/- 2: the cost of holding items saturates around seven.
        val itemsRaw = Normalise.ratio(facts.itemsToHold, 7.0)
        val decisionRaw = Normalise.hick(facts.optionCount)
        val progressRaw = if (facts.progressVisible) 0.0 else 1.0
        val irreversibleRaw = Normalise.ratio(facts.irreversibleActions, 3.0)
        val crossRefRaw = Normalise.ratio(facts.crossReferences, 4.0)
        val languageRaw = Normalise.clamp01(facts.languageComplexity)

        return Score.of(
            ScoreKind.SML,
            listOf(
                Factor("items to hold in memory", itemsRaw, itemsRaw * w.smlItemsToHold),
                Factor("decision density", decisionRaw, decisionRaw * w.smlDecisionDensity),
                Factor("progress invisible", progressRaw, progressRaw * w.smlProgressInvisibility),
                Factor("irreversible actions", irreversibleRaw, irreversibleRaw * w.smlIrreversibility),
                Factor("cross-reference burden", crossRefRaw, crossRefRaw * w.smlCrossReference),
                Factor("language complexity", languageRaw, languageRaw * w.smlLanguageComplexity),
            ),
        )
    }

    /** Cache lookup. Unknown screens get [NEUTRAL] rather than a guess. */
    fun lookup(screenId: String, cache: Map<String, Double>): Double =
        cache[screenId] ?: NEUTRAL

    fun lookupFor(state: TaskState, cache: Map<String, Double>): Double =
        lookup(state.currentScreenId, cache)
}
