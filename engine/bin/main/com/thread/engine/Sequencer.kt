package com.thread.engine

import com.thread.engine.scores.LiveFacts
import com.thread.engine.scores.LiveFacts.VisibleNode
import kotlin.math.abs

/**
 * Turns a screen into the next single action, and the one after it.
 *
 * The point is not to explain the screen. It is to cut the number of options the
 * user is *weighing* without changing the number that exist. `Normalise.hick`
 * already states the cost: log2(1 + n). Twenty-five options cost 4.70; one costs
 * 1.00. Proposing one thing is a 4.7x reduction in decision cost while every
 * control stays exactly where the app put it - nothing hidden, nothing moved,
 * nothing disabled. That distinction is the whole feature. An overlay that
 * removed the other twenty-four would be a different product, and one this
 * audience has good reason to distrust.
 *
 * Deterministic on purpose. There is no model here and none is needed: the app
 * has already told us, in the tree, what is unfinished and what is unavailable.
 * A model is only required where the *ordering* is ambiguous, and where it is,
 * this returns null rather than guessing - see [plan].
 *
 * Pure Kotlin over [VisibleNode], so it is testable without a device.
 */
object Sequencer {

    /**
     * What the screen is asking of the user. Deliberately only three.
     *
     * Navigation is not a step. A link or a tab is something the user *may* do;
     * a blank field is something the task *needs*. Counting every clickable node
     * would put the nav bar in the plan and turn "1 of 4" into "1 of 23", which
     * is not orientation, it is another list.
     */
    enum class StepKind {
        /** A blank field. */
        FILL,

        /** An unticked checkbox, switch or radio. */
        CHOOSE,

        /** The control that ends the task. Always last. */
        CONFIRM,
    }

    data class Step(
        /** The app's own words for this control. Never generated. */
        val label: String,
        val kind: StepKind,
        /** For drawing a ring at the real control, in Thread's own overlay. */
        val bounds: LiveFacts.Bounds? = null,
    )

    /**
     * [completed] is what makes this worth showing at all. "Next: enter the policy
     * number" is an instruction; "2 of 5" is orientation - it tells someone who
     * has just come back how much of this they already did, which is the thing
     * they lost. It is also why [total] counts satisfied and blocked steps too:
     * a denominator that moved as you worked would be worse than none.
     */
    data class Plan(
        val steps: List<Step>,
        val completed: Int,
        val blocked: Int,
    ) {
        val next: Step? get() = steps.firstOrNull()
        val total: Int get() = completed + steps.size + blocked
        val position: Int get() = completed + 1
    }

    /** Below this, a screen is a menu, not a form. Menus get targeting, not steps. */
    const val MIN_FIELDS_FOR_FORM = 2

    /** Nobody reads past this, and a plan that long is a confession, not a help. */
    const val MAX_STEPS = 6

    /**
     * Null when there is no defensible sequence.
     *
     * Returning null is a feature. A flat screen - a settings list, a dashboard,
     * a grid of tiles - has no completion order to recover, and a confident wrong
     * order costs far more than silence for a user who cannot easily tell it is
     * wrong. Those screens want the other mode: point at the one control that
     * matches what the user asked for.
     */
    fun plan(nodes: List<VisibleNode>): Plan? {
        val candidates = nodes.filter { it.isStepCandidate() }
        if (candidates.count { it.isEditable } < MIN_FIELDS_FOR_FORM) return null

        val labels = nodes.filter { !it.isStepCandidate() && !it.text.isNullOrBlank() }
        val decisions = group(readingOrder(candidates))

        val completed = decisions.count { it.isSatisfied() }
        val outstanding = decisions.filterNot { it.isSatisfied() }
        val blocked = outstanding.count { !it.isEnabled }

        // Terminals last regardless of where they sit. A Submit button at the top
        // of the screen is still the end of the task, and proposing it first would
        // walk the user into an irreversible action with the form half empty.
        val (terminal, rest) = outstanding
            .filter { it.isEnabled }
            .partition { it.kind == StepKind.CONFIRM }

        val steps = (rest + terminal).mapNotNull { it.toStep(labels) }
        if (steps.isEmpty()) return null

        return Plan(steps.take(MAX_STEPS), completed, blocked)
    }

    /**
     * One decision, which may be spread across several controls.
     *
     * A three-option radio group is three nodes and one choice. Treating each node
     * as its own step is what made the plan tell someone who had already picked
     * Delhi to choose Bengaluru: the other two buttons are not unfinished work,
     * they are the alternatives that were rejected by picking the first.
     */
    private data class Decision(
        val members: List<VisibleNode>,
        val kind: StepKind,
        val exclusive: Boolean,
    )

