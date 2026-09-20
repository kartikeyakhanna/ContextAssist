package com.thread.app.service

internal object OfficeApps {

    private val displayNames = mapOf(
        "com.microsoft.office.word" to "Microsoft Word",
        "com.microsoft.office.excel" to "Microsoft Excel",
        "com.microsoft.office.powerpoint" to "Microsoft PowerPoint",
        "com.thread.worddemo" to "Document Editor Demo",
    )

    fun isSupported(packageName: String): Boolean = packageName in displayNames

    fun isWord(packageName: String): Boolean = packageName == "com.microsoft.office.word"

    fun exposesDocumentText(packageName: String): Boolean = packageName == "com.thread.worddemo"

    fun displayName(packageName: String): String? = displayNames[packageName]
}
