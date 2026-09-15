package com.thread.engine.model

/**
 * Everything the collector can observe.
 *
 * Tier 1 (accessibility layer) can produce a subset of these with inferred
 * fidelity; Tier 2 (integrated SDK) produces all of them with semantic accuracy.
 * The engine does not care which tier produced an event.
 */
sealed interface ThreadEvent {
    val ts: Long
    val screenId: String
}

data class ScreenView(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

data class FieldFocus(
    override val ts: Long,
    override val screenId: String,
    val fieldId: String,
    val fieldIndex: Int,
) : ThreadEvent

/** Focus left the field without the value changing. Signal of scatter, not progress. */
data class FieldAbandon(
    override val ts: Long,
    override val screenId: String,
    val fieldId: String,
) : ThreadEvent

data class FieldCommit(
    override val ts: Long,
    override val screenId: String,
    val fieldId: String,
    val label: String,
    val value: String,
    val isDecision: Boolean = false,
) : ThreadEvent

data class ValidationError(
    override val ts: Long,
    override val screenId: String,
    val fieldId: String,
    val message: String,
) : ThreadEvent

data class NavBack(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

data class ScrollReversal(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

/** Hovered/focused an option without selecting it. */
data class OptionScan(
    override val ts: Long,
    override val screenId: String,
    val optionId: String,
) : ThreadEvent

/** A dropdown or dialog was opened and dismissed without a selection. */
data class DialogDismissed(
    override val ts: Long,
    override val screenId: String,
    val dialogId: String,
) : ThreadEvent

/** The user left the app entirely. [toPackage] is null when unknown. */
data class AppSwitchAway(
    override val ts: Long,
    override val screenId: String,
    val toPackage: String?,
) : ThreadEvent

data class AppSwitchReturn(
    override val ts: Long,
    override val screenId: String,
    val fromPackage: String?,
) : ThreadEvent

data class IdleStart(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

data class IdleEnd(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

data class Undo(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

/**
 * A value the user went elsewhere to read. This is what makes the pin chip
 * possible: the engine records what they fetched, not just that they left.
 */
data class ValueLookup(
    override val ts: Long,
    override val screenId: String,
    val label: String,
    val value: String,
    val sourcePackage: String?,
) : ThreadEvent

/** The task reached its terminal action. All state is discarded after this. */
data class TaskCommitted(
    override val ts: Long,
    override val screenId: String,
) : ThreadEvent

/** Focus landed on an action the user cannot undo. Drives the reassurance chip. */
data class IrreversibleActionFocused(
    override val ts: Long,
    override val screenId: String,
    val actionId: String,
    val consequence: String,
    val undoWindowSeconds: Int,
) : ThreadEvent
