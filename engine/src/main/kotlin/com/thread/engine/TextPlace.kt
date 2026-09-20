package com.thread.engine

import com.thread.engine.model.DocumentPlace

/**
 * Turns a cursor offset into the line a person was writing.
 *
 * This is the whole document beat, and it is deliberately arithmetic rather than
 * anything cleverer. A cursor offset plus the text is enough to say "line 12, you
 * were part-way through this sentence", and that is the thing an interruption
 * actually takes. Nothing here infers intent, ranks importance, or calls a model.
 *
 * Pure and in the engine so it can be tested without a device, because the failure
 * modes that matter are all off-by-one: a cursor at a line break, a cursor at the
 * very end of the text, an empty document. Those are cheap to get wrong and they
 * would show up as a card confidently pointing at the wrong line - which is worse
 * than no card at all for someone who cannot easily check.
 */
object TextPlace {

    /**
     * How much of the line to keep around the cursor.
     *
     * Weighted towards what came *before* it: the user was mid-sentence, and the
     * words they had just written are the ones that restart the thought. What sits
     * after the cursor is usually text they have already read.
     */
    private const val BEFORE = 60
    private const val AFTER = 20

    /** Longer than this and a "line" is really a paragraph; it still gets windowed. */
    const val MAX_SNIPPET = BEFORE + AFTER + 2

    /**
     * Null when there is nothing worth saying - an empty document, or a line with
     * no content on it. Silence is correct there: "you were on line 4" about a
     * blank line is noise dressed up as memory.
     */
    fun at(
        text: String,
        cursor: Int,
        documentName: String? = null,
        updatedAt: Long = 0L,
    ): DocumentPlace? {
        if (text.isEmpty()) return null

        val offset = cursor.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0))
            .let { if (it < 0 || offset == 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }

        val line = text.substring(lineStart, lineEnd)
        if (line.isBlank()) return null

        return DocumentPlace(
            line = text.take(lineStart).count { it == '\n' } + 1,
            snippet = window(line, offset - lineStart),
            documentName = documentName,
            updatedAt = updatedAt,
        )
    }

    /**
     * Where the text changed between two readings of the same document.
     *
     * Needed because a cursor is not always readable. Measured on device, a Compose
     * text field reports its full contents through accessibility but reports its
     * selection as -1, and emits no text or selection events at all - so the offset
     * the rest of this file depends on simply is not on offer. What *is* on offer is
     * the text before and after, and the point where those two diverge is the point
     * the person was writing at.
     *
     * This is also the better answer where both are available. A cursor moves when
     * someone taps or scrolls; the last edit is where they were actually working,
     * which is what an interruption takes from them.
     *
     * Null when nothing changed, or when there is no previous reading to compare
     * against - the first sight of a document says nothing about where anyone was.
     */
    fun cursorAfterEdit(previous: String?, current: String): Int? {
        val before = previous ?: return null
        if (before == current) return null

        val maxPrefix = minOf(before.length, current.length)
        var prefix = 0
        while (prefix < maxPrefix && before[prefix] == current[prefix]) prefix++

        // Bounded so the prefix and suffix cannot overlap and double-count a
        // character, which would otherwise place the cursor before the edit.
        val maxSuffix = maxPrefix - prefix
        var suffix = 0
        while (
            suffix < maxSuffix &&
            before[before.length - 1 - suffix] == current[current.length - 1 - suffix]
        ) suffix++

        // The end of the changed region: after an insertion that is the far side of
        // the new text, and after a deletion it collapses to the deletion point.
        return current.length - suffix
    }

    /**
     * A readable fragment of one line, centred on where the user was.
     *
     * Ellipses are only added where text was actually cut, so the card never
     * implies there is more to a sentence than there is.
     */
    private fun window(line: String, cursorInLine: Int): String {
        val collapsed = line.replace(Regex("\\s+"), " ")
        val at = cursorInLine.coerceIn(0, line.length)
            .let { collapsed.length.coerceAtMost(it) }

        if (collapsed.length <= MAX_SNIPPET) return collapsed.trim()

        val start = (at - BEFORE).coerceAtLeast(0)
        val end = (at + AFTER).coerceAtMost(collapsed.length)
        val core = collapsed.substring(start, end).trim()

        return buildString {
            if (start > 0) append('\u2026')
            append(core)
            if (end < collapsed.length) append('\u2026')
        }
    }
}
