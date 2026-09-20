package com.thread.app.tools

internal object DocumentContext {

    fun excerpt(documentText: String, selectedText: String?): String {
        val normalizedDocument = normalize(documentText)
        if (normalizedDocument.length <= BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH) {
            return normalizedDocument
        }

        val normalizedSelection = normalize(selectedText.orEmpty())
        val selectionIndex = normalizedSelection
            .takeIf { it.length >= MIN_SELECTION_MATCH_LENGTH }
            ?.let { normalizedDocument.indexOf(it, ignoreCase = true) }
            ?: -1

        if (selectionIndex < 0) {
            return normalizedDocument.take(BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH)
        }

        val halfWindow = BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH / 2
        val start = (selectionIndex - halfWindow).coerceAtLeast(0)
        val end = (start + BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH)
            .coerceAtMost(normalizedDocument.length)
        val adjustedStart = (end - BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH).coerceAtLeast(0)
        return normalizedDocument.substring(adjustedStart, end)
    }

    private fun normalize(value: String): String =
        value
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private const val MIN_SELECTION_MATCH_LENGTH = 8
}
