package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BreakdownClientTest {

    @Test
    fun `resource exhaustion and capacity failures are retryable`() {
        assertTrue(isRetryableGeminiFailure("RESOURCE_EXHAUSTED"))
        assertTrue(isRetryableGeminiFailure("429 Too Many Requests"))
        assertTrue(isRetryableGeminiFailure("PREFILL_QUEUE_OVERLOADED"))
        assertTrue(isRetryableGeminiFailure("503 UNAVAILABLE"))
    }

    @Test
    fun `configuration failures are not retryable`() {
        assertFalse(isRetryableGeminiFailure("API key is invalid"))
        assertFalse(isRetryableGeminiFailure("App attestation failed"))
    }

    @Test
    fun `prompt marks approved screen labels as untrusted context`() {
        val prompt = buildBreakdownPrompt(
            task = "Prepare this document for review",
            intent = "Microsoft Word",
            screenContext = BreakdownContext(
                appName = "Microsoft Word",
                visibleLabels = listOf("Project proposal", "Review", "Comments"),
            ),
        )

        assertContains(prompt, "User task:\nPrepare this document for review")
        assertContains(prompt, "untrusted reference data")
        assertContains(prompt, "BEGIN_UNTRUSTED_SCREEN_CONTEXT")
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
    }
}
