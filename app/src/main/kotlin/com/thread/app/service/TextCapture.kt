package com.thread.app.service

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * What the user typed, captured from apps that tell Thread nothing.
 *
 * This is the Tier 1 answer to "I searched for something and Thread did not know".
 * A text field raises TYPE_VIEW_TEXT_CHANGED on every keystroke, so the naive
 * version records "d", "de", "del"... and a card built from that would show a
 * fragment of a half-formed thought back to someone who had lost their place.
 *
 * So edits are buffered per field and only committed once typing stops. The same
 * debounce the demo app applies to its own fields, applied from the outside.
 */
class TextCapture(
    private val idleMs: Long = 1_200L,
    private val onCommit: (Entry) -> Unit,
) {

    data class Entry(
        val packageName: String,
        val fieldId: String,
        val label: String,
        val value: String,
    )

    private val pending = HashMap<String, Entry>()
    private var lastChangeAt = 0L

    /** Longer than this and it is a document, not a value someone is holding. */
    private val maxValueLength = 120

    fun onTextChanged(pkg: String, e: AccessibilityEvent, now: Long) {
        val source = e.source
        if (source?.isPassword == true) return
        record(pkg, source, e.text.joinToString(" ").trim(), fieldIdOf(source, e), now)
    }

    /**
     * The same capture, driven from the node tree instead of an event.
     *
     * Necessary because TYPE_VIEW_TEXT_CHANGED is not something every app emits.
     * Chrome's omnibox, measured on device, emits none at all while you type - it
     * reports only that the window's contents changed. An implementation built on
     * text-change events therefore works on apps that follow the convention and
     * silently captures nothing on the ones that do not, which is the worst of
     * both worlds: it looks like it works.
     *
     * So the field in focus is read directly. Whatever the user is typing into is,
     * by definition, the input-focused node.
     */
    fun onFocusedNode(pkg: String, node: AccessibilityNodeInfo?, now: Long) {
        val source = node ?: return
        if (source.isPassword || !source.isEditable) return
        record(pkg, source, source.text?.toString()?.trim().orEmpty(), fieldIdOf(source, null), now)
    }

    private fun record(
        pkg: String,
        source: AccessibilityNodeInfo?,
        value: String,
        fieldId: String,
        now: Long,
    ) {
        if (value.isEmpty() || value.length > maxValueLength) return

        // An empty field reports its own hint as its text. Recording that would
        // put "Search or type URL" on the card as something the user did - a
        // confident, plausible, entirely invented memory, handed to the person
        // least able to contradict it.
        if (value.equals(hintOf(source), ignoreCase = true)) return

        val existing = pending[key(pkg, fieldId)]
        if (existing?.value == value) return

        pending[key(pkg, fieldId)] = Entry(
            packageName = pkg,
            fieldId = fieldId,
            label = labelOf(source),
            value = value,
        )
        lastChangeAt = now
    }

    /** Commits anything that has been sitting still long enough to be finished. */
    fun flushIdle(now: Long) {
        if (pending.isEmpty() || now - lastChangeAt < idleMs) return
        flushAll()
    }

    /**
     * Commits everything immediately.
     *
     * Called when the user leaves the app, which is the one moment a half-typed
     * value is worth keeping: they did not stop typing because they finished, they
     * stopped because something took them away - and that fragment is exactly what
     * they will have lost.
     */
    fun flushAll() {
        pending.values.forEach(onCommit)
        pending.clear()
    }

    fun forget(packageName: String) {
        pending.keys.removeAll { it.startsWith("$packageName|") }
    }

    fun clear() = pending.clear()

    private fun key(pkg: String, fieldId: String) = "$pkg|$fieldId"

    private fun fieldIdOf(source: AccessibilityNodeInfo?, e: AccessibilityEvent?): String =
        source?.viewIdResourceName?.substringAfterLast('/')
            ?: e?.className?.toString()?.substringAfterLast('.')
            ?: source?.className?.toString()?.substringAfterLast('.')
            ?: "field"

    private fun hintOf(source: AccessibilityNodeInfo?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            source?.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return source?.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * A human label for the field, or none.
     *
     * Falls back to "Typed" rather than to the view id: "et_q_search_box" on a card
     * is worse than no label at all, and the value itself usually carries the
     * meaning anyway.
     */
    private fun labelOf(source: AccessibilityNodeInfo?): String {
        val described = source?.contentDescription?.toString()?.trim()
        if (!described.isNullOrEmpty() && described.length <= 40) return described

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hint = source?.hintText?.toString()?.trim()
            if (!hint.isNullOrEmpty() && hint.length <= 40) return hint
        }
        return "Typed"
    }
}
