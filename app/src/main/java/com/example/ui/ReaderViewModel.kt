package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.BookmarkEntity
import com.example.data.ReaderPreferences
import com.example.data.BookReaderSettingsCodec
import com.example.data.isHtmlGame
import com.example.engine.EpubChapter
import com.example.engine.HtmlOrientation
import com.example.engine.LiveHighlightStyle
import com.example.engine.LiveUnitMode
import com.example.engine.PageTransitionStyle
import com.example.engine.ParsedEpub
import com.example.engine.ReaderBlock
import com.example.engine.ReaderFont
import com.example.engine.ReaderTextAlign
import com.example.engine.ReaderTheme
import com.example.engine.ReadingMode
import com.example.engine.TapNavigationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ReaderLoadState {
    data object Loading : ReaderLoadState()
    data class Ready(val book: BookEntity, val epub: ParsedEpub) : ReaderLoadState()
    data class Error(val message: String) : ReaderLoadState()
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)

    private val _loadState = MutableStateFlow<ReaderLoadState>(ReaderLoadState.Loading)
    val loadState: StateFlow<ReaderLoadState> = _loadState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private val _preferences = MutableStateFlow(ReaderPreferences())
    val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _isPdfOriginalMode = MutableStateFlow(false)
    val isPdfOriginalMode: StateFlow<Boolean> = _isPdfOriginalMode.asStateFlow()

    private var loadedBookId: String? = null
    private var loadJob: Job? = null
    private var bookmarksJob: Job? = null
    private var progressJob: Job? = null
    private data class PendingProgress(val bookId: String, val chapterIndex: Int, val pageIndex: Int, val progressPercent: Int)
    @Volatile private var pendingProgress: PendingProgress? = null

    fun loadBook(bookId: String) {
        if (loadedBookId == bookId && _loadState.value is ReaderLoadState.Ready) return
        loadJob?.cancel()
        bookmarksJob?.cancel()
        progressJob?.cancel()
        loadedBookId = bookId
        _bookmarks.value = emptyList()
        _loadState.value = ReaderLoadState.Loading
        _isPdfOriginalMode.value = false
        loadJob = viewModelScope.launch {
            try {
                val book = repository.getBookById(bookId)
                    ?: throw IllegalStateException("Libro no encontrado en la biblioteca")
                val defaults = repository.preferencesRepository.preferences.first()
                _preferences.value = BookReaderSettingsCodec.decode(book.readerSettingsJson, defaults)
                val parsed = if (book.isHtmlGame) {
                    try {
                        repository.loadParsedEpub(book)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ParsedEpub(
                            title = book.title, author = book.author, coverBytes = null,
                            chapters = listOf(EpubChapter(
                                index = 0, title = book.title,
                                blocks = listOf(ReaderBlock.Paragraph(book.description))
                            )),
                            language = book.language, genre = book.genre
                        )
                    }
                } else repository.loadParsedEpub(book)
                repository.syncBookMetadataIfNeeded(book, parsed)
                if (loadedBookId != bookId) return@launch
                val safeChapter = book.currentChapterIndex.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
                _currentChapterIndex.value = safeChapter
                _currentPageIndex.value = book.currentPageInChapter.coerceAtLeast(0)
                _loadState.value = ReaderLoadState.Ready(book, parsed)
                bookmarksJob = launch {
                    repository.observeBookmarks(bookId).collect { list ->
                        if (loadedBookId == bookId) _bookmarks.value = list
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loadedBookId == bookId) {
                    _loadState.value = ReaderLoadState.Error(e.localizedMessage ?: "No se pudo abrir el libro")
                }
            }
        }
    }

    fun selectChapter(chapterIndex: Int, pageIndex: Int = 0) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val clampedChapter = chapterIndex.coerceIn(0, (ready.epub.chapters.size - 1).coerceAtLeast(0))
        _currentChapterIndex.value = clampedChapter
        val safePage = pageIndex.coerceAtLeast(0)
        _currentPageIndex.value = safePage
        persistProgress(ready, clampedChapter, safePage, totalPagesInChapter = 1)
    }

    fun updatePage(pageIndex: Int, totalPagesInChapter: Int) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val safePage = pageIndex.coerceAtLeast(0)
        _currentPageIndex.value = safePage
        persistProgress(ready, _currentChapterIndex.value, safePage, totalPagesInChapter)
    }

    private fun persistProgress(
        ready: ReaderLoadState.Ready,
        chapterIdx: Int,
        pageIdx: Int,
        totalPagesInChapter: Int
    ) {
        val totalChapters = ready.epub.chapters.size.coerceAtLeast(1)
        val safeChapter = chapterIdx.coerceIn(0, totalChapters - 1)
        val safePage = pageIdx.coerceAtLeast(0)
        val chapterFraction = (safePage + 1).toFloat() / totalPagesInChapter.coerceAtLeast(1).toFloat()
        val overallPercent = (((safeChapter + chapterFraction) / totalChapters.toFloat()) * 100f)
            .toInt()
            .coerceIn(1, 100)
        val snapshot = PendingProgress(ready.book.id, safeChapter, safePage, overallPercent)
        pendingProgress = snapshot
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(140)
            if (loadedBookId != snapshot.bookId || pendingProgress != snapshot) return@launch
            repository.updateReadingProgress(snapshot.bookId, snapshot.chapterIndex, snapshot.pageIndex, snapshot.progressPercent)
            if (pendingProgress == snapshot) pendingProgress = null
        }
    }

    override fun onCleared() {
        val snapshot = pendingProgress
        if (snapshot != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    repository.updateReadingProgress(snapshot.bookId, snapshot.chapterIndex, snapshot.pageIndex, snapshot.progressPercent)
                }
            }
        }
        super.onCleared()
    }

    fun addBookmark(excerpt: String) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val chIdx = _currentChapterIndex.value
        val chapterTitle = ready.epub.chapters.getOrNull(chIdx)?.title ?: "Capítulo ${chIdx + 1}"
        viewModelScope.launch {
            repository.addBookmark(
                bookId = ready.book.id,
                chapterIndex = chIdx,
                chapterTitle = chapterTitle,
                pageIndex = _currentPageIndex.value,
                excerpt = excerpt
            )
        }
    }

    fun addAnnotation(excerpt: String, note: String) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val chapterIdx = _currentChapterIndex.value
        val chapterTitle = ready.epub.chapters.getOrNull(chapterIdx)?.title ?: "Capítulo ${chapterIdx + 1}"
        viewModelScope.launch {
            repository.addBookmark(ready.book.id, chapterIdx, chapterTitle, _currentPageIndex.value, excerpt, note)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
        }
    }

    fun togglePdfOriginalMode() {
        _isPdfOriginalMode.value = !_isPdfOriginalMode.value
    }

    fun setTheme(theme: ReaderTheme) {
        updateBookReaderPreferences { _preferences.value.copy(readerTheme = theme) }
    }

    fun setFont(font: ReaderFont) {
        updateBookReaderPreferences { _preferences.value.copy(readerFont = font) }
    }

    fun setFontSize(sizeSp: Float) {
        updateBookReaderPreferences { _preferences.value.copy(fontSizeSp = sizeSp.coerceIn(12f, 40f)) }
    }

    fun setLineSpacing(multiplier: Float) {
        updateBookReaderPreferences { _preferences.value.copy(lineSpacingMultiplier = multiplier.coerceIn(1.2f, 2.2f)) }
    }

    fun setMargin(marginDp: Int) {
        updateBookReaderPreferences { _preferences.value.copy(horizontalMarginDp = marginDp.coerceIn(8, 44)) }
    }

    fun setReadingMode(mode: ReadingMode) {
        updateBookReaderPreferences { _preferences.value.copy(readingMode = mode) }
    }

    fun setPageTransitionStyle(style: PageTransitionStyle) {
        updateBookReaderPreferences { _preferences.value.copy(pageTransitionStyle = style) }
    }

    fun setPageTransitionDurationMs(durationMs: Int) {
        updateBookReaderPreferences { _preferences.value.copy(pageTransitionDurationMs = durationMs.coerceIn(260, 760)) }
    }

    fun setTapNavigationMode(mode: TapNavigationMode) {
        updateBookReaderPreferences { _preferences.value.copy(tapNavigationMode = mode) }
    }

    fun setTextAlign(align: ReaderTextAlign) {
        updateBookReaderPreferences { _preferences.value.copy(textAlign = align) }
    }

    fun setAmbientParticlesEnabled(enabled: Boolean) {
        updateBookReaderPreferences { _preferences.value.copy(ambientParticlesEnabled = enabled) }
    }

    fun setLiveEnabled(enabled: Boolean) {
        updateBookReaderPreferences { _preferences.value.copy(liveEnabled = enabled) }
    }

    fun setLiveUnitMode(unitMode: LiveUnitMode) {
        updateBookReaderPreferences { _preferences.value.copy(liveUnitMode = unitMode) }
    }

    fun setLiveWpm(wpm: Int) {
        updateBookReaderPreferences { _preferences.value.copy(liveWpm = wpm.coerceIn(60, 480)) }
    }

    fun setLivePhraseTempoMultiplier(multiplier: Float) {
        updateBookReaderPreferences { _preferences.value.copy(livePhraseTempoMultiplier = multiplier.coerceIn(0.6f, 2.4f)) }
    }

    fun setLiveScaleFactor(scale: Float) {
        updateBookReaderPreferences { _preferences.value.copy(liveScaleFactor = scale.coerceIn(1.08f, 1.42f)) }
    }

    fun setLiveHighlightStyle(style: LiveHighlightStyle) {
        updateBookReaderPreferences { _preferences.value.copy(liveHighlightStyle = style) }
    }

    fun setLiveAutoTurnPages(autoTurn: Boolean) {
        updateBookReaderPreferences { _preferences.value.copy(liveAutoTurnPages = autoTurn) }
    }

    fun setLivePunctuationPause(pause: Boolean) {
        updateBookReaderPreferences { _preferences.value.copy(livePunctuationPause = pause) }
    }

    private fun updateBookReaderPreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val updated = transform(_preferences.value)
        _preferences.value = updated
        viewModelScope.launch { repository.saveReaderSettings(ready.book.id, BookReaderSettingsCodec.encode(updated)) }
    }

    fun setHtmlGameNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameNotificationsEnabled(enabled) }
    }

    fun setHtmlGameFullscreenImmersive(immersive: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameFullscreenImmersive(immersive) }
    }

    fun setHtmlGameZoomPercent(percent: Int) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameZoomPercent(percent) }
    }

    fun setHtmlGameMuted(muted: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameMuted(muted) }
    }

    fun setHtmlGameSafeSoftwareMode(safeMode: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameSafeSoftwareMode(safeMode) }
    }

    fun setHtmlGameOrientation(orientation: HtmlOrientation) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameOrientation(orientation) }
    }

    fun setHtmlGameConfirmBackExit(confirm: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setHtmlGameConfirmBackExit(confirm) }
    }

    fun saveHtmlGameState(bookId: String, saveJson: String, explicitRecord: String? = null) {
        viewModelScope.launch {
            val (validJson, bestRecord) = repository.saveHtmlGameState(bookId, saveJson, explicitRecord)
            val current = _loadState.value
            if (current is ReaderLoadState.Ready && current.book.id == bookId) {
                _loadState.value = current.copy(
                    book = current.book.copy(
                        gameSaveJson = validJson,
                        gameBestRecord = bestRecord
                    )
                )
            }
        }
    }

    fun clearHtmlGameSaveData(bookId: String) {
        viewModelScope.launch {
            repository.clearHtmlGameSaveData(bookId)
            val current = _loadState.value
            if (current is ReaderLoadState.Ready && current.book.id == bookId) {
                _loadState.value = current.copy(
                    book = current.book.copy(
                        gameSaveJson = "{}",
                        gameBestRecord = ""
                    )
                )
            }
        }
    }
}
