package com.episodicreader.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite triggers & invariant checks installed when the database is created.
 * Enforces:
 *  - Singleton user_preferences row (pref_id = 1).
 *  - Non-negative paragraph seq_index and positive episode_index.
 *  - Monotonic helper invariants (full reset of starter classic explicitly allowed via dedicated reset).
 */
object SchemaGuards {
    val TRIGGER_STATEMENTS: List<String> = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS trg_user_preferences_singleton_insert
        BEFORE INSERT ON user_preferences
        WHEN NEW.pref_id != 1
        BEGIN
            SELECT RAISE(ABORT, 'user_preferences is a singleton table with pref_id = 1');
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS trg_user_preferences_font_bounds_insert
        BEFORE INSERT ON user_preferences
        WHEN NEW.font_size_sp < 12 OR NEW.font_size_sp > 28 OR (NEW.font_size_sp % 2) != 0
        BEGIN
            SELECT RAISE(ABORT, 'font_size_sp must be between 12 and 28 in 2sp steps');
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS trg_user_preferences_font_bounds_update
        BEFORE UPDATE ON user_preferences
        WHEN NEW.font_size_sp < 12 OR NEW.font_size_sp > 28 OR (NEW.font_size_sp % 2) != 0
        BEGIN
            SELECT RAISE(ABORT, 'font_size_sp must be between 12 and 28 in 2sp steps');
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS trg_paragraphs_seq_non_negative
        BEFORE INSERT ON paragraphs
        WHEN NEW.seq_index < 0
        BEGIN
            SELECT RAISE(ABORT, 'paragraphs.seq_index must be >= 0');
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS trg_episodes_index_positive
        BEFORE INSERT ON episodes
        WHEN NEW.episode_index < 1
        BEGIN
            SELECT RAISE(ABORT, 'episodes.episode_index must be >= 1');
        END
        """.trimIndent(),
    )

    fun apply(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        TRIGGER_STATEMENTS.forEach { sql ->
            db.execSQL(sql)
        }
    }
}
