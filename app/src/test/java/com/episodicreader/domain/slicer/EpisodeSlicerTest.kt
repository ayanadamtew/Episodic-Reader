package com.episodicreader.domain.slicer

import com.episodicreader.data.db.entity.ParagraphType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSlicerTest {

    private val slicer = EpisodeSlicer()

    private fun makeParagraph(words: Int, type: String = ParagraphType.BODY, prefix: String = "word"): RawParagraph {
        val text = (1..words).joinToString(" ") { "$prefix$it" }
        return RawParagraph(paragraphType = type, textContent = text)
    }

    @Test
    fun slicesStandardBookIntoTargetRangeEpisodesWithContiguousSeqIndexes() {
        val raw = (1..30).map { makeParagraph(100) } // 3,000 words -> 3 episodes
        val episodes = slicer.slice(raw, fallbackTitle = "Chapter 1")

        assertEquals(3, episodes.size)
        var expectedSeq = 0
        episodes.forEachIndexed { idx, ep ->
            assertEquals(idx + 1, ep.episodeIndex)
            assertTrue("Ep ${ep.episodeIndex} has ${ep.wordCount} words", ep.wordCount in 900..1200)
            assertEquals(expectedSeq, ep.startParagraphSeq)
            ep.paragraphs.forEach { p ->
                assertEquals(expectedSeq, p.seqIndex)
                expectedSeq++
            }
            assertEquals(expectedSeq - 1, ep.endParagraphSeq)
        }
        assertEquals(30, expectedSeq)
    }

    @Test
    fun snapsCleanlyToHeadingWithinSnapWindow() {
        val raw = mutableListOf<RawParagraph>()
        raw += RawParagraph(ParagraphType.HEADING, "Chapter One")
        repeat(8) { raw += makeParagraph(100) } // 802 words -> within 900 - 150 = 750 snap window
        raw += RawParagraph(ParagraphType.HEADING, "Chapter Two")
        repeat(10) { raw += makeParagraph(100) } // 1,002 words

        val episodes = slicer.slice(raw)
        assertEquals(2, episodes.size)
        assertEquals("Chapter One", episodes[0].sectionTitle)
        assertEquals("Chapter Two", episodes[1].sectionTitle)
        assertEquals("Chapter Two", episodes[1].paragraphs.first().textContent)
    }

    @Test
    fun keepsOversizedSingleParagraphAtomicWithoutSplitting() {
        val raw = listOf(
            makeParagraph(500),
            makeParagraph(1500, prefix = "huge"),
            makeParagraph(950),
        )
        val episodes = slicer.slice(raw)
        assertEquals(3, episodes.size)
        assertEquals(1500, episodes[1].wordCount)
        assertEquals(1, episodes[1].paragraphs.size)
    }

    @Test
    fun mergesMicroChaptersWhenCombinedWordCountFitsMaxWords() {
        val raw = listOf(
            RawParagraph(ParagraphType.HEADING, "Prologue"),
            makeParagraph(250),
            RawParagraph(ParagraphType.HEADING, "Chapter 1"),
            makeParagraph(850),
        )
        val episodes = slicer.slice(raw)
        assertEquals(1, episodes.size)
        assertTrue(episodes.first().wordCount in 1100..1110)
    }

    @Test
    fun handlesShortBookUnder900WordsAsSingleValidEpisode() {
        val raw = listOf(
            RawParagraph(ParagraphType.HEADING, "Short Essay"),
            makeParagraph(320),
        )
        val episodes = slicer.slice(raw)
        assertEquals(1, episodes.size)
        assertEquals(1, episodes.first().episodeIndex)
        assertTrue(episodes.first().estReadMinutes >= 1)
    }
}
