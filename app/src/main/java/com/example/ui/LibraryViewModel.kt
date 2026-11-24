package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.engine.AppThemeMode
import com.example.engine.ConversionProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    CONVERTED("Convertidos a EPUB"),
    EPUB("EPUB"),
    PDF("Desde PDF"),
    MOBI("Desde MOBI"),
    FAVORITES("Favoritos")
}

data class LibraryUiState(
    val allBooks: List<BookEntity> = emptyList(),
    val filteredBooks: List<BookEntity> = emptyList(),
    val convertedBooks: List<BookEntity> = emptyList(),
    val continueReadingBook: BookEntity? = null,
    val searchQuery: String = "",
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val isGridLayout: Boolean = true,
    val isOnline: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val activeConversion: ConversionProgress? = null,
    val lastConvertedBook: BookEntity? = null,
    val conversionError: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BookRepository(application)

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    private val isGridLayout = MutableStateFlow(true)
    private val activeConversion = MutableStateFlow<ConversionProgress?>(null)
    private val lastConvertedBook = MutableStateFlow<BookEntity?>(null)
    private val conversionError = MutableStateFlow<String?>(null)

    private val filterParams = combine(searchQuery, selectedFilter, isGridLayout) { q, f, grid ->
        Triple(q, f, grid)
    }

    private val conversionStatus = combine(
        activeConversion,
        lastConvertedBook,
        conversionError
    ) { active, last, err ->
        Triple(active, last, err)
    }

    private val appThemeFlow = repository.preferencesRepository.preferences
        .map { it.appThemeMode }
        .distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.allBooks,
        repository.networkMonitor.isOnlineFlow,
        filterParams,
        conversionStatus,
        appThemeFlow
    ) { books, online, (query, filter, grid), (active, last, err), themeMode ->
        val filtered = books.filter { book ->
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.CONVERTED -> book.isConvertedFromOtherFormat
                LibraryFilter.EPUB -> book.originalFormat.equals("EPUB", ignoreCase = true)
                LibraryFilter.PDF -> book.originalFormat.equals("PDF", ignoreCase = true)
                LibraryFilter.MOBI -> book.originalFormat.equals("MOBI", ignoreCase = true)
                LibraryFilter.FAVORITES -> book.isFavorite
            }
            val matchesQuery = query.isBlank() ||
                book.title.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true) ||
                book.genre.contains(query, ignoreCase = true) ||
                book.language.contains(query, ignoreCase = true) ||
                book.originalFormat.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }

        val continueBook = books.firstOrNull { it.lastReadTimestamp > 0L } ?: books.firstOrNull()

        LibraryUiState(
            allBooks = books,
            filteredBooks = filtered,
            convertedBooks = books.filter { it.isConvertedFromOtherFormat },
            continueReadingBook = continueBook,
            searchQuery = query,
            selectedFilter = filter,
            isGridLayout = grid,
            isOnline = online,
            appThemeMode = themeMode,
            activeConversion = active,
            lastConvertedBook = last,
            conversionError = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        viewModelScope.launch {
            val currentBooks = repository.allBooks.first()
            currentBooks.filter { it.wordCount == 0 }.forEach { book ->
                runCatching {
                    val parsed = repository.loadParsedEpub(book)
                    repository.syncBookMetadataIfNeeded(book, parsed)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        selectedFilter.value = filter
    }

    fun toggleLayoutMode() {
        isGridLayout.value = !isGridLayout.value
    }

    fun importBooks(uris: List<Uri>, onBookReady: ((BookEntity) -> Unit)? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            conversionError.value = null
            lastConvertedBook.value = null
            var lastBook: BookEntity? = null
            for (uri in uris) {
                try {
                    val book = repository.importAndConvertBook(uri) { progress ->
                        activeConversion.value = progress
                    }
                    lastBook = book
                } catch (e: Exception) {
                    conversionError.value = e.localizedMessage ?: "No se pudo convertir el archivo seleccionado."
                }
            }
            activeConversion.value = null
            if (lastBook != null) {
                lastConvertedBook.value = lastBook
                onBookReady?.invoke(lastBook)
            }
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(book)
        }
    }

    fun updateBookGenre(bookId: String, newGenre: String) {
        viewModelScope.launch {
            repository.updateBookGenre(bookId, newGenre)
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setAppThemeMode(mode)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun dismissConversionBanner() {
        lastConvertedBook.value = null
        conversionError.value = null
    }
}
