package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.ParagraphEntity

@Dao
interface ParagraphDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(paragraphs: List<ParagraphEntity>)

    @Query("SELECT * FROM paragraphs WHERE episode_id = :episodeId ORDER BY seq_index")
    suspend fun getForEpisode(episodeId: String): List<ParagraphEntity>

    @Query(
        """
        SELECT * FROM paragraphs
        WHERE book_id = :bookId AND seq_index = :seqIndex
        LIMIT 1
        """,
    )
    suspend fun getBySequence(bookId: String, seqIndex: Int): ParagraphEntity?

    @Query("SELECT COUNT(*) FROM paragraphs WHERE book_id = :bookId")
    suspend fun countForBook(bookId: String): Int
}

