package com.episodicreader.domain.slicer

import kotlin.math.roundToInt

/**
 * Deterministic whitespace-tokenized word counter and reading time estimator (200 wpm baseline).
 */
object WordCounter {
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(WHITESPACE_REGEX).count { it.isNotEmpty() }
    }

    fun estimateMinutes(wordCount: Int): Int {
        if (wordCount <= 0) return 1
        return (wordCount / 200.0).roundToInt().coerceAtLeast(1)
    }
}
