package com.thread.engine

import com.thread.engine.scores.LiveFacts
import com.thread.engine.scores.LiveFacts.VisibleNode
import com.thread.engine.scores.Sml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Live screen scoring, for apps nobody scored offline.
 *
 * The bar these tests hold is not "the number is correct" - there is no ground
 * truth for that. It is that a busy screen scores above a calm one, and that the
 * measurement cannot be made absurd by a long list.
 */
class LiveFactsTest {

    private fun option(text: String) = VisibleNode(text = text, isClickable = true)
    private fun field() = VisibleNode(isEditable = true)

    @Test
    fun `a heavy screen scores above a calm one`() {
        val calm = listOf(
            VisibleNode(text = "Welcome back"),
            option("Continue"),
        )

        val heavy = (1..24).map { option("CC-11$it Engineering - Platform") } +
            List(6) { field() } +
            VisibleNode(text = "Find your budget code in the Finance Portal") +
            option("Submit")

        val calmScore = Sml.score(LiveFacts.facts("calm", calm)).value
        val heavyScore = Sml.score(LiveFacts.facts("heavy", heavy)).value

        assertTrue(
            heavyScore > calmScore + 20,
            "heavy screen ($heavyScore) should score well above calm ($calmScore)",
        )
    }

    @Test
    fun `option counting saturates so a long list is not a crisis`() {
        val hugeList = (1..500).map { option("Row $it") }
        val facts = LiveFacts.facts("list", hugeList)

        assertEquals(LiveFacts.MAX_COUNTED_OPTIONS, facts.optionCount)
    }

    @Test
    fun `visible progress is recognised in the forms it actually appears in`() {
        assertTrue(LiveFacts.progressVisible(listOf("Step 2 of 4")))
        assertTrue(LiveFacts.progressVisible(listOf("3 of 7")))
        assertTrue(LiveFacts.progressVisible(listOf("60% complete")))
        assertFalse(LiveFacts.progressVisible(listOf("Cost allocation", "Continue")))
    }

    /** Regression: stock Android Settings reported progress because of the battery. */
    @Test
    fun `a bare percentage is not progress`() {
        assertFalse(LiveFacts.progressVisible(listOf("85%", "Battery")))
        assertFalse(LiveFacts.progressVisible(listOf("Storage", "47% used of 128 GB")))
    }

    @Test
    fun `only actionable nodes count as irreversible`() {
        val nodes = listOf(
            VisibleNode(text = "Submit"),                       // a label, not a button
            VisibleNode(text = "Submit", isClickable = true),   // the button
            option("Back"),
        )

        assertEquals(1, LiveFacts.facts("s", nodes).irreversibleActions)
    }

    @Test
    fun `cross references are counted only when they send you elsewhere`() {
        val texts = listOf(
            "Find your budget code in the Finance Portal",
            "Enter your reference number from the statement",
            "Destination",
        )

        assertEquals(2, LiveFacts.crossReferences(texts))
    }

    @Test
    fun `jargon scores above plain language`() {
        val plain = LiveFacts.languageComplexity(
            listOf("Where are you going", "Why are you going", "Next", "Back", "Delhi"),
        )
        val jargon = LiveFacts.languageComplexity(
            listOf(
                "Apportionment basis determination",
                "Irrevocable authorisation acknowledgement",
                "Reconciliation discrepancies outstanding",
            ),
        )

        assertTrue(jargon > plain, "jargon ($jargon) should exceed plain ($plain)")
    }

    @Test
    fun `too little text to judge is scored as nothing rather than guessed`() {
        assertEquals(0.0, LiveFacts.languageComplexity(listOf("OK", "Next")))
    }

    @Test
    fun `an app that exposes nothing produces a floor score, not a crash`() {
        val facts = LiveFacts.facts("opaque", emptyList())

        assertEquals(0, facts.optionCount)
        assertEquals(0, facts.itemsToHold)
        // Progress being invisible is itself load, so an opaque screen is not zero.
        assertFalse(facts.progressVisible)
    }
}
