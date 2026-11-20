package com.episodicreader.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.BookStatus
import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.entity.NameMentionEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.ReadingSessionLogEntity
import com.episodicreader.data.db.entity.SourceFormat
import com.episodicreader.data.repository.BookGraph
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.LookupRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var books: BookRepository
    private lateinit var lookup: LookupRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(SchemaGuards)
            .build()
        books = BookRepository(database)
        lookup = LookupRepository(database.lookupDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun schemaContainsNineTablesAndRequiredIndices() {
        val tables = queryNames(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        )
        assertTrue(
            tables.containsAll(
                setOf(
                    "books",
                    "episodes",
                    "paragraphs",
                    "name_entities",
                    "name_mentions",
                    "name_aliases",
                    "entity_facts",
                    "reading_session_logs",
                    "user_preferences",
                ),
            ),
        )

        val indices = queryNames(
            "SELECT name FROM sqlite_master WHERE type = 'index'",
        )
        assertTrue(
            indices.containsAll(
                setOf(
                    "idx_books_hash",
                    "idx_books_sort",
                    "idx_ep_book_num",
                    "idx_para_episode",
                    "idx_entity_lookup",
                    "idx_entity_first_seen",
                    "idx_mentions_entity_seq",
                    "idx_mentions_book_seq",
                    "idx_alias_entity",
                    "idx_facts_unlock",
                    "idx_logs_local_date",
                ),
            ),
        )
    }

    @Test
    fun deletingBookCascadesContentButPreservesAnonymizedHabitLog() = runBlocking {
        books.insertGraph(completeGraph())
        database.habitDao().insert(
            ReadingSessionLogEntity(
                logId = "log-1",
                bookId = BOOK_ID,
                episodeId = EPISODE_ID,
                completedLocalDate = "2026-11-20",
                completedAtUtc = 1_763_616_000_000,
            ),
        )

        books.deleteBook(BOOK_ID)

        assertEquals(0, database.bookDao().countById(BOOK_ID))
        assertEquals(0, database.episodeDao().countForBook(BOOK_ID))
        assertEquals(0, database.paragraphDao().countForBook(BOOK_ID))
        assertEquals(0, database.lookupDao().countEntitiesForBook(BOOK_ID))
        assertEquals(0, database.lookupDao().countMentionsForBook(BOOK_ID))
        assertEquals(0, scalarCount("name_aliases"))
        assertEquals(0, scalarCount("entity_facts"))

        val remainingLog = database.habitDao().getAll().single()
        assertNull(remainingLog.bookId)
        assertEquals(EPISODE_ID, remainingLog.episodeId)
    }

    @Test
    fun progressWatermarkOnlyMovesForward() = runBlocking {
        books.insertGraph(completeGraph())

        books.recordVisibleParagraph(BOOK_ID, visibleSeq = 42, nowUtcMs = 100)
        books.recordVisibleParagraph(BOOK_ID, visibleSeq = 18, nowUtcMs = 200)

        val book = requireNotNull(books.getBook(BOOK_ID))
        assertEquals(18, book.currentParagraphSeq)
        assertEquals(42, book.maxReadSeq)
        assertEquals(200, book.lastReadAtUtc)
    }

    @Test
    fun lookupNeverReturnsMentionsOrFactsBeyondReadBoundary() = runBlocking {
        books.insertGraph(completeGraph())

        val entity = lookup.findReadableEntity(BOOK_ID, "ELENA", maxReadSeq = 45)
        assertEquals(ENTITY_ID, entity?.entityId)

        val card = lookup.getPriorMentions(
            bookId = BOOK_ID,
            entityId = ENTITY_ID,
            currentParagraphSeq = 42,
            maxReadSeq = 45,
        )
        assertEquals(5, card.firstSeen?.seqIndex)
        assertEquals(listOf(18), card.recentMentions.map { it.seqIndex })
        assertFalse(card.recentMentions.any { it.seqIndex > 45 })

        val facts = lookup.getUnlockedFacts(ENTITY_ID, maxReadSeq = 28)
        assertEquals(listOf(10), facts.map { it.unlockedAtSeq })
    }

    @Test
    fun sequenceEqualToWatermarkIsReadableButLaterSequenceIsNot() = runBlocking {
        books.insertGraph(completeGraph())

        val card = lookup.getPriorMentions(
            bookId = BOOK_ID,
            entityId = ENTITY_ID,
            currentParagraphSeq = 43,
            maxReadSeq = 42,
        )
        val visibleSequences = listOfNotNull(card.firstSeen?.seqIndex) +
            card.recentMentions.map { it.seqIndex }

        assertTrue(42 in visibleSequences)
        assertTrue(visibleSequences.all { it <= 42 })
        assertFalse(190 in visibleSequences)
    }

    @Test
    fun invalidGraphRollsBackEveryInsertedRow() = runBlocking {
        val invalidMention = completeGraph().nameMentions.first().copy(
            charStart = 8,
            charEnd = 8,
        )
        val invalidGraph = completeGraph().copy(nameMentions = listOf(invalidMention))

        expectFailure { books.insertGraph(invalidGraph) }

        assertEquals(0, database.bookDao().countById(BOOK_ID))
        assertEquals(0, database.episodeDao().countForBook(BOOK_ID))
        assertEquals(0, database.paragraphDao().countForBook(BOOK_ID))
        assertEquals(0, database.lookupDao().countEntitiesForBook(BOOK_ID))
    }

    private fun completeGraph(): BookGraph = BookGraph(
        book = BookEntity(
            bookId = BOOK_ID,
            title = "White Nights",
            author = "Fyodor Dostoevsky",
            sourceFormat = SourceFormat.BUNDLED,
            fileHash = "sha256:test",
            status = BookStatus.READY,
            hasCuratedPack = true,
            importedAtUtc = 1,
        ),
        episodes = listOf(
            EpisodeEntity(
                episodeId = EPISODE_ID,
                bookId = BOOK_ID,
                episodeNumber = 1,
                sectionHeading = "First Night",
                seqStart = 0,
                seqEnd = 190,
                wordCount = 1_200,
                estMinutes = 8,
            ),
        ),
        paragraphs = listOf(5, 18, 42, 190).map { sequence ->
            ParagraphEntity(
                bookId = BOOK_ID,
                seqIndex = sequence,
                episodeId = EPISODE_ID,
                paragraphType = ParagraphType.BODY,
                plainText = "Elena appears in paragraph $sequence.",
            )
        },
        nameEntities = listOf(
            NameEntity(
                entityId = ENTITY_ID,
                bookId = BOOK_ID,
                canonicalName = "Elena",
                normalizedKey = "elena",
                firstSeenSeq = 5,
                mentionCount = 4,
                shortRole = "A familiar name",
            ),
        ),
        nameMentions = listOf(5, 18, 42, 190).map { sequence ->
            NameMentionEntity(
                mentionId = "mention-$sequence",
                bookId = BOOK_ID,
                entityId = ENTITY_ID,
                seqIndex = sequence,
                charStart = 0,
                charEnd = 5,
            )
        },
        nameAliases = listOf(
            NameAliasEntity(
                aliasId = "alias-1",
                entityId = ENTITY_ID,
                aliasText = "Lena",
            ),
        ),
        entityFacts = listOf(
            EntityFactEntity(
                factId = "fact-10",
                entityId = ENTITY_ID,
                unlockedAtSeq = 10,
                factText = "An early fact.",
            ),
            EntityFactEntity(
                factId = "fact-95",
                entityId = ENTITY_ID,
                unlockedAtSeq = 95,
                factText = "A later fact.",
            ),
        ),
    )

    private fun queryNames(sql: String): Set<String> {
        val cursor = database.openHelper.readableDatabase.query(sql)
        return cursor.use {
            buildSet {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
    }

    private fun scalarCount(tableName: String): Int {
        require(tableName in setOf("name_aliases", "entity_facts"))
        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName")
        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected database operation to fail")
        } catch (_: Exception) {
            // Expected: a schema guard aborts the transaction.
        }
    }

    private companion object {
        const val BOOK_ID = "book-1"
        const val EPISODE_ID = "episode-1"
        const val ENTITY_ID = "entity-elena"
    }
}
