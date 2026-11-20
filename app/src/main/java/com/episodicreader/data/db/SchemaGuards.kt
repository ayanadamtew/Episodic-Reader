package com.episodicreader.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room models keys and indices directly. These triggers add the CHECK-style
 * invariants from the SRS that Room annotations cannot express.
 */
internal object SchemaGuards : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        db.execSQL(
            """
            CREATE TRIGGER guard_books_insert
            BEFORE INSERT ON books
            WHEN NEW.source_format NOT IN ('EPUB','PDF','BUNDLED')
              OR NEW.status NOT IN ('PROCESSING','READY','COMPLETED')
              OR NEW.max_read_seq < -1
              OR NEW.current_paragraph_seq < 0
              OR NEW.completed_episode_count < 0
              OR NEW.has_curated_pack NOT IN (0,1)
              OR NEW.is_hidden NOT IN (0,1)
              OR NEW.pdf_hint_shown NOT IN (0,1)
            BEGIN
                SELECT RAISE(ABORT, 'invalid books row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_books_update
            BEFORE UPDATE ON books
            WHEN NEW.source_format NOT IN ('EPUB','PDF','BUNDLED')
              OR NEW.status NOT IN ('PROCESSING','READY','COMPLETED')
              OR NEW.max_read_seq < -1
              OR NEW.current_paragraph_seq < 0
              OR NEW.completed_episode_count < 0
              OR NEW.has_curated_pack NOT IN (0,1)
              OR NEW.is_hidden NOT IN (0,1)
              OR NEW.pdf_hint_shown NOT IN (0,1)
            BEGIN
                SELECT RAISE(ABORT, 'invalid books row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_episodes_insert
            BEFORE INSERT ON episodes
            WHEN NEW.episode_number < 1
              OR NEW.seq_start < 0
              OR NEW.seq_end < NEW.seq_start
              OR NEW.word_count <= 0
              OR NEW.est_minutes <= 0
              OR NEW.is_completed NOT IN (0,1)
            BEGIN
                SELECT RAISE(ABORT, 'invalid episodes row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_episodes_update
            BEFORE UPDATE ON episodes
            WHEN NEW.episode_number < 1
              OR NEW.seq_start < 0
              OR NEW.seq_end < NEW.seq_start
              OR NEW.word_count <= 0
              OR NEW.est_minutes <= 0
              OR NEW.is_completed NOT IN (0,1)
            BEGIN
                SELECT RAISE(ABORT, 'invalid episodes row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_paragraphs_insert
            BEFORE INSERT ON paragraphs
            WHEN NEW.seq_index < 0
              OR NEW.paragraph_type NOT IN ('BODY','HEADING','BLOCKQUOTE','SCENE_BREAK')
              OR length(trim(NEW.plain_text)) = 0
            BEGIN
                SELECT RAISE(ABORT, 'invalid paragraphs row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_name_entities_insert
            BEFORE INSERT ON name_entities
            WHEN NEW.first_seen_seq < 0
              OR NEW.mention_count < 0
              OR length(trim(NEW.canonical_name)) = 0
              OR length(trim(NEW.normalized_key)) = 0
            BEGIN
                SELECT RAISE(ABORT, 'invalid name_entities row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_name_mentions_insert
            BEFORE INSERT ON name_mentions
            WHEN NEW.seq_index < 0
              OR NEW.char_start < 0
              OR NEW.char_end <= NEW.char_start
            BEGIN
                SELECT RAISE(ABORT, 'invalid name_mentions row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_name_aliases_insert
            BEFORE INSERT ON name_aliases
            WHEN length(trim(NEW.alias_text)) = 0
            BEGIN
                SELECT RAISE(ABORT, 'invalid name_aliases row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_entity_facts_insert
            BEFORE INSERT ON entity_facts
            WHEN NEW.unlocked_at_seq < 0
              OR length(trim(NEW.fact_text)) = 0
            BEGIN
                SELECT RAISE(ABORT, 'invalid entity_facts row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_reading_logs_insert
            BEFORE INSERT ON reading_session_logs
            WHEN NEW.completed_local_date NOT GLOB '????-??-??'
              OR NEW.completed_at_utc < 0
            BEGIN
                SELECT RAISE(ABORT, 'invalid reading_session_logs row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_preferences_insert
            BEFORE INSERT ON user_preferences
            WHEN NEW.pref_id != 1
              OR NEW.font_size_sp < 12
              OR NEW.font_size_sp > 28
              OR NEW.line_height_mode NOT IN ('COMPACT','STANDARD','RELAXED')
              OR NEW.theme NOT IN ('LIGHT','SEPIA','DARK_HIGH_CONTRAST')
            BEGIN
                SELECT RAISE(ABORT, 'invalid user_preferences row');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER guard_preferences_update
            BEFORE UPDATE ON user_preferences
            WHEN NEW.pref_id != 1
              OR NEW.font_size_sp < 12
              OR NEW.font_size_sp > 28
              OR NEW.line_height_mode NOT IN ('COMPACT','STANDARD','RELAXED')
              OR NEW.theme NOT IN ('LIGHT','SEPIA','DARK_HIGH_CONTRAST')
            BEGIN
                SELECT RAISE(ABORT, 'invalid user_preferences row');
            END
            """.trimIndent(),
        )
    }
}

