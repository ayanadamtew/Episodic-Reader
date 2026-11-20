package com.episodicreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.episodicreader.data.db.dao.BookDao
import com.episodicreader.data.db.dao.EpisodeDao
import com.episodicreader.data.db.dao.HabitDao
import com.episodicreader.data.db.dao.LookupDao
import com.episodicreader.data.db.dao.ParagraphDao
import com.episodicreader.data.db.dao.PreferencesDao
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import com.episodicreader.data.db.entity.UserPreferencesEntity

@Database(
    entities = [
        BookEntity::class,
        EpisodeEntity::class,
        ParagraphEntity::class,
        NameEntity::class,
        NameMentionEntity::class,
        NameAliasEntity::class,
        EntityFactEntity::class,
        ReadingSessionLogEntity::class,
        UserPreferencesEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun paragraphDao(): ParagraphDao
    abstract fun lookupDao(): LookupDao
    abstract fun habitDao(): HabitDao
    abstract fun preferencesDao(): PreferencesDao

    companion object {
        private const val DATABASE_NAME = "episodic_reader.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(SchemaGuards)
                .build()
    }
}

