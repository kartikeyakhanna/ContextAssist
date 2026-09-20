package com.thread.app.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.thread.app.tools.BreakdownContext

internal object SelectionCapture {

    fun fromEvent(event: AccessibilityEvent): String? {
        val source = runCatching { event.source }.getOrNull()
        fromNode(source)?.let { return it }

        return fromEventText(
            values = event.text.map(CharSequence::toString),
            start = event.fromIndex,
            end = event.toIndex,
        )
    }

    fun fromNode(node: AccessibilityNodeInfo?): String? {
        val source = node ?: return null
        if (source.isPassword) return null
        return selectedSubstring(
            text = source.text?.toString().orEmpty(),
            start = source.textSelectionStart,
            end = source.textSelectionEnd,
        )
    }

    internal fun selectedSubstring(text: String, start: Int, end: Int): String? {
        if (start < 0 || end <= start || end > text.length) return null
        return text
            .substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(BreakdownContext.MAX_SELECTED_TEXT_LENGTH)
            .takeIf { it.isNotEmpty() }
    }

    internal fun fromEventText(values: List<String>, start: Int, end: Int): String? {
        values.forEach { value ->
            selectedSubstring(value, start, end)?.let { return it }
        }

        val selectedLength = end - start
        if (selectedLength <= 0) return null
        return values
            .asSequence()
            .map(::normalize)
            .firstOrNull { it.length == selectedLength }
    }

    private fun normalize(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(BreakdownContext.MAX_SELECTED_TEXT_LENGTH)
}
