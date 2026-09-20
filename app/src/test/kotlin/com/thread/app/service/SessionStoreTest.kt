package com.thread.app.service

import com.thread.app.tools.TaskBreakdown
import com.thread.app.tools.TodoItem
import com.thread.app.tools.ToolExecutionState
import com.thread.app.tools.ToolInvocation
import com.thread.app.tools.ToolResult
import com.thread.app.tools.BreakdownTool
import com.thread.engine.TaskStateBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SessionStoreTest {

    @Test
    fun `Word and Excel retain independent breakdowns`() {
        val store = SessionStore()
        val word = session("com.microsoft.office.word", "Edit proposal")
        val excel = session("com.microsoft.office.excel", "Update budget")
        word.toolExecutionState = success("word-step", "Rewrite the introduction")
        excel.toolExecutionState = success("excel-step", "Check the total formula")

        store.put(word)
        store.put(excel)

        assertSame(word, store.get("com.microsoft.office.word"))
        assertSame(excel, store.get("com.microsoft.office.excel"))
        assertEquals("word-step", firstItemId(store.get("com.microsoft.office.word")))
        assertEquals("excel-step", firstItemId(store.get("com.microsoft.office.excel")))
    }

    @Test
    fun `clearing Word does not clear Excel`() {
        val store = SessionStore()
        val word = session("com.microsoft.office.word", "Edit proposal")
        val excel = session("com.microsoft.office.excel", "Update budget")
        word.toolExecutionState = success("word-step", "Rewrite the introduction")
        excel.toolExecutionState = success("excel-step", "Check the total formula")
        store.put(word)
        store.put(excel)

        word.toolExecutionState = ToolExecutionState.Idle

        assertNull(firstItemId(store.get("com.microsoft.office.word")))
        assertEquals("excel-step", firstItemId(store.get("com.microsoft.office.excel")))
    }

    @Test
    fun `collapsed Office selection does not erase the last selected text`() {
        val word = session("com.microsoft.office.word", "Edit proposal")

        word.rememberSelectedText("This paragraph needs evidence.")
        word.rememberSelectedText(null)

        assertEquals("This paragraph needs evidence.", word.selectedText)
    }

    @Test
    fun `Word attachment is retained only in the Word session`() {
        val store = SessionStore()
        val word = session("com.microsoft.office.word", "Edit proposal")
        val excel = session("com.microsoft.office.excel", "Update budget")
        word.attachDocument("Proposal.docx", "Complete proposal text")
        store.put(word)
        store.put(excel)

        assertEquals("Proposal.docx", store.get("com.microsoft.office.word")?.documentName)
        assertEquals("Complete proposal text", store.get("com.microsoft.office.word")?.documentText)
        assertNull(store.get("com.microsoft.office.excel")?.documentText)
    }

    private fun session(packageName: String, intent: String): Session = Session(
        packageName = packageName,
        builder = TaskStateBuilder(
            taskId = packageName,
            intent = intent,
            startScreenId = "$packageName/main",
            startedAt = 1L,
        ),
        declared = false,
        lastSeenAt = 1L,
    )

    private fun success(id: String, text: String): ToolExecutionState.Success =
        ToolExecutionState.Success(
            requestId = 1L,
            invocation = ToolInvocation(BreakdownTool, text),
            result = ToolResult.Breakdown(
                TaskBreakdown(
                    title = text,
                    items = listOf(TodoItem(id, text, estimateMinutes = 5)),
                ),
            ),
        )

    private fun firstItemId(session: Session?): String? {
        val success = session?.toolExecutionState as? ToolExecutionState.Success ?: return null
        val result = success.result as? ToolResult.Breakdown ?: return null
        return result.value.items.firstOrNull()?.id
    }
}
