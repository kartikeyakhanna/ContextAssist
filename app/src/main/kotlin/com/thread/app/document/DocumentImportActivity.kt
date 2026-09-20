package com.thread.app.document

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentImportActivity : ComponentActivity() {

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            finish()
        } else {
            importDocument(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val sharedUri = if (intent.action == Intent.ACTION_SEND) {
            intent.streamUri()
        } else {
            null
        }

        if (sharedUri != null) {
            importDocument(sharedUri)
        } else {
            openDocument.launch(arrayOf(DOCX_MIME_TYPE))
        }
    }

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri)
                    val text = contentResolver.openInputStream(uri)?.use(DocxTextExtractor::extract)
                        ?: throw DocumentImportException("The selected document could not be opened.")
                    name to text
                }
            }

            result.onSuccess { (name, text) ->
                sendBroadcast(
                    Intent(ACTION_DOCUMENT_IMPORTED)
                        .setPackage(packageName)
                        .putExtra(
                            EXTRA_TARGET_PACKAGE,
                            intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: WORD_PACKAGE,
                        )
                        .putExtra(EXTRA_DOCUMENT_NAME, name)
                        .putExtra(EXTRA_DOCUMENT_TEXT, text),
                )
                setResult(Activity.RESULT_OK)
                Toast.makeText(
                    this@DocumentImportActivity,
                    "$name attached to the current Word task",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { error ->
                setResult(Activity.RESULT_CANCELED)
                Toast.makeText(
                    this@DocumentImportActivity,
                    error.message ?: "The Word document could not be attached.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Word document"
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        getParcelableExtra(Intent.EXTRA_STREAM)

    companion object {
        const val ACTION_DOCUMENT_IMPORTED = "com.thread.app.action.DOCUMENT_IMPORTED"
        const val EXTRA_DOCUMENT_NAME = "documentName"
        const val EXTRA_DOCUMENT_TEXT = "documentText"
        const val EXTRA_TARGET_PACKAGE = "targetPackage"
        private const val WORD_PACKAGE = "com.microsoft.office.word"
        private const val DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
