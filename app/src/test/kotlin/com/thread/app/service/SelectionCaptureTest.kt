package com.thread.app.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionCaptureTest {

    @Test
    fun `extracts and normalizes selected text`() {
        assertEquals(
            "selected words",
            SelectionCapture.selectedSubstring("before selected\nwords after", 7, 21),
        )
    }

    @Test
    fun `rejects collapsed or invalid selections`() {
        assertNull(SelectionCapture.selectedSubstring("text", 2, 2))
        assertNull(SelectionCapture.selectedSubstring("text", -1, 2))
        assertNull(SelectionCapture.selectedSubstring("text", 1, 8))
    }

    @Test
    fun `accepts event text that contains only the selected value`() {
        assertEquals(
            "selected words",
            SelectionCapture.fromEventText(
                values = listOf("selected words"),
                start = 40,
                end = 54,
            ),
        )
    }
}
