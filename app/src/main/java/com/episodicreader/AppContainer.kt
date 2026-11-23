package com.episodicreader

import android.content.Context
import com.episodicreader.data.db.AppDatabase
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.HabitRepository
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.repository.PreferencesRepository
import com.episodicreader.data.repository.ReaderRepository
import com.episodicreader.data.seed.AssetStarterPackSource
import com.episodicreader.data.seed.RoomStarterClassicStore
import com.episodicreader.data.seed.StarterClassicSeeder
import com.episodicreader.domain.importer.BookImportCoordinator
import com.episodicreader.domain.importer.OrphanImportCleaner
import com.episodicreader.domain.importer.RoomOrphanImportStore
import com.episodicreader.domain.lifecycle.BookLifecycleService
import com.episodicreader.domain.lifecycle.RoomBookLifecycleStore
import com.episodicreader.domain.lifecycle.SafePrivateFileDeleter
import com.episodicreader.domain.lookup.CuratedGlossaryService
import com.episodicreader.domain.lookup.DeterministicNameIndexer
import com.episodicreader.domain.lookup.PriorMentionService
import com.episodicreader.domain.lookup.RoomCuratedGlossaryDataSource
import com.episodicreader.domain.lookup.RoomPriorMentionDataSource
import com.episodicreader.domain.recap.BreakRecapCoordinator
import com.episodicreader.domain.recap.RoomBreakRecapDataSource
import com.episodicreader.platform.AppClock
import com.episodicreader.platform.SystemAppClock
import java.io.File

/**
 * Manual dependency container for the offline-first Episodic Reader application.
 */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val clock: AppClock = SystemAppClock()
    val privateBooksDir: File = File(applicationContext.filesDir, "books").apply { mkdirs() }
    val database: AppDatabase = AppDatabase.create(applicationContext)

    val bookRepository = BookRepository(database)
    val readerRepository = ReaderRepository(database)
    val lookupRepository = LookupRepository(database)
    val habitRepository = HabitRepository(database)
    val preferencesRepository = PreferencesRepository(database)

    val starterClassicSeeder = StarterClassicSeeder(
        store = RoomStarterClassicStore(bookRepository, preferencesRepository),
        jsonSource = AssetStarterPackSource(applicationContext),
        clock = clock,
    )

    val bookLifecycleService = BookLifecycleService(
        store = RoomBookLifecycleStore(bookRepository),
        fileDeleter = SafePrivateFileDeleter(privateBooksDir),
    )

    val orphanImportCleaner = OrphanImportCleaner(
        store = RoomOrphanImportStore(bookRepository),
        fileDeleter = SafePrivateFileDeleter(privateBooksDir),
        privateBooksDir = privateBooksDir,
    )

    val deterministicNameIndexer = DeterministicNameIndexer()

    val bookImportCoordinator = BookImportCoordinator(
        context = applicationContext,
        privateBooksDir = privateBooksDir,
        bookRepository = bookRepository,
        clock = clock,
        nameIndexerHook = { bookId, episodes, paragraphs ->
            deterministicNameIndexer.indexBook(bookId, episodes, paragraphs)
        },
    )

    val priorMentionService = PriorMentionService(
        dataSource = RoomPriorMentionDataSource(lookupRepository),
    )

    val curatedGlossaryService = CuratedGlossaryService(
        dataSource = RoomCuratedGlossaryDataSource(lookupRepository),
    )

    val breakRecapCoordinator = BreakRecapCoordinator(
        dataSource = RoomBreakRecapDataSource(readerRepository, lookupRepository),
    )
}
