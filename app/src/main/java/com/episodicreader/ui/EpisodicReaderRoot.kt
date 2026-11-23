package com.episodicreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.episodicreader.AppContainer
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.entity.UserPreferencesEntity
import com.episodicreader.data.db.model.LibraryBookRow
import com.episodicreader.domain.habit.LocalHabitCalculator
import com.episodicreader.domain.importer.ImportErrorCode
import com.episodicreader.domain.importer.ImportOutcome
import com.episodicreader.domain.lookup.CuratedCharacterProfile
import com.episodicreader.domain.lookup.PriorMentionsResult
import com.episodicreader.ui.dialogs.DeleteBookDialog
import com.episodicreader.ui.dialogs.DuplicateImportDialog
import com.episodicreader.ui.dialogs.ImportErrorDialog
import com.episodicreader.ui.dialogs.ManualNameLookupDialog
import com.episodicreader.ui.dialogs.ResetStarterClassicDialog
import com.episodicreader.ui.library.HabitSummaryBar
import com.episodicreader.ui.library.LibraryScreen
import com.episodicreader.ui.library.LibraryViewModel
import com.episodicreader.ui.overlay.PreviouslyOnOverlay
import com.episodicreader.ui.reader.EpisodeCompletionCard
import com.episodicreader.ui.reader.ReaderScreen
import com.episodicreader.ui.reader.ReaderViewModel
import com.episodicreader.ui.sheets.CuratedCharacterSheet
import com.episodicreader.ui.sheets.DisplaySettingsSheet
import com.episodicreader.ui.sheets.PriorMentionsSheet
import com.episodicreader.ui.sheets.ReadingProgressSheet
import com.episodicreader.ui.theme.EpisodicReaderTheme
import kotlinx.coroutines.launch

private object AppRoutes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"

    fun reader(bookId: String): String = "reader/${Uri.encode(bookId)}"
}

/**
 * Root Compose application host connecting Navigation, Library (SCR-01), Reader (SCR-02),
 * all modal sheets (SH-01..SH-04), Break Re-Entry overlay (OV-01), and dialogs (DLG-01a..d).
 */
