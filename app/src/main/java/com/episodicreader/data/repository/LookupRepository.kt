package com.episodicreader.data.repository

import com.episodicreader.data.db.dao.LookupDao
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.db.model.UnlockedFactRow

data class PriorMentionsCard(
    val firstSeen: MentionExcerptRow?,
    val recentMentions: List<MentionExcerptRow>,
)

class LookupRepository(private val lookupDao: LookupDao) {
    suspend fun findReadableEntity(
        bookId: String,
        normalizedKey: String,
        maxReadSeq: Int,
    ): NameEntity? {
        require(maxReadSeq >= -1)
        if (maxReadSeq == -1) return null
        return lookupDao.findEntity(bookId, normalizedKey.lowercase(), maxReadSeq)
    }

    suspend fun getPriorMentions(
        bookId: String,
        entityId: String,
        currentParagraphSeq: Int,
        maxReadSeq: Int,
    ): PriorMentionsCard {
        require(currentParagraphSeq >= 0)
        require(maxReadSeq >= -1)
        if (maxReadSeq == -1) return PriorMentionsCard(null, emptyList())

        val first = lookupDao.firstPriorMention(
            bookId = bookId,
            entityId = entityId,
            currentParagraphSeq = currentParagraphSeq,
            maxReadSeq = maxReadSeq,
        )
        val recent = lookupDao.recentPriorMentions(
            bookId = bookId,
            entityId = entityId,
            currentParagraphSeq = currentParagraphSeq,
            maxReadSeq = maxReadSeq,
            limit = 4,
        )
            .filterNot { it.mentionId == first?.mentionId }
            .take(3)

        return PriorMentionsCard(first, recent)
    }

    suspend fun getUnlockedFacts(
        entityId: String,
        maxReadSeq: Int,
    ): List<UnlockedFactRow> {
        require(maxReadSeq >= -1)
        if (maxReadSeq == -1) return emptyList()
        return lookupDao.unlockedFacts(entityId, maxReadSeq)
    }
}

