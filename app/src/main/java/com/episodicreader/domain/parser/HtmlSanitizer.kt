package com.episodicreader.domain.parser

import com.episodicreader.data.db.entity.InlineMarkType
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.domain.slicer.RawInlineMark
import com.episodicreader.domain.slicer.RawParagraph

/**
 * Pure-Kotlin deterministic XHTML/HTML block & inline mark sanitizer for EPUB spine chapters.
 * Extracts HEADING, BLOCKQUOTE, SCENE_BREAK, and BODY blocks with accurate UTF-16 BOLD/ITALIC marks,
 * stripping scripts, styles, comments, and arbitrary markup safely.
 */
object HtmlSanitizer {
    private val STRIP_HEAD_REGEX = Regex("(?is)<head\\b[^>]*>.*?</head>")
    private val STRIP_SCRIPT_REGEX = Regex("(?is)<script\\b[^>]*>.*?</script>")
    private val STRIP_STYLE_REGEX = Regex("(?is)<style\\b[^>]*>.*?</style>")
    private val STRIP_COMMENT_REGEX = Regex("(?s)<!--.*?-->")
    private val BODY_EXTRACT_REGEX = Regex("(?is)<body\\b[^>]*>(.*?)</body>")

    private val BLOCK_REGEX = Regex(
        "(?is)<(h[1-6]|p|blockquote|li)\\b[^>]*>(.*?)</\\1>|<hr\\b[^>]*/?>",
    )
    private val INLINE_TOKEN_REGEX = Regex("(?is)<(/?)(em|i|strong|b|br)\\b[^>]*/?>|<[^>]+>")
    private val SCENE_BREAK_TEXT_REGEX = Regex("^[\\s*#~•·—–-]{3,}$")

    fun extractParagraphs(html: String, defaultSectionTitle: String? = null): List<RawParagraph> {
        var cleaned = html
            .replace(STRIP_COMMENT_REGEX, " ")
            .replace(STRIP_HEAD_REGEX, " ")
            .replace(STRIP_SCRIPT_REGEX, " ")
            .replace(STRIP_STYLE_REGEX, " ")

        val bodyMatch = BODY_EXTRACT_REGEX.find(cleaned)
        if (bodyMatch != null) {
            cleaned = bodyMatch.groupValues[1]
        }

        val results = mutableListOf<RawParagraph>()
        var currentSection = defaultSectionTitle

        val matches = BLOCK_REGEX.findAll(cleaned).toList()
        if (matches.isEmpty()) {
            // Fallback for plain <div> or unwrapped XHTML body
            val plain = decodeEntities(cleaned.replace(Regex("<[^>]+>"), "\n"))
            plain.split(Regex("\\n{2,}"))
                .map { normalizeWhitespace(it) }
                .filter { it.isNotBlank() }
                .forEach { block ->
                    val type = if (SCENE_BREAK_TEXT_REGEX.matches(block)) {
                        ParagraphType.SCENE_BREAK
                    } else {
                        ParagraphType.BODY
                    }
                    results += RawParagraph(
                        paragraphType = type,
                        textContent = if (type == ParagraphType.SCENE_BREAK) "* * *" else block,
                        sectionTitle = currentSection,
                    )
                }
            return results
        }

        for (match in matches) {
            val fullTag = match.value.lowercase()
            if (fullTag.startsWith("<hr")) {
                results += RawParagraph(
                    paragraphType = ParagraphType.SCENE_BREAK,
                    textContent = "* * *",
                    sectionTitle = currentSection,
                )
                continue
            }

            val tagName = match.groupValues[1].lowercase()
            val innerHtml = match.groupValues[2]
            val (text, marks) = extractTextAndInlineMarks(innerHtml)
            if (text.isBlank()) continue

            val type = when {
                tagName.startsWith("h") -> {
                    currentSection = text
                    ParagraphType.HEADING
                }
                tagName == "blockquote" -> ParagraphType.BLOCKQUOTE
                SCENE_BREAK_TEXT_REGEX.matches(text) -> ParagraphType.SCENE_BREAK
                else -> ParagraphType.BODY
            }

            results += RawParagraph(
                paragraphType = type,
                textContent = if (type == ParagraphType.SCENE_BREAK) "* * *" else text,
                sectionTitle = currentSection,
                inlineMarks = if (type == ParagraphType.SCENE_BREAK || type == ParagraphType.HEADING) {
                    emptyList()
                } else {
                    marks
                },
            )
        }

        return results
    }

    internal fun extractTextAndInlineMarks(innerHtml: String): Pair<String, List<RawInlineMark>> {
        val sb = StringBuilder()
        val marks = mutableListOf<RawInlineMark>()
        var boldStart: Int? = null
        var italicStart: Int? = null

        var lastIndex = 0
        for (match in INLINE_TOKEN_REGEX.findAll(innerHtml)) {
            if (match.range.first > lastIndex) {
                val rawChunk = decodeEntities(innerHtml.substring(lastIndex, match.range.first))
                appendNormalized(sb, rawChunk)
            }

            val isClosing = match.groupValues[1] == "/"
            val inlineTag = match.groupValues[2].lowercase()
            when (inlineTag) {
                "strong", "b" -> {
                    if (!isClosing && boldStart == null) {
                        boldStart = sb.length
                    } else if (isClosing && boldStart != null) {
                        if (sb.length > boldStart) {
                            marks += RawInlineMark(boldStart, sb.length, InlineMarkType.BOLD)
                        }
                        boldStart = null
                    }
                }
                "em", "i" -> {
                    if (!isClosing && italicStart == null) {
                        italicStart = sb.length
                    } else if (isClosing && italicStart != null) {
                        if (sb.length > italicStart) {
                            marks += RawInlineMark(italicStart, sb.length, InlineMarkType.ITALIC)
                        }
                        italicStart = null
                    }
                }
                "br" -> appendNormalized(sb, " ")
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < innerHtml.length) {
            appendNormalized(sb, decodeEntities(innerHtml.substring(lastIndex)))
        }

        // Trim leading/trailing whitespace and adjust mark offsets cleanly
        val rawText = sb.toString()
        val leadingTrim = rawText.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawText.length else it }
        val trimmed = rawText.trim()
        val adjustedMarks = marks.mapNotNull { mark ->
            val s = (mark.start - leadingTrim).coerceIn(0, trimmed.length)
            val e = (mark.end - leadingTrim).coerceIn(s, trimmed.length)
            if (s < e) RawInlineMark(s, e, mark.style) else null
        }
        return trimmed to adjustedMarks
    }

    private fun appendNormalized(sb: StringBuilder, text: String) {
        for (ch in text) {
            val isSpace = ch.isWhitespace() || ch == '\u00A0'
            if (isSpace) {
                if (sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                }
            } else {
                sb.append(ch)
            }
        }
    }

    private fun normalizeWhitespace(value: String): String =
        value.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(text) { m ->
            val entity = m.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) -> {
                    entity.substring(2).toIntOrNull(16)?.toChar()?.toString() ?: m.value
                }
                entity.startsWith("#") -> {
                    entity.substring(1).toIntOrNull()?.toChar()?.toString() ?: m.value
                }
                else -> when (entity.lowercase()) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    "mdash" -> "\u2014"
                    "ndash" -> "\u2013"
                    "rsquo" -> "\u2019"
                    "lsquo" -> "\u2018"
                    "rdquo" -> "\u201D"
                    "ldquo" -> "\u201C"
                    "hellip" -> "\u2026"
                    else -> m.value
                }
            }
        }
    }
}
