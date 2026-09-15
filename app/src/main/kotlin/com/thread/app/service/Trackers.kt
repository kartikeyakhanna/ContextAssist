package com.thread.app.service

import com.thread.engine.scores.Cls
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
