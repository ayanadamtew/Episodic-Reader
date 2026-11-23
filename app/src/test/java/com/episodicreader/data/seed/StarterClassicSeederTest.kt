package com.episodicreader.data.seed

import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.repository.AtomicBookIngestionBundle
import com.episodicreader.platform.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StarterClassicSeederTest {

    private class FakeClock(private val now: Long = 1_700_000_000_000L) : AppClock {
        override fun nowUtcMillis(): Long = now
        override fun todayLocalDateIso(): String = "2026-11-20"
        override fun toLocalDateIso(utcMillis: Long): String = "2026-11-20"
    }

    private class InMemoryStarterStore : StarterClassicStore {
        var starterBook: BookEntity? = null
        var commitCount: Int = 0
        var defaultsEnsuredCount: Int = 0

        override suspend fun getStarterClassic(): BookEntity? = starterBook

        override suspend fun commitStarterBundle(bundle: AtomicBookIngestionBundle) {
            commitCount++
            starterBook = bundle.book
        }

        override suspend fun setBookHidden(bookId: String, hidden: Boolean) {
            starterBook = starterBook?.copy(isHidden = if (hidden) 1 else 0)
        }

        override suspend fun ensureDefaultPreferences(nowUtc: Long) {
            defaultsEnsuredCount++
        }
    }

    private fun readAsset(): String {
        val f = listOf(
            File("src/main/assets/starter_classic/white_nights_pack.json"),
            File("app/src/main/assets/starter_classic/white_nights_pack.json"),
        ).first { it.exists() }
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun seedsOnceAndPreservesProgressAndHiddenFlagOnRelaunch() = runTest {
        val store = InMemoryStarterStore()
        val seeder = StarterClassicSeeder(store, { readAsset() }, FakeClock())

        val first = seeder.seedIfNeeded()
        assertEquals(SeedResult.Seeded, first)
        assertEquals(1, store.commitCount)

        // Simulate user reading and hiding starter classic
        store.starterBook = store.starterBook!!.copy(maxReadSeq = 42, isHidden = 1)

        val second = seeder.seedIfNeeded()
        assertEquals(SeedResult.AlreadySeeded, second)
        assertEquals(1, store.commitCount)
        assertEquals(42, store.starterBook!!.maxReadSeq)
        assertEquals(1, store.starterBook!!.isHidden)

        // Restore unhides without losing progress
        seeder.restoreStarterClassic()
        assertEquals(0, store.starterBook!!.isHidden)
        assertEquals(42, store.starterBook!!.maxReadSeq)
    }

    @Test
    fun returnsRecoverableErrorWhenAssetIsCorrupt() = runTest {
        val store = InMemoryStarterStore()
        val seeder = StarterClassicSeeder(store, { "{ invalid json" }, FakeClock())

        val res = seeder.seedIfNeeded()
        assertTrue(res is SeedResult.Error)
        assertEquals(0, store.commitCount)
    }
}
