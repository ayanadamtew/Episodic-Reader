package com.episodicreader.data.repository

import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.LineHeightMode
import com.episodicreader.data.db.entity.ThemeMode
import com.episodicreader.data.db.entity.TypographyBounds
import com.episodicreader.data.db.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(
    db: AppDatabase,
) {
    private val preferencesDao = db.preferencesDao()

    fun observePreferences(): Flow<UserPreferencesEntity> =
        preferencesDao.observePreferences().map { it ?: UserPreferencesEntity() }

    suspend fun getPreferences(): UserPreferencesEntity =
        preferencesDao.getPreferences() ?: UserPreferencesEntity()

    suspend fun ensureDefaults(nowUtc: Long) {
        if (preferencesDao.getPreferences() == null) {
            preferencesDao.upsertPreferences(UserPreferencesEntity(updatedAtUtc = nowUtc))
        }
    }

    suspend fun updateTheme(themeMode: String, nowUtc: Long) {
        val current = getPreferences()
        val safeTheme = when (themeMode) {
            ThemeMode.LIGHT, ThemeMode.SEPIA, ThemeMode.DARK_HIGH_CONTRAST -> themeMode
            else -> ThemeMode.LIGHT
        }
        preferencesDao.upsertPreferences(
            current.copy(prefId = 1, themeMode = safeTheme, updatedAtUtc = nowUtc),
        )
    }

    suspend fun updateFontSize(fontSizeSp: Int, nowUtc: Long) {
        val current = getPreferences()
        preferencesDao.upsertPreferences(
            current.copy(
                prefId = 1,
                fontSizeSp = TypographyBounds.clampFontSizeSp(fontSizeSp),
                updatedAtUtc = nowUtc,
            ),
        )
    }

    suspend fun updateLineHeight(lineHeightMode: String, nowUtc: Long) {
        val current = getPreferences()
        val safeLineHeight = when (lineHeightMode) {
            LineHeightMode.COMPACT, LineHeightMode.STANDARD, LineHeightMode.RELAXED -> lineHeightMode
            else -> LineHeightMode.STANDARD
        }
        preferencesDao.upsertPreferences(
            current.copy(prefId = 1, lineHeightMode = safeLineHeight, updatedAtUtc = nowUtc),
        )
    }
}
