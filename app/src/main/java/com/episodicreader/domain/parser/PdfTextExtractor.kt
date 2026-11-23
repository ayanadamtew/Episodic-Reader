package com.episodicreader.domain.parser

import com.episodicreader.domain.importer.ImportErrorCode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Pure-Kotlin offline text-based PDF extractor (T-13 / FR-001 / ERR-001 / ERR-002 / ERR-003 / ERR-004).
 *  - Rejects invalid PDF headers (ERR-004).
 *  - Detects DRM-encrypted (ERR-001) and password-protected PDFs (ERR-003).
 *  - Extracts uncompressed and FlateDecode text streams, ToUnicode CMap tables, and Info metadata.
 *  - Rejects scanned image-only PDFs yielding < 200 non-whitespace characters (ERR-002).
 */
class PdfTextExtractor {

    companion object {
        const val MIN_NON_WHITESPACE_CHARS = 200
    }

    fun parse(pdfFile: File, fallbackTitle: String): ParseBookResult {
        return try {
            val bytes = pdfFile.readBytes()
            parseBytes(bytes, fallbackTitle)
        } catch (_: Throwable) {
            ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
        }
    }

    fun parseBytes(bytes: ByteArray, fallbackTitle: String): ParseBookResult {
        if (bytes.size < 8) return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
        val header = String(bytes, 0, minOf(16, bytes.size), Charsets.ISO_8859_1)
        if (!header.startsWith("%PDF-")) {
            return ParseBookResult.Failure(ImportErrorCode.ERR_004_CORRUPT_FILE)
        }

        val latin = String(bytes, Charsets.ISO_8859_1)

        // Encryption / password / DRM checks
        if (Regex("/Encrypt\\b").containsMatchIn(latin)) {
            return if (latin.contains("/Adobe.PubSec") || latin.contains("/EBX_HANDLER")) {
                ParseBookResult.Failure(ImportErrorCode.ERR_001_DRM_PROTECTED)
            } else {
                ParseBookResult.Failure(ImportErrorCode.ERR_003_PASSWORD_PDF)
            }
        }

        val title = extractInfoString(latin, "Title")
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle.removeSuffix(".pdf").removeSuffix(".PDF").trim().ifBlank { "Untitled PDF" }
        val author = extractInfoString(latin, "Author")
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown Author"

        val streams = extractDecodedStreams(bytes, latin)
        val toUnicodeMap = mutableMapOf<Int, String>()
        val contentStreams = mutableListOf<String>()

        for (streamText in streams) {
            if (streamText.contains("begincmap") || streamText.contains("beginbfchar") || streamText.contains("beginbfrange")) {
                parseToUnicodeCMap(streamText, toUnicodeMap)
            } else if (streamText.contains("BT") && (streamText.contains("Tj") || streamText.contains("TJ") || streamText.contains("'"))) {
                contentStreams += streamText
            }
        }

        val pages = contentStreams.mapNotNull { stream ->
            val extracted = extractTextFromContentStream(stream, toUnicodeMap).trim()
            extracted.takeIf { it.isNotEmpty() }
        }

        val nonWhitespaceCount = pages.sumOf { page -> page.count { !it.isWhitespace() } }
        if (nonWhitespaceCount < MIN_NON_WHITESPACE_CHARS) {
            return ParseBookResult.Failure(ImportErrorCode.ERR_002_SCANNED_PDF)
        }

        val paragraphs = PdfParagraphCleaner.cleanPagesToParagraphs(pages)
        if (paragraphs.isEmpty()) {
            return ParseBookResult.Failure(ImportErrorCode.ERR_002_SCANNED_PDF)
        }

        return ParseBookResult.Success(
            ParsedBookDocument(
                title = title,
                author = author,
                paragraphs = paragraphs,
            ),
        )
    }

    private fun extractInfoString(latin: String, key: String): String? {
        val literal = Regex("/$key\\s*\\(([^\\\\()]*(?:\\\\.[^\\\\()]*)*)\\)").find(latin)
        if (literal != null) {
            return decodePdfLiteralString(literal.groupValues[1]).trim()
        }
        return null
    }

