package com.thread.app.service

import android.util.Log
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
                "editable=${node.isEditable} clickable=${node.isClickable}",
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, depth + 1, max) }
        }
    }
}
