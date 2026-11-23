package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.repository.LookupRepository

data class MentionExcerpt(
    val mentionId: String,
    val episodeIndex: Int,
    val sectionTitle: String,
    val paragraphSeq: Int,
    val snippetText: String,
    val highlightStart: Int,
    val highlightEnd: Int,
    val surfaceText: String,
)

data class PriorMentionsResult(
    val queryTitle: String,
    val firstSeen: MentionExcerpt?,
    val recentMentions: List<MentionExcerpt>,
) {
    val isEmpty: Boolean get() = firstSeen == null && recentMentions.isEmpty()

    companion object {
        const val EMPTY_MESSAGE = "No earlier mentions found in what you've read so far."
    }
}

interface PriorMentionDataSource {
    suspend fun getFirstPriorMention(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
    ): MentionExcerptRow?

    suspend fun getRecentPriorMentions(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
        excludeMentionId: String,
        limit: Int,
    ): List<MentionExcerptRow>

    suspend fun getPriorParagraphsStrictlyBefore(
        bookId: String,
        maxReadSeq: Int,
        beforeSeq: Int,
    ): List<ParagraphEntity>
}

class RoomPriorMentionDataSource(
    private val lookupRepository: LookupRepository,
) : PriorMentionDataSource {
    override suspend fun getFirstPriorMention(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
    ): MentionExcerptRow? = lookupRepository.getFirstPriorMention(
        bookId = bookId,
        entityId = entityId,
        maxReadSeq = maxReadSeq,
        beforeParagraphSeq = beforeParagraphSeq,
    )

    override suspend fun getRecentPriorMentions(
        bookId: String,
        entityId: String,
        maxReadSeq: Int,
        beforeParagraphSeq: Int,
        excludeMentionId: String,
        limit: Int,
    ): List<MentionExcerptRow> = lookupRepository.getRecentPriorMentions(
        bookId = bookId,
        entityId = entityId,
        maxReadSeq = maxReadSeq,
        beforeParagraphSeq = beforeParagraphSeq,
        excludeMentionId = excludeMentionId,
        limit = limit,
    )

    override suspend fun getPriorParagraphsStrictlyBefore(
        bookId: String,
        maxReadSeq: Int,
        beforeSeq: Int,
    ): List<ParagraphEntity> = lookupRepository.getPriorParagraphsStrictlyBefore(
        bookId = bookId,
        maxReadSeq = maxReadSeq,
        beforeSeq = beforeSeq,
    )
}

/**
 * Spoiler-free "First & Recent Mentions" lookup service for uploaded books (T-16 / FR-009 / BR-004).
 * Strictly enforces paragraph_seq <= maxReadSeq AND paragraph_seq < currentParagraphSeq.
 */
