package com.thread.app.tools

data class ToolDefinition(
    val id: String,
    val command: String,
    val displayName: String,
    val description: String,
    val inputHint: String,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9-]*"))) {
            "Tool id must use lowercase letters, digits, and hyphens"
        }
        require(command.matches(Regex("@[a-z][a-z0-9-]*"))) {
            "Tool command must start with @ and use lowercase letters, digits, and hyphens"
        }
        require(displayName.isNotBlank()) { "Tool display name must not be blank" }
        require(description.isNotBlank()) { "Tool description must not be blank" }
        require(inputHint.isNotBlank()) { "Tool input hint must not be blank" }
    }
}

interface ThreadTool {
    val definition: ToolDefinition
}

data class ToolInvocation(
    val tool: ThreadTool,
    val input: String,
)

sealed interface ToolResult {
    data class Breakdown(val value: TaskBreakdown) : ToolResult
}

sealed interface ToolExecutionState {
    data object Idle : ToolExecutionState

    data class Loading(
        val requestId: Long,
        val invocation: ToolInvocation,
    ) : ToolExecutionState

    data class Success(
        val requestId: Long,
        val invocation: ToolInvocation,
        val result: ToolResult,
    ) : ToolExecutionState

    data class Failure(
        val requestId: Long,
        val invocation: ToolInvocation,
        val message: String,
    ) : ToolExecutionState
}
