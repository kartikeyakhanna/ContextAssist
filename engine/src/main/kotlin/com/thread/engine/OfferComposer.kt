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
            place = describePlace(state),
        )

    /**
     * One step, in the app's own words.
     *
     * Only the first step is rendered, even though the plan holds several. A list
     * of five steps is five things to hold, which is the load this is supposed to
     * be removing - it would look more impressive in a screenshot and be worse to
     * use. The count is carried instead, because knowing the task is four long is
     * orientation and costs nothing to hold.
     *
     * The verb is chosen from what the control *is*, never from what it might be
     * for. "Enter", "Choose" and "Then" are the only three, and each is true by
     * construction: an editable node is filled in, a checkable one is chosen, and
     * a terminal one comes last.
     */
    fun nextStep(plan: Sequencer.Plan, triggeredBy: Score?): Offer.NextStep? {
        val step = plan.next ?: return null

        val instruction = when (step.kind) {
            Sequencer.StepKind.FILL -> "Enter ${step.label.decapitalised()}"
            Sequencer.StepKind.CHOOSE -> "Choose ${step.label.decapitalised()}"
            Sequencer.StepKind.CONFIRM -> "Then ${step.label.decapitalised()}"
        }

        return Offer.NextStep(
            instruction = instruction,
            position = plan.position,
            total = plan.total,
            target = step.bounds,
            triggeredBy = triggeredBy,
        )
    }

    /**
     * "Enter Policy number" reads as a quotation; "Enter policy number" reads as a
     * sentence. Acronyms are left alone - "Enter iBAN" would be worse than either.
     */
    private fun String.decapitalised(): String {
        if (length >= 2 && this[1].isUpperCase()) return this
        return replaceFirstChar { it.lowercase() }
    }

    /**
     * Null when nothing was observed, rather than "Nothing filled in yet".
     *
     * Thread only sees field-level progress in an app that reports it. On every
     * other app an empty list means *we were not watching that closely* - it does
     * not mean the user achieved nothing. Saying so to someone who has just lost
     * their place, and who may not be certain what they did, risks them believing
     * it. So the line is omitted.
     *
     * One field and several fields are described differently, on purpose:
     *
     * - One field is almost always a lookup or a search, and the label alone
     *   ("Entered Search or type URL") tells the user nothing they did not
     *   already know. What they lost is the *query*, so the query is shown.
     * - Several fields is a form, and there the labels are the map: they say how
     *   far down the thing the user had got. Echoing every value would put a
     *   salary or a claim amount on screen to be read over a shoulder, and buy
     *   nothing, because the values are still sitting in the fields behind it.
     */
    private fun describeDone(state: TaskState): String? {
        val fields = state.completed

        // A field Thread watched rather than was told about may have no name at
        // all. It is still work the user did, so it is counted - but it is not
        // named, and no stand-in is put in its place. Listing a field that is not
        // on the screen sends someone who has lost their place looking for it.
        val named = fields.filter { it.label.isNotBlank() }
        val names = named.map { it.label }.distinct()
        val anonymous = fields.size - named.size

        // A decision is recorded as a completed field as well. That is harmless in
        // the label summary - "Entered Destination, Purpose" is true however the
        // value got there - but not in the single-field form, which would announce
        // a value picked from a list as one the user typed, immediately above the
        // decision line saying the same value again.
        val decided = state.decisions.map { it.fieldId }.toSet()
        val typed = fields.filterNot { it.fieldId in decided }

        val shown = names.take(2)
        val remainder = (names.size - shown.size) + anonymous

        return when {
            fields.isEmpty() -> null
            fields.size == 1 -> typed.firstOrNull()?.let { "You typed \"${it.value.take(60)}\"" }
            // Nothing nameable at all, so fall back to the one thing that is
            // certainly meaningful: a value the user actually entered.
            names.isEmpty() -> typed.lastOrNull()?.let { "You typed \"${it.value.take(60)}\"" }
            remainder == 0 && names.size <= 3 -> "Entered " + names.joinToString(", ")
            else -> "Entered " + shown.joinToString(", ") + " and $remainder more"
        }
    }

    /**
     * How long they were gone, and how often.
     *
     * Available without any integration at all, and for someone who has lost the
     * thread entirely it is often the most orientating thing on the card: it tells
     * them whether they stepped away or lost an hour.
     *
     * Reporting only the *last* absence was wrong, and wrong in a way that hid the
     * worst case. Someone who has been in and out of an app seven times, with a
     * four-minute gap in the middle, most likely left it three seconds ago - so the
     * last gap is the least informative one available, and using it meant the card
     * fell silent exactly when the user was most scattered. The longest gap and the
     * number of trips are both reported instead.
     */
    private fun describeAway(state: TaskState): String? {
        val interruptions = state.interruptions
        if (interruptions.isEmpty()) return null

        val trips = interruptions.size
        val longest = interruptions.maxOf { it.durationSeconds }

        // Nothing worth saying: one glance away, and no pattern of them.
        if (longest < 30 && trips < 3) return null

        val gap = describeGap(longest)
        return when {
            trips < 3 -> "You were away for $gap"
            longest < 30 -> "You have been in and out of this $trips times"
            else -> "You have been in and out $trips times, the longest for $gap"
        }
    }

    private fun describeGap(seconds: Double): String {
        // Rounded, not truncated. Three minutes fifty-nine seconds is four minutes
        // to anyone who lived through it, and reading "3 minutes" back to someone
        // checking whether they lost track of time makes the card feel wrong in a
        // way they cannot articulate - which costs trust that is hard to win back.
        val minutes = Math.round(seconds / 60.0).toInt()
        return when {
            seconds < 60 -> "under a minute"
            minutes <= 1 -> "a minute"
            minutes < 60 -> "$minutes minutes"
            else -> {
                val hours = minutes / 60
                if (hours == 1) "over an hour" else "over $hours hours"
            }
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

    /**
     * Where they were writing, quoted back.
     *
     * The line number and the words do different jobs and both are needed: the
     * number lets someone scroll to the place, and the fragment is what restarts
     * the sentence. Quoted, because it is the user's own writing and must not read
     * as Thread's description of it.
     */
    private fun describePlace(state: TaskState): String? {
        val place = state.place ?: return null
        val where = place.documentName?.let { "$it, line ${place.line}" }
            ?: "Line ${place.line}"
        return "$where - \u201c${place.snippet}\u201d"
    }

    fun pin(state: TaskState, triggeredBy: Score?): Offer.Pin? {        val lookup = state.mostFetchedLookup() ?: return null
        return Offer.Pin(label = lookup.label, value = lookup.value, triggeredBy = triggeredBy)
    }

    fun errorExplanation(state: TaskState, triggeredBy: Score?): Offer.ErrorExplanation? {
        val (message, count) = state.repeatedError() ?: return null
        return Offer.ErrorExplanation(message = message, occurrences = count, triggeredBy = triggeredBy)
    }
}
