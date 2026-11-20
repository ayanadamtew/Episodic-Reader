package com.episodicreader.data.repository

import androidx.room.withTransaction
import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import kotlinx.coroutines.flow.Flow

data class BookGraph(
    val book: BookEntity,
    val episodes: List<EpisodeEntity>,
    val paragraphs: List<ParagraphEntity>,
    val nameEntities: List<NameEntity> = emptyList(),
    val nameMentions: List<NameMentionEntity> = emptyList(),
    val nameAliases: List<NameAliasEntity> = emptyList(),
    val entityFacts: List<EntityFactEntity> = emptyList(),
)

class BookRepository(private val database: AppDatabase) {
    private val bookDao = database.bookDao()

    fun observeVisibleBooks(): Flow<List<BookEntity>> = bookDao.observeVisibleBooks()

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getById(bookId)

    suspend fun insertGraph(graph: BookGraph) {
        require(graph.episodes.isNotEmpty()) { "A book must contain at least one episode." }
        require(graph.paragraphs.isNotEmpty()) { "A book must contain at least one paragraph." }

        database.withTransaction {
            bookDao.insert(graph.book)
            database.episodeDao().insertAll(graph.episodes)
            database.paragraphDao().insertAll(graph.paragraphs)
            database.lookupDao().apply {
                if (graph.nameEntities.isNotEmpty()) insertEntities(graph.nameEntities)
                if (graph.nameMentions.isNotEmpty()) insertMentions(graph.nameMentions)
                if (graph.nameAliases.isNotEmpty()) insertAliases(graph.nameAliases)
                if (graph.entityFacts.isNotEmpty()) insertFacts(graph.entityFacts)
            }
        }
    }

    suspend fun recordVisibleParagraph(bookId: String, visibleSeq: Int, nowUtcMs: Long) {
        require(visibleSeq >= 0) { "Paragraph sequence must be non-negative." }
        check(bookDao.recordVisibleParagraph(bookId, visibleSeq, nowUtcMs) == 1) {
            "Book not found: $bookId"
        }
    }

    suspend fun setHidden(bookId: String, hidden: Boolean) {
        check(bookDao.setHidden(bookId, hidden) == 1) { "Book not found: $bookId" }
    }

    suspend fun deleteBook(bookId: String): String? = database.withTransaction {
        val privatePath = bookDao.getById(bookId)?.privateFilePath
        check(bookDao.deleteById(bookId) == 1) { "Book not found: $bookId" }
        privatePath
    }
}

