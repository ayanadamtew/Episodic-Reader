package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: ReadingSessionLogEntity)

    @Query(
        """
        SELECT COUNT(*) FROM reading_session_logs
        WHERE completed_local_date = :localDate
        """,
    )
    fun observeCompletedCount(localDate: String): Flow<Int>

    @Query(
        """
        SELECT DISTINCT completed_local_date
        FROM reading_session_logs
        WHERE completed_local_date >= :fromLocalDate
        ORDER BY completed_local_date DESC
        """,
    )
    suspend fun activeDatesSince(fromLocalDate: String): List<String>

    @Query("SELECT * FROM reading_session_logs ORDER BY completed_at_utc")
    suspend fun getAll(): List<ReadingSessionLogEntity>
}

