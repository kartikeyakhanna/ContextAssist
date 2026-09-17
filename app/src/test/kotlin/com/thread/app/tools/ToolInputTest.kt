package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToolInputTest {

    @Test
    fun `at sign starts a tool query`() {
        assertEquals("@", ToolInput.activeQuery("@"))
        assertEquals("@br", ToolInput.activeQuery("@br"))
    }

    @Test
    fun `ordinary text does not start a tool query`() {
        assertNull(ToolInput.activeQuery("prepare a report"))
    }

    @Test
    fun `selected command closes the query after its trailing space`() {
        assertNull(ToolInput.activeQuery("@breakdown "))
        assertSame(BreakdownTool, ToolInput.selectedTool("@breakdown prepare a report"))
    }

    @Test
    fun `selecting a tool inserts its command and trailing space`() {
        assertEquals("@breakdown ", ToolInput.select(BreakdownTool))
    }

    @Test
    fun `invocation separates the command from its input`() {
        val invocation = ToolInput.invocation("@breakdown prepare the report")

        assertSame(BreakdownTool, invocation?.tool)
        assertEquals("prepare the report", invocation?.input)
    }
}
