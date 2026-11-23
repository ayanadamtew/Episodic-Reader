package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.model.LibraryBookRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query(
        """
        SELECT
            b.book_id,
            b.title,
            b.author,
            b.source_type,
            b.import_status,
            b.current_episode_id,
            e.episode_index AS current_episode_index,
            e.section_title AS current_section_title,
            e.est_read_minutes AS current_est_read_minutes,
            b.completed_episodes_count,
            b.completed_at_utc,
            b.last_read_at_utc,
            b.imported_at_utc
        FROM books b
        LEFT JOIN episodes e ON e.episode_id = b.current_episode_id
        WHERE b.is_hidden = 0 AND b.import_status IN ('READY', 'PROCESSING')
        ORDER BY
            CASE WHEN b.last_read_at_utc IS NULL THEN 1 ELSE 0 END ASC,
            b.last_read_at_utc DESC,
            b.imported_at_utc DESC
        """,
    )
    fun observeVisibleLibraryBooks(): Flow<List<LibraryBookRow>>

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    fun observeBookById(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE source_hash_sha256 = :sha256 AND import_status = 'READY' LIMIT 1")
    suspend fun findReadyBookByHash(sha256: String): BookEntity?

    @Query("SELECT * FROM books WHERE import_status = 'PROCESSING'")
    suspend fun getProcessingBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE source_type = 'STARTER_CLASSIC' LIMIT 1")
    suspend fun getStarterClassic(): BookEntity?

    @Query("SELECT COUNT(*) FROM books WHERE completed_at_utc IS NOT NULL")
    fun observeFinishedBooksCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    /**
     * Advances max_read_seq monotonically using SQLite MAX(max_read_seq, :candidateMaxSeq) (BR-003).
     * Navigating backward to an earlier episode or paragraph never decreases max_read_seq.
     */
    @Query(
        """
        UPDATE books
        SET current_episode_id = :episodeId,
            current_paragraph_seq = :paragraphSeq,
            max_read_seq = MAX(max_read_seq, :candidateMaxSeq),
            last_read_at_utc = :readAtUtc
        WHERE book_id = :bookId
        """,
    )
    suspend fun updateReadingPositionMonotonic(
        bookId: String,
        episodeId: String,
        paragraphSeq: Int,
        candidateMaxSeq: Int,
        readAtUtc: Long,
    )

    @Query(
        """
        UPDATE books
        SET max_read_seq = MAX(max_read_seq, :candidateMaxSeq),
            last_read_at_utc = :readAtUtc
        WHERE book_id = :bookId
        """,
    )
    suspend fun advanceMaxReadSeq(bookId: String, candidateMaxSeq: Int, readAtUtc: Long)

    @Query(
        """
        UPDATE books
        SET completed_episodes_count = :completedCount,
            completed_at_utc = COALESCE(completed_at_utc, :completedAtUtc),
            max_read_seq = MAX(max_read_seq, :endSeq),
            last_read_at_utc = :readAtUtc
        WHERE book_id = :bookId
        """,
    )
    suspend fun markBookEpisodeProgress(
        bookId: String,
        completedCount: Int,
        completedAtUtc: Long?,
        endSeq: Int,
        readAtUtc: Long,
    )

    @Query("UPDATE books SET is_hidden = :hidden WHERE book_id = :bookId")
    suspend fun setBookHidden(bookId: String, hidden: Int)

    @Query("UPDATE books SET pdf_notice_dismissed = 1 WHERE book_id = :bookId")
    suspend fun markPdfNoticeDismissed(bookId: String)

    @Query("UPDATE books SET import_status = :status, current_episode_id = :firstEpisodeId WHERE book_id = :bookId")
    suspend fun markImportReady(bookId: String, status: String, firstEpisodeId: String)

    @Query("UPDATE books SET import_status = 'FAILED' WHERE book_id = :bookId")
    suspend fun markImportFailed(bookId: String)

    @Query(
        """
        UPDATE books
        SET current_episode_id = :firstEpisodeId,
            current_paragraph_seq = 0,
            max_read_seq = -1,
            completed_episodes_count = 0,
            completed_at_utc = NULL,
            last_read_at_utc = NULL,
            is_hidden = 0
        WHERE book_id = :bookId
        """,
    )
    suspend fun resetStarterClassicProgress(bookId: String, firstEpisodeId: String)

    @Query("DELETE FROM books WHERE book_id = :bookId")
    suspend fun deleteBookById(bookId: String)
}