class PriorMentionService(
    private val dataSource: PriorMentionDataSource,
) {
    suspend fun lookupEntity(
        bookId: String,
        entityId: String,
        displayTitle: String,
        maxReadSeq: Int,
        currentParagraphSeq: Int,
    ): PriorMentionsResult {
        if (maxReadSeq < 0 || currentParagraphSeq <= 0) {
            return PriorMentionsResult(displayTitle, firstSeen = null, recentMentions = emptyList())
        }

        val firstRow = dataSource.getFirstPriorMention(
            bookId = bookId,
            entityId = entityId,
            maxReadSeq = maxReadSeq,
            beforeParagraphSeq = currentParagraphSeq,
        ) ?: return PriorMentionsResult(displayTitle, firstSeen = null, recentMentions = emptyList())

        val recentRows = dataSource.getRecentPriorMentions(
            bookId = bookId,
            entityId = entityId,
            maxReadSeq = maxReadSeq,
            beforeParagraphSeq = currentParagraphSeq,
            excludeMentionId = firstRow.mentionId,
            limit = 3,
        )

        return PriorMentionsResult(
            queryTitle = displayTitle,
            firstSeen = toSnippetExcerpt(firstRow),
            recentMentions = recentRows.map { toSnippetExcerpt(it) },
        )
    }

    /**
     * Manual query fallback for un-underlined names (FR-009).
     * Scans ONLY paragraphs strictly satisfying seq_index <= maxReadSeq AND seq_index < currentParagraphSeq.
     */
    suspend fun lookupManualQuery(
        bookId: String,
        rawQuery: String,
        maxReadSeq: Int,
        currentParagraphSeq: Int,
        episodeInfoLookup: (String) -> Pair<Int, String> = { 1 to "" },
    ): PriorMentionsResult {
        val cleaned = DeterministicNameIndexer.stripPossessive(rawQuery.trim())
        if (cleaned.isBlank() || maxReadSeq < 0 || currentParagraphSeq <= 0) {
            return PriorMentionsResult(rawQuery.trim(), firstSeen = null, recentMentions = emptyList())
        }

        val priorParagraphs = dataSource.getPriorParagraphsStrictlyBefore(
            bookId = bookId,
            maxReadSeq = maxReadSeq,
            beforeSeq = currentParagraphSeq,
        )

        val pattern = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(cleaned) + "(?![\\p{L}\\p{N}])")
        val allMatches = mutableListOf<MentionExcerpt>()

        for (p in priorParagraphs) {
            // Double defense-in-depth against spoilers (NFR-005)
            if (p.seqIndex > maxReadSeq || p.seqIndex >= currentParagraphSeq) continue
            val m = pattern.find(p.textContent) ?: continue
            val (epIdx, epSection) = episodeInfoLookup(p.episodeId)
            allMatches += buildSnippet(
                mentionId = "manual_${p.seqIndex}_${m.range.first}",
                episodeIndex = epIdx,
                sectionTitle = epSection,
                paragraphSeq = p.seqIndex,
                fullText = p.textContent,
                matchStart = m.range.first,
                matchEnd = m.range.last + 1,
                surfaceText = m.value,
            )
        }

        if (allMatches.isEmpty()) {
            return PriorMentionsResult(cleaned, firstSeen = null, recentMentions = emptyList())
        }

        val first = allMatches.first()
        val recent = allMatches.drop(1).takeLast(3).reversed()
        return PriorMentionsResult(
            queryTitle = cleaned,
            firstSeen = first,
            recentMentions = recent,
        )
    }

    companion object {
        fun toSnippetExcerpt(row: MentionExcerptRow): MentionExcerpt = buildSnippet(
            mentionId = row.mentionId,
            episodeIndex = row.episodeIndex,
            sectionTitle = row.sectionTitle,
            paragraphSeq = row.paragraphSeq,
            fullText = row.textContent,
            matchStart = row.charStart,
            matchEnd = row.charEnd,
            surfaceText = row.surfaceText,
        )

        fun buildSnippet(
            mentionId: String,
            episodeIndex: Int,
            sectionTitle: String,
            paragraphSeq: Int,
            fullText: String,
            matchStart: Int,
            matchEnd: Int,
            surfaceText: String,
            contextRadiusChars: Int = 95,
        ): MentionExcerpt {
            val safeStart = matchStart.coerceIn(0, fullText.length)
            val safeEnd = matchEnd.coerceIn(safeStart, fullText.length)

            var winStart = (safeStart - contextRadiusChars).coerceAtLeast(0)
            var winEnd = (safeEnd + contextRadiusChars).coerceAtMost(fullText.length)

            if (winStart > 0) {
                val space = fullText.indexOf(' ', winStart)
                if (space in winStart until safeStart) winStart = space + 1
            }
            if (winEnd < fullText.length) {
                val space = fullText.lastIndexOf(' ', winEnd)
                if (space in safeEnd..winEnd) winEnd = space
            }

            val prefix = if (winStart > 0) "…" else ""
            val suffix = if (winEnd < fullText.length) "…" else ""
            val snippet = prefix + fullText.substring(winStart, winEnd).trim() + suffix
            val trimmedLeadDelta = fullText.substring(winStart, winEnd).indexOfFirst { !it.isWhitespace() }
                .let { if (it < 0) 0 else it }

            val relStart = (prefix.length + (safeStart - winStart - trimmedLeadDelta)).coerceIn(0, snippet.length)
            val relEnd = (relStart + (safeEnd - safeStart)).coerceIn(relStart, snippet.length)

            return MentionExcerpt(
                mentionId = mentionId,
                episodeIndex = episodeIndex,
                sectionTitle = sectionTitle,
                paragraphSeq = paragraphSeq,
                snippetText = snippet,
                highlightStart = relStart,
                highlightEnd = relEnd,
                surfaceText = surfaceText,
            )
        }
    }
}
