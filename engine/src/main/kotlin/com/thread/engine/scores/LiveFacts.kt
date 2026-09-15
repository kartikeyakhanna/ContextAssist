package com.thread.engine.scores

/**
 * Screen Memory Load derived from what is on screen right now, for apps that were
 * never scored offline.
 *
 * This exists because the cached path only covers screens someone thought to score
 * in advance, which is fine for a product you own and useless for the app a user
 * actually opens. Without this, an unknown screen falls back to [Sml.NEUTRAL] - a
 * number that is neither right nor wrong, and therefore tells you nothing.
 *
 * It is a weaker measurement than the offline agent, and deliberately so. The
 * agent reads a screen *template* and can reason about meaning; this reads one
 * live tree and counts things. Where the two disagree, the cache wins - see
 * `ThreadAccessibilityService.smlFor`.
 *
 * Kept free of Android types so it can be tested as ordinary Kotlin. The service
 * flattens an AccessibilityNodeInfo tree into [VisibleNode]s and hands them over.
 */
object LiveFacts {

    /** The only properties of a node this needs. Everything else is noise here. */
    data class VisibleNode(
        val text: String? = null,
        val isClickable: Boolean = false,
        val isCheckable: Boolean = false,
        val isEditable: Boolean = false,
    )

    /**
     * Progress indicators, in the forms they actually take.
     *
     * A bare percentage is deliberately not one of them. The first live run of this
     * against stock Android Settings reported progress as visible, because the
     * battery reads "85%" - which would have quietly told the engine that a screen
     * offering no orientation at all was helping the user keep their place. A
     * percentage only counts here when something nearby says it is measuring
     * progress.
     */
    private val PROGRESS = Regex(
        """(?i)\bstep\s+\d+\s*(of|/)\s*\d+\b|\b\d+\s*of\s*\d+\b|""" +
            """\b\d{1,3}\s*%[^.]{0,20}\b(complete|completed|done|uploaded|finished|remaining)\b|""" +
            """\b(complete|completed|uploaded|finished)\b[^.]{0,10}\b\d{1,3}\s*%""",
    )

    private val IRREVERSIBLE = Regex(
        "(?i)\\b(submit|send|delete|remove|pay|confirm|discard|publish|approve|reject|cancel booking)\\b",
    )

    private val CROSS_REFERENCE = Regex(
        "(?i)\\b(find|look ?up|refer to|enter your|from the)\\b[^.]{0,40}" +
            "\\b(code|reference|id|number|portal|statement|invoice)\\b",
    )

    /**
     * Counting stops well before the tree does.
     *
     * A list with two hundred rows is not two hundred decisions - past a point the
     * user is scrolling a list, not weighing options, and counting every row would
     * make every long list look like a crisis. Hick's Law saturates anyway; this
     * just stops the input being absurd before it gets there.
     */
    const val MAX_COUNTED_OPTIONS = 60

    fun facts(screenId: String, nodes: List<VisibleNode>): Sml.ScreenFacts {
        val texts = nodes.mapNotNull { it.text?.trim() }.filter { it.isNotEmpty() }

        val options = nodes.count { it.isClickable || it.isCheckable }
            .coerceAtMost(MAX_COUNTED_OPTIONS)

        val editable = nodes.count { it.isEditable }

        val irreversible = nodes.count { node ->
            (node.isClickable || node.isCheckable) &&
                node.text?.let { IRREVERSIBLE.containsMatchIn(it) } == true
        }

        return Sml.ScreenFacts(
            screenId = screenId,
            // Each field you must fill is a value you are holding until you have
            // filled it. An approximation, and the honest one available here.
            itemsToHold = editable,
            optionCount = options,
            progressVisible = progressVisible(texts),
            irreversibleActions = irreversible,
            crossReferences = crossReferences(texts),
            languageComplexity = languageComplexity(texts),
        )
    }

    fun progressVisible(texts: List<String>): Boolean = texts.any { PROGRESS.containsMatchIn(it) }

    fun crossReferences(texts: List<String>): Int = texts.count { CROSS_REFERENCE.containsMatchIn(it) }

    /**
     * Long words as a proxy for jargon.
     *
     * Crude next to a readability model, but it moves in the right direction on the
     * text that matters - "apportionment basis" and "irrevocable authorisation"
     * score high, "Next" and "Delhi" score nothing - and it cannot be wrong in a
     * way that silently inflates the score, because it saturates at 1.0.
     */
    fun languageComplexity(texts: List<String>): Double {
        val words = texts.flatMap { it.split(' ', '\n', '\t') }
            .map { it.trim { c -> !c.isLetter() } }
            .filter { it.length > 2 }

        if (words.size < 5) return 0.0

        val longWords = words.count { it.length >= 9 }
        return Normalise.clamp01(longWords.toDouble() / words.size / 0.25)
    }
}
