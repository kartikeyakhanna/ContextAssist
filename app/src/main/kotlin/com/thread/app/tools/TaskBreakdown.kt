package com.thread.app.tools

data class TodoItem(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false,
)

data class TaskBreakdown(
    val title: String,
    val items: List<TodoItem>,
    val isExpanded: Boolean = true,
) {
    val completedCount: Int
        get() = items.count { it.isCompleted }

    fun toggleItem(id: String): TaskBreakdown = copy(
        items = items.map { item ->
            if (item.id == id) item.copy(isCompleted = !item.isCompleted) else item
        },
    )

    fun toggleExpanded(): TaskBreakdown = copy(isExpanded = !isExpanded)
}

internal fun validatedTaskBreakdown(
    title: String,
    items: List<TodoItem>,
): TaskBreakdown {
    require(title.isNotBlank() && title.length <= 120) {
        "Breakdown title must contain 1 to 120 characters"
    }
    require(items.size in 3..12) {
        "Breakdown must contain between 3 and 12 steps"
    }
    require(items.map { it.id.lowercase() }.distinct().size == items.size) {
        "Breakdown step ids must be unique"
    }
    require(
        items.all { item ->
            item.id.matches(Regex("[a-z][a-z0-9-]*")) &&
                item.text.isNotBlank() &&
                item.text.length <= 160 &&
                !item.text.contains('\n') &&
                !item.text.contains('\r')
        },
    ) {
        "Breakdown steps must contain valid ids and short, single-line text"
    }

    return TaskBreakdown(
        title = title,
        items = items,
    )
}
