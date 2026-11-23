package com.episodicreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
        NameAliasEntity::class,
        EntityFactEntity::class,
        NameMentionEntity::class,
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
        const val DATABASE_NAME = "episodic_reader_v1.db"

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME,
        )
            .addCallback(schemaGuardCallback())
            .build()

        fun createInMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .addCallback(schemaGuardCallback())
            .build()

        private fun schemaGuardCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SchemaGuards.apply(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
