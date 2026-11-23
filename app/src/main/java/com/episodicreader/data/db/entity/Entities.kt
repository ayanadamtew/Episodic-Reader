package com.episodicreader.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["source_hash_sha256"]),
        Index(value = ["is_hidden", "import_status"]),
    ],
)
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_hash_sha256")
    val sourceHashSha256: String,
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String?,
    @ColumnInfo(name = "import_status")
    val importStatus: String,
    @ColumnInfo(name = "is_hidden")
    val isHidden: Int = 0,
    @ColumnInfo(name = "current_episode_id")
    val currentEpisodeId: String? = null,
    @ColumnInfo(name = "current_paragraph_seq")
    val currentParagraphSeq: Int = 0,
    @ColumnInfo(name = "max_read_seq")
    val maxReadSeq: Int = -1,
    @ColumnInfo(name = "completed_episodes_count")
    val completedEpisodesCount: Int = 0,
    @ColumnInfo(name = "completed_at_utc")
    val completedAtUtc: Long? = null,
    @ColumnInfo(name = "last_read_at_utc")
    val lastReadAtUtc: Long? = null,
    @ColumnInfo(name = "imported_at_utc")
    val importedAtUtc: Long,
    @ColumnInfo(name = "pdf_notice_dismissed")
    val pdfNoticeDismissed: Int = 0,
    @ColumnInfo(name = "attribution_note")
    val attributionNote: String? = null,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "episode_index"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "episode_index")
    val episodeIndex: Int,
    @ColumnInfo(name = "section_title")
    val sectionTitle: String,
    @ColumnInfo(name = "word_count")
    val wordCount: Int,
    @ColumnInfo(name = "est_read_minutes")
    val estReadMinutes: Int,
    @ColumnInfo(name = "start_paragraph_seq")
    val startParagraphSeq: Int,
    @ColumnInfo(name = "end_paragraph_seq")
    val endParagraphSeq: Int,
    @ColumnInfo(name = "recap_bullets_json")
    val recapBulletsJson: String? = null,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Int = 0,
    @ColumnInfo(name = "completed_at_utc")
    val completedAtUtc: Long? = null,
)

@Entity(
    tableName = "paragraphs",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["episode_id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "seq_index"], unique = true),
        Index(value = ["episode_id", "seq_index"]),
    ],
)
data class ParagraphEntity(
    @PrimaryKey
    @ColumnInfo(name = "paragraph_id")
    val paragraphId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "seq_index")
    val seqIndex: Int,
    @ColumnInfo(name = "paragraph_type")
    val paragraphType: String,
    @ColumnInfo(name = "text_content")
    val textContent: String,
    @ColumnInfo(name = "inline_marks_json")
    val inlineMarksJson: String? = null,
)

@Entity(
    tableName = "name_entities",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "canonical_name"]),
        Index(value = ["book_id", "first_mention_seq"]),
    ],
)
data class NameEntity(
    @PrimaryKey
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "source_kind")
    val sourceKind: String,
    @ColumnInfo(name = "short_role")
    val shortRole: String? = null,
    @ColumnInfo(name = "first_mention_seq")
    val firstMentionSeq: Int,
)

@Entity(
    tableName = "name_aliases",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NameEntity::class,
            parentColumns = ["entity_id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "normalized_alias"]),
        Index(value = ["entity_id"]),
    ],
)
data class NameAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_id")
    val aliasId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "alias_text")
    val aliasText: String,
    @ColumnInfo(name = "normalized_alias")
    val normalizedAlias: String,
    @ColumnInfo(name = "tone_note")
    val toneNote: String? = null,
)

@Entity(
    tableName = "entity_facts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NameEntity::class,
            parentColumns = ["entity_id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["entity_id", "unlocked_at_seq"]),
        Index(value = ["book_id"]),
    ],
)
data class EntityFactEntity(
    @PrimaryKey
    @ColumnInfo(name = "fact_id")
    val factId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "unlocked_at_seq")
    val unlockedAtSeq: Int,
    @ColumnInfo(name = "fact_text")
    val factText: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

@Entity(
    tableName = "name_mentions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NameEntity::class,
            parentColumns = ["entity_id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "entity_id", "paragraph_seq"]),
        Index(value = ["book_id", "episode_id", "paragraph_seq"]),
        Index(value = ["entity_id"]),
    ],
)
data class NameMentionEntity(
    @PrimaryKey
    @ColumnInfo(name = "mention_id")
    val mentionId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "paragraph_seq")
    val paragraphSeq: Int,
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "char_start")
    val charStart: Int,
    @ColumnInfo(name = "char_end")
    val charEnd: Int,
    @ColumnInfo(name = "surface_text")
    val surfaceText: String,
)

@Entity(
    tableName = "reading_session_logs",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["local_date_iso"]),
        Index(value = ["book_id"]),
    ],
)
data class ReadingSessionLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    val logId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String?,
    @ColumnInfo(name = "book_title_snapshot")
    val bookTitleSnapshot: String,
    @ColumnInfo(name = "episode_id_snapshot")
    val episodeIdSnapshot: String,
    @ColumnInfo(name = "episode_index_snapshot")
    val episodeIndexSnapshot: Int,
    @ColumnInfo(name = "completed_at_utc")
    val completedAtUtc: Long,
    @ColumnInfo(name = "local_date_iso")
    val localDateIso: String,
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "pref_id")
    val prefId: Int = 1,
    @ColumnInfo(name = "theme_mode")
    val themeMode: String = ThemeMode.LIGHT,
    @ColumnInfo(name = "font_size_sp")
    val fontSizeSp: Int = TypographyBounds.DEFAULT_FONT_SIZE_SP,
    @ColumnInfo(name = "line_height_mode")
    val lineHeightMode: String = LineHeightMode.STANDARD,
    @ColumnInfo(name = "updated_at_utc")
    val updatedAtUtc: Long = 0L,
)
