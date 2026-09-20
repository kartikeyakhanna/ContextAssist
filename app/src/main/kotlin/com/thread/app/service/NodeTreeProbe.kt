package com.thread.app.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Day-zero spike. Run this before committing to the Excel demo beat.
 *
 * Excel renders its grid on a custom surface, so the accessibility tree for cells
 * and formulas may be sparse or oddly shaped - ribbon and dialogs will expose far
 * more. Two hours with this probe tells you what you actually get, rather than
 * discovering it on day two.
 *
 * What to check, in priority order:
 *  1. Do window-state changes report the Excel package reliably? (If yes, the
 *     resumption beat is safe regardless of everything below.)
 *  2. Is cell or formula-bar text readable as node text? (Decides the pin beat.)
 *  3. Are there stable view IDs to fingerprint a screen with?
 *
 * Usage: enable the service, run `adb logcat -s ThreadProbe`, poke around Excel.
 */
object NodeTreeProbe {

    private const val TAG = "ThreadProbe"

    /**
     * Every event an app emits, with everything attached to it.
     *
     * The node-tree probe answers "what can Thread read if it goes looking".
     * This answers the other half: "what does the app volunteer". They are not
     * the same question, and an app can be silent in one channel and talkative
     * in the other - Excel's grid exposes no nodes at all, so if a cell
     * reference is available anywhere it is here.
     *
     * Deliberately dumps the raw event rather than a tidied summary. The point
     * of a probe is to find the thing nobody thought to look for.
     */
    fun logEvent(event: AccessibilityEvent, pkg: String) {
        val source = runCatching { event.source }.getOrNull()
        Log.i(
            TAG,
            "$pkg ${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                "cls=${event.className?.toString()?.substringAfterLast('.') ?: "-"} " +
                "text=${event.text.joinToString("|").ifBlank { "-" }} " +
                "desc=${event.contentDescription ?: "-"} " +
                "srcId=${source?.viewIdResourceName?.substringAfterLast('/') ?: "-"} " +
                "srcText=${source?.text ?: "-"} " +
                "srcDesc=${source?.contentDescription ?: "-"} " +
                "sel=${source?.textSelectionStart}..${source?.textSelectionEnd} " +
                "editable=${source?.isEditable} " +
                "from=${event.fromIndex} to=${event.toIndex} " +
                "item=${event.currentItemIndex} count=${event.itemCount}",
        )
    }

    fun dump(root: AccessibilityNodeInfo?, maxDepth: Int = 12) {
        if (root == null) {
            Log.w(TAG, "no root node - app exposes nothing to the accessibility layer")
            return
        }
        Log.i(TAG, "--- tree for ${root.packageName} ---")
        walk(root, 0, maxDepth)
    }

    /** Counts used to judge, quickly, whether an app is workable at Tier 1. */
    fun summarise(root: AccessibilityNodeInfo?): Summary {
        val summary = Summary()
        if (root == null) return summary
        collect(root, summary, 0, 12)
        return summary
    }

    data class Summary(
        var totalNodes: Int = 0,
        var withText: Int = 0,
        var withDesc: Int = 0,
        var withViewId: Int = 0,
        var editable: Int = 0,
        var clickable: Int = 0,
    ) {
        /** Rough read on whether field-level signals are viable here. */
        val fieldLevelViable: Boolean get() = editable > 0 && withViewId > totalNodes / 4
    }

    private fun collect(node: AccessibilityNodeInfo, s: Summary, depth: Int, max: Int) {
        if (depth > max) return
        s.totalNodes++
        if (!node.text.isNullOrBlank()) s.withText++
        if (!node.contentDescription.isNullOrBlank()) s.withDesc++
        if (!node.viewIdResourceName.isNullOrBlank()) s.withViewId++
        if (node.isEditable) s.editable++
        if (node.isClickable) s.clickable++
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, s, depth + 1, max) }
        }
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, max: Int) {
        if (depth > max) return
        val pad = "  ".repeat(depth)
        Log.i(
            TAG,
            "$pad${node.className?.toString()?.substringAfterLast('.')} " +
                "id=${node.viewIdResourceName?.substringAfterLast('/') ?: "-"} " +
                "text=${node.text?.take(40) ?: "-"} " +
                "desc=${node.contentDescription?.take(40) ?: "-"} " +
                "editable=${node.isEditable} clickable=${node.isClickable}",
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, depth + 1, max) }
        }
    }
}
