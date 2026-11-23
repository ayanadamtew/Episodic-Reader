package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.ParagraphEntity

@Dao
interface ParagraphDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(paragraphs: List<ParagraphEntity>)

    @Query("SELECT * FROM paragraphs WHERE episode_id = :episodeId ORDER BY seq_index ASC")
    suspend fun getParagraphsForEpisode(episodeId: String): List<ParagraphEntity>

    @Query(
        """
        SELECT * FROM paragraphs
        WHERE book_id = :bookId AND seq_index <= :maxReadSeq AND seq_index < :beforeSeq
        ORDER BY seq_index ASC
        """,
    )
    suspend fun getPriorParagraphsStrictlyBefore(
        bookId: String,
        maxReadSeq: Int,
        beforeSeq: Int,
    ): List<ParagraphEntity>

    @Query("SELECT * FROM paragraphs WHERE book_id = :bookId AND seq_index = :seqIndex LIMIT 1")
    suspend fun getParagraphBySeq(bookId: String, seqIndex: Int): ParagraphEntity?
}
