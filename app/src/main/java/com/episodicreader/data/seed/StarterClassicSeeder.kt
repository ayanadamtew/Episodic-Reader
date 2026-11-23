package com.episodicreader.data.seed

import android.content.Context
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.StarterClassicIds
import com.episodicreader.data.repository.AtomicBookIngestionBundle
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.PreferencesRepository
import com.episodicreader.platform.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface StarterPackJsonSource {
    suspend fun readStarterPackJson(): String
}

class AssetStarterPackSource(
    private val context: Context,
    private val assetPath: String = "starter_classic/white_nights_pack.json",
) : StarterPackJsonSource {
    override suspend fun readStarterPackJson(): String = withContext(Dispatchers.IO) {
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

interface StarterClassicStore {
    suspend fun getStarterClassic(): BookEntity?
    suspend fun commitStarterBundle(bundle: AtomicBookIngestionBundle)
    suspend fun setBookHidden(bookId: String, hidden: Boolean)
    suspend fun ensureDefaultPreferences(nowUtc: Long)
}

class RoomStarterClassicStore(
    private val bookRepository: BookRepository,
    private val preferencesRepository: PreferencesRepository,
) : StarterClassicStore {
    override suspend fun getStarterClassic(): BookEntity? = bookRepository.getStarterClassic()

    override suspend fun commitStarterBundle(bundle: AtomicBookIngestionBundle) {
        bookRepository.commitBookBundleAtomically(bundle)
    }

    override suspend fun setBookHidden(bookId: String, hidden: Boolean) {
        bookRepository.setBookHidden(bookId, hidden)
    }

    override suspend fun ensureDefaultPreferences(nowUtc: Long) {
        preferencesRepository.ensureDefaults(nowUtc)
    }
}

sealed interface SeedResult {
    data object AlreadySeeded : SeedResult
    data object Seeded : SeedResult
    data class Error(val message: String) : SeedResult
}

/**
 * Idempotent first-launch seeder for the pre-bundled starter classic (T-04 / FR-003 / ERR-007).
 * Guarantees that relaunches never overwrite reading progress (max_read_seq) or user hide state.
 */
class StarterClassicSeeder(
    private val store: StarterClassicStore,
    private val jsonSource: StarterPackJsonSource,
    private val clock: AppClock,
) {
    suspend fun seedIfNeeded(): SeedResult {
        return try {
            val now = clock.nowUtcMillis()
            store.ensureDefaultPreferences(now)

            val existing = store.getStarterClassic()
            if (existing != null && existing.importStatus == ImportStatus.READY) {
                return SeedResult.AlreadySeeded
            }

            val rawJson = jsonSource.readStarterPackJson()
            val pack = StarterPackParser.parse(rawJson)
            val bundle = StarterPackParser.toIngestionBundle(pack, importedAtUtc = now)
            store.commitStarterBundle(bundle)
            SeedResult.Seeded
        } catch (t: Throwable) {
            SeedResult.Error(
                t.message ?: "We couldn't prepare the starter classic. Please tap Retry to try again.",
            )
        }
    }

    suspend fun restoreStarterClassic(): SeedResult {
        val existing = store.getStarterClassic()
        if (existing != null && existing.importStatus == ImportStatus.READY) {
            if (existing.isHidden != 0) {
                store.setBookHidden(existing.bookId, hidden = false)
            }
            return SeedResult.AlreadySeeded
        }
        val result = seedIfNeeded()
        if (result is SeedResult.Seeded || result is SeedResult.AlreadySeeded) {
            store.setBookHidden(StarterClassicIds.WHITE_NIGHTS_BOOK_ID, hidden = false)
        }
        return result
    }
}
