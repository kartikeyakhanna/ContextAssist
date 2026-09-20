package com.thread.worddemo

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionFormattingTest {

    @Test
    fun `formatting applies only to selected text`() {
        val ranges = toggleFormat(
            ranges = emptyList(),
            selection = TextRange(5, 10),
            format = DemoTextFormat.Bold,
        )

        assertEquals(listOf(FormatRange(5, 10, DemoTextFormat.Bold)), ranges)
        assertTrue(selectionHasFormat(ranges, TextRange(5, 10), DemoTextFormat.Bold))
        assertFalse(selectionHasFormat(ranges, TextRange(0, 10), DemoTextFormat.Bold))
    }

    @Test
    fun `turning formatting off preserves text outside selection`() {
        val ranges = toggleFormat(
            ranges = listOf(FormatRange(0, 20, DemoTextFormat.Highlight)),
            selection = TextRange(5, 15),
            format = DemoTextFormat.Highlight,
        )

        assertEquals(
            listOf(
                FormatRange(0, 5, DemoTextFormat.Highlight),
                FormatRange(15, 20, DemoTextFormat.Highlight),
            ),
            ranges,
        )
    }

    @Test
    fun `cutting selected text shifts later formatting`() {
        val ranges = remapFormatRanges(
            ranges = listOf(
                FormatRange(2, 6, DemoTextFormat.Bold),
                FormatRange(12, 18, DemoTextFormat.Italic),
            ),
            replacedRange = TextRange(4, 10),
            insertedLength = 0,
            textLength = 14,
        )

        assertEquals(
            listOf(
                FormatRange(2, 4, DemoTextFormat.Bold),
                FormatRange(6, 12, DemoTextFormat.Italic),
            ),
            ranges,
        )
    }
}
