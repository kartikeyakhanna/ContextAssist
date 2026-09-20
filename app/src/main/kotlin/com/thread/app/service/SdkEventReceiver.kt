package com.thread.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thread.engine.model.FieldCommit
import com.thread.engine.model.IrreversibleActionFocused
import com.thread.engine.model.TaskCommitted
import com.thread.engine.model.ThreadEvent
import com.thread.engine.model.ValidationError

/**
 * Receives Tier 2 events from apps that have integrated the SDK.
 *
 * Why this exists alongside the accessibility tree: the tree tells you what is
 * on screen, but not what it meant. It cannot reliably distinguish a value that
 * was committed from one that was typed and abandoned, or a decision the user
 * deliberated over from an incidental tap. Those distinctions are exactly what
 * a resumption card is made of, so an app that can state them plainly produces
 * a better card than one that has to be inferred.
 *
 * This is additive. If nothing ever broadcasts, Thread runs on Tier 1 alone.
 */
class SdkEventReceiver(
    private val onEvent: (ThreadEvent) -> Unit,
    private val onTaskStart: (intent: String, packageName: String, screenId: String) -> Unit,
    private val onTaskEnd: () -> Unit,
    private val onLineBounds: (packageName: String, bounds: android.graphics.Rect?) -> Unit = { _, _ -> },
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val i = intent ?: return
        val now = System.currentTimeMillis()
        val screen = i.getStringExtra("screen").orEmpty()

        when (i.getStringExtra("type")) {
            "task_start" -> onTaskStart(
                i.getStringExtra("intent") ?: "your task",
                screen.substringBefore('/'),
                screen,
            )

            "field_commit" -> onEvent(
                FieldCommit(
                    ts = now,
                    screenId = screen,
                    fieldId = i.getStringExtra("field").orEmpty(),
                    label = i.getStringExtra("label").orEmpty(),
                    value = i.getStringExtra("value").orEmpty(),
                    isDecision = false,
                ),
            )

            "decision" -> onEvent(
                FieldCommit(
                    ts = now,
                    screenId = screen,
                    fieldId = i.getStringExtra("label").orEmpty(),
                    label = i.getStringExtra("label").orEmpty(),
                    value = i.getStringExtra("value").orEmpty(),
                    isDecision = true,
                ),
            )

            "validation_error" -> onEvent(
                ValidationError(
                    ts = now,
                    screenId = screen,
                    fieldId = i.getStringExtra("field").orEmpty(),
                    message = i.getStringExtra("message").orEmpty(),
                ),
            )

            "irreversible" -> onEvent(
                IrreversibleActionFocused(
                    ts = now,
                    screenId = screen,
                    actionId = i.getStringExtra("field").orEmpty(),
                    consequence = i.getStringExtra("message").orEmpty(),
                    undoWindowSeconds = i.getIntExtra("undoSeconds", 0),
                ),
            )

            "task_end" -> {
                onEvent(TaskCommitted(ts = now, screenId = screen))
                onTaskEnd()
            }

            // Where the line being edited currently sits on screen. Reported by
            // the app because the platform cannot be asked: its per-character
            // bounds API is unusable on a Compose text field that scrolls.
            "line_bounds" -> onLineBounds(
                screen.substringBefore('/'),
                if (i.getBooleanExtra("hasBounds", false)) {
                    android.graphics.Rect(
                        i.getIntExtra("left", 0),
                        i.getIntExtra("top", 0),
                        i.getIntExtra("right", 0),
                        i.getIntExtra("bottom", 0),
                    )
                } else {
                    null
                },
            )
        }
    }

    companion object {
        const val ACTION = "com.thread.sdk.EVENT"
    }
}
