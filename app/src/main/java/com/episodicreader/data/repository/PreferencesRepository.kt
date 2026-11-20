package com.episodicreader.data.repository

import com.episodicreader.data.db.dao.PreferencesDao
import com.episodicreader.data.db.entity.LineHeightMode
import com.episodicreader.data.db.entity.ReaderThemeName
import com.episodicreader.data.db.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val preferencesDao: PreferencesDao) {
    fun observe(): Flow<UserPreferencesEntity> =
        preferencesDao.observe().map { it ?: UserPreferencesEntity() }

    suspend fun update(fontSizeSp: Int, lineHeightMode: String, theme: String) {
        require(lineHeightMode in allowedLineHeights) { "Unsupported line-height mode." }
        require(theme in allowedThemes) { "Unsupported reader theme." }

        val boundedSize = fontSizeSp.coerceIn(12, 28)
        val steppedSize = boundedSize - (boundedSize % 2)
        preferencesDao.upsert(
            UserPreferencesEntity(
                fontSizeSp = steppedSize,
                lineHeightMode = lineHeightMode,
                theme = theme,
            ),
        )
    }

    private companion object {
        val allowedLineHeights = setOf(
            LineHeightMode.COMPACT,
            LineHeightMode.STANDARD,
            LineHeightMode.RELAXED,
        )
        val allowedThemes = setOf(
            ReaderThemeName.LIGHT,
            ReaderThemeName.SEPIA,
            ReaderThemeName.DARK_HIGH_CONTRAST,
        )
    }
}

