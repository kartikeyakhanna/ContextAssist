package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BreakdownClientTest {

    @Test
    fun `resource exhaustion and capacity failures are retryable`() {
        assertTrue(isRetryableModelFailure("RESOURCE_EXHAUSTED"))
        assertTrue(isRetryableModelFailure("429 Too Many Requests"))
        assertTrue(isRetryableModelFailure("PREFILL_QUEUE_OVERLOADED"))
        assertTrue(isRetryableModelFailure("503 UNAVAILABLE"))
    }

    @Test
    fun `quota failures switch to another model immediately`() {
        assertTrue(isModelQuotaFailure("Quota exceeded for the selected model"))
        assertTrue(shouldTryAnotherModel("RESOURCE_EXHAUSTED"))
    }

    @Test
    fun `unavailable model endpoint falls back to another configured model`() {
        assertTrue(shouldTryAnotherModel("Model not found for API version"))
    }

    @Test
    fun `fallback list uses distinct supported general purpose models`() {
        assertTrue(BREAKDOWN_MODEL_NAMES.size >= 3)
        assertTrue(BREAKDOWN_MODEL_NAMES.size == BREAKDOWN_MODEL_NAMES.distinct().size)
        assertTrue(BREAKDOWN_MODEL_NAMES.all { it.isNotBlank() })
    }

    @Test
    fun `configuration failures are not retryable`() {
        assertFalse(isRetryableModelFailure("API key is invalid"))
        assertFalse(isRetryableModelFailure("App attestation failed"))
        assertFalse(shouldTryAnotherModel("API key is invalid"))
    }

    @Test
    fun `prompt marks approved screen labels as untrusted context`() {
        val prompt = buildBreakdownPrompt(
            task = "Prepare this document for review",
            intent = "Microsoft Word",
            screenContext = BreakdownContext(
                appName = "Microsoft Word",
                visibleLabels = listOf("Project proposal", "Review", "Comments"),
                selectedText = "Please review the launch date.",
                documentName = "Proposal.docx",
                documentText = "Background information from the complete proposal.",
            ),
        )

        assertContains(prompt, "User task:\nPrepare this document for review")
        assertContains(prompt, "realistic whole-number estimate from 1 to 120 minutes")
        assertContains(prompt, "untrusted reference data")
        assertContains(prompt, "BEGIN_UNTRUSTED_SCREEN_CONTEXT")
        assertContains(prompt, "App: Microsoft Word")
        assertContains(prompt, "Treat the selected text as the primary context")
        assertContains(prompt, "PRIMARY_SELECTED_TEXT:\nPlease review the launch date.")
        assertContains(
            prompt,
            "ATTACHED_WORD_DOCUMENT: Proposal.docx\n" +
                "Background information from the complete proposal.",
        )
        assertTrue(
            prompt.indexOf("PRIMARY_SELECTED_TEXT") <
                prompt.indexOf("ATTACHED_WORD_DOCUMENT"),
        )
        assertTrue(
            prompt.indexOf("ATTACHED_WORD_DOCUMENT") <
                prompt.indexOf("SECONDARY_VISIBLE_LABELS"),
        )
        assertContains(prompt, "- Project proposal\n- Review\n- Comments")
    }

    @Test
    fun `prompt omits screen section without consented context`() {
        val prompt = buildBreakdownPrompt(
            task = "Prepare this document for review",
            intent = "Microsoft Word",
            screenContext = null,
        )

        assertFalse(prompt.contains("visibleLabels"))
        assertFalse(prompt.contains("untrusted reference data"))
    }

    @Test
    fun `screen context enforces the network payload bounds`() {
        assertFailsWith<IllegalArgumentException> {
            BreakdownContext(
                appName = "Microsoft Word",
                visibleLabels = List(BreakdownContext.MAX_VISIBLE_LABELS + 1) { "Label $it" },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BreakdownContext(
                appName = "Microsoft\nWord",
                visibleLabels = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BreakdownContext(
                appName = "Microsoft Word",
                visibleLabels = emptyList(),
                selectedText = "x".repeat(BreakdownContext.MAX_SELECTED_TEXT_LENGTH + 1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BreakdownContext(
                appName = "Microsoft Word",
                visibleLabels = emptyList(),
                documentName = "Proposal.docx",
                documentText = null,
            )
        }
    }
}
