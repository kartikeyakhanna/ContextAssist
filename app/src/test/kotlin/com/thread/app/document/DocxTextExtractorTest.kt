package com.thread.app.document

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocxTextExtractorTest {

    @Test
    fun `extracts paragraphs and inline text from a docx`() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>First paragraph.</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second</w:t></w:r><w:r><w:t> paragraph.</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        assertEquals(
            "First paragraph.\nSecond paragraph.",
            DocxTextExtractor.extract(docx(documentXml)),
        )
    }

    @Test
    fun `rejects an archive without Word document content`() {
        assertFailsWith<DocumentImportException> {
            DocxTextExtractor.extract(docx("<root />", entryName = "other.xml"))
        }
    }

    private fun docx(
        documentXml: String,
        entryName: String = "word/document.xml",
    ): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
