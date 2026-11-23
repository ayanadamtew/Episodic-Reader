package com.episodicreader.data.seed

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EntitySourceKind
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.repository.AtomicBookIngestionBundle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Locale

data class StarterInlineMark(
    val start: Int,
    val end: Int,
    val style: String,
)

data class StarterParagraphAsset(
    val seqIndex: Int,
    val paragraphType: String,
    val textContent: String,
    val inlineMarks: List<StarterInlineMark>,
)

data class StarterEpisodeAsset(
    val episodeId: String,
    val episodeIndex: Int,
    val sectionTitle: String,
    val wordCount: Int,
    val estReadMinutes: Int,
    val recapBullets: List<String>,
    val paragraphs: List<StarterParagraphAsset>,
)

data class StarterAliasAsset(
    val aliasText: String,
    val toneNote: String?,
)

data class StarterFactAsset(
    val factId: String,
    val unlockedAtSeq: Int,
    val factText: String,
    val sortOrder: Int,
)

data class StarterEntityAsset(
    val entityId: String,
    val canonicalName: String,
    val shortRole: String,
    val firstMentionSeq: Int,
    val aliases: List<StarterAliasAsset>,
    val facts: List<StarterFactAsset>,
)

data class StarterPackAsset(
    val bookId: String,
    val title: String,
    val author: String,
    val attributionNote: String,
    val episodes: List<StarterEpisodeAsset>,
    val entities: List<StarterEntityAsset>,
)

/**
 * Pure-Kotlin parser for `assets/starter_classic/white_nights_pack.json` using kotlinx-serialization-json
 * JsonElement parsing (works identically on Android and JVM unit tests with zero reflection).
 */
object StarterPackParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): StarterPackAsset {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val bookId = root.requireString("book_id")
        val title = root.requireString("title")
        val author = root.requireString("author")
        val attributionNote = root.requireString("attribution_note")

        val episodes = root.getValue("episodes").jsonArray.map { epEl ->
            val epObj = epEl.jsonObject
            val epId = epObj.requireString("episode_id")
            val epIndex = epObj.requireInt("episode_index")
            val sectionTitle = epObj.requireString("section_title")
            val wordCount = epObj.requireInt("word_count")
            val estReadMinutes = epObj.requireInt("est_read_minutes")
            val bullets = epObj["recap_bullets"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val paragraphs = epObj.getValue("paragraphs").jsonArray.map { pEl ->
                val pObj = pEl.jsonObject
                val marks = pObj["inline_marks"]?.jsonArray?.map { mEl ->
                    val mObj = mEl.jsonObject
                    StarterInlineMark(
                        start = mObj.requireInt("start"),
                        end = mObj.requireInt("end"),
                        style = mObj.requireString("style"),
                    )
                } ?: emptyList()
                StarterParagraphAsset(
                    seqIndex = pObj.requireInt("seq_index"),
                    paragraphType = pObj.requireString("paragraph_type"),
                    textContent = pObj.requireString("text_content"),
                    inlineMarks = marks,
                )
            }
            StarterEpisodeAsset(
                episodeId = epId,
                episodeIndex = epIndex,
                sectionTitle = sectionTitle,
                wordCount = wordCount,
                estReadMinutes = estReadMinutes,
                recapBullets = bullets,
                paragraphs = paragraphs,
            )
        }

        val entities = root.getValue("entities").jsonArray.map { entEl ->
            val entObj = entEl.jsonObject
            val aliases = entObj["aliases"]?.jsonArray?.map { aEl ->
                val aObj = aEl.jsonObject
                StarterAliasAsset(
                    aliasText = aObj.requireString("alias_text"),
                    toneNote = aObj["tone_note"]?.jsonPrimitive?.contentOrNull,
                )
            } ?: emptyList()
            val facts = entObj["facts"]?.jsonArray?.map { fEl ->
                val fObj = fEl.jsonObject
                StarterFactAsset(
                    factId = fObj.requireString("fact_id"),
                    unlockedAtSeq = fObj.requireInt("unlocked_at_seq"),
                    factText = fObj.requireString("fact_text"),
                    sortOrder = fObj.requireInt("sort_order"),
                )
            } ?: emptyList()
            StarterEntityAsset(
                entityId = entObj.requireString("entity_id"),
                canonicalName = entObj.requireString("canonical_name"),
                shortRole = entObj.requireString("short_role"),
                firstMentionSeq = entObj.requireInt("first_mention_seq"),
                aliases = aliases,
                facts = facts,
            )
        }

        return StarterPackAsset(
            bookId = bookId,
            title = title,
            author = author,
            attributionNote = attributionNote,
            episodes = episodes,
            entities = entities,
        )
    }