    private fun extractDecodedStreams(rawBytes: ByteArray, latin: String): List<String> {
        val results = mutableListOf<String>()
        var searchFrom = 0

        while (searchFrom < latin.length) {
            val streamIdx = latin.indexOf("stream", startIndex = searchFrom)
            if (streamIdx < 0) break

            // Ensure token boundary before "stream" (not "endstream")
            if (streamIdx >= 3 && latin.substring(streamIdx - 3, streamIdx) == "end") {
                searchFrom = streamIdx + 6
                continue
            }

            var dataStart = streamIdx + 6
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endIdx = latin.indexOf("endstream", startIndex = dataStart)
            if (endIdx < 0) break

            val dictLookbackStart = (streamIdx - 512).coerceAtLeast(0)
            val dictHeader = latin.substring(dictLookbackStart, streamIdx)
            val isFlate = dictHeader.contains("/FlateDecode")

            var dataEnd = endIdx
            while (dataEnd > dataStart && (rawBytes[dataEnd - 1] == '\n'.code.toByte() || rawBytes[dataEnd - 1] == '\r'.code.toByte())) {
                dataEnd--
            }

            if (dataEnd > dataStart) {
                val slice = rawBytes.copyOfRange(dataStart, dataEnd)
                val decodedBytes = if (isFlate) inflateSafely(slice) else slice
                if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                    results += String(decodedBytes, Charsets.ISO_8859_1)
                }
            }
            searchFrom = endIdx + 9
        }
        return results
    }

    private fun inflateSafely(compressed: ByteArray): ByteArray? {
        return try {
            InflaterInputStream(compressed.inputStream(), Inflater(false)).use { iis ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                val maxOutputBytes = 20 * 1024 * 1024 // guard against zip-bomb PDF streams
                while (true) {
                    val n = iis.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > maxOutputBytes) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseToUnicodeCMap(cmap: String, map: MutableMap<Int, String>) {
        val bfCharRegex = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
        val bfCharBlocks = Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(cmap)
        for (block in bfCharBlocks) {
            for (m in bfCharRegex.findAll(block.groupValues[1])) {
                val src = m.groupValues[1].toIntOrNull(16) ?: continue
                map[src] = decodeUtf16Hex(m.groupValues[2])
            }
        }

        val bfRangeRegex = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
        val bfRangeBlocks = Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(cmap)
        for (block in bfRangeBlocks) {
            for (m in bfRangeRegex.findAll(block.groupValues[1])) {
                val start = m.groupValues[1].toIntOrNull(16) ?: continue
                val end = m.groupValues[2].toIntOrNull(16) ?: continue
                var dst = m.groupValues[3].toIntOrNull(16) ?: continue
                val count = (end - start).coerceAtMost(2048)
                for (offset in 0..count) {
                    map[start + offset] = String(Character.toChars(dst + offset))
                }
            }
        }
    }

    private fun extractTextFromContentStream(stream: String, cmap: Map<Int, String>): String {
        val sb = StringBuilder()
        var i = 0
        val len = stream.length
        var inTextObject = false
        val operands = mutableListOf<String>()

        while (i < len) {
            val ch = stream[i]
            when {
                ch.isWhitespace() -> i++
                ch == '%' -> {
                    while (i < len && stream[i] != '\n' && stream[i] != '\r') i++
                }
                ch == '(' -> {
                    val (lit, nextIdx) = readLiteralString(stream, i)
                    operands += "L:$lit"
                    i = nextIdx
                }
                ch == '<' && i + 1 < len && stream[i + 1] != '<' -> {
                    val close = stream.indexOf('>', i + 1)
                    if (close < 0) break
                    val hex = stream.substring(i + 1, close).replace(Regex("\\s+"), "")
                    operands += "H:$hex"
                    i = close + 1
                }
                ch == '[' -> {
                    val arraySb = StringBuilder()
                    i++
                    while (i < len && stream[i] != ']') {
                        when {
                            stream[i] == '(' -> {
                                val (lit, nextIdx) = readLiteralString(stream, i)
                                arraySb.append(lit)
                                i = nextIdx
                            }
                            stream[i] == '<' -> {
                                val close = stream.indexOf('>', i + 1)
                                if (close < 0) {
                                    i = len
                                } else {
                                    val hex = stream.substring(i + 1, close).replace(Regex("\\s+"), "")
                                    arraySb.append(decodeHexWithCMap(hex, cmap))
                                    i = close + 1
                                }
                            }
                            stream[i] == '-' || stream[i].isDigit() -> {
                                val numStart = i
                                i++
                                while (i < len && (stream[i].isDigit() || stream[i] == '.')) i++
                                val kern = stream.substring(numStart, i).toDoubleOrNull() ?: 0.0
                                if (kern <= -140.0) {
                                    arraySb.append(' ')
                                }
                            }
                            else -> i++
                        }
                    }
                    if (i < len && stream[i] == ']') i++
                    operands += "A:${arraySb}"
                }
                else -> {
                    val tokStart = i
                    while (i < len && !stream[i].isWhitespace() && stream[i] !in "()<>[]/") i++
                    if (i == tokStart) i++
                    val token = stream.substring(tokStart, i)
                    when (token) {
                        "BT" -> {
                            inTextObject = true
                            operands.clear()
                        }
                        "ET" -> {
                            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                            inTextObject = false
                            operands.clear()
                        }
                        "T*" -> {
                            if (inTextObject) sb.append('\n')
                            operands.clear()
                        }
                        "Td", "TD" -> {
                            if (inTextObject) {
                                val ty = operands.lastOrNull()?.toDoubleOrNull() ?: 0.0
                                if (kotlin.math.abs(ty) >= 20.0) {
                                    sb.append("\n\n")
                                } else if (kotlin.math.abs(ty) > 0.5) {
                                    sb.append('\n')
                                }
                            }
                            operands.clear()
                        }
                        "Tj", "'", "\"" -> {
                            if (inTextObject) {
                                if (token == "'" || token == "\"") sb.append('\n')
                                val op = operands.lastOrNull()
                                if (op != null) {
                                    when {
                                        op.startsWith("L:") -> sb.append(op.substring(2))
                                        op.startsWith("H:") -> sb.append(decodeHexWithCMap(op.substring(2), cmap))
                                    }
                                }
                            }
                            operands.clear()
                        }
                        "TJ" -> {
                            if (inTextObject) {
                                val op = operands.lastOrNull()
                                if (op != null && op.startsWith("A:")) {
                                    sb.append(op.substring(2))
                                }
                            }
                            operands.clear()
                        }
                        else -> {
                            if (token.firstOrNull()?.let { it.isDigit() || it == '-' || it == '+' || it == '.' } == true) {
                                operands += token
                            } else {
                                operands.clear()
                            }
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun readLiteralString(stream: String, start: Int): Pair<String, Int> {
        val raw = StringBuilder()
        var i = start + 1
        var depth = 1
        while (i < stream.length && depth > 0) {
            val c = stream[i]
            if (c == '\\' && i + 1 < stream.length) {
                raw.append(c).append(stream[i + 1])
                i += 2
                continue
            }
            if (c == '(') depth++
            else if (c == ')') {
                depth--
                if (depth == 0) {
                    i++
                    break
                }
            }
            raw.append(c)
            i++
        }
        return decodePdfLiteralString(raw.toString()) to i
    }

    private fun decodePdfLiteralString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '(', ')', '\\' -> { sb.append(next); i += 2 }
                    '\r', '\n' -> { i += 2 }
                    in '0'..'7' -> {
                        var octEnd = i + 2
                        while (octEnd < raw.length && octEnd < i + 4 && raw[octEnd] in '0'..'7') octEnd++
                        val code = raw.substring(i + 1, octEnd).toIntOrNull(8) ?: 32
                        sb.append(code.toChar())
                        i = octEnd
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun decodeHexWithCMap(hex: String, cmap: Map<Int, String>): String {
        if (hex.isEmpty()) return ""
        val padded = if (hex.length % 2 == 1) "${hex}0" else hex
        val sb = StringBuilder()
        val step = if (cmap.keys.any { it > 0xFF } && padded.length % 4 == 0) 4 else 2
        var idx = 0
        while (idx + step <= padded.length) {
            val code = padded.substring(idx, idx + step).toIntOrNull(16)
            if (code != null) {
                val mapped = cmap[code]
                if (mapped != null) {
                    sb.append(mapped)
                } else if (code in 32..126 || code in 160..255) {
                    sb.append(code.toChar())
                }
            }
            idx += step
        }
        return sb.toString()
    }

    private fun decodeUtf16Hex(hex: String): String {
        if (hex.length <= 4) {
            val code = hex.toIntOrNull(16) ?: return ""
            return code.toChar().toString()
        }
        val sb = StringBuilder()
        var i = 0
        while (i + 4 <= hex.length) {
            hex.substring(i, i + 4).toIntOrNull(16)?.let { sb.append(it.toChar()) }
            i += 4
        }
        return sb.toString()
    }
}