@Composable
fun EpisodicReaderRoot(appContainer: AppContainer) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val librarySnackbarHost = remember { SnackbarHostState() }

    val preferences by appContainer.preferencesRepository
        .observePreferences()
        .collectAsState(initial = UserPreferencesEntity())

    val dailyCounts by appContainer.habitRepository
        .observeDailyCompletionCounts()
        .collectAsState(initial = emptyList())

    val finishedBooksCount by appContainer.bookRepository
        .observeFinishedBooksCount()
        .collectAsState(initial = 0)

    val habitSummary = remember(dailyCounts, finishedBooksCount) {
        LocalHabitCalculator.calculate(
            dailyCounts = dailyCounts,
            booksFinished = finishedBooksCount,
            todayIso = appContainer.clock.todayLocalDateIso(),
        )
    }

    // Startup maintenance: initialize preferences defaults and clean interrupted imports (ERR-006)
    LaunchedEffect(Unit) {
        appContainer.preferencesRepository.ensureDefaults(appContainer.clock.nowUtcMillis())
        val cleaned = appContainer.orphanImportCleaner.cleanStaleImports()
        if (cleaned > 0) {
            librarySnackbarHost.showSnackbar(
                message = ImportErrorCode.ERR_006_INTERRUPTED_IMPORT.userMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    EpisodicReaderTheme(themeMode = preferences.themeMode) {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.LIBRARY,
        ) {
            composable(AppRoutes.LIBRARY) {
                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.Factory(
                        bookRepository = appContainer.bookRepository,
                        starterClassicSeeder = appContainer.starterClassicSeeder,
                    ),
                )
                val libraryState by libraryViewModel.uiState.collectAsState()

                var showHabitSheet by remember { mutableStateOf(false) }
                var pendingDeleteBook by remember { mutableStateOf<LibraryBookRow?>(null) }
                var pendingResetStarter by remember { mutableStateOf<LibraryBookRow?>(null) }
                var pendingDuplicate by remember { mutableStateOf<ImportOutcome.DuplicateDetected?>(null) }
                var activeImportError by remember { mutableStateOf<ImportErrorCode?>(null) }

                fun handleImportOutcome(outcome: ImportOutcome) {
                    when (outcome) {
                        is ImportOutcome.Imported -> {
                            coroutineScope.launch {
                                librarySnackbarHost.showSnackbar(
                                    message = "\"${outcome.title}\" is ready to read.",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                        is ImportOutcome.DuplicateDetected -> {
                            pendingDuplicate = outcome
                        }
                        is ImportOutcome.Failed -> {
                            activeImportError = outcome.error
                        }
                    }
                }

                val documentPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        coroutineScope.launch {
                            val outcome = appContainer.bookImportCoordinator.startImportFromUri(uri)
                            handleImportOutcome(outcome)
                        }
                    }
                }

                LibraryScreen(
                    uiState = libraryState,
                    snackbarHostState = librarySnackbarHost,
                    onOpenBook = { bookId ->
                        navController.navigate(AppRoutes.reader(bookId))
                    },
                    onImportBookClick = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/pdf",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onRetrySeed = { libraryViewModel.runInitialSeed() },
                    onRestoreStarterClassic = { libraryViewModel.restoreStarterClassic() },
                    onRequestDeleteBook = { book -> pendingDeleteBook = book },
                    onRequestResetStarter = { book -> pendingResetStarter = book },
                    onRequestHideStarter = { starter ->
                        coroutineScope.launch {
                            appContainer.bookLifecycleService.hideStarterClassic(starter.bookId)
                            libraryViewModel.refreshStarterHiddenFlag()
                            val action = librarySnackbarHost.showSnackbar(
                                message = "\"${starter.title}\" hidden from Library.",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (action == SnackbarResult.ActionPerformed) {
                                appContainer.bookLifecycleService.unhideStarterClassic(starter.bookId)
                                libraryViewModel.refreshStarterHiddenFlag()
                            }
                        }
                    },
                    habitBarContent = {
                        HabitSummaryBar(
                            summary = habitSummary,
                            onClick = { showHabitSheet = true },
                        )
                    },
                )

                if (showHabitSheet) {
                    ReadingProgressSheet(
                        summary = habitSummary,
                        onDismiss = { showHabitSheet = false },
                    )
                }

                pendingDeleteBook?.let { target ->
                    DeleteBookDialog(
                        bookTitle = target.title,
                        onDismiss = { pendingDeleteBook = null },
                        onConfirmDelete = {
                            pendingDeleteBook = null
                            coroutineScope.launch {
                                appContainer.bookLifecycleService.deleteImportedBook(target.bookId)
                                librarySnackbarHost.showSnackbar(
                                    message = "\"${target.title}\" deleted.",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                }

                pendingResetStarter?.let { target ->
                    ResetStarterClassicDialog(
                        bookTitle = target.title,
                        onDismiss = { pendingResetStarter = null },
                        onConfirmReset = {
                            pendingResetStarter = null
                            coroutineScope.launch {
                                appContainer.bookLifecycleService.resetStarterClassic(target.bookId)
                                librarySnackbarHost.showSnackbar(
                                    message = "Starter classic reset to Episode 1.",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                }

                pendingDuplicate?.let { dup ->
                    DuplicateImportDialog(
                        existingTitle = dup.existingTitle,
                        onDismiss = {
                            appContainer.bookImportCoordinator.discardStagedFile(dup.stagedFilePath)
                            pendingDuplicate = null
                        },
                        onOpenExisting = {
                            appContainer.bookImportCoordinator.discardStagedFile(dup.stagedFilePath)
                            pendingDuplicate = null
                            navController.navigate(AppRoutes.reader(dup.existingBookId))
                        },
                        onImportSeparateCopy = {
                            pendingDuplicate = null
                            coroutineScope.launch {
                                val outcome = appContainer.bookImportCoordinator.importSeparateDuplicateCopy(dup)
                                handleImportOutcome(outcome)
                            }
                        },
                    )
                }

                activeImportError?.let { err ->
                    ImportErrorDialog(
                        title = err.title,
                        message = err.userMessage,
                        onDismiss = { activeImportError = null },
                    )
                }
            }

            composable(
                route = AppRoutes.READER,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val rawBookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                val bookId = Uri.decode(rawBookId)

                val readerViewModel: ReaderViewModel = viewModel(
                    key = "reader_$bookId",
                    factory = ReaderViewModel.Factory(
                        bookId = bookId,
                        readerRepository = appContainer.readerRepository,
                        bookRepository = appContainer.bookRepository,
                        lookupRepository = appContainer.lookupRepository,
                        breakRecapCoordinator = appContainer.breakRecapCoordinator,
                        clock = appContainer.clock,
                    ),
                )
                val readerState by readerViewModel.uiState.collectAsState()

                var showDisplaySettings by remember { mutableStateOf(false) }
                var showManualLookupDialog by remember { mutableStateOf(false) }
                var activeCuratedProfile by remember { mutableStateOf<CuratedCharacterProfile?>(null) }
                var activePriorMentions by remember { mutableStateOf<PriorMentionsResult?>(null) }

                ReaderScreen(
                    uiState = readerState,
                    preferences = preferences,
                    onBack = { navController.popBackStack() },
                    onToggleChrome = { readerViewModel.toggleChrome() },
                    onOpenDisplaySettings = { showDisplaySettings = true },
                    onOpenManualRecap = { readerViewModel.requestManualRecap() },
                    onOpenManualLookup = { showManualLookupDialog = true },
                    onDismissPdfNotice = { readerViewModel.dismissPdfNotice() },
                    onMentionTap = { span ->
                        val book = readerState.book ?: return@ReaderScreen
                        val effectiveMaxSeq = maxOf(book.maxReadSeq, span.paragraphSeq)
                        coroutineScope.launch {
                            if (book.sourceType == SourceType.STARTER_CLASSIC) {
                                val profile = appContainer.curatedGlossaryService.lookupByEntityId(
                                    bookId = book.bookId,
                                    entityId = span.entityId,
                                    surfaceText = span.surfaceText,
                                    maxReadSeq = effectiveMaxSeq,
                                )
                                if (profile != null) {
                                    activeCuratedProfile = profile
                                    return@launch
                                }
                            }
                            activePriorMentions = appContainer.priorMentionService.lookupEntity(
                                bookId = book.bookId,
                                entityId = span.entityId,
                                displayTitle = span.canonicalName,
                                maxReadSeq = effectiveMaxSeq,
                                currentParagraphSeq = span.paragraphSeq,
                            )
                        }
                    },
                    onVisibleParagraphChanged = { firstSeq, lastSeq ->
                        readerViewModel.onVisibleParagraphChanged(firstSeq, lastSeq)
                    },
                    onPreviousEpisode = { readerViewModel.navigateToPreviousEpisode() },
                    onNextEpisode = { readerViewModel.navigateToNextEpisode() },
                    completionCardContent = {
                        val ep = readerState.episode
                        if (ep != null) {
                            EpisodeCompletionCard(
                                episode = ep,
                                isFinalEpisode = readerState.isFinalEpisode,
                                hasNextEpisode = readerState.hasNext,
                                onCompleteAndStop = {
                                    readerViewModel.completeCurrentEpisode(
                                        advanceToNext = false,
                                        onDoneAndExit = { navController.popBackStack() },
                                    )
                                },
                                onCompleteAndContinue = {
                                    readerViewModel.completeCurrentEpisode(advanceToNext = true)
                                },
                            )
                        }
                    },
                )

                if (showDisplaySettings) {
                    DisplaySettingsSheet(
                        preferences = preferences,
                        onDismiss = { showDisplaySettings = false },
                        onSelectTheme = { mode ->
                            coroutineScope.launch {
                                appContainer.preferencesRepository.updateTheme(mode, appContainer.clock.nowUtcMillis())
                            }
                        },
                        onChangeFontSize = { sizeSp ->
                            coroutineScope.launch {
                                appContainer.preferencesRepository.updateFontSize(sizeSp, appContainer.clock.nowUtcMillis())
                            }
                        },
                        onSelectLineHeight = { heightMode ->
                            coroutineScope.launch {
                                appContainer.preferencesRepository.updateLineHeight(heightMode, appContainer.clock.nowUtcMillis())
                            }
                        },
                    )
                }

                if (showManualLookupDialog) {
                    ManualNameLookupDialog(
                        onDismiss = { showManualLookupDialog = false },
                        onSearch = { query ->
                            showManualLookupDialog = false
                            val book = readerState.book ?: return@ManualNameLookupDialog
                            val ep = readerState.episode
                            val maxSeq = book.maxReadSeq.coerceAtLeast(book.currentParagraphSeq)
                            val beforeSeq = (book.currentParagraphSeq + 1)
                                .coerceAtLeast((ep?.startParagraphSeq ?: 0) + 1)
                            coroutineScope.launch {
                                if (book.sourceType == SourceType.STARTER_CLASSIC) {
                                    val curated = appContainer.curatedGlossaryService.lookupByQuery(
                                        bookId = book.bookId,
                                        rawQuery = query,
                                        maxReadSeq = maxSeq,
                                    )
                                    if (curated != null) {
                                        activeCuratedProfile = curated
                                        return@launch
                                    }
                                }
                                activePriorMentions = appContainer.priorMentionService.lookupManualQuery(
                                    bookId = book.bookId,
                                    rawQuery = query,
                                    maxReadSeq = maxSeq,
                                    currentParagraphSeq = beforeSeq,
                                    episodeInfoLookup = { epId ->
                                        readerState.episodeMetadataById[epId] ?: (1 to "")
                                    },
                                )
                            }
                        },
                    )
                }

                activeCuratedProfile?.let { profile ->
                    CuratedCharacterSheet(
                        profile = profile,
                        onDismiss = { activeCuratedProfile = null },
                    )
                }

                activePriorMentions?.let { result ->
                    PriorMentionsSheet(
                        result = result,
                        onDismiss = { activePriorMentions = null },
                    )
                }

                readerState.activeRecapPayload?.let { payload ->
                    PreviouslyOnOverlay(
                        payload = payload,
                        onDismiss = { readerViewModel.dismissRecap() },
                        onKeyNameTap = { keyName ->
                            val book = readerState.book ?: return@PreviouslyOnOverlay
                            val capSeq = (payload as? com.episodicreader.domain.recap.RecapPayload.Uploaded)
                                ?.recapEpisodeEndSeq
                                ?: book.maxReadSeq
                            val maxSeq = maxOf(book.maxReadSeq, capSeq)
                            coroutineScope.launch {
                                activePriorMentions = appContainer.priorMentionService.lookupEntity(
                                    bookId = book.bookId,
                                    entityId = keyName.entityId,
                                    displayTitle = keyName.canonicalName,
                                    maxReadSeq = maxSeq,
                                    currentParagraphSeq = maxSeq + 1,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
