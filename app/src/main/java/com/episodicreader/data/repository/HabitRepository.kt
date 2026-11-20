package com.episodicreader.data.repository

import com.episodicreader.data.db.dao.HabitDao
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val habitDao: HabitDao) {
    suspend fun recordCompletion(log: ReadingSessionLogEntity) = habitDao.insert(log)

    fun observeCompletedCount(localDate: String): Flow<Int> =
        habitDao.observeCompletedCount(localDate)

    suspend fun activeDatesSince(fromLocalDate: String): List<String> =
        habitDao.activeDatesSince(fromLocalDate)
}

