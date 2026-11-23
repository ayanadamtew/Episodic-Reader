package com.episodicreader.data.db.model

import androidx.room.ColumnInfo

/**
 * Projection for Library book cards. Deliberately excludes total episode count,
 * total word count, and total book percentage in compliance with BR-001.
 */
data class LibraryBookRow(
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "import_status")
    val importStatus: String,
    @ColumnInfo(name = "current_episode_id")
    val currentEpisodeId: String?,
    @ColumnInfo(name = "current_episode_index")
    val currentEpisodeIndex: Int?,
    @ColumnInfo(name = "current_section_title")
    val currentSectionTitle: String?,
    @ColumnInfo(name = "current_est_read_minutes")
    val currentEstReadMinutes: Int?,
    @ColumnInfo(name = "completed_episodes_count")
    val completedEpisodesCount: Int,
    @ColumnInfo(name = "completed_at_utc")
    val completedAtUtc: Long?,
    @ColumnInfo(name = "last_read_at_utc")
    val lastReadAtUtc: Long?,
    @ColumnInfo(name = "imported_at_utc")
    val importedAtUtc: Long,
)

/**
 * Joined mention row used for spoiler-gated Prior Mentions and First Seen quote cards.
 */
data class MentionExcerptRow(
    @ColumnInfo(name = "mention_id")
    val mentionId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "paragraph_seq")
    val paragraphSeq: Int,
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "episode_index")
    val episodeIndex: Int,
    @ColumnInfo(name = "section_title")
    val sectionTitle: String,
    @ColumnInfo(name = "text_content")
    val textContent: String,
    @ColumnInfo(name = "char_start")
    val charStart: Int,
    @ColumnInfo(name = "char_end")
    val charEnd: Int,
    @ColumnInfo(name = "surface_text")
    val surfaceText: String,
)

/**
 * Lightweight inline highlight span attached to a paragraph in an episode.
 */
data class ParagraphMentionSpan(
    @ColumnInfo(name = "mention_id")
    val mentionId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "paragraph_seq")
    val paragraphSeq: Int,
    @ColumnInfo(name = "char_start")
    val charStart: Int,
    @ColumnInfo(name = "char_end")
    val charEnd: Int,
    @ColumnInfo(name = "surface_text")
    val surfaceText: String,
)

/**
 * Daily episode completion count for local habit calculation (FR-012).
 */
data class DailyCompletionCountRow(
    @ColumnInfo(name = "local_date_iso")
    val localDateIso: String,
    @ColumnInfo(name = "episodes_completed")
    val episodesCompleted: Int,
)

/**
 * Key character frequency row for uploaded-book break recaps (FR-011).
 */
data class EpisodeKeyNameRow(
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "mention_count")
    val mentionCount: Int,
)
