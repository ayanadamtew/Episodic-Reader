package com.episodicreader.data.repository

import androidx.room.withTransaction
import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import java.util.UUID

data class EpisodeContentBundle(
    val book: BookEntity,
    val episode: EpisodeEntity,
    val paragraphs: List<ParagraphEntity>,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val isFinalEpisode: Boolean,
)

class ReaderRepository(
    private val db: AppDatabase,
) {
    private val bookDao = db.bookDao()
    private val episodeDao = db.episodeDao()
    private val paragraphDao = db.paragraphDao()
    private val habitDao = db.habitDao()

    suspend fun loadCurrentEpisodeBundle(bookId: String, overrideEpisodeId: String? = null): EpisodeContentBundle? {
        val book = bookDao.getBookById(bookId) ?: return null
        val targetEpisode = when {
            overrideEpisodeId != null -> episodeDao.getEpisodeById(overrideEpisodeId)
            book.currentEpisodeId != null -> episodeDao.getEpisodeById(book.currentEpisodeId)
            else -> episodeDao.getFirstEpisode(bookId)
        } ?: episodeDao.getFirstEpisode(bookId) ?: return null

        val paragraphs = paragraphDao.getParagraphsForEpisode(targetEpisode.episodeId)
        val prev = episodeDao.getPreviousEpisode(bookId, targetEpisode.episodeIndex)
        val next = episodeDao.getNextEpisode(bookId, targetEpisode.episodeIndex)
        val maxEpIndex = episodeDao.getMaxEpisodeIndex(bookId) ?: targetEpisode.episodeIndex

        return EpisodeContentBundle(
            book = book,
            episode = targetEpisode,
            paragraphs = paragraphs,
            hasPrevious = prev != null,
            hasNext = next != null,
            isFinalEpisode = targetEpisode.episodeIndex >= maxEpIndex,
        )
    }

    suspend fun getPreviousEpisode(bookId: String, currentIndex: Int): EpisodeEntity? =
        episodeDao.getPreviousEpisode(bookId, currentIndex)

    suspend fun getNextEpisode(bookId: String, currentIndex: Int): EpisodeEntity? =
        episodeDao.getNextEpisode(bookId, currentIndex)

    suspend fun getFirstEpisode(bookId: String): EpisodeEntity? =
        episodeDao.getFirstEpisode(bookId)

    suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity? =
        episodeDao.getMostRecentlyCompletedEpisode(bookId)

    suspend fun getEpisodeMetadataMap(bookId: String): Map<String, Pair<Int, String>> =
        episodeDao.getEpisodesForBook(bookId).associate { it.episodeId to (it.episodeIndex to it.sectionTitle) }

    suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity> =
        paragraphDao.getParagraphsForEpisode(episodeId)

    suspend fun updateReadingPositionMonotonic(
        bookId: String,
        episodeId: String,
        paragraphSeq: Int,
        candidateMaxSeq: Int,
        readAtUtc: Long,
    ) {
        bookDao.updateReadingPositionMonotonic(
            bookId = bookId,
            episodeId = episodeId,
            paragraphSeq = paragraphSeq,
            candidateMaxSeq = candidateMaxSeq,
            readAtUtc = readAtUtc,
        )
    }

    suspend fun advanceMaxReadSeq(bookId: String, candidateMaxSeq: Int, readAtUtc: Long) {
        bookDao.advanceMaxReadSeq(bookId, candidateMaxSeq, readAtUtc)
    }

    /**
     * Atomically marks an episode completed, updates book completed_episodes_count,
     * sets completed_at_utc if final episode, advances max_read_seq, writes a ReadingSessionLog
     * (when newly completed in this session), and optionally advances current_episode_id to next episode.
     */
    suspend fun completeEpisodeAtomically(
        bookId: String,
        episodeId: String,
        completedAtUtc: Long,
        localDateIso: String,
        advanceToNextEpisode: Boolean,
    ): EpisodeEntity? = db.withTransaction {
        val book = bookDao.getBookById(bookId) ?: return@withTransaction null
        val episode = episodeDao.getEpisodeById(episodeId) ?: return@withTransaction null
        val wasAlreadyCompleted = episode.isCompleted == 1

        episodeDao.markEpisodeCompleted(episodeId, completedAtUtc)
        val totalCompleted = episodeDao.countCompletedEpisodes(bookId)
        val maxIndex = episodeDao.getMaxEpisodeIndex(bookId) ?: episode.episodeIndex
        val isFinal = episode.episodeIndex >= maxIndex

        bookDao.markBookEpisodeProgress(
            bookId = bookId,
            completedCount = totalCompleted,
            completedAtUtc = if (isFinal) completedAtUtc else null,
            endSeq = episode.endParagraphSeq,
            readAtUtc = completedAtUtc,
        )

        if (!wasAlreadyCompleted) {
            habitDao.insertLog(
                ReadingSessionLogEntity(
                    logId = UUID.randomUUID().toString(),
                    bookId = book.bookId,
                    bookTitleSnapshot = book.title,
                    episodeIdSnapshot = episode.episodeId,
                    episodeIndexSnapshot = episode.episodeIndex,
                    completedAtUtc = completedAtUtc,
                    localDateIso = localDateIso,
                ),
            )
        }

        val nextEp = episodeDao.getNextEpisode(bookId, episode.episodeIndex)
        if (advanceToNextEpisode && nextEp != null) {
            bookDao.updateReadingPositionMonotonic(
                bookId = bookId,
                episodeId = nextEp.episodeId,
                paragraphSeq = nextEp.startParagraphSeq,
                candidateMaxSeq = episode.endParagraphSeq,
                readAtUtc = completedAtUtc,
            )
        }
        nextEp
    }
}
