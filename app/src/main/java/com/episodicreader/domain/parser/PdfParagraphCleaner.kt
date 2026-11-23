package com.episodicreader.domain.parser

import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.domain.slicer.RawParagraph
import kotlin.math.ceil

/**
 * Deterministic PDF paragraph reconstruction and noise stripping (T-13 / FR-001).
 *  - Removes repeated running headers/footers across pages.
 *  - Removes standalone page numbers (e.g. "14", "- 14 -", "Page 14").
 *  - Rejoins hyphenated line wraps ("morn-\ning" -> "morning").
 *  - Reconstructs reflowable paragraphs and chapter headings.
 */
object PdfParagraphCleaner {

    private val PAGE_NUMBER_REGEX = Regex(
        "^(?i)(?:page\\s+\\d+(?:\\s*(?:of|/)\\s*\\d+)?|[-—–•\\s]*\\d{1,5}[-—–•\\s]*)$",
    )
    private val HEADING_REGEX = Regex(
        "^(?i)(?:chapter|part|book|section|prologue|epilogue)\\b.{0,70}$",
    )
    private val SCENE_BREAK_REGEX = Regex("^[\\s*#~•·—–-]{3,}$")

    fun cleanPagesToParagraphs(pages: List<String>): List<RawParagraph> {
        if (pages.isEmpty()) return emptyList()

        val pageLines = pages.map { page ->
            page.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { it.trim() }
        }

        val repeatedEdgeLines = findRepeatedHeadersAndFooters(pageLines)
        val paragraphs = mutableListOf<RawParagraph>()
        var currentSection: String? = null
        val buffer = StringBuilder()

        fun flushBuffer() {
            val text = buffer.toString().replace(Regex("\\s+"), " ").trim()
            buffer.clear()
            if (text.isEmpty()) return
            val type = when {
                SCENE_BREAK_REGEX.matches(text) -> ParagraphType.SCENE_BREAK
                HEADING_REGEX.matches(text) && text.length <= 80 -> {
                    currentSection = text
                    ParagraphType.HEADING
                }
                else -> ParagraphType.BODY
            }
            paragraphs += RawParagraph(
                paragraphType = type,
                textContent = if (type == ParagraphType.SCENE_BREAK) "* * *" else text,
                sectionTitle = currentSection,
            )
        }

        for (lines in pageLines) {
            val nonBlankIndices = lines.indices.filter { lines[it].isNotBlank() }
            val firstNonBlank = nonBlankIndices.firstOrNull()
            val lastNonBlank = nonBlankIndices.lastOrNull()

            for (idx in lines.indices) {
                val line = lines[idx]
                if (line.isBlank()) {
                    flushBuffer()
                    continue
                }
                if (PAGE_NUMBER_REGEX.matches(line)) {
                    continue
                }
                val isEdgeLine = idx == firstNonBlank || idx == lastNonBlank
                if (isEdgeLine && normalizeEdgeKey(line) in repeatedEdgeLines) {
                    continue
                }
                if (SCENE_BREAK_REGEX.matches(line)) {
                    flushBuffer()
                    paragraphs += RawParagraph(
                        paragraphType = ParagraphType.SCENE_BREAK,
                        textContent = "* * *",
                        sectionTitle = currentSection,
                    )
                    continue
                }
                if (HEADING_REGEX.matches(line) && line.length <= 80) {
                    flushBuffer()
                    currentSection = line
                    paragraphs += RawParagraph(
                        paragraphType = ParagraphType.HEADING,
                        textContent = line,
                        sectionTitle = currentSection,
                    )
                    continue
                }

                if (buffer.isEmpty()) {
                    buffer.append(line)
                } else {
                    val prev = buffer.toString()
                    if (prev.endsWith("-") && prev.length >= 2 && prev[prev.length - 2].isLetter() &&
                        line.firstOrNull()?.isLowerCase() == true
                    ) {
                        buffer.setLength(buffer.length - 1)
                        buffer.append(line)
                    } else {
                        buffer.append(' ').append(line)
                    }
                }
            }
            // Page boundaries flush paragraphs unless mid-sentence continuation
            val tail = buffer.toString().trimEnd()
            if (tail.endsWith('.') || tail.endsWith('!') || tail.endsWith('?') ||
                tail.endsWith('"') || tail.endsWith('\u201D')
            ) {
                flushBuffer()
            }
        }

        flushBuffer()
        return paragraphs
    }

    private fun findRepeatedHeadersAndFooters(pageLines: List<List<String>>): Set<String> {
        if (pageLines.size < 3) return emptySet()
        val threshold = ceil(pageLines.size * 0.4).toInt().coerceAtLeast(2)
        val counts = mutableMapOf<String, Int>()

        for (lines in pageLines) {
            val nonBlank = lines.filter { it.isNotBlank() }
            if (nonBlank.isEmpty()) continue
            val edges = setOfNotNull(
                nonBlank.firstOrNull()?.let { normalizeEdgeKey(it) },
                nonBlank.lastOrNull()?.let { normalizeEdgeKey(it) },
            )
            edges.filter { it.isNotEmpty() && it.length <= 80 }.forEach { key ->
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.filterValues { it >= threshold }.keys
    }

    private fun normalizeEdgeKey(line: String): String =
        line.replace(Regex("\\d+"), "").replace(Regex("\\s+"), " ").trim().lowercase()
}
