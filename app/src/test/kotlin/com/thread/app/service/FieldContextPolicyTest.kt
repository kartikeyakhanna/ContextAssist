package com.thread.app.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldContextPolicyTest {

    @Test
    fun `empty editable field exposes its label but not a value`() {
        assertEquals(
            listOf("Purpose of travel"),
            FieldContextPolicy.labelsForEditableField(
                enteredText = "",
                hint = null,
                contentDescription = null,
                directChildLabels = listOf("Purpose of travel"),
            ),
        )
    }

    @Test
    fun `field with entered text exposes no field subtree content`() {
        assertTrue(
            FieldContextPolicy.labelsForEditableField(
                enteredText = "Private customer visit",
                hint = "Purpose of travel",
                contentDescription = null,
                directChildLabels = listOf("Private customer visit"),
            ).isEmpty(),
        )
    }
}
