package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.BookmarkEntity
import com.example.data.ReaderPreferences
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    val preferences: StateFlow<ReaderPreferences> = repository.preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderPreferences()
        )

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _isPdfOriginalMode = MutableStateFlow(false)
    val isPdfOriginalMode: StateFlow<Boolean> = _isPdfOriginalMode.asStateFlow()

    private var loadedBookId: String? = null

    fun loadBook(bookId: String) {
        if (loadedBookId == bookId && _loadState.value is ReaderLoadState.Ready) return
        loadedBookId = bookId
        _loadState.value = ReaderLoadState.Loading
        _isPdfOriginalMode.value = false

        viewModelScope.launch {
            try {
                val book = repository.getBookById(bookId)
                    ?: throw IllegalStateException("Libro no encontrado en la biblioteca")
                val parsed = if (book.isHtmlGame) {
                    runCatching { repository.loadParsedEpub(book) }.getOrElse {
                        ParsedEpub(
                            title = book.title,
                            author = book.author,
                            coverBytes = null,
                            chapters = listOf(
                                EpubChapter(
                                    index = 0,
                                    title = book.title,
                                    blocks = listOf(ReaderBlock.Paragraph(book.description))
                                )
                            ),
                            language = book.language,
                            genre = book.genre
                        )
                    }
                } else {
                    repository.loadParsedEpub(book)
                }
                repository.syncBookMetadataIfNeeded(book, parsed)
                val safeChapter = book.currentChapterIndex.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
                _currentChapterIndex.value = safeChapter
                _currentPageIndex.value = book.currentPageInChapter.coerceAtLeast(0)
                _loadState.value = ReaderLoadState.Ready(book, parsed)

                launch {
                    repository.observeBookmarks(bookId).collect { list ->
                        _bookmarks.value = list
                    }
                }
            } catch (e: Exception) {
                _loadState.value = ReaderLoadState.Error(e.localizedMessage ?: "No se pudo abrir el libro")
            }
        }
    }

    fun selectChapter(chapterIndex: Int, pageIndex: Int = 0) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        val clampedChapter = chapterIndex.coerceIn(0, (ready.epub.chapters.size - 1).coerceAtLeast(0))
        _currentChapterIndex.value = clampedChapter
        _currentPageIndex.value = pageIndex.coerceAtLeast(0)
        persistProgress(ready, clampedChapter, pageIndex.coerceAtLeast(0), totalPagesInChapter = 1)
    }

    fun updatePage(pageIndex: Int, totalPagesInChapter: Int) {
        val ready = _loadState.value as? ReaderLoadState.Ready ?: return
        _currentPageIndex.value = pageIndex
        persistProgress(ready, _currentChapterIndex.value, pageIndex, totalPagesInChapter)
    }

    private fun persistProgress(
        ready: ReaderLoadState.Ready,
        chapterIdx: Int,
        pageIdx: Int,
        totalPagesInChapter: Int
    ) {
        val totalChapters = ready.epub.chapters.size.coerceAtLeast(1)
        val chapterFraction = (pageIdx + 1).toFloat() / totalPagesInChapter.coerceAtLeast(1).toFloat()
        val overallPercent = (((chapterIdx + chapterFraction) / totalChapters.toFloat()) * 100f)
            .toInt()
            .coerceIn(1, 100)

        viewModelScope.launch {
            repository.updateReadingProgress(
                bookId = ready.book.id,
                chapterIndex = chapterIdx,
                pageIndex = pageIdx,
                progressPercent = overallPercent
            )
        }
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

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
        }
    }

    fun togglePdfOriginalMode() {
        _isPdfOriginalMode.value = !_isPdfOriginalMode.value
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch { repository.preferencesRepository.setReaderTheme(theme) }
    }

    fun setFont(font: ReaderFont) {
        viewModelScope.launch { repository.preferencesRepository.setReaderFont(font) }
    }

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { repository.preferencesRepository.setFontSize(sizeSp) }
    }

    fun setLineSpacing(multiplier: Float) {
        viewModelScope.launch { repository.preferencesRepository.setLineSpacing(multiplier) }
    }

    fun setMargin(marginDp: Int) {
        viewModelScope.launch { repository.preferencesRepository.setHorizontalMargin(marginDp) }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch { repository.preferencesRepository.setReadingMode(mode) }
    }

    fun setPageTransitionStyle(style: PageTransitionStyle) {
        viewModelScope.launch { repository.preferencesRepository.setPageTransitionStyle(style) }
    }

    fun setPageTransitionDurationMs(durationMs: Int) {
        viewModelScope.launch { repository.preferencesRepository.setPageTransitionDurationMs(durationMs) }
    }

    fun setTapNavigationMode(mode: TapNavigationMode) {
        viewModelScope.launch { repository.preferencesRepository.setTapNavigationMode(mode) }
    }

    fun setTextAlign(align: ReaderTextAlign) {
        viewModelScope.launch { repository.preferencesRepository.setTextAlign(align) }
    }

    fun setAmbientParticlesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setAmbientParticlesEnabled(enabled) }
    }

    fun setLiveEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setLiveEnabled(enabled) }
    }

    fun setLiveUnitMode(unitMode: LiveUnitMode) {
        viewModelScope.launch { repository.preferencesRepository.setLiveUnitMode(unitMode) }
    }

    fun setLiveWpm(wpm: Int) {
        viewModelScope.launch { repository.preferencesRepository.setLiveWpm(wpm) }
    }

    fun setLivePhraseTempoMultiplier(multiplier: Float) {
        viewModelScope.launch { repository.preferencesRepository.setLivePhraseTempoMultiplier(multiplier) }
    }

    fun setLiveScaleFactor(scale: Float) {
        viewModelScope.launch { repository.preferencesRepository.setLiveScaleFactor(scale) }
    }

    fun setLiveHighlightStyle(style: LiveHighlightStyle) {
        viewModelScope.launch { repository.preferencesRepository.setLiveHighlightStyle(style) }
    }

    fun setLiveAutoTurnPages(autoTurn: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setLiveAutoTurnPages(autoTurn) }
    }

    fun setLivePunctuationPause(pause: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setLivePunctuationPause(pause) }
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
