package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.model.MentionExcerptRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorMentionServiceTest {

    private class FakeDataSource(
        private val mentions: List<MentionExcerptRow>,
        private val paragraphs: List<ParagraphEntity>,
    ) : PriorMentionDataSource {
        override suspend fun getFirstPriorMention(
            bookId: String,
            entityId: String,
            maxReadSeq: Int,
            beforeParagraphSeq: Int,
        ): MentionExcerptRow? = mentions
            .filter { it.entityId == entityId && it.paragraphSeq <= maxReadSeq && it.paragraphSeq < beforeParagraphSeq }
            .minByOrNull { it.paragraphSeq }

        override suspend fun getRecentPriorMentions(
            bookId: String,
            entityId: String,
            maxReadSeq: Int,
            beforeParagraphSeq: Int,
            excludeMentionId: String,
            limit: Int,
        ): List<MentionExcerptRow> = mentions
            .filter {
                it.entityId == entityId &&
                    it.paragraphSeq <= maxReadSeq &&
                    it.paragraphSeq < beforeParagraphSeq &&
                    it.mentionId != excludeMentionId
            }
            .sortedByDescending { it.paragraphSeq }
            .take(limit)

        override suspend fun getPriorParagraphsStrictlyBefore(
            bookId: String,
            maxReadSeq: Int,
            beforeSeq: Int,
        ): List<ParagraphEntity> = paragraphs
            .filter { it.seqIndex <= maxReadSeq && it.seqIndex < beforeSeq }
            .sortedBy { it.seqIndex }
    }

    @Test
    fun returnsFirstSeenAndUpTo3RecentPriorMentionsExcludingFutureParagraphs() = runTest {
        val rows = listOf(1, 3, 5, 7, 9, 12).map { seq ->
            MentionExcerptRow(
                mentionId = "m$seq",
                entityId = "e1",
                canonicalName = "Darcy",
                paragraphSeq = seq,
                episodeId = "ep1",
                episodeIndex = 1,
                sectionTitle = "Chapter 1",
                textContent = "Paragraph $seq mentions Darcy at the estate.",
                charStart = "Paragraph $seq mentions ".length,
                charEnd = "Paragraph $seq mentions Darcy".length,
                surfaceText = "Darcy",
            )
        }
        val service = PriorMentionService(FakeDataSource(rows, emptyList()))

        val result = service.lookupEntity("b1", "e1", "Darcy", maxReadSeq = 10, currentParagraphSeq = 10)
        assertEquals(1, result.firstSeen?.paragraphSeq)
        assertEquals(listOf(9, 7, 5), result.recentMentions.map { it.paragraphSeq })
        // Seq 12 is strictly excluded
        assertTrue(result.recentMentions.none { it.paragraphSeq > 10 })
    }

    @Test
    fun manualLookupRespectsStrictReadBoundaryAndReturnsErr012WhenEmpty() = runTest {
        val paragraphs = listOf(
            ParagraphEntity("p0", "b1", "ep1", 0, ParagraphType.BODY, "Opening scene without the name."),
            ParagraphEntity("p1", "b1", "ep1", 1, ParagraphType.BODY, "First mention of Pemberley in the valley."),
            ParagraphEntity("p5", "b1", "ep2", 5, ParagraphType.BODY, "Spoiler mention of Pemberley in future episode."),
        )
        val service = PriorMentionService(FakeDataSource(emptyList(), paragraphs))

        // At seq 1 (first appearance itself), no *earlier* mentions exist -> ERR-012 empty state
        val empty = service.lookupManualQuery("b1", "Pemberley", maxReadSeq = 1, currentParagraphSeq = 1)
        assertTrue(empty.isEmpty)
        assertNull(empty.firstSeen)

        // At seq 3 with maxReadSeq 3, seq 1 is visible while seq 5 is hidden
        val found = service.lookupManualQuery("b1", "Pemberley", maxReadSeq = 3, currentParagraphSeq = 3)
        assertEquals(1, found.firstSeen?.paragraphSeq)
        assertTrue(found.recentMentions.isEmpty())
    }
}
