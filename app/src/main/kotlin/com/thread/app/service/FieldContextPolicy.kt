package com.thread.app.service

internal object FieldContextPolicy {

    fun labelsForEditableField(
        enteredText: CharSequence?,
        hint: CharSequence?,
        contentDescription: CharSequence?,
        directChildLabels: List<CharSequence?>,
    ): List<CharSequence> {
        if (!enteredText.isNullOrBlank()) return emptyList()
        return buildList {
            hint?.let(::add)
            contentDescription?.let(::add)
            directChildLabels.filterNotNullTo(this)
        }
    }
}
