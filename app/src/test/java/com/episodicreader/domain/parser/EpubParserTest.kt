package com.episodicreader.domain.parser

import com.episodicreader.data.db.entity.InlineMarkType
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.domain.importer.ImportErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = EpubParser()

    private fun buildEpub(entries: Map<String, String>): File {
        val file = tempFolder.newFile("sample_${System.nanoTime()}.epub")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun parsesValidDrmFreeEpubWithMetadataAndInlineMarks() {
        val epub = buildEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Notes from the Canal</dc:title>
                        <dc:creator>Fyodor Dostoevsky</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="ch1"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "OEBPS/ch1.xhtml" to """
                    <html><head><title>Ch1</title><script>alert('xss')</script></head>
                    <body>
                      <h1>Chapter One</h1>
                      <p>Hello <em>Nastenka</em> and <strong>Matryona</strong> &mdash; welcome.</p>
                      <blockquote>A quiet northern twilight.</blockquote>
                      <hr/>
                      <p>Second paragraph.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )

        val result = parser.parse(epub, "fallback.epub")
        assertTrue(result is ParseBookResult.Success)
        val doc = (result as ParseBookResult.Success).document
        assertEquals("Notes from the Canal", doc.title)
        assertEquals("Fyodor Dostoevsky", doc.author)
        assertEquals(5, doc.paragraphs.size)
        assertEquals(ParagraphType.HEADING, doc.paragraphs[0].paragraphType)
        assertEquals("Chapter One", doc.paragraphs[0].textContent)

        val p1 = doc.paragraphs[1]
        assertTrue(p1.textContent.contains("Nastenka"))
        assertTrue(p1.textContent.contains("\u2014"))
        assertEquals(2, p1.inlineMarks.size)
        val italic = p1.inlineMarks.first { it.style == InlineMarkType.ITALIC }
        assertEquals("Nastenka", p1.textContent.substring(italic.start, italic.end))
        val bold = p1.inlineMarks.first { it.style == InlineMarkType.BOLD }
        assertEquals("Matryona", p1.textContent.substring(bold.start, bold.end))

        assertEquals(ParagraphType.BLOCKQUOTE, doc.paragraphs[2].paragraphType)
        assertEquals(ParagraphType.SCENE_BREAK, doc.paragraphs[3].paragraphType)
    }

    @Test
    fun rejectsDrmEncryptedEpubWithErr001() {
        val epub = buildEpub(
            mapOf(
                "META-INF/encryption.xml" to """
                    <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <EncryptedData><CipherData><CipherReference URI="OEBPS/ch1.xhtml"/></CipherData></EncryptedData>
                    </encryption>
                """.trimIndent(),
                "META-INF/container.xml" to "<container/>",
            ),
        )
        val result = parser.parse(epub, "drm.epub")
        assertEquals(ParseBookResult.Failure(ImportErrorCode.ERR_001_DRM_PROTECTED), result)
    }

    @Test
    fun rejectsZipSlipAndMissingOpfWithErr004() {
        val zipSlip = buildEpub(mapOf("../evil.txt" to "bad"))
        assertEquals(
            ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE),
            parser.parse(zipSlip, "bad.epub"),
        )

        val missingOpf = buildEpub(mapOf("mimetype" to "application/epub+zip"))
        assertEquals(
            ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE),
            parser.parse(missingOpf, "no_opf.epub"),
        )
    }
}
