package com.thread.engine

import com.thread.engine.model.AppSwitchAway
import com.thread.engine.model.AppSwitchReturn
import com.thread.engine.model.FieldCommit
import com.thread.engine.model.Offer
import com.thread.engine.model.OfferKind
import com.thread.engine.model.OfferOutcome
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState
import com.thread.engine.model.ValueLookup
import com.thread.engine.scores.Cls
import com.thread.engine.scores.DsOrbit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArbiterTest {

    private fun offer(kind: OfferKind): Offer = when (kind) {
        OfferKind.RESUMPTION -> Offer.Resumption("Claim", "Entered dates", null, "Attach receipt", null)
        OfferKind.PIN -> Offer.Pin("Budget code", "DEL-4471", null)
        else -> Offer.Reassurance("Goes to your manager", 600, null)
    }

    @Test
    fun `only one offer is ever on screen at a time`() {
        val arbiter = Arbiter()
        assertNotNull(arbiter.select(listOf(offer(OfferKind.RESUMPTION)), now = 0))
        assertNull(
            arbiter.select(listOf(offer(OfferKind.PIN)), now = 1_000),
            "a second offer must not stack on top of a live one",
        )
    }

    @Test
    fun `a cooldown separates consecutive offers`() {
        val arbiter = Arbiter()
        arbiter.select(listOf(offer(OfferKind.RESUMPTION)), now = 0)
        arbiter.record(OfferKind.RESUMPTION, OfferOutcome.ACCEPTED, now = 1_000)

        assertNull(arbiter.select(listOf(offer(OfferKind.PIN)), now = 5_000), "still inside cooldown")
        assertNotNull(arbiter.select(listOf(offer(OfferKind.PIN)), now = 60_000), "cooldown elapsed")
    }

    @Test
    fun `two dismissals and that offer goes quiet for the rest of the task`() {
        val arbiter = Arbiter()

        arbiter.select(listOf(offer(OfferKind.PIN)), now = 0)
        arbiter.record(OfferKind.PIN, OfferOutcome.DISMISSED, now = 1_000)
        arbiter.select(listOf(offer(OfferKind.PIN)), now = 100_000)
        arbiter.record(OfferKind.PIN, OfferOutcome.DISMISSED, now = 101_000)

        assertTrue(arbiter.isSuppressed(OfferKind.PIN))
        assertNull(
            arbiter.select(listOf(offer(OfferKind.PIN)), now = 500_000),
            "the engine must learn that it is wrong about this user",
        )
    }

    @Test
    fun `don't show this again is permanent and immediate`() {
        val arbiter = Arbiter()
        arbiter.select(listOf(offer(OfferKind.RESUMPTION)), now = 0)
        arbiter.record(OfferKind.RESUMPTION, OfferOutcome.SUPPRESSED, now = 1_000)

        assertTrue(arbiter.isSuppressed(OfferKind.RESUMPTION))
        assertNull(arbiter.select(listOf(offer(OfferKind.RESUMPTION)), now = 900_000))
    }

    @Test
    fun `asking directly always works, even when everything else is suppressed`() {
        val arbiter = Arbiter()
        arbiter.select(listOf(offer(OfferKind.RESUMPTION)), now = 0)
        arbiter.record(OfferKind.RESUMPTION, OfferOutcome.SUPPRESSED, now = 1_000)

        // Tapping the dot is a pull, not a push. Detection is an assist, never a
        // gatekeeper - if the user asks where they were, they get an answer.
        val requested = arbiter.userRequested(offer(OfferKind.RESUMPTION), now = 2_000)
        assertIs<Offer.Resumption>(requested)
    }

    @Test
    fun `precision is reported as accepted over shown`() {
        val arbiter = Arbiter()
        arbiter.record(OfferKind.PIN, OfferOutcome.ACCEPTED, now = 0)
        arbiter.record(OfferKind.PIN, OfferOutcome.ACCEPTED, now = 1)
        arbiter.record(OfferKind.PIN, OfferOutcome.ACCEPTED, now = 2)
        arbiter.record(OfferKind.RESUMPTION, OfferOutcome.DISMISSED, now = 3)

        assertEquals(0.75, arbiter.precision(), 0.001)
        assertEquals(4, arbiter.offersShown())
    }
}

class TriggersTest {

    private fun score(kind: ScoreKind, value: Double) =
        Score(kind, value, listOf(com.thread.engine.model.Factor("test", 1.0, value)))

    private val orbitingState = TaskState(
        taskId = "t1",
        intent = "Submitting claim",
        currentScreenId = "expense/allocation",
        startedAt = 0L,
        lookups = listOf(
            ValueLookup(1, "expense/allocation", "Budget code", "DEL-4471", null),
            ValueLookup(2, "expense/allocation", "Budget code", "DEL-4471", null),
        ),
    )

    @Test
    fun `never hand a resumption card to someone who is orbiting`() {
        val result = Triggers.evaluate(
            state = orbitingState,
            cls = score(ScoreKind.CLS, 80.0),
            orbit = score(ScoreKind.DS_ORBIT, 75.0),
            justReturned = true,
        )

        assertTrue(
            result.candidates.none { it is Offer.Resumption },
            "they know what they were doing - they cannot find the thing",
        )
        assertTrue(result.candidates.any { it is Offer.Pin })
    }

