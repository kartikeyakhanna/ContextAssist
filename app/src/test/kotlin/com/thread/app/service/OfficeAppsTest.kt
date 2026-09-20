package com.thread.app.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficeAppsTest {

    @Test
    fun `Word-like demo is supported and may expose its document body`() {
        assertTrue(OfficeApps.isSupported("com.thread.worddemo"))
        assertTrue(OfficeApps.exposesDocumentText("com.thread.worddemo"))
        assertEquals(
            "Document Editor Demo",
            OfficeApps.displayName("com.thread.worddemo"),
        )
    }

    @Test
    fun `real Office apps do not automatically expose editable document bodies`() {
        assertFalse(OfficeApps.exposesDocumentText("com.microsoft.office.word"))
        assertFalse(OfficeApps.exposesDocumentText("com.microsoft.office.excel"))
        assertFalse(OfficeApps.exposesDocumentText("com.microsoft.office.powerpoint"))
    }
}
