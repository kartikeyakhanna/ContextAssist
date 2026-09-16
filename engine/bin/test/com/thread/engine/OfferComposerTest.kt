package com.thread.engine

import com.thread.engine.model.Decision
import com.thread.engine.model.FieldSnapshot
import com.thread.engine.model.Interruption
import com.thread.engine.model.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the card is allowed to claim.
 *
 * These exist because of a specific regression risk: once Thread runs on apps
 * that report nothing, "no observed progress" and "no progress" become easy to
 * confuse, and the card is the one place that mistake reaches the user.
 */
class OfferComposerTest {

    private fun state(
        completed: List<FieldSnapshot> = emptyList(),
        decisions: List<Decision> = emptyList(),
        interruptions: List<Interruption> = emptyList(),
    ) = TaskState(
        taskId = "t",
        intent = "what you were doing in Excel",
        currentScreenId = "com.microsoft.office.excel/Workbook",
        startedAt = 0L,
        completed = completed,
        decisions = decisions,
        interruptions = interruptions,
    )

    @Test
    fun `an app that reports nothing produces no Done line at all`() {
        val offer = OfferComposer.resumption(state(), triggeredBy = null)

        assertNull(
            offer.done,
            "an unobserved task must not be described as an empty one",
        )
    }

    @Test
    fun `time away is offered even when nothing else is known`() {
        val offer = OfferComposer.resumption(
            state(interruptions = listOf(Interruption(0L, 240_000L, 0.0, "com.teams"))),
            triggeredBy = null,
        )

        assertNull(offer.done)
        assertEquals("You were away for 4 minutes", offer.awayFor)
    }

    @Test
    fun `a glance away is not worth reporting`() {
        val offer = OfferComposer.resumption(
            state(interruptions = listOf(Interruption(0L, 12_000L, 0.0, "com.teams"))),
            triggeredBy = null,
        )

        assertNull(offer.awayFor, "12 seconds away is not disorientating, and saying so is noise")
    }

    @Test
    fun `an integrated app still gets the full card`() {
        val offer = OfferComposer.resumption(
            state(
                completed = listOf(
                    FieldSnapshot("dest", "Destination", "Delhi", 1L),
                    FieldSnapshot("purpose", "Purpose", "Client workshop", 2L),
                ),
                decisions = listOf(Decision("dest", "Destination", "Delhi", 0.4, 1L)),
            ),
            triggeredBy = null,
        )

        assertEquals("Entered Destination, Purpose", offer.done)
        assertTrue(offer.decided!!.contains("Delhi"))
    }
}
