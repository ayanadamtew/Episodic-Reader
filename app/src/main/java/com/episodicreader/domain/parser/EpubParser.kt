package com.episodicreader.domain.parser

import com.episodicreader.domain.importer.ImportErrorCode
import com.episodicreader.domain.slicer.RawParagraph
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedBookDocument(
    val title: String,
    val author: String,
    val paragraphs: List<RawParagraph>,
)

sealed interface ParseBookResult {
    data class Success(val document: ParsedBookDocument) : ParseBookResult
    data class Failure(val error: ImportErrorCode) : ParseBookResult
}

/**
 * Hardened DRM-free EPUB parser (T-12 / FR-001 / ERR-001 / ERR-004).
 *  - Prevents Zip-Slip entry traversal.
 *  - Hardens XML DOM parser against XXE / external DTD resolution.
 *  - Detects DRM (META-INF/encryption.xml non-font encrypted content or rights.xml) -> ERR-001.
 *  - Traverses META-INF/container.xml -> OPF manifest & spine -> HtmlSanitizer.
 */
class EpubParser {

    fun parse(epubFile: File, fallbackTitle: String): ParseBookResult {
        return try {
            ZipFile(epubFile).use { zip ->
                // 1. Validate all entries against Zip-Slip & inspect for DRM files
                val entriesByNormalizedPath = mutableMapOf<String, ZipEntry>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    val name = entry.name
                    if (isUnsafeZipPath(name)) {
                        return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
                    }
                    entriesByNormalizedPath[normalizePath(name)] = entry
                }

                // 2. DRM checks (ERR-001)
                if (entriesByNormalizedPath.containsKey("meta-inf/rights.xml")) {
                    return ParseBookResult.Failure(ImportErrorCode.ERR_001_DRM_PROTECTED)
                }
                val encryptionEntry = entriesByNormalizedPath["meta-inf/encryption.xml"]
                if (encryptionEntry != null) {
                    val encXml = zip.getInputStream(encryptionEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (indicatesContentDrm(encXml)) {
                        return ParseBookResult.Failure(ImportErrorCode.ERR_001_DRM_PROTECTED)
                    }
                }

                // 3. Locate OPF via META-INF/container.xml
                val containerEntry = entriesByNormalizedPath["meta-inf/container.xml"]
                    ?: return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
                val containerBytes = zip.getInputStream(containerEntry).use { it.readBytes() }
                val opfRelPath = extractOpfPath(containerBytes)
                    ?: return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
                val opfEntry = entriesByNormalizedPath[normalizePath(opfRelPath)]
                    ?: return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)

                // 4. Parse OPF metadata, manifest, and spine
                val opfBytes = zip.getInputStream(opfEntry).use { it.readBytes() }
                val opf = parseOpf(opfBytes)
                    ?: return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)

                val opfDir = opfRelPath.substringBeforeLast('/', missingDelimiterValue = "")
                val allParagraphs = mutableListOf<RawParagraph>()

                for (idref in opf.spineIdRefs) {
                    val href = opf.manifestById[idref] ?: continue
                    val cleanHref = href.substringBefore('#')
                    val fullPath = if (opfDir.isBlank()) cleanHref else "$opfDir/$cleanHref"
                    val chapterEntry = entriesByNormalizedPath[normalizePath(fullPath)] ?: continue
                    val html = zip.getInputStream(chapterEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    allParagraphs += HtmlSanitizer.extractParagraphs(html)
                }

                if (allParagraphs.isEmpty()) {
                    return ParseBookResult.Failure(ImportErrorCode.ERR_011_EMPTY_CONTENT)
                }

                val resolvedTitle = opf.title?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle.removeSuffix(".epub").removeSuffix(".EPUB").trim().ifBlank { "Untitled EPUB" }
                val resolvedAuthor = opf.author?.takeIf { it.isNotBlank() } ?: "Unknown Author"

                ParseBookResult.Success(
                    ParsedBookDocument(
                        title = resolvedTitle,
                        author = resolvedAuthor,
                        paragraphs = allParagraphs,
                    ),
                )
            }
        } catch (_: Throwable) {
            ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
        }
    }

    internal fun indicatesContentDrm(encryptionXml: String): Boolean {
        val lower = encryptionXml.lowercase()
        if (!lower.contains("encrypteddata")) return false
        // Allow pure font obfuscation (IDPF / Adobe font mangling) only when every CipherReference is a font
        val cipherRefs = Regex("(?i)uri\\s*=\\s*\"([^\"]+)\"").findAll(encryptionXml)
            .map { it.groupValues[1].lowercase() }
            .toList()
        if (cipherRefs.isEmpty()) return true
        val fontExtensions = listOf(".ttf", ".otf", ".woff", ".woff2")
        val allFonts = cipherRefs.all { ref -> fontExtensions.any { ref.endsWith(it) } }
        return !allFonts
    }

    private fun isUnsafeZipPath(name: String): Boolean {
        if (name.startsWith("/") || name.startsWith("\\")) return true
        val segments = name.replace('\\', '/').split('/')
        return segments.any { it == ".." }
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        for (segment in path.replace('\\', '/').split('/')) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/").lowercase()
    }

    private fun createSafeDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            try {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Throwable) {
                // Best-effort feature hardening across JVM/Android XML implementations
            }
        }
    }

    private fun extractOpfPath(containerBytes: ByteArray): String? {
        val doc = createSafeDocumentBuilderFactory().newDocumentBuilder()
            .parse(ByteArrayInputStream(containerBytes))
        val rootfiles = doc.getElementsByTagNameNS("*", "rootfile")
        for (i in 0 until rootfiles.length) {
            val el = rootfiles.item(i) as? Element ?: continue
            val fullPath = el.getAttribute("full-path")
            if (fullPath.isNotBlank()) return fullPath
        }
        return null
    }

    private data class OpfModel(
        val title: String?,
        val author: String?,
        val manifestById: Map<String, String>,
        val spineIdRefs: List<String>,
    )

    private fun parseOpf(opfBytes: ByteArray): OpfModel? {
        val doc = createSafeDocumentBuilderFactory().newDocumentBuilder()
            .parse(ByteArrayInputStream(opfBytes))

        val title = doc.getElementsByTagNameNS("*", "title").let { nodes ->
            if (nodes.length > 0) nodes.item(0)?.textContent?.trim() else null
        }
        val author = doc.getElementsByTagNameNS("*", "creator").let { nodes ->
            if (nodes.length > 0) nodes.item(0)?.textContent?.trim() else null
        }

        val manifest = mutableMapOf<String, String>()
        val items = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            val mediaType = el.getAttribute("media-type").lowercase()
            if (id.isNotBlank() && href.isNotBlank() &&
                (mediaType.contains("html") || mediaType.contains("xml") || href.endsWith(".xhtml", true) || href.endsWith(".html", true))
            ) {
                manifest[id] = href
            }
        }

        val spine = mutableListOf<String>()
        val itemrefs = doc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemrefs.length) {
            val el = itemrefs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotBlank()) spine += idref
        }

        if (spine.isEmpty()) return null
        return OpfModel(title, author, manifest, spine)
    }
}
