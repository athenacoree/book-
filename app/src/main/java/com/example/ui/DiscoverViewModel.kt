package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.GutendexBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CatalogLoadState {
    data object Loading : CatalogLoadState()
    data class Success(val books: List<GutendexBook>) : CatalogLoadState()
    data object Offline : CatalogLoadState()
    data class Error(val message: String) : CatalogLoadState()
}

data class DiscoverUiState(
    val isOnline: Boolean = true,
    val searchQuery: String = "",
    val selectedLanguage: String = "es",
    val selectedTopic: String = "",
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val downloadingIds: Set<Int> = emptySet(),
    val downloadedLibraryIds: Set<String> = emptySet(),
    val downloadFeedback: String? = null
)

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)

    private val searchQuery = MutableStateFlow("")
    private val selectedLanguage = MutableStateFlow("es")
    private val selectedTopic = MutableStateFlow("")
    private val loadState = MutableStateFlow<CatalogLoadState>(CatalogLoadState.Loading)
    private val downloadingIds = MutableStateFlow<Set<Int>>(emptySet())
    private val downloadFeedback = MutableStateFlow<String?>(null)

    private val queryParams = combine(searchQuery, selectedLanguage, selectedTopic) { q, lang, topic ->
        Triple(q, lang, topic)
    }

    val uiState: StateFlow<DiscoverUiState> = combine(
        repository.networkMonitor.isOnlineFlow,
        repository.allBooks,
        queryParams,
        loadState,
        combine(downloadingIds, downloadFeedback) { d, f -> d to f }
    ) { online, libraryBooks, (q, lang, topic), state, (downloading, feedback) ->
        val effectiveState = if (!online && state is CatalogLoadState.Loading) {
            CatalogLoadState.Offline
        } else {
            state
        }
        DiscoverUiState(
            isOnline = online,
            searchQuery = q,
            selectedLanguage = lang,
            selectedTopic = topic,
            loadState = effectiveState,
            downloadingIds = downloading,
            downloadedLibraryIds = libraryBooks.map { it.id }.toSet(),
            downloadFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiscoverUiState()
    )

    init {
        refreshCatalog()
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setLanguage(langCode: String) {
        selectedLanguage.value = langCode
        refreshCatalog()
    }

    fun setTopic(topic: String) {
        selectedTopic.value = if (selectedTopic.value == topic) "" else topic
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            if (!repository.networkMonitor.isCurrentlyOnline()) {
                loadState.value = CatalogLoadState.Offline
                return@launch
            }
            loadState.value = CatalogLoadState.Loading
            try {
                val results = repository.searchOnlineCatalog(
                    query = searchQuery.value,
                    languageCode = selectedLanguage.value,
                    topic = selectedTopic.value
                )
                loadState.value = CatalogLoadState.Success(results)
            } catch (e: Exception) {
                if (!repository.networkMonitor.isCurrentlyOnline()) {
                    loadState.value = CatalogLoadState.Offline
                } else {
                    loadState.value = CatalogLoadState.Error(
                        e.localizedMessage ?: "No se pudo cargar el catálogo en línea."
                    )
                }
            }
        }
    }

    fun downloadBook(book: GutendexBook, onDownloaded: (BookEntity) -> Unit = {}) {
        if (downloadingIds.value.contains(book.id)) return
        viewModelScope.launch {
            downloadingIds.value = downloadingIds.value + book.id
            downloadFeedback.value = null
            try {
                val saved = repository.downloadOnlineBook(book)
                downloadFeedback.value = "Descargado para lectura sin conexión: ${saved.title}"
                onDownloaded(saved)
            } catch (e: Exception) {
                downloadFeedback.value = "Error al descargar: ${e.localizedMessage ?: "revisa tu conexión"}"
            } finally {
                downloadingIds.value = downloadingIds.value - book.id
            }
        }
    }

    fun clearFeedback() {
        downloadFeedback.value = null
    }
}