    /** Enabled if any member is: a group is open if any of its options is. */
    private val Decision.isEnabled: Boolean get() = members.any { it.isEnabled }

    /** The whole group, so the ring encloses every option rather than picking one. */
    private val Decision.bounds: LiveFacts.Bounds?
        get() {
            val boxes = members.mapNotNull { it.bounds }
            if (boxes.isEmpty()) return null
            return LiveFacts.Bounds(
                left = boxes.minOf { it.left },
                top = boxes.minOf { it.top },
                right = boxes.maxOf { it.right },
                bottom = boxes.maxOf { it.bottom },
            )
        }

    private fun Decision.isSatisfied(): Boolean =
        if (exclusive) members.any { it.isChecked } else members.all { it.isSatisfied() }

    /**
     * A group is named by its heading, never by one of its options.
     *
     * The ordinary label search starts with text drawn *inside* the control, which
     * for a group box is every option caption - it would name the whole Destination
     * choice "Delhi". So a group only accepts a label from above it, and if the app
     * draws no heading the group is skipped rather than named after an arbitrary
     * member.
     */
    private fun Decision.toStep(labels: List<VisibleNode>): Step? {
        if (!exclusive) return members.single().toStep(labels)

        val box = bounds ?: return null
        val label = labels
            .filter { it.bounds != null && above(it.bounds!!, box) && overlapsHorizontally(it.bounds!!, box) }
            .minByOrNull { box.top - it.bounds!!.bottom }
            ?.text?.trimmedOrNull()
            ?: return null

        return Step(label, kind, box)
    }

    /**
     * Runs of mutually exclusive options become one decision.
     *
     * Grouped by adjacency in reading order and proximity, not by parent, because
     * a Compose radio list gives each option its own wrapper - there is no group
     * node to read. The limitation that follows is real and worth stating: two
     * different radio groups stacked with no more spacing than their own options
     * would merge into one. Proximity is what keeps that from being the common
     * case, and a merged group still names itself from the heading above it, so
     * the failure is a step that reads too broadly rather than one that is wrong.
     */
    private fun group(ordered: List<VisibleNode>): List<Decision> {
        val out = mutableListOf<Decision>()
        var run = mutableListOf<VisibleNode>()

        fun flush() {
            if (run.isEmpty()) return
            out += Decision(run.toList(), StepKind.CHOOSE, exclusive = true)
            run = mutableListOf()
        }

        for (node in ordered) {
            if (node.isExclusiveChoice()) {
                if (run.isEmpty() || adjoins(run.last(), node)) {
                    run.add(node)
                    continue
                }
                flush()
                run.add(node)
                continue
            }
            flush()
            out += Decision(listOf(node), node.kind() ?: continue, exclusive = false)
        }
        flush()
        return out
    }

    /** Close enough to be options in the same list rather than two separate ones. */
    private fun adjoins(previous: VisibleNode, next: VisibleNode): Boolean {
        val a = previous.bounds ?: return true
        val b = next.bounds ?: return true
        val gap = b.top - a.bottom
        return gap <= maxOf(a.height, b.height) * 3 / 2
    }

    private fun VisibleNode.isExclusiveChoice(): Boolean =
        isCheckable && !isEditable && className?.substringAfterLast('.') == "RadioButton"

    /**
     * Top-to-bottom, then left-to-right - the order forms are built to be filled in.
     *
     * Grouped into rows first rather than sorted with a tolerance, because "within
     * n pixels" is not a transitive comparison and a sort built on one is free to
     * produce a different answer for the same screen.
     *
     * Nodes without bounds keep tree order and go last. That is the honest fallback:
     * tree order is usually right and is never worse than a fabricated position.
     */
    fun readingOrder(nodes: List<VisibleNode>): List<VisibleNode> {
        if (nodes.none { it.bounds != null }) return nodes

        val positioned = nodes.filter { it.bounds != null }.sortedBy { it.bounds!!.top }
        val unpositioned = nodes.filter { it.bounds == null }

        val rows = mutableListOf<MutableList<VisibleNode>>()
        for (node in positioned) {
            val current = rows.lastOrNull()
            if (current != null && sameRow(current.first().bounds!!, node.bounds!!)) {
                current.add(node)
            } else {
                rows.add(mutableListOf(node))
            }
        }

        return rows.flatMap { row -> row.sortedBy { it.bounds!!.left } } + unpositioned
    }

