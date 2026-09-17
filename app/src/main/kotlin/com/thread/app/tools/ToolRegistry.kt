package com.thread.app.tools

object BreakdownTool : ThreadTool {
    override val definition = ToolDefinition(
        id = "breakdown",
        command = "@breakdown",
        displayName = "Break down task",
        description = "Turn a task into smaller actionable steps",
        inputHint = "Describe the task to break down",
    )
}

/**
 * Reducing the screen to the next single action is not a tool.
 *
 * It was one briefly, as `@next`. It is a button on the card instead, because
 * making someone type a command to find out what to do next asks for exactly
 * the composure they have run out of - and because the sequencer takes no
 * input, so there was nothing for the text bar to carry.
 */
object ToolRegistry {
    private val registered: List<ThreadTool> = listOf(
        BreakdownTool,
    )

    init {
        require(registered.map { it.definition.id }.distinct().size == registered.size) {
            "Tool ids must be unique"
        }
        require(registered.map { it.definition.command }.distinct().size == registered.size) {
            "Tool commands must be unique"
        }
    }

    fun all(): List<ThreadTool> = registered

    fun findByCommand(command: String): ThreadTool? {
        val normalized = command.trim().lowercase()
        return registered.firstOrNull { it.definition.command == normalized }
    }

    fun search(query: String): List<ThreadTool> {
        val normalized = query.trim().removePrefix("@").lowercase()
        if (normalized.isEmpty()) return registered

        return registered.filter { tool ->
            val definition = tool.definition
            definition.command.removePrefix("@").startsWith(normalized) ||
                definition.displayName.lowercase().contains(normalized)
        }
    }
}
