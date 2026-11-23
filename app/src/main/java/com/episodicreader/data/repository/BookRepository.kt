package com.episodicreader.data.repository

import androidx.room.withTransaction
import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.model.LibraryBookRow
import kotlinx.coroutines.flow.Flow

data class AtomicBookIngestionBundle(
    val book: BookEntity,
    val episodes: List<EpisodeEntity>,
    val paragraphs: List<ParagraphEntity>,
    val entities: List<NameEntity> = emptyList(),
    val aliases: List<NameAliasEntity> = emptyList(),
    val facts: List<EntityFactEntity> = emptyList(),
    val mentions: List<NameMentionEntity> = emptyList(),
)

class BookRepository(
    private val db: AppDatabase,
) {
    private val bookDao = db.bookDao()
    private val episodeDao = db.episodeDao()
    private val paragraphDao = db.paragraphDao()
    private val lookupDao = db.lookupDao()

    fun observeVisibleLibraryBooks(): Flow<List<LibraryBookRow>> =
        bookDao.observeVisibleLibraryBooks()

    fun observeBookById(bookId: String): Flow<BookEntity?> =
        bookDao.observeBookById(bookId)

    fun observeFinishedBooksCount(): Flow<Int> =
        bookDao.observeFinishedBooksCount()

    suspend fun getBookById(bookId: String): BookEntity? =
        bookDao.getBookById(bookId)

    suspend fun getStarterClassic(): BookEntity? =
        bookDao.getStarterClassic()

    suspend fun findReadyBookByHash(sha256: String): BookEntity? =
        bookDao.findReadyBookByHash(sha256)

    suspend fun getProcessingBooks(): List<BookEntity> =
        bookDao.getProcessingBooks()

    suspend fun insertProcessingPlaceholder(book: BookEntity) {
        bookDao.insertBook(book.copy(importStatus = ImportStatus.PROCESSING))
    }

    /**
     * Atomically persists a parsed or seeded book along with its episodes, paragraphs,
     * entities, aliases, facts, and mentions, transitioning the book to READY.
     */
    suspend fun commitBookBundleAtomically(bundle: AtomicBookIngestionBundle) {
        val firstEpisodeId = bundle.episodes.minByOrNull { it.episodeIndex }?.episodeId
        db.withTransaction {
            bookDao.upsertBook(
                bundle.book.copy(
                    importStatus = ImportStatus.READY,
                    currentEpisodeId = bundle.book.currentEpisodeId ?: firstEpisodeId,
                ),
            )
            if (bundle.episodes.isNotEmpty()) {
                episodeDao.insertAll(bundle.episodes)
            }
            if (bundle.paragraphs.isNotEmpty()) {
                paragraphDao.insertAll(bundle.paragraphs)
            }
            if (bundle.entities.isNotEmpty()) {
                lookupDao.insertEntities(bundle.entities)
            }
            if (bundle.aliases.isNotEmpty()) {
                lookupDao.insertAliases(bundle.aliases)
            }
            if (bundle.facts.isNotEmpty()) {
                lookupDao.insertFacts(bundle.facts)
            }
            if (bundle.mentions.isNotEmpty()) {
                lookupDao.insertMentions(bundle.mentions)
            }
        }
    }

    suspend fun markImportFailed(bookId: String) {
        bookDao.markImportFailed(bookId)
    }

    suspend fun setBookHidden(bookId: String, hidden: Boolean) {
        bookDao.setBookHidden(bookId, if (hidden) 1 else 0)
    }

    suspend fun markPdfNoticeDismissed(bookId: String) {
        bookDao.markPdfNoticeDismissed(bookId)
    }

    suspend fun resetStarterClassic(bookId: String) {
        db.withTransaction {
            val firstEp = episodeDao.getFirstEpisode(bookId) ?: return@withTransaction
            episodeDao.resetEpisodesForBook(bookId)
            bookDao.resetStarterClassicProgress(bookId, firstEp.episodeId)
        }
    }

    suspend fun deleteBookById(bookId: String) {
        bookDao.deleteBookById(bookId)
    }
}
