package com.thread.app.tools

data class BreakdownContext(
    val appName: String,
    val visibleLabels: List<String>,
    val selectedText: String? = null,
    val documentName: String? = null,
    val documentText: String? = null,
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
            selectedText == null ||
                (
                    selectedText.isNotBlank() &&
                        selectedText.length <= MAX_SELECTED_TEXT_LENGTH &&
                        !selectedText.contains('\n') &&
                        !selectedText.contains('\r')
                    )
        )
        require((documentName == null) == (documentText == null))
        require(
            documentName == null ||
                (
                    documentName.isNotBlank() &&
                        documentName.length <= MAX_DOCUMENT_NAME_LENGTH &&
                        !documentName.contains('\n') &&
                        !documentName.contains('\r')
                    )
        )
        require(
            documentText == null ||
                (
                    documentText.isNotBlank() &&
                        documentText.length <= MAX_DOCUMENT_TEXT_LENGTH
                    )
        )
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
        const val MAX_SELECTED_TEXT_LENGTH = 1_000
        const val MAX_DOCUMENT_NAME_LENGTH = 180
        const val MAX_DOCUMENT_TEXT_LENGTH = 20_000
    }
}
