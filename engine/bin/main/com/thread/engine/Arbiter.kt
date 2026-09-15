package com.thread.engine

import com.thread.engine.model.Offer
import com.thread.engine.model.OfferKind
import com.thread.engine.model.OfferOutcome
import com.thread.engine.model.OfferRecord

/**
 * Decides what the user is *not* shown.
 *
 * This is the most important class in the engine, and the restraint it enforces
 * is the design - not a safety net bolted on afterwards. Competing prompts
 * fired at an already-overloaded person are precisely the harm Thread exists to
 * prevent, so:
 *
 *  - one offer at a time, never stacked
 *  - a cooldown between offers
 *  - two dismissals of a kind and it goes quiet for the rest of the task
 *  - "don't show this again" is permanent, immediately, no confirmation
 *
 * Tuned for precision, not recall. A missed offer costs a little; an unwanted one
 * costs trust, and trust is not recoverable within a single session.
 */
class Arbiter(
    private val weights: Weights = Weights.DEFAULT,
) {
    private val dismissals = mutableMapOf<OfferKind, Int>()
    private val suppressed = mutableSetOf<OfferKind>()
    private val history = mutableListOf<OfferRecord>()

    private var lastOfferAt: Long? = null
    private var activeOffer: Offer? = null

    /**
     * Picks at most one offer from the candidates, in the order Triggers produced
     * them. Returns null when the right answer is to stay silent.
     */
    fun select(candidates: List<Offer>, now: Long): Offer? {
        if (activeOffer != null) return null
        if (inCooldown(now)) return null

        val chosen = candidates.firstOrNull { it.kind.isAllowed() } ?: return null

        activeOffer = chosen
        lastOfferAt = now
        return chosen
    }

    fun record(kind: OfferKind, outcome: OfferOutcome, now: Long) {
        history += OfferRecord(kind, now, outcome)
        activeOffer = null

        when (outcome) {
            OfferOutcome.SUPPRESSED -> suppressed += kind
            OfferOutcome.DISMISSED -> {
                val count = (dismissals[kind] ?: 0) + 1
                dismissals[kind] = count
                // Learn that we are wrong about this user, in this task, right now.
                if (count >= weights.dismissalsBeforeSilence) suppressed += kind
            }
            OfferOutcome.ACCEPTED, OfferOutcome.IGNORED -> Unit
        }
    }

    /**
     * The user tapped the dot and asked directly.
     *
     * Always honoured: no cooldown, no threshold, no suppression check. Detection
     * is an assist, not a gatekeeper - if someone asks where they were, they get
     * an answer. This path is why the product still works when the detector is
     * wrong, and why the user keeps their agency over it.
     */
    fun userRequested(offer: Offer, now: Long): Offer {
        activeOffer = offer
        lastOfferAt = now
        return offer
    }

    fun dismissActive(now: Long) {
        activeOffer?.let { record(it.kind, OfferOutcome.DISMISSED, now) }
    }

    /** Accepted over shown. The headline quality metric - reported as precision. */
    fun precision(): Double {
        val shown = history.count { it.outcome != OfferOutcome.IGNORED }
        if (shown == 0) return 0.0
        return history.count { it.outcome == OfferOutcome.ACCEPTED }.toDouble() / shown
    }

    fun offersShown(): Int = history.size

    fun isSuppressed(kind: OfferKind): Boolean = kind in suppressed

    fun activeOffer(): Offer? = activeOffer

    /** Counts only, never content - safe to report without transmitting anything. */
    fun outcomeCounts(): Map<OfferOutcome, Int> =
        history.groupingBy { it.outcome }.eachCount()

    private fun inCooldown(now: Long): Boolean {
        val last = lastOfferAt ?: return false
        return (now - last) / 1000.0 < weights.offerCooldownSeconds
    }

    private fun OfferKind.isAllowed(): Boolean = this !in suppressed
}