    /**
     * Converts a parsed [StarterPackAsset] into an [AtomicBookIngestionBundle] ready for Room.
     * Deterministically scans paragraphs longest-alias-first to build exact UTF-16 mention spans.
     */
    fun toIngestionBundle(pack: StarterPackAsset, importedAtUtc: Long): AtomicBookIngestionBundle {
        val sortedEpisodes = pack.episodes.sortedBy { it.episodeIndex }
        val firstEpisode = sortedEpisodes.firstOrNull()
            ?: error("Starter pack must contain at least one episode")

        val bookHash = sha256Hex("${pack.bookId}:${pack.title}:${pack.author}:${sortedEpisodes.size}")
        val book = BookEntity(
            bookId = pack.bookId,
            title = pack.title,
            author = pack.author,
            sourceType = SourceType.STARTER_CLASSIC,
            sourceHashSha256 = bookHash,
            localFilePath = null,
            importStatus = ImportStatus.READY,
            isHidden = 0,
            currentEpisodeId = firstEpisode.episodeId,
            currentParagraphSeq = firstEpisode.paragraphs.minOfOrNull { it.seqIndex } ?: 0,
            maxReadSeq = -1,
            completedEpisodesCount = 0,
            completedAtUtc = null,
            lastReadAtUtc = null,
            importedAtUtc = importedAtUtc,
            pdfNoticeDismissed = 1,
            attributionNote = pack.attributionNote,
        )

        val episodeEntities = mutableListOf<EpisodeEntity>()
        val paragraphEntities = mutableListOf<ParagraphEntity>()

        sortedEpisodes.forEach { ep ->
            val sortedParagraphs = ep.paragraphs.sortedBy { it.seqIndex }
            val startSeq = sortedParagraphs.first().seqIndex
            val endSeq = sortedParagraphs.last().seqIndex
            val bulletsJson = JsonArray(ep.recapBullets.map { JsonPrimitive(it) }).toString()

            episodeEntities += EpisodeEntity(
                episodeId = ep.episodeId,
                bookId = pack.bookId,
                episodeIndex = ep.episodeIndex,
                sectionTitle = ep.sectionTitle,
                wordCount = ep.wordCount,
                estReadMinutes = ep.estReadMinutes,
                startParagraphSeq = startSeq,
                endParagraphSeq = endSeq,
                recapBulletsJson = bulletsJson,
                isCompleted = 0,
                completedAtUtc = null,
            )

            sortedParagraphs.forEach { p ->
                val marksJson = if (p.inlineMarks.isEmpty()) {
                    null
                } else {
                    JsonArray(
                        p.inlineMarks.map { mark ->
                            JsonObject(
                                mapOf(
                                    "start" to JsonPrimitive(mark.start),
                                    "end" to JsonPrimitive(mark.end),
                                    "style" to JsonPrimitive(mark.style),
                                ),
                            )
                        },
                    ).toString()
                }
                paragraphEntities += ParagraphEntity(
                    paragraphId = "${pack.bookId}_p_${p.seqIndex}",
                    bookId = pack.bookId,
                    episodeId = ep.episodeId,
                    seqIndex = p.seqIndex,
                    paragraphType = p.paragraphType,
                    textContent = p.textContent,
                    inlineMarksJson = marksJson,
                )
            }
        }

        val nameEntities = mutableListOf<NameEntity>()
        val aliasEntities = mutableListOf<NameAliasEntity>()
        val factEntities = mutableListOf<EntityFactEntity>()

        pack.entities.forEach { ent ->
            nameEntities += NameEntity(
                entityId = ent.entityId,
                bookId = pack.bookId,
                canonicalName = ent.canonicalName,
                sourceKind = EntitySourceKind.CURATED,
                shortRole = ent.shortRole,
                firstMentionSeq = ent.firstMentionSeq,
            )

            ent.aliases.forEachIndexed { idx, alias ->
                aliasEntities += NameAliasEntity(
                    aliasId = "${ent.entityId}_alias_$idx",
                    bookId = pack.bookId,
                    entityId = ent.entityId,
                    aliasText = alias.aliasText,
                    normalizedAlias = normalizeAlias(alias.aliasText),
                    toneNote = alias.toneNote,
                )
            }

            ent.facts.forEach { fact ->
                factEntities += EntityFactEntity(
                    factId = fact.factId,
                    bookId = pack.bookId,
                    entityId = ent.entityId,
                    unlockedAtSeq = fact.unlockedAtSeq,
                    factText = fact.factText,
                    sortOrder = fact.sortOrder,
                )
            }
        }

        val mentions = scanCuratedMentions(
            bookId = pack.bookId,
            paragraphs = paragraphEntities,
            aliases = aliasEntities,
        )

        return AtomicBookIngestionBundle(
            book = book,
            episodes = episodeEntities,
            paragraphs = paragraphEntities,
            entities = nameEntities,
            aliases = aliasEntities,
            facts = factEntities,
            mentions = mentions,
        )
    }

