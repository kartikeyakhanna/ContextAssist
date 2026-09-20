package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentContextTest {

    @Test
    fun `short document is included completely`() {
        assertEquals(
            "Introduction\n\nConclusion",
            DocumentContext.excerpt("Introduction\n\n\nConclusion", selectedText = null),
        )
    }

    @Test
    fun `long document excerpt is centered around selected text`() {
        val document = "a".repeat(15_000) +
            " selected paragraph that matters " +
            "b".repeat(15_000)

        val excerpt = DocumentContext.excerpt(
            documentText = document,
            selectedText = "selected paragraph that matters",
        )

        assertEquals(BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH, excerpt.length)
        assertContains(excerpt, "selected paragraph that matters")
    }

    @Test
    fun `long document without a matching selection uses a bounded prefix`() {
        val excerpt = DocumentContext.excerpt(
            documentText = "x".repeat(30_000),
            selectedText = "not present",
        )

        assertTrue(excerpt.length <= BreakdownContext.MAX_DOCUMENT_TEXT_LENGTH)
    }
}
