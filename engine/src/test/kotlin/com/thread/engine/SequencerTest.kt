package com.thread.engine

import com.thread.engine.Sequencer.StepKind
import com.thread.engine.model.Factor
import com.thread.engine.model.Offer
import com.thread.engine.model.Score
import com.thread.engine.model.ScoreKind
import com.thread.engine.model.TaskState
import com.thread.engine.scores.LiveFacts
import com.thread.engine.scores.LiveFacts.VisibleNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The deterministic sequencer.
 *
 * What these tests hold is not "the steps are the ones a designer would pick" -
 * there is no ground truth for that on an arbitrary app. It is narrower and
 * checkable: every step is a control that exists, nothing already done is
 * proposed, nothing unavailable is proposed, the irreversible thing is never
 * first, and a screen with no recoverable order produces nothing at all.
 */
class SequencerTest {

    private fun field(
        hint: String,
        value: String? = null,
        top: Int = 0,
        left: Int = 0,
    ) = VisibleNode(
        text = value ?: hint,
        isEditable = true,
        hintText = hint,
        bounds = LiveFacts.Bounds(left, top, left + 400, top + 40),
    )

    private fun button(label: String, top: Int = 0, enabled: Boolean = true) = VisibleNode(
        text = label,
        isClickable = true,
        isEnabled = enabled,
        bounds = LiveFacts.Bounds(0, top, 400, top + 40),
    )

    private fun check(label: String, checked: Boolean = false, top: Int = 0) = VisibleNode(
        text = label,
        isCheckable = true,
        isChecked = checked,
        bounds = LiveFacts.Bounds(0, top, 400, top + 40),
    )

    private fun claimForm() = listOf(
        field("Policy number", top = 100),
        field("Amount", top = 200),
        check("I confirm this is accurate", top = 300),
        button("Submit claim", top = 400),
        // Navigation, which is not work the task is asking for.
        button("Help", top = 500),
        button("Back", top = 20),
    )

    @Test
    fun `proposes the first unfinished thing on the screen`() {
        val plan = assertNotNull(Sequencer.plan(claimForm()))

        assertEquals("Policy number", plan.next?.label)
        assertEquals(StepKind.FILL, plan.next?.kind)
    }

    @Test
    fun `navigation is never a step`() {
        val plan = assertNotNull(Sequencer.plan(claimForm()))
        val labels = plan.steps.map { it.label }

        assertTrue("Help" !in labels, "a help link is not work the task is asking for")
        assertTrue("Back" !in labels, "back is not a step, and it sits above everything")
    }

