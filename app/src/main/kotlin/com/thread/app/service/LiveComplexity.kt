package com.thread.app.service

import android.graphics.Rect
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

    /**
     * The same tree, plus what the user would see by scrolling.
     *
     * Scoring asks "how much is in front of this person right now", so it stops
     * at the fold. Sequencing asks "what order does this form go in", and the
     * fold is not where the form ends.
     *
     * Widened only inside a scrollable container, and only for nodes that have
     * been given a size. Content the user can bring into view by scrolling is
     * part of this screen; a collapsed section or the page behind a tab is not,
     * and proposing either would send someone looking for a control that is not
     * there.
     *
     * How far this actually reaches depends on the toolkit, and the difference is
     * larger than it looks. A View-based form keeps its off-screen children in
     * the tree, so they are recovered here. Compose does not: content clipped out
     * of a scroll container is absent from the accessibility tree altogether, not
     * merely marked invisible. Measured on the demo expense form as it was built
     * originally - twenty-five stacked options with every field beneath them -
     * the whole screen offered forty-four reachable nodes, none of them pruned,
     * because the rest had never been there to prune. Sequencing correctly
     * reported "not a form".
     *
     * There is no fix for that from this side; the nodes do not exist to be read.
     * It is a reason to prefer compact controls when building a form, which is
     * why the demo now uses a picker, and a reason the SDK path exists for apps
     * that want to be certain.
     */
    fun flattenForm(root: AccessibilityNodeInfo?): List<LiveFacts.VisibleNode> {
        if (root == null) return emptyList()
        val out = ArrayList<LiveFacts.VisibleNode>(64)
        walk(root, 0, out, insideScrollable = false)
        return out
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<LiveFacts.VisibleNode>,
        insideScrollable: Boolean? = null,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return

        // Nodes the user cannot see are not load. Invisible tabs, off-screen
        // recycler children and collapsed sections all appear in the tree.
        val reachable = node.isVisibleToUser ||
            (insideScrollable == true && node.screenBounds() != null)
        if (!reachable) return

        out.add(
            LiveFacts.VisibleNode(
                // Merged, as scoring has always read it - widening the type must
                // not move an SML number.
                text = node.text?.toString() ?: node.contentDescription?.toString(),
                isClickable = node.isClickable,
                isCheckable = node.isCheckable,
                isEditable = node.isEditable,
                // Carried separately for the sequencer, which has to tell a
                // field's label from the value the user typed into it.
                hintText = node.hintText?.toString(),
                contentDescription = node.contentDescription?.toString(),
                isEnabled = node.isEnabled,
                isChecked = node.isChecked,
                bounds = node.screenBounds(),
                className = node.className?.toString(),
            ),
        )

        val childScrollable = insideScrollable?.let { it || node.isScrollable }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            walk(child, depth + 1, out, childScrollable)
        }
    }

    /**
     * Screen coordinates, or null if the node reports none.
     *
     * Null rather than a zero rectangle: the sequencer falls back to tree order
     * for unpositioned nodes, and (0,0) would instead sort them to the top of the
     * screen - putting a control the app never placed at the front of the plan.
     */
    private fun AccessibilityNodeInfo.screenBounds(): LiveFacts.Bounds? {
        val rect = Rect()
        getBoundsInScreen(rect)
        if (rect.isEmpty) return null
        return LiveFacts.Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }
}
