package com.thread.engine.model

/**
 * The record of the *session*, not the document. This is the thing that does not
 * exist in any app today, and it is the reason "where was I?" is answerable.
 *
 * Scope note: a task is bounded by *intent*, not by screen. Navigating A -> B -> C
 * must not reset it, otherwise the memory-impairment scenario (the one this whole
 * product is named for) breaks at exactly the moment it matters.
 *
 * Lifetime: in-memory only, discarded on [TaskCommitted] or abandonment. Nothing
 * here is ever written to disk unless the user explicitly opts into a multi-session
 * task, and then only locally.
 */
data class TaskState(
    /** Ephemeral, random. Never tied to user identity. */
    val taskId: String,
    /** Human-readable goal, e.g. "Submitting Q3 travel claim". */
    val intent: String,
    val currentScreenId: String,
    val startedAt: Long,
    /** 0..1. Drives the mid-task peak in context loss. */
    val progress: Double = 0.0,
    val completed: List<FieldSnapshot> = emptyList(),
    val decisions: List<Decision> = emptyList(),
    val nextAction: String? = null,
    val interruptions: List<Interruption> = emptyList(),
    /** screenId -> times entered. Drives orbit detection. */
    val visitCounts: Map<String, Int> = emptyMap(),
    /** normalised error message -> times seen. Drives the error-loop offer. */
    val errorCounts: Map<String, Int> = emptyMap(),
    /** Values the user left the app to read. Candidates for pinning. */
    val lookups: List<ValueLookup> = emptyList(),
    /** Where in a document the user was writing. Null unless the app exposes it. */
    val place: DocumentPlace? = null,
    val lastEventAt: Long = startedAt,
) {
    val interruptionCount: Int get() = interruptions.size

    fun screensVisited(): Int = visitCounts.size

    fun actionsCommitted(): Int = completed.size

    /** Most recently repeated error, if any error has been seen [min] or more times. */
    fun repeatedError(min: Int = 3): Pair<String, Int>? =
        errorCounts.entries.firstOrNull { it.value >= min }?.toPair()

    /** The value fetched most often from elsewhere - the thing worth pinning. */
    fun mostFetchedLookup(min: Int = 2): ValueLookup? =
        lookups.groupBy { it.label }
            .entries
            .filter { it.value.size >= min }
            .maxByOrNull { it.value.size }
            ?.value
            ?.last()
}

data class FieldSnapshot(
    val fieldId: String,
    val label: String,
    val value: String,
    val committedAt: Long,
)

/**
 * The line someone was writing, and roughly where it sat.
 *
 * A document is the one place where naming the screen tells the user nothing:
 * forty pages are all "Word", so "you were in Word" is a fact they already have.
 * What an interruption actually takes is the half-formed sentence, so that is
 * what this holds.
 *
 * Deliberately not the document. [snippet] is a bounded window around the cursor
 * and nothing else is kept, because the point is to hand back the thread of a
 * thought, not to make a copy of someone's writing in a process they did not
 * choose to trust. Like everything else in [TaskState] it lives in memory only.
 */
data class DocumentPlace(
    /** 1-based, as a human counts lines. */
    val line: Int,
    val snippet: String,
    /** Null when the app exposes a cursor but not a name for what it is in. */
    val documentName: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * A choice the user made. Restoring *what* they did is useful; restoring *why*
 * is what stops them re-deliberating the same decision after an interruption.
 */
data class Decision(
    val fieldId: String,
    val label: String,
    val value: String,
    val atProgress: Double,
    val decidedAt: Long,
)

data class Interruption(
    val leftAt: Long,
    val returnedAt: Long?,
    val progressAtExit: Double,
    val toPackage: String?,
) {
    val durationSeconds: Double
        get() = returnedAt?.let { (it - leftAt) / 1000.0 } ?: 0.0
}
