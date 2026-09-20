package com.thread.engine

import com.thread.engine.model.AppSwitchAway
import com.thread.engine.model.AppSwitchReturn
import com.thread.engine.model.Decision
import com.thread.engine.model.DocumentPlace
import com.thread.engine.model.FieldCommit
import com.thread.engine.model.FieldSnapshot
import com.thread.engine.model.IdleStart
import com.thread.engine.model.Interruption
import com.thread.engine.model.NavBack
import com.thread.engine.model.ScreenView
import com.thread.engine.model.TaskState
import com.thread.engine.model.ThreadEvent
import com.thread.engine.model.ValidationError
import com.thread.engine.model.ValueLookup

/**
 * Reduces the event stream into a TaskState.
 *
 * Everything here is in-memory and discarded when the task ends. There is no
 * persistence layer anywhere in this repository - the privacy claim is meant to
 * be verifiable by reading the code, not taken on trust from a slide.
 *
 * Scope rule, and it is the one that must not be got wrong: a task is bounded by
 * *intent*, not by screen. Navigating A -> B -> C does not start a new task. If
 * it did, the memory-impairment case - someone landing on C asking "what was I
 * trying to do?" - would find an empty TaskState at exactly the moment it is
 * needed. Only an explicit commit or abandonment ends a task.
 */
class TaskStateBuilder(
    taskId: String,
    intent: String,
    startScreenId: String,
    startedAt: Long,
    /** Total steps expected, used to derive progress. */
    private val expectedSteps: Int = 10,
) {
    var state: TaskState = TaskState(
        taskId = taskId,
        intent = intent,
        currentScreenId = startScreenId,
        startedAt = startedAt,
        visitCounts = mapOf(startScreenId to 1),
    )
        private set

    /** True between an AppSwitchAway and the matching return. */
    val isAway: Boolean
        get() = state.interruptions.lastOrNull()?.returnedAt == null &&
            state.interruptions.isNotEmpty()

    fun apply(event: ThreadEvent): TaskState {
        state = when (event) {
            is ScreenView -> onScreenView(event)
            is FieldCommit -> onFieldCommit(event)
            is ValidationError -> onValidationError(event)
            is ValueLookup -> state.copy(lookups = state.lookups + event)
            is AppSwitchAway -> onAway(event.ts, event.toPackage)
            is IdleStart -> onAway(event.ts, null)
            is AppSwitchReturn -> onReturn(event.ts)
            is NavBack -> state
            else -> state
        }.copy(lastEventAt = event.ts)
        return state
    }

    /** Idle counts as an interruption too - S5 never involves leaving the app. */
    fun onIdleReturn(ts: Long): TaskState {
        state = onReturn(ts).copy(lastEventAt = ts)
        return state
    }

    fun setNextAction(next: String?): TaskState {
        state = state.copy(nextAction = next)
        return state
    }

    /**
     * Records where in a document the user was writing.
     *
     * Only ever replaced, never accumulated: there is one cursor, and keeping a
     * history of everywhere it has been would turn a memory aid into a keystroke
     * log. A null candidate leaves the last known place standing, because moving
     * focus to Thread's own overlay collapses the selection in the app behind it -
     * and that must not erase the thing the user came back for.
     */
    fun setPlace(place: DocumentPlace?): TaskState {
        if (place != null) state = state.copy(place = place)
        return state
    }

    private fun onScreenView(e: ScreenView): TaskState {
        val counts = state.visitCounts.toMutableMap()
        counts[e.screenId] = (counts[e.screenId] ?: 0) + 1
        return state.copy(currentScreenId = e.screenId, visitCounts = counts)
    }

    private fun onFieldCommit(e: FieldCommit): TaskState {
        val snapshot = FieldSnapshot(e.fieldId, e.label, e.value, e.ts)
        val completed = state.completed.filterNot { it.fieldId == e.fieldId } + snapshot
        val progress = (completed.size.toDouble() / expectedSteps).coerceIn(0.0, 1.0)

        val decisions = if (e.isDecision) {
            state.decisions.filterNot { it.fieldId == e.fieldId } +
                Decision(e.fieldId, e.label, e.value, progress, e.ts)
        } else {
            state.decisions
        }

        return state.copy(completed = completed, decisions = decisions, progress = progress)
    }

    private fun onValidationError(e: ValidationError): TaskState {
        val key = normaliseError(e.message)
        val counts = state.errorCounts.toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        return state.copy(errorCounts = counts)
    }

    private fun onAway(ts: Long, toPackage: String?): TaskState {
        if (isAway) return state
        return state.copy(
            interruptions = state.interruptions + Interruption(
                leftAt = ts,
                returnedAt = null,
                progressAtExit = state.progress,
                toPackage = toPackage,
            ),
        )
    }

    private fun onReturn(ts: Long): TaskState {
        val open = state.interruptions.lastOrNull() ?: return state
        if (open.returnedAt != null) return state
        val closed = open.copy(returnedAt = ts)
        return state.copy(interruptions = state.interruptions.dropLast(1) + closed)
    }

    /**
     * Errors are grouped by shape, not exact text, so "must be after 14 Sep" and
     * "must be after 15 Sep" are recognised as the same wall being hit twice.
     */
    private fun normaliseError(message: String): String =
        message.lowercase()
            .replace(Regex("[0-9]+"), "#")
            .replace(Regex("\\s+"), " ")
            .trim()
}
