package com.thread.sdk

import android.content.Context
import android.content.Intent

/**
 * Tier 2. What an app includes if it wants Thread to be accurate rather than
 * inferred.
 *
 * Tier 1 - the accessibility service - works on any app with no integration at
 * all, but it has to *guess* from the node tree what a screen means: which text
 * was a label, whether a value was committed or merely typed, whether a choice
 * was a decision or a stray tap. Most of the time the guess is good. Sometimes
 * it is wrong, and a resumption card built on a wrong guess is worse than none.
 *
 * This is the whole SDK. Six calls, no initialisation, no keys, no callbacks,
 * and nothing returned - an app cannot read anything back, and Thread stores
 * nothing about the app. That asymmetry is intentional: integrating is meant to
 * be a five-minute decision with no ongoing obligation.
 *
 * Nothing here is required. Thread degrades to Tier 1 if it is never called.
 */
object ThreadSdk {

    private const val ACTION = "com.thread.sdk.EVENT"
    private const val THREAD_PACKAGE = "com.thread.app"

    /**
     * The user began something with a goal. [intent] should be phrased the way
     * the user would say it out loud - "Submitting the Q3 travel request", not
     * "TravelRequestFlow" - because it is read back to them verbatim when they
     * return, possibly hours later.
     */
    fun startTask(context: Context, intent: String, screen: String) =
        send(context, "task_start") {
            putExtra("intent", intent)
            putExtra("screen", screen)
        }

    /** A value was actually committed, not merely typed. */
    fun fieldCommitted(
        context: Context,
        screen: String,
        fieldId: String,
        label: String,
        value: String,
    ) = send(context, "field_commit") {
        putExtra("screen", screen)
        putExtra("field", fieldId)
        putExtra("label", label)
        putExtra("value", value)
    }

    /**
     * A choice the user deliberated over and settled.
     *
     * Reported separately from an ordinary commit because it is the single most
     * valuable thing to restore. Telling someone what they filled in is useful;
     * telling them what they *decided* is what stops them reopening a question
     * they already closed before the interruption.
     */
    fun decisionMade(
        context: Context,
        screen: String,
        label: String,
        value: String,
    ) = send(context, "decision") {
        putExtra("screen", screen)
        putExtra("label", label)
        putExtra("value", value)
    }

    fun validationFailed(
        context: Context,
        screen: String,
        fieldId: String,
        message: String,
    ) = send(context, "validation_error") {
        putExtra("screen", screen)
        putExtra("field", fieldId)
        putExtra("message", message)
    }

    /**
     * Focus landed on something that cannot be taken back. Drives the one-line
     * reassurance chip, which exists because commit hesitation is often not
     * indecision - it is the absence of a stated consequence.
     */
    fun irreversibleAction(
        context: Context,
        screen: String,
        actionId: String,
        consequence: String,
        undoWindowSeconds: Int,
    ) = send(context, "irreversible") {
        putExtra("screen", screen)
        putExtra("field", actionId)
        putExtra("message", consequence)
        putExtra("undoSeconds", undoWindowSeconds)
    }

    /**
     * The task reached its terminal action.
     *
     * This is the call that matters most for privacy: everything Thread was
     * holding is dropped the moment this arrives. There is no history to review
     * and no store to leak, because there is no store.
     */
    fun taskCompleted(context: Context, screen: String) =
        send(context, "task_end") { putExtra("screen", screen) }

    private fun send(context: Context, type: String, build: Intent.() -> Unit) {
        val intent = Intent(ACTION).apply {
            setPackage(THREAD_PACKAGE)
            putExtra("type", type)
            build()
        }
        runCatching { context.sendBroadcast(intent) }
    }
}
