package com.episodicreader.data.repository

import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.db.model.ParagraphMentionSpan

class LookupRepository(
    db: AppDatabase,
) {
    private val lookupDao = db.lookupDao()
    private val paragraphDao = db.paragraphDao()

    suspend fun getMentionSpansForEpisode(
        bookId: String,
        episodeId: String,
    ): List<ParagraphMentionSpan> = lookupDao.getMentionSpansForEpisode(bookId, episodeId)

    suspend fun getEntityById(bookId: String, entityId: String): NameEntity? =
        lookupDao.getEntityById(bookId, entityId)

    suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity> =
        lookupDao.getAllAliasesForBook(bookId)

    suspend fun findAliasByNormalized(bookId: String, normalizedAlias: String): NameAliasEntity? =
        lookupDao.findAliasByNormalized(bookId, normalizedAlias)

    suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity> =
        lookupDao.getAliasesForEntity(entityId)

    suspend fun getUnlockedFacts(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): List<EntityFactEntity> = lookupDao.getUnlockedFacts(bookId, entityId, maxReadSeq)

    suspend fun getFirstPriorMention(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
    ): MentionExcerptRow? = lookupDao.getFirstPriorMention(
        bookId = bookId,
        entityId = entityId,
        maxReadSeq = maxReadSeq,
        beforeParagraphSeq = beforeParagraphSeq,
    )

    suspend fun getRecentPriorMentions(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
        excludeMentionId: String,
        limit: Int = 3,
    ): List<MentionExcerptRow> = lookupDao.getRecentPriorMentions(
        bookId = bookId,
        entityId = entityId,
        maxReadSeq = maxReadSeq,
        beforeParagraphSeq = beforeParagraphSeq,
        excludeMentionId = excludeMentionId,
        limit = limit,
    )

    suspend fun getFirstMentionUpTo(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): MentionExcerptRow? = lookupDao.getFirstMentionUpTo(bookId, entityId, maxReadSeq)

    suspend fun getPriorParagraphsStrictlyBefore(
        bookId: String,
        maxReadSeq: Int,
        beforeSeq: Int,
    ): List<ParagraphEntity> = paragraphDao.getPriorParagraphsStrictlyBefore(
        bookId = bookId,
        maxReadSeq = maxReadSeq,
        beforeSeq = beforeSeq,
    )

    suspend fun getTopEntitiesForEpisode(
        bookId: String,
        episodeId: String,
        maxReadSeq: Int,
        limit: Int = 5,
    ): List<EpisodeKeyNameRow> = lookupDao.getTopEntitiesForEpisode(
        bookId = bookId,
        episodeId = episodeId,
        maxReadSeq = maxReadSeq,
        limit = limit,
    )
}