    @Test
    fun `an elevated but not conclusive score stays passive`() {
        val result = Triggers.evaluate(
            state = orbitingState,
            cls = score(ScoreKind.CLS, 45.0),
            justReturned = true,
        )
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.passiveOnly, "should mark quietly, not interrupt")
    }

    @Test
    fun `a quiet session produces nothing at all`() {
        val result = Triggers.evaluate(
            state = orbitingState,
            cls = score(ScoreKind.CLS, 10.0),
            orbit = score(ScoreKind.DS_ORBIT, 5.0),
            justReturned = true,
        )
        assertTrue(result.candidates.isEmpty())
        assertTrue(!result.passiveOnly)
    }

    @Test
    fun `hesitating on an irreversible action outranks everything else`() {
        val result = Triggers.evaluate(
            state = orbitingState,
            orbit = score(ScoreKind.DS_ORBIT, 90.0),
            commitContext = Triggers.CommitContext(
                actionId = "submit",
                consequence = "Sends to your manager",
                undoWindowSeconds = 600,
                hesitationSeconds = 12.0,
            ),
        )
        assertIs<Offer.Reassurance>(result.candidates.first())
    }
}

/**
 * The demo, as an executable test.
 *
 * Maya, 34. Returned to work after treatment; cognitive fatigue. She starts an
 * expense claim, is interrupted by Teams for four minutes, and comes back.
 */
class MayaScenarioTest {

    @Test
    fun `Maya is handed back the thread after a four-minute interruption`() {
        val builder = TaskStateBuilder(
            taskId = "task-maya",
            intent = "Submitting Q3 travel claim",
            startScreenId = "expense/details",
            startedAt = 0L,
            expectedSteps = 10,
        )

        builder.apply(FieldCommit(10_000, "expense/details", "dates", "Travel dates", "12-15 Sep"))
        builder.apply(FieldCommit(20_000, "expense/details", "amount", "Amount", "42000"))
        builder.apply(
            FieldCommit(
                30_000, "expense/details", "cc", "Cost centre", "Delhi",
                isDecision = true,
            ),
        )
        builder.setNextAction("Attach the receipt")

        // Teams pings. She switches away for four minutes.
        builder.apply(AppSwitchAway(40_000, "expense/details", "com.microsoft.teams"))
        val state = builder.apply(AppSwitchReturn(280_000, "expense/details", "com.microsoft.teams"))

        // She lands back on a frozen screen: scrolls, re-reads, opens a field and closes it.
        val cls = Cls.score(
            state,
            signals = Cls.PostReturnSignals(
                scrollReversals = 3,
                focusWithoutEdit = 2,
                secondsBeforeFirstAction = 18.0,
            ),
            smlForScreen = 78.0,
        )

        assertTrue(cls.value >= 60.0, "expected a resumption offer, got ${cls.value} (${cls.explanation})")

        val evaluation = Triggers.evaluate(
            state = state,
            cls = cls,
            orbit = DsOrbit.score(state),
            justReturned = true,
        )

        val chosen = Arbiter().select(evaluation.candidates, now = 281_000)
        val card = assertIs<Offer.Resumption>(chosen)

        assertEquals("Submitting Q3 travel claim", card.intent)
        assertTrue(card.done.contains("Travel dates"), "got: ${card.done}")
        assertTrue(card.done.contains("Amount"), "got: ${card.done}")
        assertEquals("Attach the receipt", card.next)
        assertNotNull(card.decided)
        assertTrue(card.decided.contains("Delhi"), "the why must survive, not just the what")
    }

    @Test
    fun `navigating between screens does not reset what she was doing`() {
        val builder = TaskStateBuilder("t", "Submitting Q3 travel claim", "screen/a", 0L)
        builder.apply(FieldCommit(1_000, "screen/a", "f1", "Dates", "12 Sep"))
        builder.apply(com.thread.engine.model.ScreenView(2_000, "screen/b"))
        builder.apply(com.thread.engine.model.ScreenView(3_000, "screen/c"))

        // A -> B -> C, then "what was I trying to do?". The answer must still be there.
        val state = builder.state
        assertEquals("Submitting Q3 travel claim", state.intent)
        assertEquals(1, state.completed.size)
        assertEquals("screen/c", state.currentScreenId)
    }

    @Test
    fun `a task with no interruption is left completely alone`() {
        val builder = TaskStateBuilder("t", "Submitting claim", "expense/details", 0L)
        repeat(5) { i ->
            builder.apply(FieldCommit(i * 1_000L, "expense/details", "f$i", "Field $i", "v"))
        }
        val state = builder.state

        val cls = Cls.score(state, smlForScreen = 40.0)
        val evaluation = Triggers.evaluate(state = state, cls = cls, justReturned = false)

        assertTrue(evaluation.candidates.isEmpty(), "silence is the default")
        assertNull(Arbiter().select(evaluation.candidates, now = 10_000))
    }
}
