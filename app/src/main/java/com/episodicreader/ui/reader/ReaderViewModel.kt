package com.episodicreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.episodicreader.data.db.entity.BookEntity
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.model.ParagraphMentionSpan
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.repository.LookupRepository
import com.episodicreader.data.repository.ReaderRepository
import com.episodicreader.domain.recap.BreakRecapCoordinator
import com.episodicreader.domain.recap.RecapPayload
import com.episodicreader.platform.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val episode: EpisodeEntity? = null,
    val paragraphs: List<ParagraphEntity> = emptyList(),
    val mentionsBySeq: Map<Int, List<ParagraphMentionSpan>> = emptyMap(),
    val episodeMetadataById: Map<String, Pair<Int, String>> = emptyMap(),
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isFinalEpisode: Boolean = false,
    val initialScrollParagraphIndex: Int = 0,
    val chromeVisible: Boolean = true,
    val showPdfNoticeBanner: Boolean = false,
    val activeRecapPayload: RecapPayload? = null,
)

class ReaderViewModel(
    private val bookId: String,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val lookupRepository: LookupRepository,
    private val breakRecapCoordinator: BreakRecapCoordinator,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var hasEvaluatedAutoRecap = false

    init {
        loadEpisode(overrideEpisodeId = null)
    }

    fun loadEpisode(overrideEpisodeId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val bundle = readerRepository.loadCurrentEpisodeBundle(bookId, overrideEpisodeId)
            if (bundle == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val nowUtc = clock.nowUtcMillis()

            // Evaluate 48h break re-entry BEFORE updateReadingPositionMonotonic overwrites last_read_at_utc
            val autoRecapPayload = if (!hasEvaluatedAutoRecap && overrideEpisodeId == null) {
                hasEvaluatedAutoRecap = true
                if (breakRecapCoordinator.shouldAutoTriggerRecap(bundle.book, nowUtc)) {
                    breakRecapCoordinator.buildRecap(bundle.book, isManualRequest = false)
                } else {
                    null
                }
            } else {
                _uiState.value.activeRecapPayload
            }

            val mentions = lookupRepository.getMentionSpansForEpisode(bookId, bundle.episode.episodeId)
            val grouped = mentions.groupBy { it.paragraphSeq }
            val episodeMeta = readerRepository.getEpisodeMetadataMap(bookId)
            val resumeIndex = if (overrideEpisodeId == null) {
                bundle.paragraphs.indexOfFirst { it.seqIndex >= bundle.book.currentParagraphSeq }
                    .coerceAtLeast(0)
            } else {
                0
            }

            // Record initial position opening monotonically (never decreases max_read_seq)
            val openingSeq = bundle.paragraphs.getOrNull(resumeIndex)?.seqIndex ?: bundle.episode.startParagraphSeq
            readerRepository.updateReadingPositionMonotonic(
                bookId = bookId,
                episodeId = bundle.episode.episodeId,
                paragraphSeq = openingSeq,
                candidateMaxSeq = openingSeq,
                readAtUtc = nowUtc,
            )
            val refreshedBook = bookRepository.getBookById(bookId) ?: bundle.book

            _uiState.value = ReaderUiState(
                isLoading = false,
                book = refreshedBook,
                episode = bundle.episode,
                paragraphs = bundle.paragraphs,
                mentionsBySeq = grouped,
                episodeMetadataById = episodeMeta,
                hasPrevious = bundle.hasPrevious,
                hasNext = bundle.hasNext,
                isFinalEpisode = bundle.isFinalEpisode,
                initialScrollParagraphIndex = resumeIndex,
                chromeVisible = true,
                showPdfNoticeBanner = refreshedBook.sourceType == SourceType.PDF &&
                    refreshedBook.pdfNoticeDismissed == 0,
                activeRecapPayload = autoRecapPayload,
            )
        }
    }

    fun requestManualRecap() {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            val payload = breakRecapCoordinator.buildRecap(currentBook, isManualRequest = true)
            _uiState.update { it.copy(activeRecapPayload = payload) }
        }
    }

    fun dismissRecap() {
        _uiState.update { it.copy(activeRecapPayload = null) }
    }

    fun toggleChrome() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun dismissPdfNotice() {
        viewModelScope.launch {
            bookRepository.markPdfNoticeDismissed(bookId)
            _uiState.update { it.copy(showPdfNoticeBanner = false) }
        }
    }

    fun onVisibleParagraphChanged(firstVisibleSeq: Int, lastVisibleSeq: Int) {
        val currentEp = _uiState.value.episode ?: return
        val candidateMax = maxOf(firstVisibleSeq, lastVisibleSeq)
        viewModelScope.launch {
            readerRepository.updateReadingPositionMonotonic(
                bookId = bookId,
                episodeId = currentEp.episodeId,
                paragraphSeq = firstVisibleSeq,
                candidateMaxSeq = candidateMax,
                readAtUtc = clock.nowUtcMillis(),
            )
            val updated = bookRepository.getBookById(bookId)
            if (updated != null) {
                _uiState.update { it.copy(book = updated) }
            }
        }
    }

    fun navigateToPreviousEpisode() {
        val currentEp = _uiState.value.episode ?: return
        viewModelScope.launch {
            val prev = readerRepository.getPreviousEpisode(bookId, currentEp.episodeIndex) ?: return@launch
            loadEpisode(prev.episodeId)
        }
    }

    fun navigateToNextEpisode() {
        val currentEp = _uiState.value.episode ?: return
        viewModelScope.launch {
            val next = readerRepository.getNextEpisode(bookId, currentEp.episodeIndex) ?: return@launch
            loadEpisode(next.episodeId)
        }
    }

    fun completeCurrentEpisode(advanceToNext: Boolean, onDoneAndExit: () -> Unit = {}) {
        val currentEp = _uiState.value.episode ?: return
        viewModelScope.launch {
            val now = clock.nowUtcMillis()
            val nextEp = readerRepository.completeEpisodeAtomically(
                bookId = bookId,
                episodeId = currentEp.episodeId,
                completedAtUtc = now,
                localDateIso = clock.todayLocalDateIso(),
                advanceToNextEpisode = advanceToNext,
            )
            if (advanceToNext && nextEp != null) {
                loadEpisode(nextEp.episodeId)
            } else {
                onDoneAndExit()
            }
        }
    }

    class Factory(
        private val bookId: String,
        private val readerRepository: ReaderRepository,
        private val bookRepository: BookRepository,
        private val lookupRepository: LookupRepository,
        private val breakRecapCoordinator: BreakRecapCoordinator,
        private val clock: AppClock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(
                bookId = bookId,
                readerRepository = readerRepository,
                bookRepository = bookRepository,
                lookupRepository = lookupRepository,
                breakRecapCoordinator = breakRecapCoordinator,
                clock = clock,
            ) as T
        }
    }
}
