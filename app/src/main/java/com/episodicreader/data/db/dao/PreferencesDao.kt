package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE pref_id = 1 LIMIT 1")
    fun observe(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE pref_id = 1 LIMIT 1")
    suspend fun get(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: UserPreferencesEntity)
}

