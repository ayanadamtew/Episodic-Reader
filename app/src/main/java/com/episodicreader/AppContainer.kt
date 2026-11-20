package com.episodicreader

import android.content.Context
import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.HabitRepository
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.repository.PreferencesRepository
import com.episodicreader.data.repository.ReaderRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val books = BookRepository(database)
    val reader = ReaderRepository(database.episodeDao(), database.paragraphDao())
    val lookup = LookupRepository(database.lookupDao())
    val habits = HabitRepository(database.habitDao())
    val preferences = PreferencesRepository(database.preferencesDao())
}

