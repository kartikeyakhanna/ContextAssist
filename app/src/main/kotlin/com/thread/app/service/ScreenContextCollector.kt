package com.thread.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.thread.app.tools.BreakdownContext
import com.thread.app.tools.DocumentContext
import java.util.ArrayDeque

object ScreenContextCollector {

    private const val MAX_NODES = 250

    fun capture(
        root: AccessibilityNodeInfo?,
        appName: String,
        expectedPackage: String,
        selectedText: String? = null,
        documentName: String? = null,
        documentText: String? = null,
        readEditableDocumentText: Boolean = false,
    ): BreakdownContext {
        val labels = ArrayList<String>(BreakdownContext.MAX_VISIBLE_LABELS)
        val seen = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        var totalLength = 0
        var visited = 0
        var detectedSelection = normalizeSelection(selectedText)
        var exposedDocumentText: String? = null

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
            if (detectedSelection == null) {
                detectedSelection = SelectionCapture.fromNode(node)
            }

            if (node.isEditable) {
                if (readEditableDocumentText) {
                    val candidate = normalizeDocumentText(node.text?.toString())
                    if (
                        candidate != null &&
                        candidate.length > exposedDocumentText.orEmpty().length
                    ) {
                        exposedDocumentText = candidate
                    }
                }
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
            selectedText = detectedSelection,
            documentName = normalizeDocumentName(documentName)
                ?: exposedDocumentText?.let { "Open document" },
            documentText = (documentText ?: exposedDocumentText)?.let {
                DocumentContext.excerpt(it, detectedSelection)
            },
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

    private fun normalizeSelection(value: String?): String? =
        value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(BreakdownContext.MAX_SELECTED_TEXT_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    private fun normalizeDocumentName(value: String?): String? =
        value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(BreakdownContext.MAX_DOCUMENT_NAME_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    private fun normalizeDocumentText(value: String?): String? =
        value
            ?.replace(Regex("[\\t ]+"), " ")
            ?.replace(Regex(" *\\n *"), "\n")
            ?.trim()
            ?.take(BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH)
            ?.takeIf { it.length >= 20 }
}
