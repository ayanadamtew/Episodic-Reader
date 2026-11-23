package com.episodicreader.domain

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import com.episodicreader.data.db.model.MentionExcerptRow
import com.episodicreader.data.seed.StarterPackParser
import com.episodicreader.domain.importer.ImportErrorCode
import com.episodicreader.domain.lookup.CuratedGlossaryDataSource
import com.episodicreader.domain.lookup.CuratedGlossaryService
import com.episodicreader.domain.lookup.DeterministicNameIndexer
import com.episodicreader.domain.lookup.PriorMentionDataSource
import com.episodicreader.domain.lookup.PriorMentionService
import com.episodicreader.domain.lookup.PriorMentionsResult
import com.episodicreader.domain.recap.BreakRecapCoordinator
import com.episodicreader.domain.recap.BreakRecapDataSource
import com.episodicreader.domain.slicer.EpisodeSlicer
import com.episodicreader.domain.slicer.RawParagraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-20: Comprehensive Anti-Spoiler Invariant & SRS v1.1 Error Matrix Verification Suite.
 * Covers NFR-005, BR-003, BR-004, BR-005, AC-006..AC-015, and ERR-001..ERR-012.
 */
class AntiSpoilerAndErrorMatrixTest {

    private fun loadStarterPackJson(): String {
        val candidates = listOf(
            File("src/main/assets/starter_classic/white_nights_pack.json"),
            File("app/src/main/assets/starter_classic/white_nights_pack.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Starter pack asset not found in working dir ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun starterClassic_curatedGlossary_neverLeaksFutureFactsOrMentionsAtAnySeq() = runBlocking {
        val pack = StarterPackParser.parse(loadStarterPackJson())
        val seed = StarterPackParser.toIngestionBundle(pack, importedAtUtc = 1_700_000_000_000L)

        val episodeById = seed.episodes.associateBy { it.episodeId }
        val paragraphBySeq = seed.paragraphs.associateBy { it.seqIndex }
        val entityById = seed.entities.associateBy { it.entityId }

        // Deliberately return unfiltered rows from the mock data source to verify defense-in-depth!
        val leakySource = object : CuratedGlossaryDataSource {
            override suspend fun getEntityById(bookId: String, entityId: String): NameEntity? =
                seed.entities.firstOrNull { it.bookId == bookId && it.entityId == entityId }

            override suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity> =
                seed.aliases

            override suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity> =
                seed.aliases.filter { it.entityId == entityId }

            override suspend fun getUnlockedFacts(
                bookId: String,
                entityId: String,
                maxReadSeq: Int,
            ): List<EntityFactEntity> =
                // Intentionally return ALL facts (including future ones) to prove service guards them!
                seed.facts.filter { it.bookId == bookId && it.entityId == entityId }

            override suspend fun getFirstMentionUpTo(
                bookId: String,
                entityId: String,
                maxReadSeq: Int,
            ): MentionExcerptRow? {
                val m = seed.mentions
                    .filter { it.bookId == bookId && it.entityId == entityId }
                    .minByOrNull { it.paragraphSeq }
                    ?: return null
                val p = paragraphBySeq[m.paragraphSeq] ?: return null
                val ep = episodeById[m.episodeId] ?: return null
                val ent = entityById[m.entityId] ?: return null
                return MentionExcerptRow(
                    mentionId = m.mentionId,
                    entityId = m.entityId,
                    canonicalName = ent.canonicalName,
                    paragraphSeq = m.paragraphSeq,
                    episodeId = m.episodeId,
                    episodeIndex = ep.episodeIndex,
                    sectionTitle = ep.sectionTitle,
                    textContent = p.textContent,
                    charStart = m.charStart,
                    charEnd = m.charEnd,
                    surfaceText = m.surfaceText,
                )
            }
        }

        val service = CuratedGlossaryService(leakySource)
        val checkpoints = listOf(0, 5, 14, 15, 30, 45, 61, 77, 94)

        for (maxReadSeq in checkpoints) {
            for (entity in seed.entities) {
                val profile = service.lookupByEntityId(
                    bookId = seed.book.bookId,
                    entityId = entity.entityId,
                    surfaceText = entity.canonicalName,
                    maxReadSeq = maxReadSeq,
                )
                assertNotNull("Expected curated profile for ${entity.canonicalName}", profile)
                profile!!

                // CRITICAL NFR-005 INVARIANT: zero future facts
                assertTrue(
                    "Spoiler leak at maxReadSeq=$maxReadSeq for ${entity.canonicalName}",
                    profile.unlockedFacts.all { it.unlockedAtSeq <= maxReadSeq },
                )

                // CRITICAL NFR-005 INVARIANT: zero future first-seen mention
                profile.firstSeen?.let { seen ->
                    assertTrue(
                        "Future firstSeen leak (${seen.paragraphSeq} > $maxReadSeq) for ${entity.canonicalName}",
                        seen.paragraphSeq <= maxReadSeq,
                    )
                }
            }
        }

        // Verify progressive unlock actually works: Nastenka unlocks more facts by seq 94 than at seq 0
        val nastenka = seed.entities.first { it.canonicalName.contains("Nastenka", ignoreCase = true) }
        val early = service.lookupByEntityId(seed.book.bookId, nastenka.entityId, "Nastenka", maxReadSeq = 0)!!
        val late = service.lookupByEntityId(seed.book.bookId, nastenka.entityId, "Nastenka", maxReadSeq = 94)!!
        assertTrue(
            "Expected more unlocked facts at end of book (${late.unlockedFacts.size}) than at start (${early.unlockedFacts.size})",
            late.unlockedFacts.size > early.unlockedFacts.size,
        )
    }

    @Test
    fun uploadedBookPipeline_priorMentions_neverLeaksCurrentOrFutureParagraphs() = runBlocking {
        val rawParagraphs = (0 until 24).map { idx ->
            val filler = List(110) { "walking" }.joinToString(" ")
            val text = when {
                idx % 4 == 0 -> "Elizabeth Bennet met Darcy in Hertfordshire. $filler"
                idx % 4 == 2 -> "Bingley spoke with Jane Bennet near Netherfield. $filler"
                else -> "Darcy considered Elizabeth Bennet quietly. $filler"
            }
            RawParagraph(
                paragraphType = ParagraphType.BODY,
                textContent = text,
                sectionTitle = "Chapter ${(idx / 8) + 1}",
            )
        }

        val episodes = EpisodeSlicer().slice(rawParagraphs)
        assertTrue(episodes.size >= 2)

        val episodeEntities = episodes.map { ep ->
            EpisodeEntity(
                episodeId = "ep_${ep.episodeIndex}",
                bookId = "book_u1",
                episodeIndex = ep.episodeIndex,
                sectionTitle = ep.sectionTitle,
                wordCount = ep.wordCount,
                estReadMinutes = ep.estReadMinutes,
                startParagraphSeq = ep.startParagraphSeq,
                endParagraphSeq = ep.endParagraphSeq,
            )
        }
        val paragraphs = episodes.flatMap { ep ->
            ep.paragraphs.map { sp ->
                ParagraphEntity(
                    paragraphId = "p_${sp.seqIndex}",
                    bookId = "book_u1",
                    episodeId = "ep_${ep.episodeIndex}",
                    seqIndex = sp.seqIndex,
                    paragraphType = sp.paragraphType,
                    textContent = sp.textContent,
                )
            }
        }
        val epIndexById = episodes.associate { "ep_${it.episodeIndex}" to (it.episodeIndex to it.sectionTitle) }
        val paragraphBySeq = paragraphs.associateBy { it.seqIndex }
        val indexed = DeterministicNameIndexer().indexBook("book_u1", episodeEntities, paragraphs)
        val entityById = indexed.entities.associateBy { it.entityId }
        assertTrue("Expected indexed names", indexed.entities.isNotEmpty())

        val dataSource = object : PriorMentionDataSource {
            override suspend fun getFirstPriorMention(
                bookId: String,
                entityId: String,
                maxReadSeq: Int,
                beforeParagraphSeq: Int,
            ): MentionExcerptRow? {
                val m = indexed.mentions
                    .filter { it.entityId == entityId && it.paragraphSeq <= maxReadSeq && it.paragraphSeq < beforeParagraphSeq }
                    .minByOrNull { it.paragraphSeq } ?: return null
                val p = paragraphBySeq[m.paragraphSeq] ?: return null
                val (epIdx, sec) = epIndexById[m.episodeId] ?: (1 to "")
                val canonical = entityById[m.entityId]?.canonicalName ?: m.surfaceText
                return MentionExcerptRow(
                    mentionId = m.mentionId,
                    entityId = m.entityId,
                    canonicalName = canonical,
                    paragraphSeq = m.paragraphSeq,
                    episodeId = m.episodeId,
                    episodeIndex = epIdx,
                    sectionTitle = sec,
                    textContent = p.textContent,
                    charStart = m.charStart,
                    charEnd = m.charEnd,
                    surfaceText = m.surfaceText,
                )
            }

            override suspend fun getRecentPriorMentions(
                bookId: String,
                entityId: String,
                maxReadSeq: Int,
                beforeParagraphSeq: Int,
                excludeMentionId: String,
                limit: Int,
            ): List<MentionExcerptRow> {
                return indexed.mentions
                    .filter {
                        it.entityId == entityId &&
                            it.paragraphSeq <= maxReadSeq &&
                            it.paragraphSeq < beforeParagraphSeq &&
                            it.mentionId != excludeMentionId
                    }
                    .sortedByDescending { it.paragraphSeq }
                    .take(limit)
                    .mapNotNull { m ->
                        val p = paragraphBySeq[m.paragraphSeq] ?: return@mapNotNull null
                        val (epIdx, sec) = epIndexById[m.episodeId] ?: (1 to "")
                        val canonical = entityById[m.entityId]?.canonicalName ?: m.surfaceText
                        MentionExcerptRow(
                            mentionId = m.mentionId,
                            entityId = m.entityId,
                            canonicalName = canonical,
                            paragraphSeq = m.paragraphSeq,
                            episodeId = m.episodeId,
                            episodeIndex = epIdx,
                            sectionTitle = sec,
                            textContent = p.textContent,
                            charStart = m.charStart,
                            charEnd = m.charEnd,
                            surfaceText = m.surfaceText,
                        )
                    }
            }

            override suspend fun getPriorParagraphsStrictlyBefore(
                bookId: String,
                maxReadSeq: Int,
                beforeSeq: Int,
            ): List<ParagraphEntity> = paragraphs // Deliberately unfiltered to verify service guard!
        }

        val service = PriorMentionService(dataSource)

        for (maxReadSeq in listOf(0, 3, 8, 15, 23)) {
            for (currentSeq in listOf(0, 2, 8, 12, 20)) {
                val manual = service.lookupManualQuery(
                    bookId = "book_u1",
                    rawQuery = "Darcy",
                    maxReadSeq = maxReadSeq,
                    currentParagraphSeq = currentSeq,
                    episodeInfoLookup = { epId -> epIndexById[epId] ?: (1 to "") },
                )
                manual.firstSeen?.let { first ->
                    assertTrue(
                        "Manual firstSeen leaked (${first.paragraphSeq}) at maxReadSeq=$maxReadSeq, currentSeq=$currentSeq",
                        first.paragraphSeq <= maxReadSeq && first.paragraphSeq < currentSeq,
                    )
                }
                assertTrue(
                    "Manual recent mentions leaked at maxReadSeq=$maxReadSeq, currentSeq=$currentSeq",
                    manual.recentMentions.all { it.paragraphSeq <= maxReadSeq && it.paragraphSeq < currentSeq },
                )
            }
        }
    }

    @Test
    fun errorMatrix_allImportErrorCodes_areCalmNonEmptyAndJargonFree() {
        val forbiddenTechnicalTerms = listOf("Exception", "NullPointer", "StackTrace", "SQLite", "IOException")
        val codes = ImportErrorCode.entries

        val expectedPrefixes = setOf(
            "ERR-001", "ERR-002", "ERR-003", "ERR-004",
            "ERR-005", "ERR-006", "ERR-007", "ERR-008", "ERR-011",
        )
        assertEquals(expectedPrefixes, codes.map { it.code }.toSet())

        for (error in codes) {
            assertTrue("Title must not be blank for ${error.code}", error.title.isNotBlank())
            assertTrue("Message must not be blank for ${error.code}", error.userMessage.isNotBlank())
            for (term in forbiddenTechnicalTerms) {
                assertFalse(
                    "Message for ${error.code} leaked technical jargon: $term",
                    error.userMessage.contains(term, ignoreCase = true),
                )
            }
        }

        // ERR-012 empty prior mentions message
        assertEquals(
            "No earlier mentions found in what you've read so far.",
            PriorMentionsResult.EMPTY_MESSAGE,
        )
    }

    @Test
    fun error010_negativeClockDelta_clampedAndNeverFalseTriggersRecap() {
        val emptyDataSource = object : BreakRecapDataSource {
            override suspend fun getMostRecentlyCompletedEpisode(bookId: String) = null
            override suspend fun getParagraphsForEpisode(episodeId: String) = emptyList<ParagraphEntity>()
            override suspend fun getTopEntitiesForEpisode(
                bookId: String,
                episodeId: String,
                maxReadSeq: Int,
                limit: Int,
            ) = emptyList<EpisodeKeyNameRow>()
        }

        val coordinator = BreakRecapCoordinator(emptyDataSource)
        val book = BookEntity(
            bookId = "book_clock",
            title = "Clock Test",
            author = "Tester",
            sourceType = SourceType.EPUB,
            sourceHashSha256 = "abc123",
            localFilePath = null,
            importStatus = ImportStatus.READY,
            completedEpisodesCount = 2,
            lastReadAtUtc = 2_000_000_000_000L,
            importedAtUtc = 1_900_000_000_000L,
        )

        // Device clock jumped backward by 72 hours (ERR-010)
        val backwardsNow = book.lastReadAtUtc!! - (72L * 60L * 60L * 1000L)
        assertFalse(coordinator.shouldAutoTriggerRecap(book, backwardsNow))
    }
}
