package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.EntitySourceKind
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.domain.importer.IndexedNamesOutput
import java.util.Locale

/**
 * Deterministic proper-name indexer for uploaded EPUB and PDF books (T-15 / BR-010).
 *  - Detects capitalized 1–3 word phrases (including optional honorific prefix like "Mr. Darcy").
 *  - Normalizes trailing possessives ('s / ’s).
 *  - Excludes English stopwords, days/months, and standalone honorifics.
 *  - Promotes candidates meeting BR-010 frequency: >= 2 non-sentence-initial OR >= 3 total occurrences.
 *  - Records exact UTF-16 (char_start, char_end) non-overlapping mention spans per paragraph.
 */
class DeterministicNameIndexer {

    private data class CandidateOccurrence(
        val paragraphSeq: Int,
        val episodeId: String,
        val charStart: Int,
        val charEnd: Int,
        val surfaceText: String,
        val normalizedKey: String,
        val isSentenceInitial: Boolean,
    )

    // Matches optional honorific ("Mr.", "Mrs.", "Dr.", "St.", "Prof.") + 1..3 capitalized name tokens, never absorbing sentence-ending periods.
    private val CANDIDATE_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])(?:(?:Mr|Mrs|Ms|Dr|St|Prof)\\.\\s+)?[A-Z][a-z]+(?:[-'][A-Z]?[a-z]+)*(?:\\s+[A-Z][a-z]+(?:[-'][A-Z]?[a-z]+)*){0,2}(?:['\\u2019]s)?(?![\\p{L}\\p{N}])",
    )

    fun indexBook(
        bookId: String,
        @Suppress("UNUSED_PARAMETER") episodes: List<EpisodeEntity>,
        paragraphs: List<ParagraphEntity>,
    ): IndexedNamesOutput {
        val rawOccurrences = mutableListOf<CandidateOccurrence>()

        for (paragraph in paragraphs) {
            if (paragraph.paragraphType == ParagraphType.SCENE_BREAK) continue
            val text = paragraph.textContent
            if (text.isBlank()) continue

            for (match in CANDIDATE_REGEX.findAll(text)) {
                val rawSurface = match.value
                val cleanedDisplay = stripPossessive(rawSurface).trim()
                val normalized = normalizeName(cleanedDisplay)
                if (!isValidCandidate(cleanedDisplay, normalized)) continue

                val start = match.range.first
                val end = start + cleanedDisplay.length
                val sentenceInitial = isSentenceStart(text, start)

                rawOccurrences += CandidateOccurrence(
                    paragraphSeq = paragraph.seqIndex,
                    episodeId = paragraph.episodeId,
                    charStart = start,
                    charEnd = end,
                    surfaceText = text.substring(start, end),
                    normalizedKey = normalized,
                    isSentenceInitial = sentenceInitial,
                )
            }
        }

        // Group by normalized key & apply BR-010 qualification rule
        val grouped = rawOccurrences.groupBy { it.normalizedKey }
        val qualifiedKeys = grouped.filterValues { list ->
            val nonInitialCount = list.count { !it.isSentenceInitial }
            nonInitialCount >= 2 || list.size >= 3
        }

        if (qualifiedKeys.isEmpty()) return IndexedNamesOutput()

        val entities = mutableListOf<NameEntity>()
        val aliases = mutableListOf<NameAliasEntity>()
        val allCandidateMentions = mutableListOf<Pair<String, CandidateOccurrence>>()

        var entityCounter = 0
        qualifiedKeys.entries
            .sortedBy { entry -> entry.value.minOf { it.paragraphSeq } }
            .forEach { (normKey, occs) ->
                val entityId = "${bookId}_idx_ent_${entityCounter++}"
                val canonical = occs
                    .groupingBy { it.surfaceText }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: occs.first().surfaceText
                val firstSeq = occs.minOf { it.paragraphSeq }

                entities += NameEntity(
                    entityId = entityId,
                    bookId = bookId,
                    canonicalName = canonical,
                    sourceKind = EntitySourceKind.INDEXED,
                    shortRole = null,
                    firstMentionSeq = firstSeq,
                )
                aliases += NameAliasEntity(
                    aliasId = "${entityId}_alias_0",
                    bookId = bookId,
                    entityId = entityId,
                    aliasText = canonical,
                    normalizedAlias = normKey,
                    toneNote = null,
                )
                occs.forEach { occ ->
                    allCandidateMentions += entityId to occ
                }
            }

        // Resolve overlaps per paragraph (prefer longest surface text)
        val mentions = mutableListOf<NameMentionEntity>()
        var mentionCounter = 0
        val byParagraph = allCandidateMentions.groupBy { it.second.paragraphSeq }
        for ((_, paraItems) in byParagraph.entries.sortedBy { it.key }) {
            val accepted = mutableListOf<Pair<Int, Int>>()
            val prioritized = paraItems.sortedWith(
                compareByDescending<Pair<String, CandidateOccurrence>> { it.second.charEnd - it.second.charStart }
                    .thenBy { it.second.charStart },
            )
            for ((entityId, occ) in prioritized) {
                val overlaps = accepted.any { (s, e) -> occ.charStart < e && occ.charEnd > s }
                if (!overlaps) {
                    accepted += occ.charStart to occ.charEnd
                    mentions += NameMentionEntity(
                        mentionId = "${bookId}_idx_m_${mentionCounter++}",
                        bookId = bookId,
                        entityId = entityId,
                        paragraphSeq = occ.paragraphSeq,
                        episodeId = occ.episodeId,
                        charStart = occ.charStart,
                        charEnd = occ.charEnd,
                        surfaceText = occ.surfaceText,
                    )
                }
            }
        }

        return IndexedNamesOutput(
            entities = entities,
            aliases = aliases,
            mentions = mentions.sortedWith(compareBy<NameMentionEntity> { it.paragraphSeq }.thenBy { it.charStart }),
        )
    }

    internal fun isValidCandidate(display: String, normalized: String): Boolean {
        if (display.length < 2 || normalized.isBlank()) return false
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 3) return false

        // Reject standalone stopwords or standalone honorifics
        if (tokens.size == 1) {
            val single = tokens.first().removeSuffix(".")
            if (single in EnglishStopwords.STOPWORDS || single in EnglishStopwords.HONORIFICS) {
                return false
            }
            if (single.length < 2) return false
            return true
        }

        // For multi-word phrases, reject if ALL tokens are stopwords/honorifics or if first word is a non-honorific stopword
        val firstClean = tokens.first().removeSuffix(".")
        if (firstClean in EnglishStopwords.STOPWORDS && firstClean !in EnglishStopwords.HONORIFICS) {
            return false
        }
        val lastClean = tokens.last().removeSuffix(".")
        if (lastClean in EnglishStopwords.STOPWORDS || lastClean in EnglishStopwords.HONORIFICS) {
            return false
        }
        return true
    }

    internal fun isSentenceStart(text: String, matchStart: Int): Boolean {
        var i = matchStart - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] in "\"'\u201C\u2018([")) {
            i--
        }
        if (i < 0) return true
        return text[i] == '.' || text[i] == '!' || text[i] == '?' || text[i] == '\n'
    }

    companion object {
        fun stripPossessive(raw: String): String =
            raw.replace(Regex("['\\u2019]s$"), "")

        fun normalizeName(raw: String): String =
            stripPossessive(raw)
                .trim()
                .replace(Regex("\\s+"), " ")
                .lowercase(Locale.ROOT)
    }
}
