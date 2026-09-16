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
    fun `a single captured field shows what was typed, not the field name`() {
        val offer = OfferComposer.resumption(
            state(
                completed = listOf(
                    FieldSnapshot("omnibox", "Search or type URL", "ramp grant form", 1L),
                ),
            ),
            triggeredBy = null,
        )

        assertEquals(
            "You typed \"ramp grant form\"",
            offer.done,
            "One field is a search. 'Entered Search or type URL' restores nothing - " +
                "the query is the only part the user actually lost.",
        )
    }

    @Test
    fun `several fields are described by label, so values are not put on screen`() {
        val offer = OfferComposer.resumption(
            state(
                completed = listOf(
                    FieldSnapshot("a", "Claim amount", "4820.00", 1L),
                    FieldSnapshot("b", "Cost centre", "GB-4471", 2L),
                ),
            ),
            triggeredBy = null,
        )

        assertEquals("Entered Claim amount, Cost centre", offer.done)
        assertTrue(
            !offer.done!!.contains("4820"),
            "A form's values stay in its fields. The card is read in public.",
        )
    }

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
    fun `the longest gap is reported, not the most recent one`() {
        val offer = OfferComposer.resumption(
            state(
                interruptions = listOf(
                    Interruption(0L, 40_000L, 0.0, "com.settings"),
                    Interruption(1L, 240_000L, 0.0, "com.teams"),
                    Interruption(2L, 3_000L, 0.0, "com.clock"),
                ),
            ),
            triggeredBy = null,
        )

        assertEquals("You have been in and out 3 times, the longest for 4 minutes", offer.awayFor)
        assertTrue(
            offer.hasContent,
            "Nothing was read from this app, but having been away four minutes is " +
                "still worth a dot - it is the most orientating fact available.",
        )
    }

    @Test
    fun `being in and out repeatedly counts even when each trip is brief`() {
        val offer = OfferComposer.resumption(
            state(
                interruptions = (1..6).map { Interruption(it.toLong(), 5_000L, 0.0, "com.x") },
            ),
            triggeredBy = null,
        )

        assertEquals("You have been in and out of this 6 times", offer.awayFor)
    }

    @Test
    fun `a card with nothing to say is marked as such, so no dot is shown`() {
        val offer = OfferComposer.resumption(state(), triggeredBy = null)

        assertTrue(
            !offer.hasContent,
            "Naming the app the user is already looking at restores nothing. A tap " +
                "that yields an empty card teaches them not to tap again.",
        )
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
