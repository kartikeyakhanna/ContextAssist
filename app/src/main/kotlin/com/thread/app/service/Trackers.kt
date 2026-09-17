package com.thread.app.service

import com.thread.engine.scores.Cls
import com.thread.engine.scores.DsFreeze
import com.thread.engine.scores.DsOrbit

/**
 * Watches the sixty seconds after the user comes back.
 *
 * The distinction being drawn: someone who returns and immediately types is fine;
 * someone who returns and scrolls, re-reads, opens a field and closes it again is
 * rebuilding context. Both look like "activity" - only the second is struggle.
 */
class PostReturnWatcher {

    private var returnedAt: Long? = null
    private var firstProductiveActionAt: Long? = null
    private var scrollReversals = 0
    private var focusEvents = 0
    private var editsSinceFocus = 0
    private var focusWithoutEdit = 0

    fun begin(now: Long) {
        returnedAt = now
        firstProductiveActionAt = null
        scrollReversals = 0
        focusEvents = 0
        focusWithoutEdit = 0
        editsSinceFocus = 0
    }

    fun onScrollReversal() {
        if (returnedAt != null) scrollReversals++
    }

    fun onFocus(now: Long) {
        if (returnedAt == null) return
        // The previous focus ended without an edit: re-reading, not working.
        if (focusEvents > 0 && editsSinceFocus == 0) focusWithoutEdit++
        focusEvents++
        editsSinceFocus = 0
    }

    fun onProductiveAction(now: Long) {
        if (returnedAt == null) return
        editsSinceFocus++
        if (firstProductiveActionAt == null) firstProductiveActionAt = now
    }

    fun signals(now: Long): Cls.PostReturnSignals {
        val start = returnedAt ?: return Cls.PostReturnSignals()
        val end = firstProductiveActionAt ?: now
        return Cls.PostReturnSignals(
            scrollReversals = scrollReversals,
            focusWithoutEdit = focusWithoutEdit,
            secondsBeforeFirstAction = (end - start) / 1000.0,
            navBacks = 0,
        )
    }

    fun clear() {
        returnedAt = null
    }
}

/**
 * Tracks the shape of movement through the app: dead ends, scroll direction
 * changes, and how long it has been since anything was actually committed.
 */
class OrbitTracker {

    private var lastScrollDirectionDown: Boolean? = null
    private var lastScrollAt = 0L
    private var currentScreenEnteredAt = 0L
    private var currentScreenInteracted = false
    private var deadEndReturns = 0
    private var lastCommitAt = 0L

    /** A visit shorter than this with no interaction is a dead end, not a visit. */
    private val deadEndThresholdMs = 4_000L

    fun onScreen(screenId: String, now: Long) {
        if (currentScreenEnteredAt > 0 &&
            !currentScreenInteracted &&
            now - currentScreenEnteredAt < deadEndThresholdMs
        ) {
            deadEndReturns++
        }
        currentScreenEnteredAt = now
        currentScreenInteracted = false
    }

    fun onInteraction(now: Long) {
        currentScreenInteracted = true
    }

    fun onCommit(now: Long) {
        lastCommitAt = now
        currentScreenInteracted = true
    }

    /**
     * A direction change is the signal; continuous scrolling one way is reading.
     *
     * Two guards, both there to avoid inventing evidence. The burst debounce stops
     * a single flick - which arrives as a stream of events - being counted many
     * times over. The direction check means the first scroll after arriving on a
     * screen is never a reversal, because there is nothing yet to reverse.
     */
    fun isReversal(scrollingDown: Boolean, now: Long): Boolean {
        if (now - lastScrollAt < 300) {
            lastScrollAt = now
            return false
        }
        lastScrollAt = now

        val previous = lastScrollDirectionDown
        lastScrollDirectionDown = scrollingDown
        return previous != null && previous != scrollingDown
    }

    fun signals(now: Long): DsOrbit.OrbitSignals = DsOrbit.OrbitSignals(
        deadEndReturns = deadEndReturns,
        zeroProgressSeconds = if (lastCommitAt == 0L) 0.0 else (now - lastCommitAt) / 1000.0,
    )

    fun reset() {
        deadEndReturns = 0
        lastCommitAt = 0L
        currentScreenEnteredAt = 0L
        currentScreenInteracted = false
        lastScrollDirectionDown = null
        lastScrollAt = 0L
    }
}

/**
 * Watches a single screen for someone who is scanning it and choosing nothing.
 *
 * The distinction this has to draw is between *reading* and *stuck*, and the only
 * thing that separates them is time against what the screen usually takes. So it
 * measures dwell as a multiple of a baseline rather than in absolute seconds -
 * fifteen seconds on a confirmation dialog is a long time, and on a form it is
 * nothing at all.
 *
 * Resets on every screen change. Freeze is a property of standing still in one
 * place; carrying it across screens would turn ordinary navigation into evidence
 * of struggle.
 */
class FreezeWatcher {

    private var screenId: String? = null
    private var enteredAt = 0L
    private var selected = false
    private val scanned = HashSet<String>()

    fun onScreen(screenId: String, now: Long) {
        if (screenId == this.screenId) return
        this.screenId = screenId
        enteredAt = now
        selected = false
        scanned.clear()
    }

    /**
     * Looked at an option. Deduplicated by label, because the signal being sought
     * is breadth - how many different things were weighed - and focus events
     * bounce between the same two controls often enough that counting repeats
     * would make indecision look like far more of it than there was.
     */
    fun onScan(key: String?) {
        if (key.isNullOrBlank() || selected) return
        scanned += key
    }

    /** Anything committed clears the suspicion entirely. They chose. */
    fun onSelect() {
        selected = true
    }

    /**
     * Still worth another look: they are here, and they have not chosen.
     *
     * Stopping once something is selected matters more than it looks. Watching a
     * screen indefinitely is a poll of the user's display, and the moment they act
     * there is nothing left to detect - continuing would be collecting for its own
     * sake, which is the thing Thread has to be able to say it does not do.
     */
    fun stillWatching(now: Long, windowMs: Long): Boolean {
        if (selected || enteredAt == 0L) return false
        return now - enteredAt < windowMs
    }

    fun signals(
        now: Long,
        optionCount: Int,
        irreversiblePresent: Boolean,
        baselineSeconds: Double = DEFAULT_BASELINE_SECONDS,
    ): DsFreeze.FreezeSignals = DsFreeze.FreezeSignals(
        secondsOnScreen = if (enteredAt == 0L) 0.0 else (now - enteredAt) / 1000.0,
        baselineSecondsForScreen = baselineSeconds,
        optionsScanned = scanned.size,
        // Not collected. A dropdown opening is a window change indistinguishable
        // from a dialog, and guessing would put weight on a number that is not a
        // measurement. Zero costs at most 20 of 100 and is honest.
        openCloseLoops = 0,
        anythingSelected = selected,
        optionCount = optionCount,
        irreversibleActionPresent = irreversiblePresent,
    )

    fun reset() {
        screenId = null
        enteredAt = 0L
        selected = false
        scanned.clear()
    }

    companion object {
        /**
         * How long a screen is assumed to take when nothing better is known.
         *
         * Per-screen medians would be better and need a population to learn from,
         * which Thread does not have and will not collect. Until then this is one
         * number applied everywhere, and it is the reason freeze is reported as
         * the weakest of the four scores.
         */
        const val DEFAULT_BASELINE_SECONDS = 30.0
    }
}
