package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicNameIndexerTest {

    private val indexer = DeterministicNameIndexer()

    @Test
    fun indexesRecurringProperNamesNormalizesPossessivesAndExcludesStopwordsAndDays() {
        val ep = EpisodeEntity("ep1", "b1", 1, "Chapter 1", 950, 5, 0, 3)
        val paragraphs = listOf(
            ParagraphEntity("p0", "b1", "ep1", 0, ParagraphType.BODY, "On Monday morning, Elizabeth Bennet met Mr. Darcy near Pemberley."),
            ParagraphEntity("p1", "b1", "ep1", 1, ParagraphType.BODY, "However, Elizabeth Bennet's letter surprised Mr. Darcy completely."),
            ParagraphEntity("p2", "b1", "ep1", 2, ParagraphType.BODY, "On Tuesday evening, Jane spoke once with Elizabeth Bennet."),
            ParagraphEntity("p3", "b1", "ep1", 3, ParagraphType.BODY, "Meanwhile, Mr. Darcy walked quietly back through Pemberley."),
        )

        val indexed = indexer.indexBook("b1", listOf(ep), paragraphs)
        val names = indexed.entities.map { it.canonicalName }.toSet()

        assertTrue("Expected Elizabeth Bennet to be indexed: $names", "Elizabeth Bennet" in names)
        assertTrue("Expected Mr. Darcy to be indexed: $names", "Mr. Darcy" in names)
        assertTrue("Expected Pemberley to be indexed: $names", "Pemberley" in names)

        // Exclusions: stopwords, days, standalone honorifics, single-mention names
        assertFalse("Monday" in names)
        assertFalse("Tuesday" in names)
        assertFalse("However" in names)
        assertFalse("Meanwhile" in names)
        assertFalse("Jane" in names)

        // Exact UTF-16 span accuracy
        indexed.mentions.forEach { m ->
            val p = paragraphs.first { it.seqIndex == m.paragraphSeq }
            assertEquals(m.surfaceText, p.textContent.substring(m.charStart, m.charEnd))
        }
    }
}
