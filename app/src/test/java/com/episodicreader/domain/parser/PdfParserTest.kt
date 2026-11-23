package com.episodicreader.domain.parser

import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.domain.importer.ImportErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class PdfParserTest {

    private val extractor = PdfTextExtractor()

    private fun buildMinimalPdf(
        pageStreamText: String,
        flateCompress: Boolean = false,
        extraTrailerTokens: String = "",
        title: String? = "Northern Tales",
        author: String? = "Ivan Turgenev",
    ): ByteArray {
        val streamPayload = if (flateCompress) {
            val out = ByteArrayOutputStream()
            DeflaterOutputStream(out).use { it.write(pageStreamText.toByteArray(Charsets.ISO_8859_1)) }
            out.toByteArray()
        } else {
            pageStreamText.toByteArray(Charsets.ISO_8859_1)
        }
        val filter = if (flateCompress) "/Filter /FlateDecode " else ""
        val info = buildString {
            append("<< ")
            if (title != null) append("/Title ($title) ")
            if (author != null) append("/Author ($author) ")
            append(">>")
        }
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))
        out.write("1 0 obj\n$info\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write("2 0 obj\n<< $filter/Length ${streamPayload.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(streamPayload)
        out.write("\nendstream\nendobj\ntrailer\n<< $extraTrailerTokens >>\n%%EOF".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    @Test
    fun extractsFlateCompressedTextBasedPdfAndMetadata() {
        val content = """
            BT
            /F1 12 Tf
            (Chapter 1) Tj
            0 -24 Td
            (The rain drifted across the Petersburg embankment while Anna waited near the granite rail.) Tj
            0 -14 Td
            (She held the sealed letter tightly inside her dark cloak and listened to the bells chime over the Neva river as night settled over the quiet wooden houses.) Tj
            0 -24 Td
            (By morning the fog lifted from the canal, revealing bright sunlight on the wet slate rooftops and birch trees.) Tj
            ET
        """.trimIndent()

        val pdfBytes = buildMinimalPdf(content, flateCompress = true)
        val result = extractor.parseBytes(pdfBytes, "fallback.pdf")
        assertTrue(result is ParseBookResult.Success)
        val doc = (result as ParseBookResult.Success).document
        assertEquals("Northern Tales", doc.title)
        assertEquals("Ivan Turgenev", doc.author)
        assertTrue(doc.paragraphs.any { it.paragraphType == ParagraphType.HEADING && it.textContent == "Chapter 1" })
        assertTrue(doc.paragraphs.any { it.textContent.contains("Petersburg embankment") })
    }

    @Test
    fun rejectsScannedPdfWithUnder200NonWhitespaceCharsAsErr002() {
        val tiny = "BT /F1 12 Tf (Only forty characters here.) Tj ET"
        val res = extractor.parseBytes(buildMinimalPdf(tiny), "scan.pdf")
        assertEquals(ParseBookResult.Failure(ImportErrorCode.ERR_002_SCANNED_PDF), res)
    }

    @Test
    fun rejectsPasswordAndDrmPdfsWithErr003AndErr001() {
        val pw = buildMinimalPdf("BT (hello) Tj ET", extraTrailerTokens = "/Encrypt 5 0 R")
        assertEquals(ParseBookResult.Failure(ImportErrorCode.ERR_003_PASSWORD_PDF), extractor.parseBytes(pw, "pw.pdf"))

        val drm = buildMinimalPdf("BT (hello) Tj ET", extraTrailerTokens = "/Encrypt << /Filter /Adobe.PubSec >>")
        assertEquals(ParseBookResult.Failure(ImportErrorCode.ERR_001_DRM_PROTECTED), extractor.parseBytes(drm, "drm.pdf"))
    }

    @Test
    fun pdfParagraphCleanerStripsRepeatedHeadersFootersPageNumbersAndRejoinsHyphens() {
        val pages = listOf(
            "WHITE NIGHTS\nChapter One\nA quiet even-\ning settled over the canal.\n1",
            "WHITE NIGHTS\nThe lanterns flickered along the wet stone.\n2",
            "WHITE NIGHTS\nNastenka smiled beneath the streetlamp.\nPage 3",
        )
        val cleaned = PdfParagraphCleaner.cleanPagesToParagraphs(pages)
        assertFalse(cleaned.any { it.textContent == "WHITE NIGHTS" })
        assertFalse(cleaned.any { it.textContent == "1" || it.textContent == "2" || it.textContent == "Page 3" })
        assertTrue(cleaned.any { it.textContent.contains("evening settled over the canal.") })
    }
}
