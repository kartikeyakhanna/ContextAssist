package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskBreakdownTest {

    private fun breakdown(): TaskBreakdown = validatedTaskBreakdown(
        title = "Prepare the report",
        items = listOf(
            TodoItem("review-notes", "Review the existing notes"),
            TodoItem("draft-report", "Draft the report"),
            TodoItem("review-report", "Review the final report"),
        ),
    )

    @Test
    fun `items can be completed and restored`() {
        val original = breakdown()
        val completed = original.toggleItem("review-notes")

        assertEquals(1, completed.completedCount)
        assertTrue(completed.items.first { it.id == "review-notes" }.isCompleted)
        assertFalse(completed.toggleItem("review-notes").items.first().isCompleted)
    }

    @Test
    fun `list can be collapsed without losing completion`() {
        val completed = breakdown().toggleItem("review-notes")
        val collapsed = completed.toggleExpanded()

        assertFalse(collapsed.isExpanded)
        assertTrue(collapsed.items.first { it.id == "review-notes" }.isCompleted)
    }

    @Test
    fun `validated breakdown rejects malformed model output`() {
        assertFailsWith<IllegalArgumentException> {
            validatedTaskBreakdown(
                title = "Too short",
                items = listOf(TodoItem("only-step", "Only one step")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validatedTaskBreakdown(
                title = "Duplicate ids",
                items = listOf(
                    TodoItem("same", "First"),
                    TodoItem("same", "Second"),
                    TodoItem("third", "Third"),
                ),
            )
        }
    }
}
