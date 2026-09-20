package com.thread.app.document

import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal object DocxTextExtractor {

    const val MAX_DOCUMENT_CHARACTERS = 20_000
    private const val MAX_DOCUMENT_XML_BYTES = 5 * 1024 * 1024

    fun extract(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    if (entry.size > MAX_DOCUMENT_XML_BYTES) {
                        throw DocumentImportException("The Word document is too large to import.")
                    }
                    return parseDocumentXml(
                        LimitedInputStream(zip, MAX_DOCUMENT_XML_BYTES.toLong()),
                    )
                }
            }
        }
        throw DocumentImportException("The selected file is not a readable Word document.")
    }

    private fun parseDocumentXml(input: InputStream): String {
        val output = StringBuilder()
        var readingText = false

        val handler = object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes?,
            ) {
                when (localName ?: qName?.substringAfter(':')) {
                    "t" -> readingText = true
                    "tab" -> appendBounded(output, "\t")
                    "br", "cr" -> appendBounded(output, "\n")
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (readingText) appendBounded(output, String(ch, start, length))
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName ?: qName?.substringAfter(':')) {
                    "t" -> readingText = false
                    "p" -> appendBounded(output, "\n")
                }
            }
        }

        try {
            SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newSAXParser().parse(input, handler)
        } catch (error: Exception) {
            generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<DocumentImportException>()
                .firstOrNull()
                ?.let { throw it }
            throw DocumentImportException("The Word document text could not be read.", error)
        }

        return output
            .toString()
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: throw DocumentImportException("The Word document contains no readable text.")
    }

    private fun appendBounded(output: StringBuilder, value: String) {
        val remaining = MAX_DOCUMENT_CHARACTERS - output.length
        if (remaining <= 0) return
        output.append(value.take(remaining))
    }

    private class LimitedInputStream(
        input: InputStream,
        private val limit: Long,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) record(read)
            return read
        }

        private fun record(read: Int) {
            count += read
            if (count > limit) {
                throw DocumentImportException("The Word document is too large to import.")
            }
        }
    }
}

class DocumentImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
