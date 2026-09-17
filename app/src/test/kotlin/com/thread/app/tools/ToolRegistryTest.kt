package com.thread.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToolRegistryTest {

    @Test
    fun `breakdown is the first registered tool`() {
        assertSame(BreakdownTool, ToolRegistry.all().first())
    }

    @Test
    fun `at sign lists all available tools`() {
        assertEquals(listOf(BreakdownTool), ToolRegistry.search("@"))
    }

    @Test
    fun `partial command finds breakdown`() {
        assertEquals(listOf(BreakdownTool), ToolRegistry.search("@br"))
    }

    @Test
    fun `command lookup ignores case and surrounding whitespace`() {
        assertSame(BreakdownTool, ToolRegistry.findByCommand("  @BREAKDOWN  "))
    }

    @Test
    fun `unknown command returns no tool`() {
        assertNull(ToolRegistry.findByCommand("@unknown"))
    }
}
