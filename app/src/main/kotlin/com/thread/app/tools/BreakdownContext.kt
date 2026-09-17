package com.thread.app.tools

data class BreakdownContext(
    val appName: String,
    val visibleLabels: List<String>,
) {
    init {
        require(
            appName.isNotBlank() &&
                appName.length <= MAX_APP_NAME_LENGTH &&
                !appName.contains('\n') &&
                !appName.contains('\r'),
        )
        require(visibleLabels.size <= MAX_VISIBLE_LABELS)
        require(visibleLabels.sumOf(String::length) <= MAX_TOTAL_LABEL_LENGTH)
        require(
            visibleLabels.all { label ->
                label.isNotBlank() &&
                    label.length <= MAX_LABEL_LENGTH &&
                    !label.contains('\n') &&
                    !label.contains('\r')
            },
        )
    }

    companion object {
        const val MAX_APP_NAME_LENGTH = 120
        const val MAX_VISIBLE_LABELS = 20
        const val MAX_LABEL_LENGTH = 160
        const val MAX_TOTAL_LABEL_LENGTH = 1_500
    }
}
