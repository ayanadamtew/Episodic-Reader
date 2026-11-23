package com.episodicreader.domain.recap

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakRecapTest {

    @Test
    fun sentenceSplitterDoesNotSplitOnAbbreviationsInitialsOrDecimals() {
        val text = "Mr. Darcy met Dr. Watson in St. Petersburg at 3.14 p.m. while F. M. Dostoevsky watched. Then it rained! Did they leave?"
        val sentences = SentenceBoundaryExtractor.splitSentences(text)
        assertEquals(3, sentences.size)
        assertTrue(sentences[0].startsWith("Mr. Darcy"))
        assertTrue(sentences[0].contains("St. Petersburg"))
        assertTrue(sentences[0].contains("3.14"))
        assertEquals("Then it rained!", sentences[1])
        assertEquals("Did they leave?", sentences[2])
    }

    @Test
    fun autoTriggerRespects48HourThresholdCompletedCountAndBackwardClockJumpErr010() {
        val dummySource = object : BreakRecapDataSource {
            override suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity? = null
            override suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity> = emptyList()
            override suspend fun getTopEntitiesForEpisode(bookId: String, episodeId: String, maxReadSeq: Int, limit: Int): List<EpisodeKeyNameRow> = emptyList()
        }
        val coordinator = BreakRecapCoordinator(dummySource)
        val baseTime = 1_700_000_000_000L
        val fortyEightHours = 48L * 60L * 60L * 1000L

        val completedBook = BookEntity(
            bookId = "b1", title = "T", author = "A", sourceType = SourceType.EPUB,
            sourceHashSha256 = "h", localFilePath = null, importStatus = ImportStatus.READY,
            completedEpisodesCount = 1, lastReadAtUtc = baseTime, importedAtUtc = baseTime,
        )

        // Under 48h -> false
        assertFalse(coordinator.shouldAutoTriggerRecap(completedBook, baseTime + fortyEightHours - 1L))
        // Exactly 48h -> true
        assertTrue(coordinator.shouldAutoTriggerRecap(completedBook, baseTime + fortyEightHours))
        // Backward clock jump (ERR-010) -> clamped to 0, false
        assertFalse(coordinator.shouldAutoTriggerRecap(completedBook, baseTime - fortyEightHours))
        // Zero completed episodes -> false even after 72h
        val ep1Only = completedBook.copy(completedEpisodesCount = 0)
        assertFalse(coordinator.shouldAutoTriggerRecap(ep1Only, baseTime + fortyEightHours * 2))
    }

    @Test
    fun buildsCuratedBulletsOrUploadedExcerptsWithKeyNames() = runTest {
        val curatedEp = EpisodeEntity(
            "ep1", "wn", 1, "First Night", 950, 5, 0, 2,
            recapBulletsJson = """["Bullet one.","Bullet two.","Bullet three."]""",
            isCompleted = 1,
        )
        val uploadedParas = listOf(
            ParagraphEntity("p0", "up", "u_ep1", 0, ParagraphType.HEADING, "Chapter One"),
            ParagraphEntity("p1", "up", "u_ep1", 1, ParagraphType.BODY, "Mr. Darcy arrived at Netherfield in autumn. Everyone noticed him at once."),
            ParagraphEntity("p2", "up", "u_ep1", 2, ParagraphType.BODY, "Elizabeth laughed softly. Jane smiled back across the ballroom."),
        )
        val uploadedEp = EpisodeEntity("u_ep1", "up", 1, "Chapter One", 950, 5, 0, 2, isCompleted = 1)

        val source = object : BreakRecapDataSource {
            override suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity? =
                if (bookId == "wn") curatedEp else uploadedEp

            override suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity> =
                if (episodeId == "u_ep1") uploadedParas else emptyList()

            override suspend fun getTopEntitiesForEpisode(bookId: String, episodeId: String, maxReadSeq: Int, limit: Int): List<EpisodeKeyNameRow> =
                listOf(EpisodeKeyNameRow("e1", "Mr. Darcy", 4), EpisodeKeyNameRow("e2", "Elizabeth", 3))
        }

        val coordinator = BreakRecapCoordinator(source)

        val curatedBook = BookEntity("wn", "White Nights", "Dostoevsky", SourceType.STARTER_CLASSIC, "h", null, ImportStatus.READY, completedEpisodesCount = 1, maxReadSeq = 2, importedAtUtc = 1L)
        val curatedPayload = coordinator.buildRecap(curatedBook, isManualRequest = true) as RecapPayload.Curated
        assertEquals(3, curatedPayload.bullets.size)

        val uploadedBook = BookEntity("up", "Pride", "Austen", SourceType.EPUB, "h2", null, ImportStatus.READY, completedEpisodesCount = 1, maxReadSeq = 2, importedAtUtc = 1L)
        val uploadedPayload = coordinator.buildRecap(uploadedBook, isManualRequest = true) as RecapPayload.Uploaded
        assertEquals("Mr. Darcy arrived at Netherfield in autumn.", uploadedPayload.openingSentence)
        assertEquals(listOf("Elizabeth laughed softly.", "Jane smiled back across the ballroom."), uploadedPayload.closingSentences)
        assertEquals(2, uploadedPayload.keyNames.size)
    }
}