    /**
     * Scans paragraphs longest-alias-first with word boundary checks and non-overlapping spans.
     */
    fun scanCuratedMentions(
        bookId: String,
        paragraphs: List<ParagraphEntity>,
        aliases: List<NameAliasEntity>,
    ): List<NameMentionEntity> {
        val sortedAliases = aliases
            .filter { it.aliasText.isNotBlank() }
            .sortedByDescending { it.aliasText.length }

        val mentions = mutableListOf<NameMentionEntity>()
        var mentionCounter = 0

        for (paragraph in paragraphs) {
            val text = paragraph.textContent
            if (text.isBlank()) continue
            val occupied = BooleanArray(text.length)

            for (alias in sortedAliases) {
                val pattern = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(alias.aliasText) + "(?![\\p{L}\\p{N}])")
                for (match in pattern.findAll(text)) {
                    val start = match.range.first
                    val end = match.range.last + 1
                    var overlaps = false
                    for (i in start until end) {
                        if (occupied[i]) {
                            overlaps = true
                            break
                        }
                    }
                    if (!overlaps) {
                        for (i in start until end) occupied[i] = true
                        mentions += NameMentionEntity(
                            mentionId = "${bookId}_m_${mentionCounter++}",
                            bookId = bookId,
                            entityId = alias.entityId,
                            paragraphSeq = paragraph.seqIndex,
                            episodeId = paragraph.episodeId,
                            charStart = start,
                            charEnd = end,
                            surfaceText = match.value,
                        )
                    }
                }
            }
        }

        return mentions.sortedWith(compareBy<NameMentionEntity> { it.paragraphSeq }.thenBy { it.charStart })
    }

    fun normalizeAlias(raw: String): String {
        return raw.trim()
            .replace("\u2019s", "")
            .replace("'s", "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: error("Missing required string field '$key'")

    private fun JsonObject.requireInt(key: String): Int =
        this[key]?.jsonPrimitive?.int ?: error("Missing required integer field '$key'")
}
