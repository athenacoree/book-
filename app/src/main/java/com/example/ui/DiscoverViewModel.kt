package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.GutendexBook
import com.example.data.SupabaseCatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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
    val installedGameVersions: Map<Long, String> = emptyMap(),
    val downloadFeedback: String? = null,
    val selectedCatalogSection: String = "books",
    val games: List<SupabaseCatalogItem> = emptyList(),
    val gamesLoading: Boolean = false,
    val gamesError: String? = null,
    val downloadingGameIds: Set<Long> = emptySet()
)

private data class CatalogBaseState(
    val online: Boolean,
    val libraryBooks: List<BookEntity>,
    val query: String,
    val language: String,
    val topic: String,
    val loadState: CatalogLoadState
)

private data class CatalogDownloadState(
    val bookIds: Set<Int>,
    val feedback: String?
)

private data class CatalogGameState(
    val games: List<SupabaseCatalogItem>,
    val loading: Boolean,
    val error: String?,
    val downloadingIds: Set<Long>,
    val selectedSection: String
)

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)

    private val searchQuery = MutableStateFlow("")
    private val selectedLanguage = MutableStateFlow("es")
    private val selectedTopic = MutableStateFlow("")
    private val selectedCatalogSection = MutableStateFlow("books")
    private val loadState = MutableStateFlow<CatalogLoadState>(CatalogLoadState.Loading)
    private val downloadingIds = MutableStateFlow<Set<Int>>(emptySet())
    private val downloadFeedback = MutableStateFlow<String?>(null)
    private val games = MutableStateFlow<List<SupabaseCatalogItem>>(emptyList())
    private val gamesLoading = MutableStateFlow(false)
    private val gamesError = MutableStateFlow<String?>(null)
    private val downloadingGameIds = MutableStateFlow<Set<Long>>(emptySet())
    private var catalogRefreshJob: Job? = null

    private val queryParams = combine(searchQuery, selectedLanguage, selectedTopic) { q, lang, topic ->
        Triple(q, lang, topic)
    }

    private val baseState = combine(
        repository.networkMonitor.isOnlineFlow,
        repository.allBooks,
        queryParams,
        loadState
    ) { online, libraryBooks, params, state ->
        CatalogBaseState(online, libraryBooks, params.first, params.second, params.third, state)
    }

    private val downloadState = combine(downloadingIds, downloadFeedback) { ids, feedback ->
        CatalogDownloadState(ids, feedback)
    }

    private val gameState = combine(
        games,
        gamesLoading,
        gamesError,
        downloadingGameIds,
        selectedCatalogSection
    ) { items, loading, error, downloading, section ->
        CatalogGameState(items, loading, error, downloading, section)
    }

    val uiState: StateFlow<DiscoverUiState> = combine(
        baseState,
        downloadState,
        gameState
    ) { base, downloads, gameCatalog ->
        val effectiveState = if (!base.online && base.loadState is CatalogLoadState.Loading) {
            CatalogLoadState.Offline
        } else {
            base.loadState
        }
        DiscoverUiState(
            isOnline = base.online,
            searchQuery = base.query,
            selectedLanguage = base.language,
            selectedTopic = base.topic,
            loadState = effectiveState,
            downloadingIds = downloads.bookIds,
            downloadedLibraryIds = base.libraryBooks.map { it.id }.toSet(),
            installedGameVersions = base.libraryBooks.mapNotNull { book ->
                book.catalogGameId?.let { it to book.gameVersion }
            }.toMap(),
            downloadFeedback = downloads.feedback,
            selectedCatalogSection = gameCatalog.selectedSection,
            games = gameCatalog.games,
            gamesLoading = gameCatalog.loading,
            gamesError = gameCatalog.error,
            downloadingGameIds = gameCatalog.downloadingIds
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

    fun setCatalogSection(section: String) {
        selectedCatalogSection.value = if (section == "games") "games" else "books"
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
        // Keep only the newest request when the user changes filters or submits a new search.
        catalogRefreshJob?.cancel()
        catalogRefreshJob = viewModelScope.launch {
            if (!repository.networkMonitor.isCurrentlyOnline()) {
                loadState.value = CatalogLoadState.Offline
                gamesError.value = "El catálogo de juegos necesita conexión a Internet."
                gamesLoading.value = false
                return@launch
            }

            val query = searchQuery.value.trim()
            val language = selectedLanguage.value
            val topic = selectedTopic.value
            loadState.value = CatalogLoadState.Loading
            gamesLoading.value = true
            gamesError.value = null

            // Load books and games at the same time so the slower catalog does not block the other.
            supervisorScope {
                launch {
                    try {
                        val results = repository.searchOnlineCatalog(
                            query = query,
                            languageCode = language,
                            topic = topic
                        )
                        loadState.value = CatalogLoadState.Success(results)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loadState.value = if (!repository.networkMonitor.isCurrentlyOnline()) {
                            CatalogLoadState.Offline
                        } else {
                            CatalogLoadState.Error(
                                e.localizedMessage ?: "No se pudo cargar el catálogo de libros."
                            )
                        }
                    }
                }

                launch {
                    try {
                        games.value = repository.searchOnlineGames(query)
                        gamesError.value = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        gamesError.value = e.localizedMessage ?: "No se pudo cargar el catálogo de juegos."
                    } finally {
                        if (currentCoroutineContext()[Job]?.parent == catalogRefreshJob) {
                            gamesLoading.value = false
                        }
                    }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadFeedback.value = "Error al descargar: ${e.localizedMessage ?: "revisa tu conexión"}"
            } finally {
                downloadingIds.value = downloadingIds.value - book.id
            }
        }
    }

    fun downloadGame(item: SupabaseCatalogItem, onDownloaded: (BookEntity) -> Unit = {}) {
        if (downloadingGameIds.value.contains(item.id)) return
        viewModelScope.launch {
            downloadingGameIds.value = downloadingGameIds.value + item.id
            downloadFeedback.value = null
            try {
                val saved = repository.downloadOnlineGame(item)
                downloadFeedback.value = "Juego instalado o actualizado. Tu progreso se conservó: ${saved.title}"
                onDownloaded(saved)
            } catch (e: Exception) {
                downloadFeedback.value = "Error al descargar el juego: ${e.localizedMessage ?: "revisa tu conexión"}"
            } finally {
                downloadingGameIds.value = downloadingGameIds.value - item.id
            }
        }
    }

    fun clearFeedback() {
        downloadFeedback.value = null
    }
}
