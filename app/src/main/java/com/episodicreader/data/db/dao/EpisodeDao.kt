package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.EpisodeEntity

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE episode_id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE book_id = :bookId ORDER BY episode_index ASC")
    suspend fun getEpisodesForBook(bookId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE book_id = :bookId AND episode_index = :episodeIndex LIMIT 1")
    suspend fun getEpisodeByIndex(bookId: String, episodeIndex: Int): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE book_id = :bookId ORDER BY episode_index ASC LIMIT 1")
    suspend fun getFirstEpisode(bookId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE book_id = :bookId ORDER BY episode_index DESC LIMIT 1")
    suspend fun getLastEpisode(bookId: String): EpisodeEntity?

    @Query(
        """
        SELECT * FROM episodes
        WHERE book_id = :bookId AND episode_index = :currentIndex - 1
        LIMIT 1
        """,
    )
    suspend fun getPreviousEpisode(bookId: String, currentIndex: Int): EpisodeEntity?

    @Query(
        """
        SELECT * FROM episodes
        WHERE book_id = :bookId AND episode_index = :currentIndex + 1
        LIMIT 1
        """,
    )
    suspend fun getNextEpisode(bookId: String, currentIndex: Int): EpisodeEntity?

    @Query(
        """
        SELECT * FROM episodes
        WHERE book_id = :bookId AND is_completed = 1
        ORDER BY episode_index DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentlyCompletedEpisode(bookId: String): EpisodeEntity?

    @Query("SELECT COUNT(*) FROM episodes WHERE book_id = :bookId AND is_completed = 1")
    suspend fun countCompletedEpisodes(bookId: String): Int

    @Query("SELECT MAX(episode_index) FROM episodes WHERE book_id = :bookId")
    suspend fun getMaxEpisodeIndex(bookId: String): Int?

    @Query(
        """
        UPDATE episodes
        SET is_completed = 1,
            completed_at_utc = COALESCE(completed_at_utc, :completedAtUtc)
        WHERE episode_id = :episodeId
        """,
    )
    suspend fun markEpisodeCompleted(episodeId: String, completedAtUtc: Long)

    @Query("UPDATE episodes SET is_completed = 0, completed_at_utc = NULL WHERE book_id = :bookId")
    suspend fun resetEpisodesForBook(bookId: String)
}
