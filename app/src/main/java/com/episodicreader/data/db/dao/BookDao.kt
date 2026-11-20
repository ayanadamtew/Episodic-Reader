package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT * FROM books
        WHERE is_hidden = 0
        ORDER BY
            CASE WHEN last_read_at_utc IS NULL THEN 1 ELSE 0 END,
            last_read_at_utc DESC,
            imported_at_utc DESC
        """,
    )
    fun observeVisibleBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    suspend fun getById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query(
        """
        UPDATE books
        SET current_paragraph_seq = :visibleSeq,
            max_read_seq = CASE
                WHEN :visibleSeq > max_read_seq THEN :visibleSeq
                ELSE max_read_seq
            END,
            last_read_at_utc = :nowUtcMs
        WHERE book_id = :bookId
        """,
    )
    suspend fun recordVisibleParagraph(bookId: String, visibleSeq: Int, nowUtcMs: Long): Int

    @Query("UPDATE books SET is_hidden = :hidden WHERE book_id = :bookId")
    suspend fun setHidden(bookId: String, hidden: Boolean): Int

    @Query("DELETE FROM books WHERE book_id = :bookId")
    suspend fun deleteById(bookId: String): Int

    @Query("SELECT COUNT(*) FROM books WHERE book_id = :bookId")
    suspend fun countById(bookId: String): Int
}

