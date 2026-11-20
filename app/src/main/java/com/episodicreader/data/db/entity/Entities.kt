package com.episodicreader.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["file_hash"], name = "idx_books_hash"),
        Index(
            value = ["is_hidden", "last_read_at_utc", "imported_at_utc"],
            name = "idx_books_sort",
        ),
    ],
)
data class BookEntity(
    @ColumnInfo(name = "book_id")
    @androidx.room.PrimaryKey
    val bookId: String,
    val title: String,
    val author: String,
    @ColumnInfo(name = "source_format") val sourceFormat: String,
    @ColumnInfo(name = "file_hash") val fileHash: String,
    @ColumnInfo(name = "private_file_path") val privateFilePath: String? = null,
    val status: String,
    @ColumnInfo(name = "has_curated_pack", defaultValue = "0")
    val hasCuratedPack: Boolean = false,
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "pdf_hint_shown", defaultValue = "0")
    val pdfHintShown: Boolean = false,
    @ColumnInfo(name = "current_episode_id") val currentEpisodeId: String? = null,
    @ColumnInfo(name = "current_paragraph_seq", defaultValue = "0")
    val currentParagraphSeq: Int = 0,
    @ColumnInfo(name = "max_read_seq", defaultValue = "-1")
    val maxReadSeq: Int = -1,
    @ColumnInfo(name = "completed_episode_count", defaultValue = "0")
    val completedEpisodeCount: Int = 0,
    @ColumnInfo(name = "imported_at_utc") val importedAtUtc: Long,
    @ColumnInfo(name = "last_read_at_utc") val lastReadAtUtc: Long? = null,
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
        Index(
            value = ["book_id", "episode_number"],
            name = "idx_ep_book_num",
            unique = true,
        ),
    ],
)
data class EpisodeEntity(
    @ColumnInfo(name = "episode_id")
    @androidx.room.PrimaryKey
    val episodeId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int,
    @ColumnInfo(name = "section_heading") val sectionHeading: String? = null,
    @ColumnInfo(name = "seq_start") val seqStart: Int,
    @ColumnInfo(name = "seq_end") val seqEnd: Int,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "est_minutes") val estMinutes: Int,
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at_utc") val completedAtUtc: Long? = null,
    @ColumnInfo(name = "curated_recap_json") val curatedRecapJson: String? = null,
)

@Entity(
    tableName = "paragraphs",
    primaryKeys = ["book_id", "seq_index"],
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
        Index(value = ["episode_id", "seq_index"], name = "idx_para_episode"),
    ],
)
data class ParagraphEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "seq_index") val seqIndex: Int,
    @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "paragraph_type") val paragraphType: String,
    @ColumnInfo(name = "plain_text") val plainText: String,
    @ColumnInfo(name = "inline_marks_json") val inlineMarksJson: String? = null,
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
        Index(value = ["book_id", "normalized_key"], name = "idx_entity_lookup"),
        Index(value = ["book_id", "first_seen_seq"], name = "idx_entity_first_seen"),
    ],
)
data class NameEntity(
    @ColumnInfo(name = "entity_id")
    @androidx.room.PrimaryKey
    val entityId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "normalized_key") val normalizedKey: String,
    @ColumnInfo(name = "first_seen_seq") val firstSeenSeq: Int,
    @ColumnInfo(name = "mention_count") val mentionCount: Int,
    @ColumnInfo(name = "short_role") val shortRole: String? = null,
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
        Index(value = ["entity_id", "seq_index"], name = "idx_mentions_entity_seq"),
        Index(value = ["book_id", "seq_index"], name = "idx_mentions_book_seq"),
    ],
)
data class NameMentionEntity(
    @ColumnInfo(name = "mention_id")
    @androidx.room.PrimaryKey
    val mentionId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "seq_index") val seqIndex: Int,
    @ColumnInfo(name = "char_start") val charStart: Int,
    @ColumnInfo(name = "char_end") val charEnd: Int,
)

@Entity(
    tableName = "name_aliases",
    foreignKeys = [
        ForeignKey(
            entity = NameEntity::class,
            parentColumns = ["entity_id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entity_id"], name = "idx_alias_entity")],
)
data class NameAliasEntity(
    @ColumnInfo(name = "alias_id")
    @androidx.room.PrimaryKey
    val aliasId: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "alias_text") val aliasText: String,
    @ColumnInfo(name = "tone_note") val toneNote: String? = null,
)

@Entity(
    tableName = "entity_facts",
    foreignKeys = [
        ForeignKey(
            entity = NameEntity::class,
            parentColumns = ["entity_id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entity_id", "unlocked_at_seq"], name = "idx_facts_unlock")],
)
data class EntityFactEntity(
    @ColumnInfo(name = "fact_id")
    @androidx.room.PrimaryKey
    val factId: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "unlocked_at_seq") val unlockedAtSeq: Int,
    @ColumnInfo(name = "fact_text") val factText: String,
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
        Index(value = ["completed_local_date"], name = "idx_logs_local_date"),
        Index(value = ["book_id"], name = "idx_logs_book"),
    ],
)
data class ReadingSessionLogEntity(
    @ColumnInfo(name = "log_id")
    @androidx.room.PrimaryKey
    val logId: String,
    @ColumnInfo(name = "book_id") val bookId: String?,
    @ColumnInfo(name = "episode_id") val episodeId: String?,
    @ColumnInfo(name = "completed_local_date") val completedLocalDate: String,
    @ColumnInfo(name = "completed_at_utc") val completedAtUtc: Long,
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @ColumnInfo(name = "pref_id")
    @androidx.room.PrimaryKey
    val prefId: Int = 1,
    @ColumnInfo(name = "font_size_sp", defaultValue = "16")
    val fontSizeSp: Int = 16,
    @ColumnInfo(name = "line_height_mode", defaultValue = "'STANDARD'")
    val lineHeightMode: String = "STANDARD",
    @ColumnInfo(name = "theme", defaultValue = "'LIGHT'")
    val theme: String = "LIGHT",
)

