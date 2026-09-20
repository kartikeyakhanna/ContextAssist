package com.thread.worddemo

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypedTextDiffTest {

    @Test
    fun `returns text appended to the document`() {
        assertEquals(" new text", newlyInsertedText("Existing", "Existing new text"))
    }

    @Test
    fun `returns replacement typed over a selection`() {
        assertEquals(
            "inclusive",
            newlyInsertedText(
                "Make the workplace accessible",
                "Make the workplace inclusive",
                TextRange(19, 29),
            ),
        )
    }

    @Test
    fun `ignores deletion-only edits`() {
        assertNull(newlyInsertedText("Remove this text", "Remove text"))
    }

    @Test
    fun `ignores unchanged documents`() {
        assertNull(newlyInsertedText("Same", "Same"))
    }
}
