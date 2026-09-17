package com.thread.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.thread.app.tools.BreakdownContext
import java.util.ArrayDeque

object ScreenContextCollector {

    private const val MAX_NODES = 250

    fun capture(
        root: AccessibilityNodeInfo?,
        appName: String,
        expectedPackage: String,
    ): BreakdownContext {
        val labels = ArrayList<String>(BreakdownContext.MAX_VISIBLE_LABELS)
        val seen = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        var totalLength = 0
        var visited = 0

        fun addCandidate(candidate: CharSequence?) {
            val label = normalize(candidate?.toString()) ?: return
            val key = label.lowercase()
            if (!seen.add(key)) return
            if (totalLength + label.length > BreakdownContext.MAX_TOTAL_LABEL_LENGTH) return
            labels += label
            totalLength += label.length
        }

        if (root?.packageName?.toString() == expectedPackage) {
            queue.add(root)
        }

        while (
            queue.isNotEmpty() &&
            visited < MAX_NODES &&
            labels.size < BreakdownContext.MAX_VISIBLE_LABELS
        ) {
            val node = queue.removeFirst()
            visited += 1

            if (!node.isVisibleToUser || node.isPassword) continue

            if (node.isEditable) {
                val childLabels = buildList {
                    for (index in 0 until node.childCount) {
                        val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                        if (!child.isVisibleToUser || child.isPassword || child.isEditable) continue
                        add(child.text)
                        add(child.contentDescription)
                        add(child.hintText)
                    }
                }
                FieldContextPolicy.labelsForEditableField(
                    enteredText = node.text,
                    hint = node.hintText,
                    contentDescription = node.contentDescription,
                    directChildLabels = childLabels,
                ).forEach(::addCandidate)
                continue
            }

            listOf(node.text, node.contentDescription, node.hintText).forEach(::addCandidate)

            if (labels.size < BreakdownContext.MAX_VISIBLE_LABELS) {
                for (index in 0 until node.childCount) {
                    runCatching { node.getChild(index) }.getOrNull()?.let(queue::add)
                }
            }
        }

        return BreakdownContext(
            appName = normalize(appName)?.take(BreakdownContext.MAX_APP_NAME_LENGTH)
                ?: "Current app",
            visibleLabels = labels,
        )
    }

    private fun normalize(value: String?): String? {
        val normalized = value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(BreakdownContext.MAX_LABEL_LENGTH)
            .orEmpty()
        return normalized.takeIf { it.length >= 2 }
    }
}
