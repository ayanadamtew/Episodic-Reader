package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.EpisodeEntity

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE episode_id = :episodeId LIMIT 1")
    suspend fun getById(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE book_id = :bookId ORDER BY episode_number")
    suspend fun getForBook(bookId: String): List<EpisodeEntity>

    @Query("SELECT COUNT(*) FROM episodes WHERE book_id = :bookId")
    suspend fun countForBook(bookId: String): Int
}