    private fun sameRow(anchor: LiveFacts.Bounds, other: LiveFacts.Bounds): Boolean {
        val tolerance = (minOf(anchor.height, other.height).coerceAtLeast(2)) / 2
        return abs(anchor.centreY - other.centreY) <= tolerance
    }

    private fun VisibleNode.kind(): StepKind? = when {
        isEditable -> StepKind.FILL
        isCheckable -> StepKind.CHOOSE
        isClickable && LiveFacts.irreversible(text) -> StepKind.CONFIRM
        else -> null
    }

    private fun VisibleNode.isStepCandidate(): Boolean = kind() != null

    /**
     * Done, and therefore not a step.
     *
     * A field's value and its label arrive through the same property, and an empty
     * field reports its hint as its text. So a value that merely repeats the hint
     * is treated as no value at all - the alternative is telling someone a field is
     * filled because the app labelled it.
     */
    private fun VisibleNode.isSatisfied(): Boolean = when (kind()) {
        StepKind.FILL -> {
            val value = text?.trim()
            !value.isNullOrEmpty() && !value.equals(hintText?.trim(), ignoreCase = true) &&
                !value.equals(contentDescription?.trim(), ignoreCase = true)
        }
        StepKind.CHOOSE -> isChecked
        StepKind.CONFIRM -> false
        null -> false
    }

    /**
     * A step the app can be quoted on, or no step at all.
     *
     * A filled field's [text] is the user's own data, not a label, so a field is
     * named from its hint or description or it is skipped. Naming it "Enter
     * 4471-2290" would be absurd; inventing "Enter the reference" would be worse,
     * because it reads exactly like something the app said.
     */
    private fun VisibleNode.toStep(labels: List<VisibleNode>): Step? {
        val kind = kind() ?: return null

        val own = when (kind) {
            StepKind.FILL -> hintText?.trimmedOrNull() ?: contentDescription?.trimmedOrNull()
            else -> text?.trimmedOrNull() ?: contentDescription?.trimmedOrNull()
        }

        val label = own ?: labelFor(this, labels) ?: return null
        return Step(label, kind, bounds)
    }

    private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }

    /**
     * The label the app draws next to a control but does not attach to it.
     *
     * Necessary because a plain Compose form exposes neither: a `TextField` with a
     * placeholder and a radio row with a caption both arrive as controls with null
     * text, null hint and null description, while the words the user can plainly
     * see sit in separate nodes nearby. Verified on device - the demo form in this
     * repo produces exactly that, and without this the sequencer correctly but
     * uselessly declined to name anything at all.
     *
     * So the association is made the way a sighted user makes it, by position, in
     * the order of how strongly each arrangement implies a label:
     *
     *  1. Text drawn *inside* the control - a placeholder.
     *  2. Text on the same row - a checkbox or radio caption.
     *  3. Text immediately above - the classic form label.
     *
     * Bounded at every step. An unbounded "nearest text" search will always find
     * something, and a label pulled from the far side of the screen would read
     * exactly like one the app intended.
     */
    private fun labelFor(control: VisibleNode, labels: List<VisibleNode>): String? {
        val box = control.bounds ?: return null
        val candidates = labels.filter { it.bounds != null }

        candidates.firstOrNull { it.bounds!!.within(box) }?.let { return it.text?.trimmedOrNull() }

        candidates
            .filter { sameRow(box, it.bounds!!) && horizontalGap(box, it.bounds!!) <= box.height * 8 }
            .minByOrNull { horizontalGap(box, it.bounds!!) }
            ?.let { return it.text?.trimmedOrNull() }

        candidates
            .filter { above(it.bounds!!, box) && overlapsHorizontally(it.bounds!!, box) }
            .minByOrNull { box.top - it.bounds!!.bottom }
            ?.let { return it.text?.trimmedOrNull() }

        return null
    }

    private fun LiveFacts.Bounds.within(outer: LiveFacts.Bounds): Boolean =
        left >= outer.left && right <= outer.right && top >= outer.top && bottom <= outer.bottom

    private fun horizontalGap(a: LiveFacts.Bounds, b: LiveFacts.Bounds): Int = when {
        b.left >= a.right -> b.left - a.right
        a.left >= b.right -> a.left - b.right
        else -> 0
    }

    private fun above(label: LiveFacts.Bounds, control: LiveFacts.Bounds): Boolean {
        val gap = control.top - label.bottom
        return gap >= 0 && gap <= control.height * 2
    }

    private fun overlapsHorizontally(a: LiveFacts.Bounds, b: LiveFacts.Bounds): Boolean =
        a.left < b.right && b.left < a.right
}
