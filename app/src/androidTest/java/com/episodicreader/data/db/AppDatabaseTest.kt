package com.episodicreader.data.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EntitySourceKind
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.repository.AtomicBookIngestionBundle
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.HabitRepository
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.repository.ReaderRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var bookRepository: BookRepository
    private lateinit var readerRepository: ReaderRepository
    private lateinit var lookupRepository: LookupRepository
    private lateinit var habitRepository: HabitRepository

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        bookRepository = BookRepository(db)
        readerRepository = ReaderRepository(db)
        lookupRepository = LookupRepository(db)
        habitRepository = HabitRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun monotonicMaxReadSeqNeverDecreasesOnBackNavigation() = runTest {
        val book = BookEntity(
            bookId = "b1",
            title = "Test Classic",
            author = "Author",
            sourceType = SourceType.EPUB,
            sourceHashSha256 = "hash1",
            localFilePath = null,
            importStatus = ImportStatus.READY,
            importedAtUtc = 1000L,
        )
        val ep1 = EpisodeEntity("e1", "b1", 1, "Chapter 1", 950, 5, 0, 1)
        val ep2 = EpisodeEntity("e2", "b1", 2, "Chapter 2", 980, 5, 2, 3)
        val paragraphs = listOf(
            ParagraphEntity("p0", "b1", "e1", 0, ParagraphType.BODY, "Zero"),
            ParagraphEntity("p1", "b1", "e1", 1, ParagraphType.BODY, "One"),
            ParagraphEntity("p2", "b1", "e2", 2, ParagraphType.BODY, "Two"),
            ParagraphEntity("p3", "b1", "e2", 3, ParagraphType.BODY, "Three"),
        )
        bookRepository.commitBookBundleAtomically(AtomicBookIngestionBundle(book, listOf(ep1, ep2), paragraphs))

        readerRepository.updateReadingPositionMonotonic("b1", "e2", 3, 3, 2000L)
        readerRepository.updateReadingPositionMonotonic("b1", "e1", 0, 0, 3000L)

        val updated = bookRepository.getBookById("b1")!!
        assertEquals(3, updated.maxReadSeq)
        assertEquals(0, updated.currentParagraphSeq)
    }

    @Test
    fun lookupQueriesStrictlyFilterFutureFactsAndMentions() = runTest {
        val book = BookEntity("b2", "Story", "Writer", SourceType.STARTER_CLASSIC, "h2", null, ImportStatus.READY, importedAtUtc = 1L)
        val ep1 = EpisodeEntity("e1", "b2", 1, "Night 1", 900, 5, 0, 2)
        val paragraphs = listOf(
            ParagraphEntity("p0", "b2", "e1", 0, ParagraphType.BODY, "Nastenka met the narrator."),
            ParagraphEntity("p1", "b2", "e1", 1, ParagraphType.BODY, "They walked by the canal."),
            ParagraphEntity("p2", "b2", "e1", 2, ParagraphType.BODY, "Nastenka revealed a future twist."),
        )
        val entity = NameEntity("ent1", "b2", "Nastenka", EntitySourceKind.CURATED, "Young woman", 0)
        val facts = listOf(
            EntityFactEntity("f1", "b2", "ent1", 0, "Met on the bridge.", 1),
            EntityFactEntity("f2", "b2", "ent1", 2, "Future spoiler fact.", 2),
        )
        val mentions = listOf(
            NameMentionEntity("m0", "b2", "ent1", 0, "e1", 0, 8, "Nastenka"),
            NameMentionEntity("m2", "b2", "ent1", 2, "e1", 0, 8, "Nastenka"),
        )
        bookRepository.commitBookBundleAtomically(
            AtomicBookIngestionBundle(book, listOf(ep1), paragraphs, listOf(entity), emptyList(), facts, mentions),
        )

        val unlockedAtSeq1 = lookupRepository.getUnlockedFacts("b2", "ent1", maxReadSeq = 1)
        assertEquals(1, unlockedAtSeq1.size)
        assertEquals("f1", unlockedAtSeq1.first().factId)

        val firstPrior = lookupRepository.getFirstPriorMention("b2", "ent1", maxReadSeq = 1, beforeParagraphSeq = 1)
        assertEquals("m0", firstPrior?.mentionId)
        val recent = lookupRepository.getRecentPriorMentions("b2", "ent1", maxReadSeq = 1, beforeParagraphSeq = 1, excludeMentionId = "m0")
        assertEquals(0, recent.size)
    }

    @Test
    fun deletingBookPreservesReadingSessionLogsWithSetNull() = runTest {
        val book = BookEntity("b3", "Imported", "Auth", SourceType.EPUB, "h3", null, ImportStatus.READY, importedAtUtc = 1L)
        val ep1 = EpisodeEntity("e1", "b3", 1, "Part 1", 950, 5, 0, 0)
        val p0 = ParagraphEntity("p0", "b3", "e1", 0, ParagraphType.BODY, "Text")
        bookRepository.commitBookBundleAtomically(AtomicBookIngestionBundle(book, listOf(ep1), listOf(p0)))

        readerRepository.completeEpisodeAtomically("b3", "e1", 5000L, "2026-11-20", advanceToNextEpisode = false)
        bookRepository.deleteBookById("b3")

        val logs = habitRepository.getAllLogs()
        assertEquals(1, logs.size)
        assertNull(logs.first().bookId)
        assertEquals("Imported", logs.first().bookTitleSnapshot)
    }
}
