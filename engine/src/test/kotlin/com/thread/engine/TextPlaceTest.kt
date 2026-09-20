package com.thread.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The document beat is arithmetic, and every way it can be wrong is an off-by-one
 * that would put a confident, incorrect line number in front of someone who cannot
 * easily check it. So the boundaries are pinned here rather than on a device.
 */
class TextPlaceTest {

    private val doc = "First line\nSecond line\nThird line"

    @Test
    fun `a cursor in the middle of a line reports that line`() {
        val place = TextPlace.at(doc, cursor = doc.indexOf("Second") + 3)
        assertEquals(2, place?.line)
        assertEquals("Second line", place?.snippet)
    }

    @Test
    fun `the first character is line one, not line zero`() {
        assertEquals(1, TextPlace.at(doc, cursor = 0)?.line)
    }

    @Test
    fun `a cursor at the very end of the text stays on the last line`() {
        val place = TextPlace.at(doc, cursor = doc.length)
        assertEquals(3, place?.line)
        assertEquals("Third line", place?.snippet)
    }

    /**
     * A cursor sitting just after a newline belongs to the line it starts, not the
     * one it ended. Getting this backwards sends the user one line too high.
     */
    @Test
    fun `a cursor immediately after a break belongs to the new line`() {
        val place = TextPlace.at(doc, cursor = doc.indexOf("Second"))
        assertEquals(2, place?.line)
    }

    @Test
    fun `a cursor at the end of a line stays on that line`() {
        val place = TextPlace.at(doc, cursor = doc.indexOf('\n'))
        assertEquals(1, place?.line)
        assertEquals("First line", place?.snippet)
    }

    @Test
    fun `an empty document has no place worth reporting`() {
        assertNull(TextPlace.at("", cursor = 0))
    }

    /** "You were on line 4" about a blank line is noise, not memory. */
    @Test
    fun `a blank line is not a place`() {
        assertNull(TextPlace.at("First\n\nThird", cursor = 6))
    }

    @Test
    fun `an out of range cursor is clamped rather than throwing`() {
        assertEquals(3, TextPlace.at(doc, cursor = 9_999)?.line)
        assertEquals(1, TextPlace.at(doc, cursor = -5)?.line)
    }

    @Test
    fun `a long paragraph is windowed around the cursor`() {
        val long = "word ".repeat(200) + "TARGET tail"
        val place = TextPlace.at(long, cursor = long.indexOf("TARGET") + 6)

        val snippet = place?.snippet.orEmpty()
        assertTrue(snippet.contains("TARGET"), "kept the words at the cursor: $snippet")
        assertTrue(snippet.length <= TextPlace.MAX_SNIPPET + 2, "bounded: ${snippet.length}")
        assertTrue(snippet.startsWith("\u2026"), "marks that text was cut: $snippet")
    }

    /**
     * The words *before* the cursor are the ones that restart a sentence, so they
     * are what the window has to keep.
     */
    @Test
    fun `the window favours what was just written`() {
        val long = "alpha ".repeat(40) + "justwritten" + " beta".repeat(40)
        val place = TextPlace.at(long, cursor = long.indexOf("justwritten") + "justwritten".length)
        assertTrue(place!!.snippet.contains("justwritten"), place.snippet)
    }

    @Test
    fun `tabs and runs of spaces are collapsed so the card stays readable`() {
        val place = TextPlace.at("a\t\t  lot   of   space", cursor = 4)
        assertEquals("a lot of space", place?.snippet)
    }

    @Test
    fun `the document name is carried through when the app exposes one`() {
        val place = TextPlace.at(doc, cursor = 2, documentName = "Q3 Report")
        assertEquals("Q3 Report", place?.documentName)
    }

    @Test
    fun `an insertion puts the cursor after the text just typed`() {
        assertEquals(6, TextPlace.cursorAfterEdit("hello world", "hello, world"))
    }

    @Test
    fun `typing at the end of a document is tracked`() {
        assertEquals(6, TextPlace.cursorAfterEdit("hello", "hello!"))
    }

    @Test
    fun `typing at the very start is tracked`() {
        assertEquals(1, TextPlace.cursorAfterEdit("ello", "hello"))
    }

    @Test
    fun `a deletion leaves the cursor at the point the text was removed`() {
        assertEquals(5, TextPlace.cursorAfterEdit("hello world", "helloworld"))
    }

    @Test
    fun `no previous reading means no claim about where anyone was`() {
        assertNull(TextPlace.cursorAfterEdit(null, "hello"))
    }

    @Test
    fun `an unchanged document reports no edit`() {
        assertNull(TextPlace.cursorAfterEdit("hello", "hello"))
    }

    @Test
    fun `repeated characters do not drag the cursor before the edit`() {
        // The naive prefix and suffix both match the same 'a's; if they are allowed
        // to overlap the answer lands left of where the user actually typed.
        assertEquals(4, TextPlace.cursorAfterEdit("aaa", "aaaa"))
    }

    @Test
    fun `an edit mid document resolves to the line it happened on`() {
        val before = "first line\nsecond line\nthird line"
        val after = "first line\nsecond XX line\nthird line"
        val cursor = TextPlace.cursorAfterEdit(before, after)
        val place = TextPlace.at(after, cursor!!)
        assertEquals(2, place?.line)
        assertEquals("second XX line", place?.snippet)
    }
}
