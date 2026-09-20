package com.thread.app.tools

data class TodoItem(
    val id: String,
    val text: String,
    val estimateMinutes: Int,
    val isCompleted: Boolean = false,
    val remainingSeconds: Int = estimateMinutes * 60,
    val isTimerRunning: Boolean = false,
)

data class TaskBreakdown(
    val title: String,
    val items: List<TodoItem>,
    val isExpanded: Boolean = true,
) {
    val completedCount: Int
        get() = items.count { it.isCompleted }

    val totalEstimateMinutes: Int
        get() = items.sumOf { it.estimateMinutes }

    fun toggleItem(id: String): TaskBreakdown = copy(
        items = items.map { item ->
            if (item.id != id) {
                item
            } else {
                val completed = !item.isCompleted
                item.copy(
                    isCompleted = completed,
                    remainingSeconds = if (!completed && item.remainingSeconds == 0) {
                        item.estimateMinutes * 60
                    } else {
                        item.remainingSeconds
                    },
                    isTimerRunning = false,
                )
            }
        },
    )

    fun toggleTimer(id: String): TaskBreakdown = copy(
        items = items.map { item ->
            when {
                item.id != id -> item.copy(isTimerRunning = false)
                item.isCompleted -> item
                item.isTimerRunning -> item.copy(isTimerRunning = false)
                else -> item.copy(
                    remainingSeconds = if (item.remainingSeconds == 0) {
                        item.estimateMinutes * 60
                    } else {
                        item.remainingSeconds
                    },
                    isTimerRunning = true,
                )
            }
        },
    )

    fun tickTimer(id: String): TaskBreakdown = copy(
        items = items.map { item ->
            if (item.id != id || !item.isTimerRunning) return@map item
            val remaining = (item.remainingSeconds - 1).coerceAtLeast(0)
            item.copy(
                remainingSeconds = remaining,
                isTimerRunning = remaining > 0,
            )
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
                item.estimateMinutes in 1..120 &&
                item.remainingSeconds in 0..(item.estimateMinutes * 60) &&
                !item.text.contains('\n') &&
                !item.text.contains('\r')
        },
    ) {
        "Breakdown steps must contain valid ids, text, and 1 to 120 minute estimates"
    }

    return TaskBreakdown(
        title = title,
        items = items,
    )
}
