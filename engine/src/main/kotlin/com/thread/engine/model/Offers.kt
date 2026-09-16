package com.thread.engine.model

/**
 * What the user actually sees. There are only six, deliberately.
 *
 * Every offer is additive and reversible. None of them mutate the underlying app,
 * hide options, or block interaction. That constraint is the product, not a
 * limitation of it.
 */
sealed interface Offer {
    val kind: OfferKind
    val triggeredBy: Score?

    /**
     * S5 / S1: restore the thread after an interruption. The hero.
     *
     * [done], [decided] and [next] are all nullable because they come from an app
     * that chose to describe its own task. Without that, Thread still knows the
     * user left and came back, and how long for - and a card that says only that
     * is honest. One that fills the gap with "Nothing filled in yet" would be
     * telling someone who had been working for ten minutes that they had done
     * nothing, which is worse than silence.
     */
    data class Resumption(
        val intent: String,
        val done: String?,
        val decided: String?,
        val next: String?,
        /** "You were away for 4 minutes" - all Tier 1 can offer, and not nothing. */
        val awayFor: String? = null,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.RESUMPTION
    }

    /** C1: working-memory prosthetic. The value they keep leaving to fetch. */
    data class Pin(
        val label: String,
        val value: String,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.PIN
    }

    /** C1: the same error keeps happening. Explain the error, not the screen. */
    data class ErrorExplanation(
        val message: String,
        val occurrences: Int,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.ERROR_EXPLANATION
    }

    /** C2: reduce the stakes of choosing. Never reduce the options. */
    data class DefaultHint(
        val suggestion: String,
        val reversibility: String,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.DEFAULT_HINT
    }

    /** C3: task initiation support. A lens over the form, never a deletion. */
    data class SequencingMode(
        val sectionCount: Int,
        val estimateMinutes: Int,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.SEQUENCING
    }

    /** Commit-point freeze: anxiety reduction, in plain language. */
    data class Reassurance(
        val consequence: String,
        val undoWindowSeconds: Int,
        override val triggeredBy: Score?,
    ) : Offer {
        override val kind get() = OfferKind.REASSURANCE
    }
}

enum class OfferKind {
    RESUMPTION,
    PIN,
    ERROR_EXPLANATION,
    DEFAULT_HINT,
    SEQUENCING,
    REASSURANCE,
}

enum class OfferOutcome {
    ACCEPTED,
    DISMISSED,
    /** Shown, neither accepted nor dismissed before it became irrelevant. */
    IGNORED,
    /** "Don't show this again" - permanently suppresses this kind for the task. */
    SUPPRESSED,
}

/**
 * The only thing worth logging. Counts, never content - so the accepted/dismissed
 * ratio can be reported without transmitting anything about the user's work.
 */
data class OfferRecord(
    val kind: OfferKind,
    val shownAt: Long,
    val outcome: OfferOutcome,
)
