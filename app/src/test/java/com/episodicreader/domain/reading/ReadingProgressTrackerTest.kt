package com.episodicreader.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressTrackerTest {

    @Test
    fun maxReadSeqAdvancesMonotonicallyAndNeverRegresses() {
        var watermark = -1
        watermark = ReadingProgressTracker.nextMonotonicMaxReadSeq(watermark, 0)
        assertEquals(0, watermark)
        watermark = ReadingProgressTracker.nextMonotonicMaxReadSeq(watermark, 24)
        assertEquals(24, watermark)
        // Re-reading earlier episode must never lower watermark
        watermark = ReadingProgressTracker.nextMonotonicMaxReadSeq(watermark, 5)
        assertEquals(24, watermark)
        watermark = ReadingProgressTracker.nextMonotonicMaxReadSeq(watermark, -1)
        assertEquals(24, watermark)
    }

    @Test
    fun intraEpisodeProgressIsBoundedBetweenZeroAndOne() {
        assertEquals(0f, ReadingProgressTracker.computeIntraEpisodeProgress(10, 20, 10), 0.001f)
        assertEquals(0.5f, ReadingProgressTracker.computeIntraEpisodeProgress(10, 20, 15), 0.001f)
        assertEquals(1f, ReadingProgressTracker.computeIntraEpisodeProgress(10, 20, 20), 0.001f)
        assertEquals(1f, ReadingProgressTracker.computeIntraEpisodeProgress(10, 20, 99), 0.001f)
        assertEquals(1f, ReadingProgressTracker.computeIntraEpisodeProgress(10, 10, 10), 0.001f)
    }

    @Test
    fun finalEpisodeCheckDetectsBookCompletion() {
        assertFalse(ReadingProgressTracker.isFinalEpisode(1, 6))
        assertTrue(ReadingProgressTracker.isFinalEpisode(6, 6))
    }
}
