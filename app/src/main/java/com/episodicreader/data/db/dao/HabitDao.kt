package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import com.episodicreader.data.db.model.DailyCompletionCountRow
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ReadingSessionLogEntity)

    @Query("SELECT * FROM reading_session_logs ORDER BY completed_at_utc DESC")
    fun observeAllLogs(): Flow<List<ReadingSessionLogEntity>>

    @Query("SELECT * FROM reading_session_logs ORDER BY completed_at_utc DESC")
    suspend fun getAllLogs(): List<ReadingSessionLogEntity>

    @Query(
        """
        SELECT local_date_iso, COUNT(*) AS episodes_completed
        FROM reading_session_logs
        GROUP BY local_date_iso
        ORDER BY local_date_iso DESC
        """,
    )
    fun observeDailyCompletionCounts(): Flow<List<DailyCompletionCountRow>>

    @Query("SELECT COUNT(*) FROM reading_session_logs WHERE local_date_iso = :localDateIso")
    suspend fun countEpisodesForDate(localDateIso: String): Int

    @Query("SELECT COUNT(*) FROM reading_session_logs")
    suspend fun countAllCompletedEpisodes(): Int
}
