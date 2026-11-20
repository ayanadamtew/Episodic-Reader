package com.episodicreader.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.db.model.UnlockedFactRow

@Dao
interface LookupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntities(entities: List<NameEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMentions(mentions: List<NameMentionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAliases(aliases: List<NameAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFacts(facts: List<EntityFactEntity>)

    @Query(
        """
        SELECT * FROM name_entities
        WHERE book_id = :bookId
          AND normalized_key = :normalizedKey
          AND first_seen_seq <= :maxReadSeq
        ORDER BY first_seen_seq
        LIMIT 1
        """,
    )
    suspend fun findEntity(
        bookId: String,
        normalizedKey: String,
        maxReadSeq: Int,
    ): NameEntity?

    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            n.canonical_name,
            m.seq_index,
            p.plain_text,
            e.episode_number,
            e.section_heading,
            m.char_start,
            m.char_end
        FROM name_mentions AS m
        INNER JOIN name_entities AS n ON n.entity_id = m.entity_id
        INNER JOIN paragraphs AS p
            ON p.book_id = m.book_id AND p.seq_index = m.seq_index
        INNER JOIN episodes AS e ON e.episode_id = p.episode_id
        WHERE m.book_id = :bookId
          AND m.entity_id = :entityId
          AND m.seq_index <= :maxReadSeq
          AND m.seq_index < :currentParagraphSeq
        ORDER BY m.seq_index ASC
        LIMIT 1
        """,
    )
    suspend fun firstPriorMention(
        bookId: String,
        entityId: String,
        currentParagraphSeq: Int,
        maxReadSeq: Int,
    ): MentionExcerptRow?

    @Query(
        """
        SELECT
            m.mention_id,
            m.entity_id,
            n.canonical_name,
            m.seq_index,
            p.plain_text,
            e.episode_number,
            e.section_heading,
            m.char_start,
            m.char_end
        FROM name_mentions AS m
        INNER JOIN name_entities AS n ON n.entity_id = m.entity_id
        INNER JOIN paragraphs AS p
            ON p.book_id = m.book_id AND p.seq_index = m.seq_index
        INNER JOIN episodes AS e ON e.episode_id = p.episode_id
        WHERE m.book_id = :bookId
          AND m.entity_id = :entityId
          AND m.seq_index <= :maxReadSeq
          AND m.seq_index < :currentParagraphSeq
        ORDER BY m.seq_index DESC
        LIMIT :limit
        """,
    )
    suspend fun recentPriorMentions(
        bookId: String,
        entityId: String,
        currentParagraphSeq: Int,
        maxReadSeq: Int,
        limit: Int,
    ): List<MentionExcerptRow>

    @Query(
        """
        SELECT fact_id, unlocked_at_seq, fact_text
        FROM entity_facts
        WHERE entity_id = :entityId
          AND unlocked_at_seq <= :maxReadSeq
        ORDER BY unlocked_at_seq ASC
        """,
    )
    suspend fun unlockedFacts(entityId: String, maxReadSeq: Int): List<UnlockedFactRow>

    @Query("SELECT COUNT(*) FROM name_entities WHERE book_id = :bookId")
    suspend fun countEntitiesForBook(bookId: String): Int

    @Query("SELECT COUNT(*) FROM name_mentions WHERE book_id = :bookId")
    suspend fun countMentionsForBook(bookId: String): Int
}

