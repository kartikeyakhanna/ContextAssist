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
            TodoItem("review-notes", "Review the existing notes", estimateMinutes = 5),
            TodoItem("draft-report", "Draft the report", estimateMinutes = 20),
            TodoItem("review-report", "Review the final report", estimateMinutes = 10),
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
    fun `timer can be started paused and counted down`() {
        val started = breakdown().toggleTimer("review-notes")
        assertTrue(started.items.first().isTimerRunning)

        val ticked = started.tickTimer("review-notes")
        assertEquals(299, ticked.items.first().remainingSeconds)

        val paused = ticked.toggleTimer("review-notes")
        assertFalse(paused.items.first().isTimerRunning)
        assertEquals(299, paused.items.first().remainingSeconds)
    }

    @Test
    fun `starting another timer pauses the current timer`() {
        val switched = breakdown()
            .toggleTimer("review-notes")
            .toggleTimer("draft-report")

        assertFalse(switched.items[0].isTimerRunning)
        assertTrue(switched.items[1].isTimerRunning)
    }

    @Test
    fun `completing a step pauses its timer`() {
        val completed = breakdown()
            .toggleTimer("review-notes")
            .toggleItem("review-notes")

        assertTrue(completed.items.first().isCompleted)
        assertFalse(completed.items.first().isTimerRunning)
    }

    @Test
    fun `total estimate sums every step`() {
        assertEquals(35, breakdown().totalEstimateMinutes)
    }

    @Test
    fun `validated breakdown rejects malformed model output`() {
        assertFailsWith<IllegalArgumentException> {
            validatedTaskBreakdown(
                title = "Too short",
                items = listOf(TodoItem("only-step", "Only one step", estimateMinutes = 5)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validatedTaskBreakdown(
                title = "Duplicate ids",
                items = listOf(
                    TodoItem("same", "First", estimateMinutes = 5),
                    TodoItem("same", "Second", estimateMinutes = 5),
                    TodoItem("third", "Third", estimateMinutes = 5),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validatedTaskBreakdown(
                title = "Invalid estimate",
                items = listOf(
                    TodoItem("first", "First", estimateMinutes = 0),
                    TodoItem("second", "Second", estimateMinutes = 5),
                    TodoItem("third", "Third", estimateMinutes = 5),
                ),
            )
        }
    }
}
