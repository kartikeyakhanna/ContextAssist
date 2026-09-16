package com.thread.engine

import com.thread.engine.model.Decision
import com.thread.engine.model.Offer
import com.thread.engine.model.Score
import com.thread.engine.model.TaskState

/**
 * Turns a TaskState into the text the user reads - with no model call.
 *
 * This is deliberately the primary path, not a fallback. Three consequences:
 *
 *  1. Behavioural signals never leave the device, and neither does task content,
 *     because nothing needs to be sent to render a resumption card.
 *  2. The card appears instantly, which matters because the one moment it must
 *     feel effortless is the moment the user comes back.
 *  3. The demo survives a dead network or a flaky API key.
 *
 * The model is an enhancement layer on top of this - it makes the phrasing
 * warmer, it does not make the feature work.
 */
object OfferComposer {

    fun resumption(state: TaskState, triggeredBy: Score?): Offer.Resumption =
        Offer.Resumption(
            intent = state.intent,
            done = describeDone(state),
            decided = describeDecisions(state.decisions),
            next = state.nextAction,
            awayFor = describeAway(state),
            triggeredBy = triggeredBy,
        )

    /**
     * Null when nothing was observed, rather than "Nothing filled in yet".
     *
     * Thread only sees field-level progress in an app that reports it. On every
     * other app an empty list means *we were not watching that closely* - it does
     * not mean the user achieved nothing. Saying so to someone who has just lost
     * their place, and who may not be certain what they did, risks them believing
     * it. So the line is omitted.
     */
    private fun describeDone(state: TaskState): String? {
        val labels = state.completed.map { it.label }.distinct()
        return when {
            labels.isEmpty() -> null
            labels.size <= 3 -> "Entered " + labels.joinToString(", ")
            else -> "Entered " + labels.take(2).joinToString(", ") +
                " and ${labels.size - 2} more"
        }
    }

    /**
     * How long they were gone. Available without any integration at all, and for
     * someone who has lost the thread entirely it is often the most orientating
     * thing on the card: it tells them whether they stepped away or lost an hour.
     */
    private fun describeAway(state: TaskState): String? {
        val last = state.interruptions.lastOrNull() ?: return null
        val seconds = last.durationSeconds
        if (seconds < 30) return null

        val minutes = (seconds / 60).toInt()
        return when {
            minutes < 1 -> "You were away for under a minute"
            minutes == 1 -> "You were away for a minute"
            else -> "You were away for $minutes minutes"
        }
    }

    /**
     * Restoring *what* was chosen is useful. Restoring *why* is what stops the
     * user re-deliberating a decision they already made before the interruption.
     */
    private fun describeDecisions(decisions: List<Decision>): String? {
        if (decisions.isEmpty()) return null
        val recent = decisions.takeLast(2)
        return recent.joinToString(", ") { "${it.label}: ${it.value}" }
    }

    fun pin(state: TaskState, triggeredBy: Score?): Offer.Pin? {
        val lookup = state.mostFetchedLookup() ?: return null
        return Offer.Pin(label = lookup.label, value = lookup.value, triggeredBy = triggeredBy)
    }

    fun errorExplanation(state: TaskState, triggeredBy: Score?): Offer.ErrorExplanation? {
        val (message, count) = state.repeatedError() ?: return null
        return Offer.ErrorExplanation(message = message, occurrences = count, triggeredBy = triggeredBy)
    }
}
