package com.thread.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.thread.engine.scores.LiveFacts

/**
 * Flattens a live accessibility tree into the handful of properties [LiveFacts]
 * needs. All Android-specific awkwardness is contained here.
 */
object LiveComplexity {

    /**
     * Bounds on the walk. A deep tree traversal runs on the main thread of the
     * accessibility service, and an app that is busy is exactly the app the user
     * is struggling with - spending 40ms in here would make the struggle worse.
     */
    private const val MAX_DEPTH = 14
    private const val MAX_NODES = 400

    fun flatten(root: AccessibilityNodeInfo?): List<LiveFacts.VisibleNode> {
        if (root == null) return emptyList()
        val out = ArrayList<LiveFacts.VisibleNode>(64)
        walk(root, 0, out)
        return out
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<LiveFacts.VisibleNode>) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return

        // Nodes the user cannot see are not load. Invisible tabs, off-screen
        // recycler children and collapsed sections all appear in the tree.
        if (!node.isVisibleToUser) return

        out.add(
            LiveFacts.VisibleNode(
                text = node.text?.toString() ?: node.contentDescription?.toString(),
                isClickable = node.isClickable,
                isCheckable = node.isCheckable,
                isEditable = node.isEditable,
            ),
        )

        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            walk(child, depth + 1, out)
        }
    }
}
