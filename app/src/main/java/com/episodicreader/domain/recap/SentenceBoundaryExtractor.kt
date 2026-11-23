package com.episodicreader.domain.recap

/**
 * Abbreviation-safe sentence splitter (T-18 / BR-011).
 * Avoids false splits on Mr., Mrs., Ms., Dr., St., Prof., initials (F. M.), decimals (3.14), and ellipses (...).
 */
object SentenceBoundaryExtractor {

    private val ABBREVIATIONS: Set<String> = setOf(
        "mr", "mrs", "ms", "dr", "st", "prof", "sr", "jr", "rev", "capt", "col",
        "gen", "lt", "sgt", "vs", "etc", "fig", "no", "vol", "ch", "pp", "e.g", "i.e", "a.m", "p.m",
    )

    fun splitSentences(text: String): List<String> {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()

        val sentences = mutableListOf<String>()
        var start = 0
        var i = 0
        val len = cleaned.length

        while (i < len) {
            val ch = cleaned[i]
            if (ch == '.' || ch == '!' || ch == '?') {
                // Consume ellipses or repeated punctuation (..., ?!, !!)
                var endPunct = i
                while (endPunct + 1 < len && cleaned[endPunct + 1] in ".!?") {
                    endPunct++
                }
                val isEllipsis = (endPunct - i) >= 2 && ch == '.'

                // Check decimal number like 3.14
                val isDecimal = ch == '.' &&
                    i > 0 && cleaned[i - 1].isDigit() &&
                    i + 1 < len && cleaned[i + 1].isDigit()

                // Check abbreviation or single-letter initial before '.'
                val isAbbreviation = ch == '.' && !isEllipsis && isPrecededByAbbreviationOrInitial(cleaned, i)

                // Consume closing quotation marks or parentheses right after sentence punctuation
                var closeIdx = endPunct
                while (closeIdx + 1 < len && cleaned[closeIdx + 1] in "\"'\u201D\u2019)]") {
                    closeIdx++
                }

                val followedBySpaceOrEnd = (closeIdx + 1 >= len) || cleaned[closeIdx + 1].isWhitespace()

                if (!isDecimal && !isAbbreviation && !isEllipsis && followedBySpaceOrEnd) {
                    val segment = cleaned.substring(start, closeIdx + 1).trim()
                    if (segment.isNotEmpty()) {
                        sentences += segment
                    }
                    start = closeIdx + 1
                    while (start < len && cleaned[start].isWhitespace()) start++
                    i = start
                    continue
                }
                i = endPunct + 1
                continue
            }
            i++
        }

        if (start < len) {
            val remainder = cleaned.substring(start).trim()
            if (remainder.isNotEmpty()) {
                sentences += remainder
            }
        }
        return sentences
    }

    fun firstSentence(paragraphs: List<String>): String? {
        for (p in paragraphs) {
            val s = splitSentences(p).firstOrNull()
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    fun lastSentences(paragraphs: List<String>, count: Int = 2): List<String> {
        val collected = mutableListOf<String>()
        for (p in paragraphs.asReversed()) {
            val split = splitSentences(p)
            for (s in split.asReversed()) {
                collected += s
                if (collected.size >= count) {
                    return collected.reversed()
                }
            }
        }
        return collected.reversed()
    }

    private fun isPrecededByAbbreviationOrInitial(text: String, periodIndex: Int): Boolean {
        var j = periodIndex - 1
        while (j >= 0 && (text[j].isLetter() || text[j] == '.')) {
            j--
        }
        val token = text.substring(j + 1, periodIndex).lowercase()
        if (token.isEmpty()) return false
        // Single capital letter initial such as "F." or "M."
        if (token.length == 1 && text[periodIndex - 1].isUpperCase()) {
            return true
        }
        return token in ABBREVIATIONS
    }
}
