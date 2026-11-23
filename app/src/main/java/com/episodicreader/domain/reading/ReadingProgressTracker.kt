package com.episodicreader.domain.reading

/**
 * Pure domain logic for monotonic reading watermark tracking (BR-003) and
 * explicit episode completion rules (BR-006).
 */
object ReadingProgressTracker {

    /**
     * Computes the new monotonic max_read_seq watermark.
     * Navigating backward to earlier paragraphs or episodes MUST NEVER decrease max_read_seq.
     */
    fun nextMonotonicMaxReadSeq(currentMaxReadSeq: Int, observedSeq: Int): Int {
        return maxOf(currentMaxReadSeq, observedSeq)
    }

    /**
     * Computes intra-episode progress fraction [0f..1f] strictly within the current episode (BR-001).
     */
    fun computeIntraEpisodeProgress(
        episodeStartSeq: Int,
        episodeEndSeq: Int,
        lastVisibleSeq: Int,
    ): Float {
        if (episodeEndSeq <= episodeStartSeq) return 1f
        val clamped = lastVisibleSeq.coerceIn(episodeStartSeq, episodeEndSeq)
        val total = (episodeEndSeq - episodeStartSeq).toFloat()
        return ((clamped - episodeStartSeq).toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Determines whether tapping completion should also set books.completed_at_utc.
     */
    fun isFinalEpisode(episodeIndex: Int, maxEpisodeIndex: Int): Boolean {
        return episodeIndex >= maxEpisodeIndex
    }
}
