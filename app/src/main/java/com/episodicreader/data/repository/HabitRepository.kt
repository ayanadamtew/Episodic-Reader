package com.episodicreader.data.repository

import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import com.episodicreader.data.db.model.DailyCompletionCountRow
import kotlinx.coroutines.flow.Flow

class HabitRepository(
    db: AppDatabase,
) {
    private val habitDao = db.habitDao()

    fun observeAllLogs(): Flow<List<ReadingSessionLogEntity>> = habitDao.observeAllLogs()

    fun observeDailyCompletionCounts(): Flow<List<DailyCompletionCountRow>> =
        habitDao.observeDailyCompletionCounts()

    suspend fun getAllLogs(): List<ReadingSessionLogEntity> = habitDao.getAllLogs()

    suspend fun countEpisodesForDate(localDateIso: String): Int =
        habitDao.countEpisodesForDate(localDateIso)

    suspend fun countAllCompletedEpisodes(): Int = habitDao.countAllCompletedEpisodes()
}