    @Test
    fun `the irreversible action is last even when it sits first`() {
        val nodes = listOf(
            button("Submit claim", top = 10),
            field("Policy number", top = 100),
            field("Amount", top = 200),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals("Policy number", plan.next?.label)
        assertEquals(StepKind.CONFIRM, plan.steps.last().kind)
    }

    @Test
    fun `work already done is not proposed again and moves the position`() {
        val nodes = listOf(
            field("Policy number", value = "4471-2290", top = 100),
            field("Amount", top = 200),
            check("I confirm this is accurate", checked = true, top = 300),
            button("Submit claim", top = 400),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals("Amount", plan.next?.label)
        assertEquals(2, plan.completed)
        assertEquals(4, plan.total)
        assertEquals(3, plan.position)
    }

    @Test
    fun `an empty field reporting its own hint is not treated as filled`() {
        // The bug this exists to prevent: node.text returns the hint for an
        // untouched box, which once had Thread telling a user they had typed
        // something they had not.
        val nodes = listOf(
            field("Policy number", top = 100),
            field("Amount", top = 200),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(0, plan.completed)
        assertEquals("Policy number", plan.next?.label)
    }

    @Test
    fun `a disabled control is counted but never proposed`() {
        val nodes = listOf(
            field("Policy number", top = 100),
            field("Amount", top = 200),
            button("Submit claim", top = 400, enabled = false),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertTrue(plan.steps.none { it.kind == StepKind.CONFIRM })
        assertEquals(1, plan.blocked)
        assertEquals(3, plan.total, "the denominator must not grow as Submit becomes available")
    }

    @Test
    fun `a flat screen produces nothing rather than a guess`() {
        val menu = (1..20).map { button("Setting $it", top = it * 50) }

        assertNull(Sequencer.plan(menu), "a menu has no completion order to recover")
    }

    @Test
    fun `a firing freeze score with a plan offers the next step`() {
        val plan = assertNotNull(Sequencer.plan(claimForm()))
        val freeze = Score.of(
            ScoreKind.DS_FREEZE,
            listOf(Factor("stuck", 1.0, 75.0)),
        )

        val evaluation = Triggers.evaluate(
            state = TaskState(taskId = "t", intent = "Travel request", currentScreenId = "s", startedAt = 0L),
            freeze = freeze,
            plan = plan,
        )

        val offer = assertNotNull(
            evaluation.candidates.filterIsInstance<Offer.NextStep>().firstOrNull(),
        )
        assertEquals("Enter policy number", offer.instruction)
        assertEquals(1, offer.position)
    }

    @Test
    fun `a firing freeze score with no plan says nothing at all`() {
        // The old behaviour fabricated a default hint here. On an arbitrary app
        // that is a confident sentence about a decision nothing has examined.
        val freeze = Score.of(
            ScoreKind.DS_FREEZE,
            listOf(Factor("stuck", 1.0, 75.0)),
        )

        val evaluation = Triggers.evaluate(
            state = TaskState(taskId = "t", intent = "Settings", currentScreenId = "s", startedAt = 0L),
            freeze = freeze,
            plan = null,
        )

        assertTrue(evaluation.candidates.isEmpty())
    }

    @Test
    fun `a field that cannot be named is skipped rather than invented`() {
        val nodes = listOf(
            VisibleNode(text = "", isEditable = true),
            field("Policy number", top = 100),
            field("Amount", top = 200),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(2, plan.steps.size)
        assertTrue(plan.steps.all { it.label.isNotBlank() })
    }

    @Test
    fun `every step is a control that is actually on the screen`() {
        val nodes = claimForm()
        val onScreen = nodes.mapNotNull { it.hintText ?: it.text }

        val plan = assertNotNull(Sequencer.plan(nodes))

        plan.steps.forEach {
            assertTrue(it.label in onScreen, "step '${it.label}' is not a control on this screen")
        }
    }

    @Test
    fun `reading order is top to bottom then left to right`() {
        val nodes = listOf(
            field("Last name", top = 100, left = 500),
            field("First name", top = 100, left = 0),
            field("Postcode", top = 200, left = 0),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(listOf("First name", "Last name", "Postcode"), plan.steps.map { it.label })
    }

    @Test
    fun `nodes without bounds keep tree order`() {
        val nodes = listOf(
            VisibleNode(isEditable = true, hintText = "First name"),
            VisibleNode(isEditable = true, hintText = "Last name"),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(listOf("First name", "Last name"), plan.steps.map { it.label })
    }

    @Test
    fun `a label the app draws but does not attach is found by position`() {
        // Verified against the demo form on device: Compose exposes the controls
        // with null text, null hint and null description, and the words the user
        // can see in separate nodes nearby.
        val nodes = listOf(
            VisibleNode(
                text = "Purpose of travel",
                bounds = LiveFacts.Bounds(80, 410, 400, 450),
            ),
            VisibleNode(
                isEditable = true,
                bounds = LiveFacts.Bounds(44, 355, 848, 475),
            ),
            VisibleNode(
                text = "Travel dates",
                bounds = LiveFacts.Bounds(80, 580, 300, 620),
            ),
            VisibleNode(
                isEditable = true,
                bounds = LiveFacts.Bounds(44, 525, 848, 645),
            ),
            VisibleNode(
                isCheckable = true,
                bounds = LiveFacts.Bounds(75, 795, 120, 840),
            ),
            VisibleNode(
                text = "Delhi",
                bounds = LiveFacts.Bounds(148, 795, 220, 840),
            ),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(
            listOf("Purpose of travel", "Travel dates", "Delhi"),
            plan.steps.map { it.label },
        )
    }

    @Test
    fun `a distant text is not borrowed as a label`() {
        val nodes = listOf(
            field("Policy number", top = 100),
            field("Amount", top = 200),
            VisibleNode(isCheckable = true, bounds = LiveFacts.Bounds(0, 900, 40, 940)),
            // Same row, but the width of the screen away.
            VisibleNode(text = "Unrelated", bounds = LiveFacts.Bounds(2000, 900, 2400, 940)),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertTrue("Unrelated" !in plan.steps.map { it.label })
    }

    @Test
    fun `an attached label is preferred over a nearby one`() {
        val nodes = listOf(
            VisibleNode(text = "Heading", bounds = LiveFacts.Bounds(0, 60, 400, 90)),
            VisibleNode(
                isEditable = true,
                hintText = "Policy number",
                bounds = LiveFacts.Bounds(0, 100, 400, 140),
            ),
            field("Amount", top = 200),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals("Policy number", plan.next?.label)
    }

    @Test
    fun `a finished form proposes nothing`() {
        val nodes = listOf(
            field("Policy number", value = "4471-2290", top = 100),
            field("Amount", value = "250.00", top = 200),
        )

        assertNull(Sequencer.plan(nodes), "there is nothing left to say")
    }

    /**
     * The demo travel form, whose radio group is the shape that produced the bug:
     * three options, one decision, one heading drawn above them.
     */
    private fun radio(label: String, checked: Boolean = false, top: Int) = listOf(
        VisibleNode(
            isCheckable = true,
            isChecked = checked,
            className = "android.widget.RadioButton",
            bounds = LiveFacts.Bounds(54, top, 180, top + 126),
        ),
        VisibleNode(text = label, bounds = LiveFacts.Bounds(220, top + 40, 400, top + 86)),
    )

    private fun destinationForm(chosen: String? = null) =
        listOf(
            field("Purpose of travel", top = 408),
            field("Travel dates", top = 600),
            VisibleNode(text = "Destination", bounds = LiveFacts.Bounds(53, 844, 226, 884)),
        ) +
            radio("Delhi", chosen == "Delhi", top = 927) +
            radio("Bengaluru", chosen == "Bengaluru", top = 1100) +
            radio("Hyderabad", chosen == "Hyderabad", top = 1273)

    @Test
    fun `a radio group is one step, not one per option`() {
        val plan = assertNotNull(Sequencer.plan(destinationForm()))

        assertEquals(
            listOf("Purpose of travel", "Travel dates", "Destination"),
            plan.steps.map { it.label },
        )
        assertEquals(3, plan.total, "three options are three ways to answer, not three questions")
    }

    @Test
    fun `choosing one option finishes the whole group`() {
        val plan = assertNotNull(Sequencer.plan(destinationForm(chosen = "Delhi")))

        assertTrue(
            plan.steps.none { it.label in setOf("Destination", "Bengaluru", "Hyderabad") },
            "the rejected alternatives are not unfinished work",
        )
        assertEquals(1, plan.completed)
    }

    @Test
    fun `a group is pointed at as a whole`() {
        val plan = assertNotNull(Sequencer.plan(destinationForm()))
        val group = assertNotNull(plan.steps.first { it.label == "Destination" }.bounds)

        assertEquals(927, group.top, "from the first option")
        assertEquals(1273 + 126, group.bottom, "to the last")
    }

    @Test
    fun `independent checkboxes stay separate steps`() {
        val nodes = listOf(
            field("Policy number", top = 100),
            field("Amount", top = 200),
            check("Email me a copy", top = 300),
            check("Text me a copy", top = 400),
        )

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertTrue("Email me a copy" in plan.steps.map { it.label })
        assertTrue("Text me a copy" in plan.steps.map { it.label })
    }

    @Test
    fun `two separated radio groups are not merged`() {
        val nodes = listOf(
            field("Purpose of travel", top = 100),
            field("Travel dates", top = 200),
            VisibleNode(text = "Destination", bounds = LiveFacts.Bounds(53, 300, 226, 340)),
        ) +
            radio("Delhi", top = 360) +
            listOf(VisibleNode(text = "Cabin", bounds = LiveFacts.Bounds(53, 900, 226, 940))) +
            radio("Economy", top = 1000)

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertTrue("Destination" in plan.steps.map { it.label })
        assertTrue("Cabin" in plan.steps.map { it.label })
    }

    @Test
    fun `an unheaded group is skipped rather than named after one option`() {
        val nodes = listOf(field("Policy number", top = 100), field("Amount", top = 200)) +
            radio("Delhi", top = 900) +
            radio("Bengaluru", top = 1073)

        val plan = assertNotNull(Sequencer.plan(nodes))

        assertEquals(listOf("Policy number", "Amount"), plan.steps.map { it.label })
    }
}
