package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EntitySourceKind
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.seed.StarterPackParser

data class CuratedCharacterProfile(
    val entityId: String,
    val canonicalName: String,
    val shortRole: String,
    val matchedAlias: String?,
    val aliasToneNote: String?,
    val knownAliases: List<NameAliasEntity>,
    val unlockedFacts: List<EntityFactEntity>,
    val firstSeen: MentionExcerpt?,
)

interface CuratedGlossaryDataSource {
    suspend fun getEntityById(bookId: String, entityId: String): NameEntity?
    suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity>
    suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity>
    suspend fun getUnlockedFacts(bookId: String, entityId: String, maxReadSeq: Int): List<EntityFactEntity>
    suspend fun getFirstMentionUpTo(bookId: String, entityId: String, maxReadSeq: Int): MentionExcerptRow?
}

class RoomCuratedGlossaryDataSource(
    private val lookupRepository: LookupRepository,
) : CuratedGlossaryDataSource {
    override suspend fun getEntityById(bookId: String, entityId: String): NameEntity? =
        lookupRepository.getEntityById(bookId, entityId)

    override suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity> =
        lookupRepository.getAllAliasesForBook(bookId)

    override suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity> =
        lookupRepository.getAliasesForEntity(entityId)

    override suspend fun getUnlockedFacts(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): List<EntityFactEntity> = lookupRepository.getUnlockedFacts(bookId, entityId, maxReadSeq)

    override suspend fun getFirstMentionUpTo(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
    ): MentionExcerptRow? = lookupRepository.getFirstMentionUpTo(bookId, entityId, maxReadSeq)
}

/**
 * Spoiler-free curated character glossary service for the starter classic (T-17 / FR-010 / BR-004).
 * Strictly filters facts with `unlockedAtSeq <= maxReadSeq` and matches aliases longest-first.
 */
class CuratedGlossaryService(
    private val dataSource: CuratedGlossaryDataSource,
) {
    suspend fun lookupByEntityId(
        bookId: String,
        entityId: String,
        surfaceText: String?,
        maxReadSeq: Int,
    ): CuratedCharacterProfile? {
        val entity = dataSource.getEntityById(bookId, entityId) ?: return null
        if (entity.sourceKind != EntitySourceKind.CURATED) return null
        return buildProfile(bookId, entity, surfaceText, maxReadSeq)
    }

    suspend fun lookupByQuery(
        bookId: String,
        rawQuery: String,
        maxReadSeq: Int,
    ): CuratedCharacterProfile? {
        val normalizedQuery = StarterPackParser.normalizeAlias(rawQuery)
        if (normalizedQuery.isBlank()) return null

        val allAliases = dataSource.getAllAliasesForBook(bookId)
            .sortedByDescending { it.normalizedAlias.length }

        val matched = allAliases.firstOrNull { alias ->
            alias.normalizedAlias == normalizedQuery ||
                normalizedQuery.contains(alias.normalizedAlias) ||
                alias.normalizedAlias.contains(normalizedQuery)
        } ?: return null

        val entity = dataSource.getEntityById(bookId, matched.entityId) ?: return null
        if (entity.sourceKind != EntitySourceKind.CURATED) return null
        return buildProfile(bookId, entity, matched.aliasText, maxReadSeq)
    }

    private suspend fun buildProfile(
        bookId: String,
        entity: NameEntity,
        surfaceText: String?,
        maxReadSeq: Int,
    ): CuratedCharacterProfile {
        val aliases = dataSource.getAliasesForEntity(entity.entityId)
        val normSurface = surfaceText?.let { StarterPackParser.normalizeAlias(it) }
        val matchedAlias = normSurface?.let { norm ->
            aliases.sortedByDescending { it.normalizedAlias.length }
                .firstOrNull { it.normalizedAlias == norm }
        }

        // Defense-in-depth anti-spoiler filter (NFR-005 / BR-004)
        val unlockedFacts = dataSource
            .getUnlockedFacts(bookId, entity.entityId, maxReadSeq)
            .filter { it.unlockedAtSeq <= maxReadSeq }

        val firstRow = dataSource.getFirstMentionUpTo(bookId, entity.entityId, maxReadSeq)
            ?.takeIf { it.paragraphSeq <= maxReadSeq }

        return CuratedCharacterProfile(
            entityId = entity.entityId,
            canonicalName = entity.canonicalName,
            shortRole = entity.shortRole.orEmpty(),
            matchedAlias = matchedAlias?.aliasText ?: surfaceText,
            aliasToneNote = matchedAlias?.toneNote,
            knownAliases = aliases,
            unlockedFacts = unlockedFacts,
            firstSeen = firstRow?.let { PriorMentionService.toSnippetExcerpt(it) },
        )
    }
}
