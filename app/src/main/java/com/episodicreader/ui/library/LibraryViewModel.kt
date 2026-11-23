package com.episodicreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.episodicreader.data.db.model.LibraryBookRow
import com.episodicreader.data.repository.BookRepository
import com.episodicreader.data.seed.SeedResult
import com.episodicreader.data.seed.StarterClassicSeeder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<LibraryBookRow> = emptyList(),
    val isSeeding: Boolean = true,
    val seedErrorMessage: String? = null,
    val hasHiddenStarterClassic: Boolean = false,
)

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val starterClassicSeeder: StarterClassicSeeder,
) : ViewModel() {

    private val seedingState = MutableStateFlow<Pair<Boolean, String?>>(true to null)
    private val starterHiddenState = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.observeVisibleLibraryBooks(),
        seedingState,
        starterHiddenState,
    ) { books, (isSeeding, seedError), starterHidden ->
        LibraryUiState(
            books = books,
            isSeeding = isSeeding,
            seedErrorMessage = seedError,
            hasHiddenStarterClassic = starterHidden,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    init {
        runInitialSeed()
    }

    fun runInitialSeed() {
        viewModelScope.launch {
            seedingState.value = true to null
            when (val res = starterClassicSeeder.seedIfNeeded()) {
                is SeedResult.Seeded, SeedResult.AlreadySeeded -> {
                    seedingState.value = false to null
                    refreshStarterHiddenFlag()
                }
                is SeedResult.Error -> {
                    seedingState.value = false to res.message
                }
            }
        }
    }

    fun restoreStarterClassic() {
        viewModelScope.launch {
            starterClassicSeeder.restoreStarterClassic()
            refreshStarterHiddenFlag()
        }
    }

    fun refreshStarterHiddenFlag() {
        viewModelScope.launch {
            val starter = bookRepository.getStarterClassic()
            starterHiddenState.value = starter == null || starter.isHidden != 0
        }
    }

    class Factory(
        private val bookRepository: BookRepository,
        private val starterClassicSeeder: StarterClassicSeeder,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(bookRepository, starterClassicSeeder) as T
        }
    }
}
