package com.episodicreader.domain.recap

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.repository.ReaderRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

sealed interface RecapPayload {
    data class Curated(
        val episodeIndex: Int,
        val sectionTitle: String,
        val bullets: List<String>,
    ) : RecapPayload

    data class Uploaded(
        val episodeIndex: Int,
        val sectionTitle: String,
        val openingSentence: String?,
        val closingSentences: List<String>,
        val keyNames: List<EpisodeKeyNameRow>,
        val recapEpisodeEndSeq: Int,
    ) : RecapPayload

    data class EpisodeOneInProgress(
        val message: String = "You are currently in Episode 1. Complete your first episode to unlock \"Previously On...\" recaps.",
    ) : RecapPayload
}

interface BreakRecapDataSource {
    suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity?
    suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity>
    suspend fun getTopEntitiesForEpisode(
        bookId: String,
        episodeId: String,
        maxReadSeq: Int,
        limit: Int,
    ): List<EpisodeKeyNameRow>
}

class RoomBreakRecapDataSource(
    private val readerRepository: ReaderRepository,
    private val lookupRepository: LookupRepository,
) : BreakRecapDataSource {
    override suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity? =
        readerRepository.getMostRecentlyCompletedEpisode(bookId)

    override suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity> =
        readerRepository.getParagraphsForEpisode(episodeId)

    override suspend fun getTopEntitiesForEpisode(
        bookId: String,
        episodeId: String,
        maxReadSeq: Int,
        limit: Int,
    ): List<EpisodeKeyNameRow> = lookupRepository.getTopEntitiesForEpisode(
        bookId = bookId,
        episodeId = episodeId,
        maxReadSeq = maxReadSeq,
        limit = limit,
    )
}

/**
 * Coordinates 48-hour break re-entry detection (BR-005, ERR-010) and recap assembly (FR-011).
 */
class BreakRecapCoordinator(
    private val dataSource: BreakRecapDataSource,
) {
    companion object {
        const val BREAK_THRESHOLD_MILLIS: Long = 48L * 60L * 60L * 1000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Auto-triggers ONLY if gap >= 48 hours AND at least 1 episode is completed.
     * Clamps negative clock deltas to 0 per ERR-010 so backward clock jumps never false-trigger.
     */
    fun shouldAutoTriggerRecap(book: BookEntity, nowUtcMillis: Long): Boolean {
        if (book.completedEpisodesCount < 1) return false
        val lastRead = book.lastReadAtUtc ?: return false
        val elapsedMillis = (nowUtcMillis - lastRead).coerceAtLeast(0L)
        return elapsedMillis >= BREAK_THRESHOLD_MILLIS
    }

    suspend fun buildRecap(book: BookEntity, isManualRequest: Boolean): RecapPayload? {
        val lastCompleted = dataSource.getMostRecentlyCompletedEpisode(book.bookId)
        if (lastCompleted == null) {
            return if (isManualRequest) RecapPayload.EpisodeOneInProgress() else null
        }

        // Starter classic uses pre-authored 3-bullet recaps when present
        if (book.sourceType == SourceType.STARTER_CLASSIC && !lastCompleted.recapBulletsJson.isNullOrBlank()) {
            val bullets = parseBullets(lastCompleted.recapBulletsJson)
            if (bullets.isNotEmpty()) {
                return RecapPayload.Curated(
                    episodeIndex = lastCompleted.episodeIndex,
                    sectionTitle = lastCompleted.sectionTitle,
                    bullets = bullets,
                )
            }
        }

        // Uploaded books: opening sentence + final 2 sentences + up to 5 key names strictly <= maxReadSeq
        val paragraphs = dataSource.getParagraphsForEpisode(lastCompleted.episodeId)
            .filter { it.paragraphType != ParagraphType.HEADING && it.paragraphType != ParagraphType.SCENE_BREAK }
            .filter { it.seqIndex <= book.maxReadSeq.coerceAtLeast(lastCompleted.endParagraphSeq) }

        val texts = paragraphs.map { it.textContent }
        val opening = SentenceBoundaryExtractor.firstSentence(texts)
        val closing = SentenceBoundaryExtractor.lastSentences(texts, count = 2)
            .filter { it != opening }

        val effectiveMaxSeq = book.maxReadSeq.coerceAtLeast(lastCompleted.endParagraphSeq)
        val keyNames = dataSource.getTopEntitiesForEpisode(
            bookId = book.bookId,
            episodeId = lastCompleted.episodeId,
            maxReadSeq = effectiveMaxSeq,
            limit = 5,
        )

        return RecapPayload.Uploaded(
            episodeIndex = lastCompleted.episodeIndex,
            sectionTitle = lastCompleted.sectionTitle,
            openingSentence = opening,
            closingSentences = closing,
            keyNames = keyNames,
            recapEpisodeEndSeq = lastCompleted.endParagraphSeq,
        )
    }

    private fun parseBullets(rawJson: String): List<String> {
        return try {
            json.parseToJsonElement(rawJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
