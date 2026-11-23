package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.db.model.ParagraphMentionSpan

/**
 * Spoiler-free lookup DAO. Every user-facing lookup query strictly enforces
 * `seq_index <= :maxReadSeq` (BR-004 / NFR-005) directly at the SQL layer.
 */
@Dao
interface LookupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<NameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<NameAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(facts: List<EntityFactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMentions(mentions: List<NameMentionEntity>)

    @Query("SELECT * FROM name_entities WHERE entity_id = :entityId AND book_id = :bookId LIMIT 1")
    suspend fun getEntityById(bookId: String, entityId: String): NameEntity?

    @Query("SELECT * FROM name_entities WHERE book_id = :bookId AND source_kind = 'CURATED'")
    suspend fun getCuratedEntities(bookId: String): List<NameEntity>

    @Query("SELECT * FROM name_aliases WHERE book_id = :bookId")
    suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity>

    @Query("SELECT * FROM name_aliases WHERE book_id = :bookId AND normalized_alias = :normalizedAlias LIMIT 1")
    suspend fun findAliasByNormalized(bookId: String, normalizedAlias: String): NameAliasEntity?

    @Query("SELECT * FROM name_aliases WHERE entity_id = :entityId ORDER BY LENGTH(alias_text) DESC")
    suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity>

    /**
     * Returns only facts unlocked at or before maxReadSeq (BR-004 / FR-010).
     */
    @Query(
        """
        SELECT * FROM entity_facts
        WHERE book_id = :bookId
          AND entity_id = :entityId
          AND unlocked_at_seq <= :maxReadSeq
        ORDER BY sort_order ASC, unlocked_at_seq ASC
        """,
    )
    suspend fun getUnlockedFacts(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): List<EntityFactEntity>

    /**
     * Inline highlight spans for the currently visible episode.
     */
    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            ne.canonical_name,
            m.paragraph_seq,
            m.char_start,
            m.char_end,
            m.surface_text
        FROM name_mentions m
        INNER JOIN name_entities ne ON ne.entity_id = m.entity_id
        WHERE m.book_id = :bookId AND m.episode_id = :episodeId
        ORDER BY m.paragraph_seq ASC, m.char_start ASC
        """,
    )
    suspend fun getMentionSpansForEpisode(
        bookId: String,
        episodeId: String,
    ): List<ParagraphMentionSpan>

    /**
     * Returns the earliest prior mention strictly within read history (paragraph_seq <= maxReadSeq)
     * and strictly earlier than the tapped paragraph (paragraph_seq < currentParagraphSeq).
     */
    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            ne.canonical_name,
            m.paragraph_seq,
            m.episode_id,
            e.episode_index,
            e.section_title,
            p.text_content,
            m.char_start,
            m.char_end,
            m.surface_text
        FROM name_mentions m
        INNER JOIN name_entities ne ON ne.entity_id = m.entity_id
        INNER JOIN paragraphs p ON p.book_id = m.book_id AND p.seq_index = m.paragraph_seq
        INNER JOIN episodes e ON e.episode_id = m.episode_id
        WHERE m.book_id = :bookId
          AND m.entity_id = :entityId
          AND m.paragraph_seq <= :maxReadSeq
          AND m.paragraph_seq < :beforeParagraphSeq
        ORDER BY m.paragraph_seq ASC, m.char_start ASC
        LIMIT 1
        """,
    )
    suspend fun getFirstPriorMention(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
    ): MentionExcerptRow?

    /**
     * Returns up to :limit most recent prior mentions in reverse chronological order,
     * strictly within read history and excluding a specific mention_id (typically the First Seen row).
     */
    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            ne.canonical_name,
            m.paragraph_seq,
            m.episode_id,
            e.episode_index,
            e.section_title,
            p.text_content,
            m.char_start,
            m.char_end,
            m.surface_text
        FROM name_mentions m
        INNER JOIN name_entities ne ON ne.entity_id = m.entity_id
        INNER JOIN paragraphs p ON p.book_id = m.book_id AND p.seq_index = m.paragraph_seq
        INNER JOIN episodes e ON e.episode_id = m.episode_id
        WHERE m.book_id = :bookId
          AND m.entity_id = :entityId
          AND m.paragraph_seq <= :maxReadSeq
          AND m.paragraph_seq < :beforeParagraphSeq
          AND m.mention_id != :excludeMentionId
        ORDER BY m.paragraph_seq DESC, m.char_start DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentPriorMentions(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
        excludeMentionId: String,
        limit: Int = 3,
    ): List<MentionExcerptRow>

    /**
     * Returns the earliest mention up to maxReadSeq (inclusive) for curated character First Seen card.
     */
    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            ne.canonical_name,
            m.paragraph_seq,
            m.episode_id,
            e.episode_index,
            e.section_title,
            p.text_content,
            m.char_start,
            m.char_end,
            m.surface_text
        FROM name_mentions m
        INNER JOIN name_entities ne ON ne.entity_id = m.entity_id
        INNER JOIN paragraphs p ON p.book_id = m.book_id AND p.seq_index = m.paragraph_seq
        INNER JOIN episodes e ON e.episode_id = m.episode_id
        WHERE m.book_id = :bookId
          AND m.entity_id = :entityId
          AND m.paragraph_seq <= :maxReadSeq
        ORDER BY m.paragraph_seq ASC, m.char_start ASC
        LIMIT 1
        """,
    )
    suspend fun getFirstMentionUpTo(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): MentionExcerptRow?

    /**
     * Up to 5 most frequent entities in a specific completed episode (bounded by maxReadSeq) for FR-011 recap chips.
     */
    @Query(
        """
        SELECT
            m.entity_id,
            ne.canonical_name,
            COUNT(*) AS mention_count
        FROM name_mentions m
        INNER JOIN name_entities ne ON ne.entity_id = m.entity_id
        WHERE m.book_id = :bookId
          AND m.episode_id = :episodeId
          AND m.paragraph_seq <= :maxReadSeq
        GROUP BY m.entity_id, ne.canonical_name
        ORDER BY mention_count DESC, MIN(m.paragraph_seq) ASC
        LIMIT :limit
        """,
    )
    suspend fun getTopEntitiesForEpisode(
        bookId: String,
        episodeId: String,
        maxReadSeq: Int,
        limit: Int = 5,
    ): List<EpisodeKeyNameRow>
}
