package com.thread.app.tools

object ToolInput {

    fun activeQuery(text: String): String? {
        val normalized = text.trimStart()
        if (!normalized.startsWith("@") || normalized.contains(' ')) return null
        return normalized
    }

    fun selectedTool(text: String): ThreadTool? {
        val command = text.trimStart().substringBefore(' ')
        return ToolRegistry.findByCommand(command)
    }

    fun invocation(text: String): ToolInvocation? {
        val normalized = text.trimStart()
        val command = normalized.substringBefore(' ')
        val tool = ToolRegistry.findByCommand(command) ?: return null
        return ToolInvocation(
            tool = tool,
            input = normalized.drop(command.length).trim(),
        )
    }

    fun select(tool: ThreadTool): String = "${tool.definition.command} "
}
