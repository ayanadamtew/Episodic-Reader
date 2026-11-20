package com.episodicreader.data.db.model

import androidx.room.ColumnInfo

data class MentionExcerptRow(
    @ColumnInfo(name = "mention_id") val mentionId: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "seq_index") val seqIndex: Int,
    @ColumnInfo(name = "plain_text") val plainText: String,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int,
    @ColumnInfo(name = "section_heading") val sectionHeading: String?,
    @ColumnInfo(name = "char_start") val charStart: Int,
    @ColumnInfo(name = "char_end") val charEnd: Int,
)

data class UnlockedFactRow(
    @ColumnInfo(name = "fact_id") val factId: String,
    @ColumnInfo(name = "unlocked_at_seq") val unlockedAtSeq: Int,
    @ColumnInfo(name = "fact_text") val factText: String,
)

