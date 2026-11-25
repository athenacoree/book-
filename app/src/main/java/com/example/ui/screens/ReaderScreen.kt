package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.BookmarkEntity
import com.example.data.ReaderPreferences
import com.example.data.isHtmlGame
import com.example.engine.EpubChapter
import com.example.engine.HtmlOrientation
import com.example.engine.LiveHighlightStyle
import com.example.engine.LiveUnitMode
import com.example.engine.PageTransitionStyle
import com.example.engine.ReaderBlock
import com.example.engine.ReaderFont
import com.example.engine.ReaderTextAlign
import com.example.engine.ReaderTheme
import com.example.engine.ReadingMode
import com.example.engine.TapNavigationMode
import com.example.ui.ReaderLoadState
import com.example.ui.components.AmbientReaderParticles
import com.example.ui.components.ChapterCompletionStampOverlay
import com.example.ui.components.ElasticBookmarkRibbon
import com.example.ui.components.HtmlConsoleEntry
import com.example.ui.components.HtmlGameReaderView
import com.example.ui.components.InteractiveBookDemoView
import com.example.ui.components.YouTubeBookVideoView
import com.example.ui.components.LongPressQuickMenuOverlay
import com.example.ui.components.ReaderColors
import com.example.ui.components.TocAndBookmarksSheetContent
import com.example.ui.components.UnifiedReaderSettingsSheet
import com.example.ui.components.resolveFontFamily
import com.example.ui.components.resolveReaderColors
import com.example.ui.components.shareEpubFile
import com.example.ui.theme.LoraFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed class PageElement {
    data class TextSlice(val text: AnnotatedString) : PageElement()
    data class InlineImage(val imagePath: String, val caption: String) : PageElement()
    data class InteractiveDemo(val demo: ReaderBlock.InteractiveDemo) : PageElement()
    data class YouTubeVideo(val video: ReaderBlock.YouTubeVideo) : PageElement()
}

private data class ReaderPage(
    val elements: List<PageElement>
) {
    fun plainTextSummary(): String = elements
        .filterIsInstance<PageElement.TextSlice>()
        .joinToString(" ") { it.text.text }
        .trim()
}

private data class LiveToken(
    val elementIndex: Int,
    val startInElement: Int,
    val endInElement: Int,
    val text: String,
    val wordCount: Int
)

private val WORD_REGEX = Regex("\\S+")
private val PHRASE_PUNCT_CHARS = setOf('.', ',', ';', ':', '!', '?', '…', '—')
private val TRAILING_CLOSE_CHARS = setOf('"', '”', '’', '»', ')', ']')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    loadState: ReaderLoadState,
    preferences: ReaderPreferences,
    currentChapterIndex: Int,
    currentPageIndex: Int,
    bookmarks: List<BookmarkEntity>,
    isPdfOriginalMode: Boolean,
    onBack: () -> Unit,
    onSelectChapter: (Int, Int) -> Unit,
    onUpdatePage: (Int, Int) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onTogglePdfMode: () -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetFont: (ReaderFont) -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetMargin: (Int) -> Unit,
    onSetReadingMode: (ReadingMode) -> Unit,
    onSetPageTransitionStyle: (PageTransitionStyle) -> Unit,
    onSetPageTransitionDurationMs: (Int) -> Unit,
    onSetTapNavigationMode: (TapNavigationMode) -> Unit,
    onSetTextAlign: (ReaderTextAlign) -> Unit,
    onSetAmbientParticlesEnabled: (Boolean) -> Unit,
    onSetLiveEnabled: (Boolean) -> Unit,
    onSetLiveUnitMode: (LiveUnitMode) -> Unit,
    onSetLiveWpm: (Int) -> Unit,
    onSetLivePhraseTempoMultiplier: (Float) -> Unit,
    onSetLiveScaleFactor: (Float) -> Unit,
    onSetLiveHighlightStyle: (LiveHighlightStyle) -> Unit,
    onSetLiveAutoTurnPages: (Boolean) -> Unit,
    onSetLivePunctuationPause: (Boolean) -> Unit,
    onSetHtmlGameNotificationsEnabled: (Boolean) -> Unit = {},
    onSetHtmlGameFullscreenImmersive: (Boolean) -> Unit = {},
    onSetHtmlGameZoomPercent: (Int) -> Unit = {},
    onSetHtmlGameMuted: (Boolean) -> Unit = {},
    onSetHtmlGameSafeSoftwareMode: (Boolean) -> Unit = {},
    onSetHtmlGameOrientation: (HtmlOrientation) -> Unit = {},
    onSetHtmlGameConfirmBackExit: (Boolean) -> Unit = {},
    onSaveHtmlGameState: (String, String, String?) -> Unit = { _, _, _ -> },
    onClearHtmlGameSaveData: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showControls by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var initialSettingsTab by remember { mutableIntStateOf(0) }
    var showTocSheet by remember { mutableStateOf(false) }
    var currentPageExcerpt by remember { mutableStateOf("") }
    var bookmarkSavedToast by remember { mutableStateOf(false) }
    var livePlaying by remember { mutableStateOf(true) }
    var liveResetSignal by remember { mutableIntStateOf(0) }
    var totalPagesCurrentChapter by remember { mutableIntStateOf(1) }
    var showCompletionStamp by remember { mutableStateOf(false) }
    var htmlReloadSignal by remember { mutableIntStateOf(0) }
    var htmlTestNotificationSignal by remember { mutableIntStateOf(0) }
    var showHtmlExitConfirm by remember { mutableStateOf(false) }
    val htmlConsoleEntries = remember { mutableStateListOf<HtmlConsoleEntry>() }

    val activeHtmlGame = (loadState as? ReaderLoadState.Ready)?.let {
        it.book.isHtmlGame && !it.book.originalPath.isNullOrBlank()
    } == true

    fun openSettings(tab: Int) {
        livePlaying = false
        showControls = false
        showLongPressMenu = false
        initialSettingsTab = tab
        showSettingsSheet = true
    }

    fun openTableOfContents() {
        livePlaying = false
        showControls = false
        showLongPressMenu = false
        showTocSheet = true
    }

    BackHandler {
        when {
            showSettingsSheet -> showSettingsSheet = false
            showTocSheet -> showTocSheet = false
            showLongPressMenu -> showLongPressMenu = false
            showControls -> showControls = false
            showHtmlExitConfirm -> showHtmlExitConfirm = false
            activeHtmlGame && preferences.htmlGameConfirmBackExit -> showHtmlExitConfirm = true
            else -> onBack()
        }
    }

    val colors = resolveReaderColors(preferences.readerTheme)

    LaunchedEffect(bookmarkSavedToast) {
        if (bookmarkSavedToast) {
            delay(1800)
            bookmarkSavedToast = false
        }
    }

    val isCurrentPageBookmarked = remember(bookmarks, currentChapterIndex, currentPageIndex) {
        bookmarks.any { it.chapterIndex == currentChapterIndex && it.pageIndex == currentPageIndex }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // #9 Subtle Ambient 2D Particles Layer
        if (preferences.ambientParticlesEnabled) {
            AmbientReaderParticles(theme = preferences.readerTheme)
        }

        when (loadState) {
            ReaderLoadState.Loading -> {
                ReaderLoadingSkeleton(colors = colors)
            }

            is ReaderLoadState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = loadState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Volver a Biblioteca") }
                }
            }

            is ReaderLoadState.Ready -> {
                val book = loadState.book
                val epub = loadState.epub
                val chapter = epub.chapters.getOrNull(currentChapterIndex) ?: epub.chapters.first()
                val isFinalChapter = currentChapterIndex >= epub.chapters.lastIndex
                val htmlGameFile = book.originalPath?.takeIf { book.isHtmlGame }?.let(::File)?.takeIf { it.exists() }
                val isInteractiveHtml = htmlGameFile != null

                fun triggerAddBookmark() {
                    val excerpt = currentPageExcerpt.ifBlank {
                        chapter.blocks.firstOrNull()?.let {
                            when (it) {
                                is ReaderBlock.Heading -> it.text
                                is ReaderBlock.Paragraph -> it.text
                                is ReaderBlock.Quote -> it.text
                                is ReaderBlock.ImageBlock -> it.caption.ifBlank { chapter.title }
                                is ReaderBlock.InteractiveDemo -> it.title
                                is ReaderBlock.YouTubeVideo -> it.title
                                ReaderBlock.Divider -> chapter.title
                            }
                        } ?: chapter.title
                    }
                    onAddBookmark(excerpt)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    bookmarkSavedToast = true
                }

                if (htmlGameFile != null) {
                    HtmlGameReaderView(
                        htmlFile = htmlGameFile,
                        bookTitle = book.title,
                        savedGameJson = book.gameSaveJson,
                        preferences = preferences,
                        colors = colors,
                        reloadSignal = htmlReloadSignal,
                        testNotificationSignal = htmlTestNotificationSignal,
                        showTopToolbar = showControls,
                        isExternalOverlayOpen = showSettingsSheet || showTocSheet || showHtmlExitConfirm,
                        onToggleControls = { showControls = !showControls },
                        onOpenSettings = { tab ->
                            openSettings(tab)
                        },
                        onToggleMute = onSetHtmlGameMuted,
                        onEnableSafeSoftwareMode = { onSetHtmlGameSafeSoftwareMode(true) },
                        onSaveGameState = { saveJson, explicitRecord ->
                            onSaveHtmlGameState(book.id, saveJson, explicitRecord)
                        },
                        onConsoleMessage = { entry ->
                            if (htmlConsoleEntries.size >= 60) {
                                htmlConsoleEntries.removeAt(0)
                            }
                            htmlConsoleEntries.add(entry)
                        }
                    )
                } else if (isPdfOriginalMode && book.originalPath != null && File(book.originalPath).exists()) {
                    PdfOriginalReaderView(
                        pdfFile = File(book.originalPath),
                        colors = colors,
                        onToggleControls = { showControls = !showControls },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLongPressMenu = true
                        }
                    )
                } else if (
                    preferences.liveEnabled &&
                    preferences.liveUnitMode == LiveUnitMode.PHRASE &&
                    chapter.blocks.none { it is ReaderBlock.InteractiveDemo || it is ReaderBlock.YouTubeVideo }
                ) {
                    LiveLyricsStageView(
                        chapter = chapter,
                        totalChapters = epub.chapters.size,
                        initialProgressIndex = currentPageIndex,
                        preferences = preferences,
                        colors = colors,
                        livePlaying = livePlaying,
                        liveResetSignal = liveResetSignal,
                        showTopToolbar = showControls,
                        onToggleLivePlay = { livePlaying = !livePlaying },
                        onRestartLive = { liveResetSignal++ },
                        onOpenLiveSettings = {
                            openSettings(2)
                        },
                        onToggleControls = { showControls = !showControls },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLongPressMenu = true
                        },
                        onUpdateExcerpt = { currentPageExcerpt = it },
                        onUpdateProgress = { idx, total ->
                            totalPagesCurrentChapter = total.coerceAtLeast(1)
                            onUpdatePage(idx, total.coerceAtLeast(1))
                        },
                        onNextChapter = {
                            if (currentChapterIndex < epub.chapters.lastIndex) {
                                onSelectChapter(currentChapterIndex + 1, 0)
                            } else {
                                showCompletionStamp = true
                            }
                        },
                        onTriggerCompletionStamp = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCompletionStamp = true
                        }
                    )
                } else if (preferences.readingMode == ReadingMode.CONTINUOUS_SCROLL) {
                    ScrollEpubReaderView(
                        chapter = chapter,
                        totalChapters = epub.chapters.size,
                        preferences = preferences,
                        colors = colors,
                        livePlaying = livePlaying,
                        liveResetSignal = liveResetSignal,
                        showTopToolbar = showControls,
                        onPreviousChapter = {
                            if (currentChapterIndex > 0) onSelectChapter(currentChapterIndex - 1, 0)
                        },
                        onNextChapter = {
                            if (currentChapterIndex < epub.chapters.lastIndex) {
                                onSelectChapter(currentChapterIndex + 1, 0)
                            } else {
                                showCompletionStamp = true
                            }
                        },
                        onToggleControls = { showControls = !showControls },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLongPressMenu = true
                        },
                        initialPageIndex = currentPageIndex,
                        onUpdatePage = { pageIdx, totalBlocks ->
                            onUpdatePage(pageIdx, totalBlocks.coerceAtLeast(1))
                        },
                        onToggleLivePlay = { livePlaying = !livePlaying },
                        onOpenLiveSettings = {
                            openSettings(2)
                        },
                        onUpdateExcerpt = { currentPageExcerpt = it }
                    )
                } else {
                    PaginatedEpubReaderView(
                        chapter = chapter,
                        totalChapters = epub.chapters.size,
                        initialPageIndex = currentPageIndex,
                        preferences = preferences,
                        colors = colors,
                        livePlaying = livePlaying,
                        liveResetSignal = liveResetSignal,
                        showTopToolbar = showControls,
                        onPageChanged = { pageIdx, totalPages, excerpt ->
                            totalPagesCurrentChapter = totalPages
                            currentPageExcerpt = excerpt
                            onUpdatePage(pageIdx, totalPages)
                        },
                        onPreviousChapter = {
                            if (currentChapterIndex > 0) {
                                onSelectChapter(currentChapterIndex - 1, 0)
                            }
                        },
                        onNextChapter = {
                            if (currentChapterIndex < epub.chapters.lastIndex) {
                                onSelectChapter(currentChapterIndex + 1, 0)
                            } else {
                                showCompletionStamp = true
                            }
                        },
                        onTriggerCompletionStamp = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCompletionStamp = true
                        },
                        onToggleControls = { showControls = !showControls },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLongPressMenu = true
                        },
                        onToggleLivePlay = { livePlaying = !livePlaying },
                        onRestartLivePage = { liveResetSignal++ },
                        onOpenLiveSettings = {
                            openSettings(2)
                        }
                    )
                }

                // #6 Elastic 2D Bookmark Ribbon dropping from top-right
                ElasticBookmarkRibbon(
                    isBookmarked = isCurrentPageBookmarked || bookmarkSavedToast,
                    accentColor = colors.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(end = 26.dp)
                )

                // Top Floating Glass Toolbar
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = colors.barSurface,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, colors.barBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reader_back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver",
                                        tint = colors.text
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isInteractiveHtml) "Juego HTML · Pantalla completa" else chapter.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.mutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!book.isHtmlGame && book.originalPath != null && File(book.originalPath).exists()) {
                                    Surface(
                                        color = if (isPdfOriginalMode) colors.accent else colors.barBorder.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(onClick = onTogglePdfMode)
                                            .testTag("toggle_pdf_mode_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.PictureAsPdf,
                                                contentDescription = null,
                                                tint = if (isPdfOriginalMode) Color.White else colors.text,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isPdfOriginalMode) "EPUB" else "PDF",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isPdfOriginalMode) Color.White else colors.text
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { triggerAddBookmark() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reader_bookmark_button")
                                ) {
                                    Icon(
                                        Icons.Filled.BookmarkAdd,
                                        contentDescription = "Añadir marcador",
                                        tint = colors.accent
                                    )
                                }

                                IconButton(
                                    onClick = { openTableOfContents() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reader_toc_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = "Índice y marcadores",
                                        tint = colors.text
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        openSettings(0)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reader_settings_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = "Centro de lectura",
                                        tint = colors.text
                                    )
                                }
                            }
                        }
                    }
                }

                // Bookmark Toast
                AnimatedVisibility(
                    visible = bookmarkSavedToast,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 68.dp)
                ) {
                    Surface(
                        color = colors.accent,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Marcador guardado",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Bottom Floating Navigation Bar
                AnimatedVisibility(
                    visible = showControls && !isPdfOriginalMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = colors.barSurface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, colors.barBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            if (isInteractiveHtml) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { htmlReloadSignal++ }) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = null,
                                            tint = colors.text,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reiniciar", color = colors.text)
                                    }
                                    TextButton(onClick = { htmlTestNotificationSignal++ }) {
                                        Icon(
                                            Icons.Filled.NotificationsActive,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Probar aviso", color = colors.accent)
                                    }
                                    TextButton(
                                        onClick = {
                                            openSettings(4)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.SportsEsports,
                                            contentDescription = null,
                                            tint = colors.text,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ajustes", color = colors.text)
                                    }
                                    TextButton(onClick = { shareEpubFile(context, book) }) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(".html")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (currentChapterIndex > 0) {
                                                onSelectChapter(currentChapterIndex - 1, 0)
                                            }
                                        },
                                        enabled = currentChapterIndex > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                                        Text("Anterior")
                                    }

                                    Text(
                                        text = "Capítulo ${currentChapterIndex + 1} de ${epub.chapters.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.text
                                    )

                                    TextButton(
                                        onClick = {
                                            if (currentChapterIndex < epub.chapters.lastIndex) {
                                                onSelectChapter(currentChapterIndex + 1, 0)
                                            }
                                        },
                                        enabled = currentChapterIndex < epub.chapters.lastIndex
                                    ) {
                                        Text("Siguiente")
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                    }
                                }

                                if (epub.chapters.size > 1) {
                                    Slider(
                                        value = currentChapterIndex.toFloat(),
                                        onValueChange = { idx ->
                                            onSelectChapter(idx.roundToInt(), 0)
                                        },
                                        valueRange = 0f..epub.chapters.lastIndex.toFloat(),
                                        steps = (epub.chapters.size - 2).coerceAtLeast(0),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            openSettings(2)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.GraphicEq,
                                            contentDescription = null,
                                            tint = if (preferences.liveEnabled) colors.accent else colors.mutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (preferences.liveEnabled) "Modo Live activo" else "Modo Live",
                                            color = if (preferences.liveEnabled) colors.accent else colors.mutedText
                                        )
                                    }
                                    TextButton(onClick = { shareEpubFile(context, book) }) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Exportar .epub")
                                    }
                                }
                            }
                        }
                    }
                }

                // Long-Press Quick Control Overlay inside Book
                LongPressQuickMenuOverlay(
                    visible = showLongPressMenu,
                    bookTitle = book.title,
                    chapterTitle = chapter.title,
                    preferences = preferences,
                    livePlaying = livePlaying,
                    onDismiss = { showLongPressMenu = false },
                    onToggleLiveEnabled = { enabled ->
                        onSetLiveEnabled(enabled)
                        if (enabled) livePlaying = true
                    },
                    onToggleLivePlay = { livePlaying = !livePlaying },
                    onSetLiveUnitMode = onSetLiveUnitMode,
                    onAdjustLiveWpm = onSetLiveWpm,
                    onAdjustLivePhraseTempo = onSetLivePhraseTempoMultiplier,
                    onSetTheme = onSetTheme,
                    onSetPageTransitionStyle = onSetPageTransitionStyle,
                    onAddBookmark = { triggerAddBookmark() },
                    onOpenToc = { openTableOfContents() },
                    onOpenFullSettings = { tab ->
                        openSettings(tab)
                    }
                )

                // #10 Editorial Ink Stamp Celebration Overlay
                ChapterCompletionStampOverlay(
                    visible = showCompletionStamp,
                    chapterTitle = chapter.title,
                    isFinalChapter = isFinalChapter,
                    pagesInChapter = totalPagesCurrentChapter,
                    onNextChapter = {
                        showCompletionStamp = false
                        if (!isFinalChapter) onSelectChapter(currentChapterIndex + 1, 0)
                    },
                    onBackToLibrary = {
                        showCompletionStamp = false
                        onBack()
                    },
                    onDismiss = { showCompletionStamp = false }
                )

                // Unified Reading Center Sheet (Apariencia • Páginas 3D • Modo Live • Efectos)
                if (showSettingsSheet) {
                    ModalBottomSheet(onDismissRequest = {
                            showSettingsSheet = false
                        }) {
                        UnifiedReaderSettingsSheet(
                            initialTab = initialSettingsTab,
                            preferences = preferences,
                            livePlaying = livePlaying,
                            onSetTheme = onSetTheme,
                            onSetFont = onSetFont,
                            onSetFontSize = onSetFontSize,
                            onSetLineSpacing = onSetLineSpacing,
                            onSetMargin = onSetMargin,
                            onSetReadingMode = onSetReadingMode,
                            onSetPageTransitionStyle = onSetPageTransitionStyle,
                            onSetPageTransitionDurationMs = onSetPageTransitionDurationMs,
                            onSetTapNavigationMode = onSetTapNavigationMode,
                            onSetTextAlign = onSetTextAlign,
                            onSetAmbientParticlesEnabled = onSetAmbientParticlesEnabled,
                            onSetLiveEnabled = onSetLiveEnabled,
                            onToggleLivePlay = {
                                // Resume from the settings sheet only after it closes.
                                livePlaying = true
                                showSettingsSheet = false
                            },
                            onRestartLivePage = { liveResetSignal++ },
                            onSetLiveUnitMode = onSetLiveUnitMode,
                            onSetLiveWpm = onSetLiveWpm,
                            onSetLivePhraseTempoMultiplier = onSetLivePhraseTempoMultiplier,
                            onSetLiveScaleFactor = onSetLiveScaleFactor,
                            onSetLiveHighlightStyle = onSetLiveHighlightStyle,
                            onSetLiveAutoTurnPages = onSetLiveAutoTurnPages,
                            onSetLivePunctuationPause = onSetLivePunctuationPause,
                            onExportEpub = { shareEpubFile(context, book) },
                            isHtmlGame = isInteractiveHtml,
                            onSetHtmlGameNotificationsEnabled = onSetHtmlGameNotificationsEnabled,
                            onSetHtmlGameFullscreenImmersive = onSetHtmlGameFullscreenImmersive,
                            onSetHtmlGameZoomPercent = onSetHtmlGameZoomPercent,
                            onSetHtmlGameMuted = onSetHtmlGameMuted,
                            onSetHtmlGameSafeSoftwareMode = onSetHtmlGameSafeSoftwareMode,
                            onSetHtmlGameOrientation = onSetHtmlGameOrientation,
                            onSetHtmlGameConfirmBackExit = onSetHtmlGameConfirmBackExit,
                            htmlBestRecord = book.gameBestRecord,
                            htmlHasSavedData = book.gameSaveJson.isNotBlank() && book.gameSaveJson != "{}",
                            onClearHtmlSaveData = { onClearHtmlGameSaveData(book.id) },
                            htmlConsoleEntries = htmlConsoleEntries,
                            onClearHtmlConsole = { htmlConsoleEntries.clear() },
                            onReloadHtmlGame = {
                                showSettingsSheet = false
                                htmlReloadSignal++
                            },
                            onTestHtmlNotification = {
                                showSettingsSheet = false
                                htmlTestNotificationSignal++
                            }
                        )
                    }
                }

                // TOC & Bookmarks Sheet
                if (showTocSheet) {
                    ModalBottomSheet(onDismissRequest = {
                        showTocSheet = false
                    }) {
                        TocAndBookmarksSheetContent(
                            epub = epub,
                            currentChapterIndex = currentChapterIndex,
                            bookmarks = bookmarks,
                            onSelectChapter = { idx ->
                                showTocSheet = false
                                onSelectChapter(idx, 0)
                            },
                            onSelectBookmark = { bm ->
                                showTocSheet = false
                                onSelectChapter(bm.chapterIndex, bm.pageIndex)
                            },
                            onDeleteBookmark = onDeleteBookmark
                        )
                    }
                }

                // Accidental Back-Exit Protection Dialog for HTML Games
                if (showHtmlExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showHtmlExitConfirm = false },
                        title = { Text("¿Salir del juego?") },
                        text = {
                            Text("Tu récord y progreso guardados permanecerán intactos en la biblioteca.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showHtmlExitConfirm = false
                                    onBack()
                                }
                            ) {
                                Text("Salir")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showHtmlExitConfirm = false }) {
                                Text("Seguir jugando")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLoadingSkeleton(colors: ReaderColors) {
    val transition = rememberInfiniteTransition(label = "readerSkeleton")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.34f, targetValue = 0.72f,
        animationSpec = infiniteRepeatable(animation = tween(850), repeatMode = RepeatMode.Reverse),
        label = "readerSkeletonAlpha"
    )
    val blockColor = colors.mutedText.copy(alpha = shimmerAlpha * 0.22f)
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 92.dp, height = 18.dp).clip(RoundedCornerShape(8.dp)).background(blockColor))
            Box(Modifier.size(42.dp).clip(CircleShape).background(blockColor))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.76f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(blockColor))
        Box(Modifier.fillMaxWidth(0.48f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(blockColor))
        Spacer(Modifier.height(18.dp))
        repeat(11) { index ->
            Box(Modifier.fillMaxWidth(if (index % 4 == 3) 0.62f else 1f)
                .height(if (index % 5 == 0) 13.dp else 16.dp)
                .clip(RoundedCornerShape(7.dp)).background(blockColor))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(blockColor))
    }
}

@Composable
private fun PaginatedEpubReaderView(
    chapter: EpubChapter,
    totalChapters: Int,
    initialPageIndex: Int,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    livePlaying: Boolean,
    liveResetSignal: Int,
    showTopToolbar: Boolean,
    onPageChanged: (Int, Int, String) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onTriggerCompletionStamp: () -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onToggleLivePlay: () -> Unit,
    onRestartLivePage: () -> Unit,
    onOpenLiveSettings: () -> Unit
) {
    val fontFamily = resolveFontFamily(preferences.readerFont)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val horizontalPaddingPx = with(density) { preferences.horizontalMarginDp.dp.roundToPx() }
        val elementSpacingPx = with(density) { 10.dp.roundToPx() }
        // Reserve full chrome (top-center Live pill when active + header + footer + safety buffer so text NEVER clips)
        val verticalChromePx = with(density) {
            (if (preferences.liveEnabled) 168.dp else 104.dp).roundToPx()
        }
        val imageMaxHeightPx = with(density) { 240.dp.roundToPx() }
        val availWidthPx = (constraints.maxWidth - horizontalPaddingPx * 2).coerceAtLeast(120)
        val availHeightPx = (constraints.maxHeight - verticalChromePx).coerceAtLeast(160)

        val pages = remember(
            chapter,
            preferences.fontSizeSp,
            preferences.lineSpacingMultiplier,
            preferences.textAlign,
            fontFamily,
            colors,
            availWidthPx,
            availHeightPx,
            elementSpacingPx
        ) {
            paginateChapterBlocks(
                blocks = chapter.blocks,
                fontFamily = fontFamily,
                fontSizeSp = preferences.fontSizeSp,
                lineSpacingMultiplier = preferences.lineSpacingMultiplier,
                textAlign = preferences.textAlign,
                colors = colors,
                textMeasurer = textMeasurer,
                maxWidthPx = availWidthPx,
                maxHeightPx = availHeightPx,
                elementSpacingPx = elementSpacingPx,
                imageReserveHeightPx = imageMaxHeightPx
            )
        }

        val pagerState = rememberPagerState(
            initialPage = initialPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            pageCount = { pages.size }
        )

        LaunchedEffect(chapter.index, pages.size) {
            val safeTarget = initialPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (pagerState.currentPage != safeTarget) {
                pagerState.scrollToPage(safeTarget)
            }
        }

        LaunchedEffect(pagerState.currentPage, pages) {
            val excerpt = pages.getOrNull(pagerState.currentPage)?.plainTextSummary()?.take(110).orEmpty()
            onPageChanged(pagerState.currentPage, pages.size, excerpt)
        }

        // Modo Live Word / Phrase Progression Engine (waits until page fully settles on screen!)
        val currentPage = pages.getOrNull(pagerState.currentPage)
        val pageTokens = remember(currentPage, preferences.liveUnitMode) {
            extractPageLiveTokens(currentPage, preferences.liveUnitMode)
        }
        var activeTokenIdx by remember(
            chapter.index,
            pagerState.currentPage,
            preferences.liveEnabled,
            preferences.liveUnitMode,
            liveResetSignal
        ) {
            mutableIntStateOf(0)
        }

        LaunchedEffect(
            preferences.liveEnabled,
            livePlaying,
            preferences.liveUnitMode,
            preferences.liveWpm,
            preferences.livePhraseTempoMultiplier,
            preferences.liveAutoTurnPages,
            preferences.livePunctuationPause,
            preferences.pageTransitionDurationMs,
            pagerState.currentPage,
            chapter.index,
            pageTokens,
            liveResetSignal
        ) {
            if (!preferences.liveEnabled || !livePlaying) return@LaunchedEffect

            // Wait until page transition animation finishes completely before advancing highlights!
            snapshotFlow {
                !pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) < 0.015f
            }.first { it }
            // Brief visual settle beat so user clearly sees the full newly displayed page first
            delay(220L)

            if (pageTokens.isEmpty()) {
                if (preferences.liveAutoTurnPages) {
                    delay(1200L)
                    if (pagerState.currentPage < pages.lastIndex) {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(
                                durationMillis = preferences.pageTransitionDurationMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    } else if (chapter.index < totalChapters - 1) {
                        onNextChapter()
                    } else {
                        onTriggerCompletionStamp()
                    }
                }
                return@LaunchedEffect
            }

            while (activeTokenIdx < pageTokens.size) {
                val token = pageTokens[activeTokenIdx]
                val stepMs = computeLiveTokenDelayMs(
                    token = token,
                    unitMode = preferences.liveUnitMode,
                    wpm = preferences.liveWpm,
                    phraseTempoMultiplier = preferences.livePhraseTempoMultiplier,
                    punctuationPause = preferences.livePunctuationPause
                )
                delay(stepMs)

                if (activeTokenIdx + 1 < pageTokens.size) {
                    activeTokenIdx++
                } else if (preferences.liveAutoTurnPages) {
                    delay(280L)
                    if (pagerState.currentPage < pages.lastIndex) {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(
                                durationMillis = preferences.pageTransitionDurationMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    } else if (chapter.index < totalChapters - 1) {
                        onNextChapter()
                    } else {
                        onTriggerCompletionStamp()
                    }
                    break
                } else {
                    break
                }
            }
        }

        val turnAnimSpec = tween<Float>(
            durationMillis = preferences.pageTransitionDurationMs,
            easing = FastOutSlowInEasing
        )

        fun goPrevPage() {
            if (pagerState.currentPage > 0) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = turnAnimSpec)
                }
            } else {
                onPreviousChapter()
            }
        }

        fun goNextPage() {
            if (pagerState.currentPage < pages.lastIndex) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = turnAnimSpec)
                }
            } else {
                onTriggerCompletionStamp()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top-Center Prettier Modo Live Pill (positioned right at the top-center)
            AnimatedVisibility(visible = preferences.liveEnabled && !showTopToolbar) {
                TopCenterLiveControlPill(
                    livePlaying = livePlaying,
                    preferences = preferences,
                    colors = colors,
                    currentStep = activeTokenIdx,
                    totalSteps = pageTokens.size,
                    onToggleLivePlay = onToggleLivePlay,
                    onRestart = {
                        activeTokenIdx = 0
                        onRestartLivePage()
                    },
                    onOpenLiveSettings = onOpenLiveSettings,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            // Running Chapter Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = preferences.horizontalMarginDp.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Cap. ${chapter.index + 1}/$totalChapters",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }

            // Pager Area with Fluid & Stabilized 3D Page Transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isVerticalMode = preferences.readingMode == ReadingMode.VERTICAL_PAGES

                val pageContent: @Composable (Int) -> Unit = { pageIdx ->
                    val page = pages.getOrNull(pageIdx) ?: ReaderPage(emptyList())
                    val isCurrentActivePage = pageIdx == pagerState.currentPage
                    val activeToken = if (preferences.liveEnabled && isCurrentActivePage) {
                        pageTokens.getOrNull(activeTokenIdx)
                    } else {
                        null
                    }
                    val prevToken = if (preferences.liveEnabled && isCurrentActivePage && activeTokenIdx > 0) {
                        pageTokens.getOrNull(activeTokenIdx - 1)
                    } else {
                        null
                    }

                    val pageOffset = pagerState.currentPageOffsetFraction(pageIdx)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                cameraDistance = 26f * this.density
                                val absOff = abs(pageOffset).coerceIn(0f, 1f)
                                when (preferences.pageTransitionStyle) {
                                    PageTransitionStyle.CURL_3D -> {
                                        if (isVerticalMode) {
                                            transformOrigin = TransformOrigin(0.5f, if (pageOffset >= 0) 0f else 1f)
                                            rotationX = (-pageOffset * 38f).coerceIn(-52f, 52f)
                                        } else {
                                            transformOrigin = TransformOrigin(if (pageOffset >= 0) 0f else 1f, 0.5f)
                                            rotationY = (pageOffset * 42f).coerceIn(-54f, 54f)
                                        }
                                        val s = 1f - absOff * 0.045f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    PageTransitionStyle.CUBE_3D -> {
                                        if (isVerticalMode) {
                                            transformOrigin = TransformOrigin(0.5f, if (pageOffset >= 0) 1f else 0f)
                                            rotationX = (-pageOffset * 58f).coerceIn(-72f, 72f)
                                        } else {
                                            transformOrigin = TransformOrigin(if (pageOffset >= 0) 1f else 0f, 0.5f)
                                            rotationY = (pageOffset * 58f).coerceIn(-72f, 72f)
                                        }
                                    }
                                    PageTransitionStyle.FLIP_3D -> {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        if (isVerticalMode) {
                                            rotationX = (-pageOffset * 56f).coerceIn(-70f, 70f)
                                        } else {
                                            rotationY = (pageOffset * 56f).coerceIn(-70f, 70f)
                                        }
                                        val s = 1f - absOff * 0.13f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    PageTransitionStyle.FAN_3D -> {
                                        transformOrigin = TransformOrigin(0.5f, 1.05f)
                                        rotationZ = pageOffset * 11f
                                        rotationY = if (isVerticalMode) 0f else pageOffset * 16f
                                        val s = 1f - absOff * 0.07f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    PageTransitionStyle.SLIDE -> {
                                        val s = 1f - absOff * 0.025f
                                        scaleX = s
                                        scaleY = s
                                    }
                                }
                            }
                            .background(colors.background)
                            .pointerInput(pageIdx, pages.size, chapter.index, preferences.tapNavigationMode) {
                                detectTapGestures(
                                    onLongPress = { onLongPress() },
                                    onTap = { tapOffset ->
                                        when (preferences.tapNavigationMode) {
                                            TapNavigationMode.VERTICAL_ZONES -> {
                                                val h = size.height
                                                when {
                                                    tapOffset.y < h * 0.26f -> goPrevPage()
                                                    tapOffset.y > h * 0.74f -> goNextPage()
                                                    else -> onToggleControls()
                                                }
                                            }
                                            TapNavigationMode.HORIZONTAL_ZONES -> {
                                                val w = size.width
                                                when {
                                                    tapOffset.x < w * 0.25f -> goPrevPage()
                                                    tapOffset.x > w * 0.75f -> goNextPage()
                                                    else -> onToggleControls()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        // Dynamic Page Fold Shadow / Highlight during 3D turns (smooth lighting, never broken)
                        if (preferences.pageTransitionStyle != PageTransitionStyle.SLIDE && abs(pageOffset) > 0.01f) {
                            val shadowAlpha = (abs(pageOffset) * 0.20f).coerceIn(0f, 0.20f)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isVerticalMode) {
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Black.copy(alpha = shadowAlpha),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = shadowAlpha * 0.45f)
                                                )
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Black.copy(alpha = shadowAlpha),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = shadowAlpha * 0.45f)
                                                )
                                            )
                                        }
                                    )
                            )
                        }

                        // Subtle spine crease
                        if (!isVerticalMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(10.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Black.copy(alpha = 0.045f), Color.Transparent)
                                        )
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.045f), Color.Transparent)
                                        )
                                    )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = preferences.horizontalMarginDp.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            page.elements.forEachIndexed { elemIdx, element ->
                                when (element) {
                                    is PageElement.TextSlice -> {
                                        val styledSlice = remember(
                                            element.text,
                                            activeToken,
                                            prevToken,
                                            elemIdx,
                                            preferences.fontSizeSp,
                                            preferences.liveScaleFactor,
                                            preferences.liveHighlightStyle,
                                            preferences.liveUnitMode,
                                            colors
                                        ) {
                                            applyLiveHighlight(
                                                source = element.text,
                                                elementIndex = elemIdx,
                                                activeToken = activeToken,
                                                prevToken = prevToken,
                                                baseFontSizeSp = preferences.fontSizeSp,
                                                liveScale = preferences.liveScaleFactor,
                                                highlightStyle = preferences.liveHighlightStyle,
                                                unitMode = preferences.liveUnitMode,
                                                colors = colors
                                            )
                                        }
                                        Text(
                                            text = styledSlice,
                                            style = TextStyle(
                                                fontFamily = fontFamily,
                                                fontSize = preferences.fontSizeSp.sp,
                                                lineHeight = (preferences.fontSizeSp * preferences.lineSpacingMultiplier).sp,
                                                color = colors.text
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is PageElement.InlineImage -> {
                                        InlineBookImage(
                                            imagePath = element.imagePath,
                                            caption = element.caption,
                                            colors = colors
                                        )
                                    }
                                    is PageElement.InteractiveDemo -> {
                                        InteractiveBookDemoView(
                                            demo = element.demo,
                                            isVisible = isCurrentActivePage,
                                            livePlaying = preferences.liveEnabled && livePlaying,
                                            onPauseLive = { if (preferences.liveEnabled && livePlaying) onToggleLivePlay() },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is PageElement.YouTubeVideo -> {
                                        YouTubeBookVideoView(
                                            video = element.video,
                                            isVisible = isCurrentActivePage,
                                            livePlaying = preferences.liveEnabled && livePlaying,
                                            onPauseLive = { if (preferences.liveEnabled && livePlaying) onToggleLivePlay() },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val smoothSnapSpec = spring<Float>(
                    dampingRatio = 0.92f,
                    stiffness = Spring.StiffnessMediumLow
                )

                if (isVerticalMode) {
                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = smoothSnapSpec
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx -> pageContent(pageIdx) }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = smoothSnapSpec
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx -> pageContent(pageIdx) }
                }
            }

            // Subtle Running Footer + Remaining Time Estimate
            val remainingPages = (pages.size - 1 - pagerState.currentPage).coerceAtLeast(0)
            val estMinutes = ((remainingPages + 1) * 140 / preferences.liveWpm.coerceAtLeast(100)).coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = preferences.horizontalMarginDp.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pág. ${pagerState.currentPage + 1}/${pages.size} · ~$estMinutes min",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (pagerState.currentPage == 0 && chapter.index > 0) {
                        Text(
                            text = "‹ Cap. anterior",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier.clickable(onClick = onPreviousChapter)
                        )
                    }
                    if (pagerState.currentPage == pages.lastIndex) {
                        Text(
                            text = if (chapter.index < totalChapters - 1) "Finalizar cap. ›" else "Finalizar libro ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier.clickable(onClick = onTriggerCompletionStamp)
                        )
                    }
                }
            }
        }
    }
}

private fun PagerState.currentPageOffsetFraction(page: Int): Float {
    return (currentPage - page) + currentPageOffsetFraction
}

@Composable
private fun InlineBookImage(
    imagePath: String,
    caption: String,
    colors: ReaderColors
) {
    val file = remember(imagePath) { File(imagePath) }
    if (!file.exists()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = file,
            contentDescription = caption.ifBlank { "Ilustración del libro" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 240.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, colors.barBorder, RoundedCornerShape(10.dp))
        )
        if (caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun paginateChapterBlocks(
    blocks: List<ReaderBlock>,
    fontFamily: FontFamily,
    fontSizeSp: Float,
    lineSpacingMultiplier: Float,
    textAlign: ReaderTextAlign,
    colors: ReaderColors,
    textMeasurer: TextMeasurer,
    maxWidthPx: Int,
    maxHeightPx: Int,
    elementSpacingPx: Int,
    imageReserveHeightPx: Int
): List<ReaderPage> {
    if (blocks.isEmpty()) return listOf(ReaderPage(emptyList()))

    val resolveAlign = if (textAlign == ReaderTextAlign.JUSTIFY) TextAlign.Justify else TextAlign.Start
    val pages = mutableListOf<ReaderPage>()
    var currentElements = mutableListOf<PageElement>()
    var remainingHeightPx = maxHeightPx

    fun flushPage() {
        if (currentElements.isNotEmpty()) {
            pages.add(ReaderPage(currentElements))
            currentElements = mutableListOf()
        }
        remainingHeightPx = maxHeightPx
    }

    fun appendAnnotatedText(text: AnnotatedString) {
        if (text.isEmpty()) return
        val layout = textMeasurer.measure(
            text = text,
            constraints = Constraints(maxWidth = maxWidthPx)
        )
        val lineCount = layout.lineCount
        if (lineCount == 0) return

        var startLine = 0
        while (startLine < lineCount) {
            val topY = layout.getLineTop(startLine)
            var endLine = startLine - 1
            for (line in startLine until lineCount) {
                if ((layout.getLineBottom(line) - topY) <= remainingHeightPx) {
                    endLine = line
                } else {
                    break
                }
            }

            if (endLine < startLine) {
                if (currentElements.isNotEmpty()) {
                    flushPage()
                    continue
                } else {
                    endLine = startLine
                }
            }

            val startOffset = layout.getLineStart(startLine)
            val endOffset = layout.getLineEnd(endLine, visibleEnd = true).coerceAtLeast(startOffset)
            val slice = text.subSequence(startOffset, endOffset)
            val usedHeight = (layout.getLineBottom(endLine) - topY).toInt()

            if (slice.text.isNotBlank()) {
                val spacingCost = if (currentElements.isNotEmpty()) elementSpacingPx else 0
                currentElements.add(PageElement.TextSlice(slice))
                remainingHeightPx = (remainingHeightPx - usedHeight - spacingCost).coerceAtLeast(0)
            }

            startLine = endLine + 1
            if (startLine < lineCount) {
                flushPage()
            }
        }
    }

    val textBatch = mutableListOf<ReaderBlock>()

    fun flushTextBatch() {
        if (textBatch.isEmpty()) return
        val annotated = buildAnnotatedString {
            textBatch.forEachIndexed { idx, block ->
                when (block) {
                    is ReaderBlock.Heading -> {
                        val sizeSp = if (block.level == 1) fontSizeSp + 7f else fontSizeSp + 3f
                        pushStyle(ParagraphStyle(lineHeight = (sizeSp * 1.3f).sp, textAlign = TextAlign.Start))
                        pushStyle(
                            SpanStyle(
                                fontFamily = LoraFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = sizeSp.sp,
                                color = colors.text
                            )
                        )
                        append(block.text)
                        pop()
                        pop()
                        if (idx < textBatch.lastIndex) append("\n\n")
                    }
                    is ReaderBlock.Paragraph -> {
                        pushStyle(
                            ParagraphStyle(
                                lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                                textAlign = resolveAlign
                            )
                        )
                        pushStyle(
                            SpanStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = fontSizeSp.sp,
                                color = colors.text
                            )
                        )
                        append(block.text)
                        pop()
                        pop()
                        if (idx < textBatch.lastIndex) append("\n\n")
                    }
                    is ReaderBlock.Quote -> {
                        pushStyle(ParagraphStyle(lineHeight = (fontSizeSp * lineSpacingMultiplier).sp))
                        pushStyle(
                            SpanStyle(
                                fontFamily = LoraFontFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = (fontSizeSp - 0.5f).sp,
                                color = colors.mutedText
                            )
                        )
                        append("“${block.text}”")
                        pop()
                        pop()
                        if (idx < textBatch.lastIndex) append("\n\n")
                    }
                    ReaderBlock.Divider -> {
                        pushStyle(ParagraphStyle(textAlign = TextAlign.Center))
                        pushStyle(SpanStyle(color = colors.mutedText, fontSize = fontSizeSp.sp))
                        append("*  *  *")
                        pop()
                        pop()
                        if (idx < textBatch.lastIndex) append("\n\n")
                    }
                    is ReaderBlock.ImageBlock, is ReaderBlock.InteractiveDemo, is ReaderBlock.YouTubeVideo -> Unit
                }
            }
        }
        appendAnnotatedText(annotated)
        textBatch.clear()
    }

    for (block in blocks) {
        if (block is ReaderBlock.InteractiveDemo) {
            flushTextBatch()
            if (currentElements.isNotEmpty()) flushPage()
            currentElements.add(PageElement.InteractiveDemo(block))
            flushPage()
        } else if (block is ReaderBlock.YouTubeVideo) {
            flushTextBatch()
            if (currentElements.isNotEmpty()) flushPage()
            currentElements.add(PageElement.YouTubeVideo(block))
            flushPage()
        } else if (block is ReaderBlock.ImageBlock) {
            flushTextBatch()
            val neededPx = imageReserveHeightPx.coerceAtMost(maxHeightPx)
            if (remainingHeightPx < neededPx && currentElements.isNotEmpty()) {
                flushPage()
            }
            val spacingCost = if (currentElements.isNotEmpty()) elementSpacingPx else 0
            currentElements.add(PageElement.InlineImage(block.imagePath, block.caption))
            remainingHeightPx = (remainingHeightPx - neededPx - spacingCost).coerceAtLeast(0)
            if (remainingHeightPx < elementSpacingPx * 4) {
                flushPage()
            }
        } else {
            textBatch.add(block)
        }
    }
    flushTextBatch()
    flushPage()

    return pages.ifEmpty { listOf(ReaderPage(emptyList())) }
}

private fun extractPhrasesFromText(raw: String, elementIndex: Int): List<LiveToken> {
    if (raw.isBlank()) return emptyList()
    val tokens = mutableListOf<LiveToken>()
    val len = raw.length
    var cursor = 0

    while (cursor < len) {
        while (cursor < len && raw[cursor].isWhitespace()) cursor++
        if (cursor >= len) break

        val start = cursor
        var wordsInPhrase = 0
        var lastCharWasSpace = true

        while (cursor < len) {
            val ch = raw[cursor]
            if (ch.isWhitespace()) {
                if (ch == '\n') {
                    break
                }
                lastCharWasSpace = true
                if (wordsInPhrase >= 22) {
                    break
                }
                cursor++
            } else {
                if (lastCharWasSpace) wordsInPhrase++
                lastCharWasSpace = false
                cursor++
                if (ch in PHRASE_PUNCT_CHARS) {
                    // Consume consecutive ellipsis/punctuation and closing quotes/parens
                    while (cursor < len && (raw[cursor] in PHRASE_PUNCT_CHARS || raw[cursor] in TRAILING_CLOSE_CHARS)) {
                        cursor++
                    }
                    break
                }
            }
        }

        var end = cursor.coerceAtMost(len)
        while (end > start && raw[end - 1].isWhitespace()) end--
        if (end > start) {
            val phrase = raw.substring(start, end)
            val wc = WORD_REGEX.findAll(phrase).count().coerceAtLeast(1)
            tokens.add(
                LiveToken(
                    elementIndex = elementIndex,
                    startInElement = start,
                    endInElement = end,
                    text = phrase,
                    wordCount = wc
                )
            )
        }
    }
    return tokens
}

private fun extractPageLiveTokens(page: ReaderPage?, unitMode: LiveUnitMode): List<LiveToken> {
    if (page == null) return emptyList()
    val tokens = mutableListOf<LiveToken>()
    page.elements.forEachIndexed { elemIdx, elem ->
        if (elem is PageElement.TextSlice) {
            if (unitMode == LiveUnitMode.PHRASE) {
                tokens.addAll(extractPhrasesFromText(elem.text.text, elemIdx))
            } else {
                WORD_REGEX.findAll(elem.text.text).forEach { match ->
                    tokens.add(
                        LiveToken(
                            elementIndex = elemIdx,
                            startInElement = match.range.first,
                            endInElement = match.range.last + 1,
                            text = match.value,
                            wordCount = 1
                        )
                    )
                }
            }
        }
    }
    return tokens
}

private fun computeLiveTokenDelayMs(
    token: LiveToken,
    unitMode: LiveUnitMode,
    wpm: Int,
    phraseTempoMultiplier: Float,
    punctuationPause: Boolean
): Long {
    val baseWordMs = (60_000.0 / wpm.coerceIn(60, 480)).coerceAtLeast(85.0)
    val t = token.text.trimEnd { it in TRAILING_CLOSE_CHARS }
    val endsSentence = t.endsWith(".") || t.endsWith("!") || t.endsWith("?") || t.endsWith("…")
    val endsClause = t.endsWith(",") || t.endsWith(";") || t.endsWith(":") || t.endsWith("—")

    return if (unitMode == LiveUnitMode.PHRASE) {
        val rawMs = token.wordCount.coerceAtLeast(1) * baseWordMs * phraseTempoMultiplier.coerceIn(0.6f, 2.4f)
        val punctBonus = if (punctuationPause) {
            when {
                endsSentence -> 550.0
                endsClause -> 280.0
                else -> 120.0
            }
        } else {
            0.0
        }
        (rawMs + punctBonus).toLong().coerceAtLeast(1050L)
    } else {
        val factor = if (punctuationPause) {
            when {
                endsSentence -> 1.75
                endsClause -> 1.35
                else -> 1.0
            }
        } else {
            1.0
        }
        (baseWordMs * factor).toLong().coerceAtLeast(85L)
    }
}

private fun applyLiveHighlight(
    source: AnnotatedString,
    elementIndex: Int,
    activeToken: LiveToken?,
    prevToken: LiveToken?,
    baseFontSizeSp: Float,
    liveScale: Float,
    highlightStyle: LiveHighlightStyle,
    unitMode: LiveUnitMode,
    colors: ReaderColors
): AnnotatedString {
    val activeForElement = activeToken?.takeIf { it.elementIndex == elementIndex }
    val prevForElement = prevToken?.takeIf { it.elementIndex == elementIndex }
    if (activeForElement == null && prevForElement == null && highlightStyle != LiveHighlightStyle.FOCUS_GLOW) return source
    if (activeToken == null && prevToken == null) return source

    // Stabilize letter/line geometry so highlighted words NEVER bounce or push wrapped lines vertically
    val gentleScale = if (unitMode == LiveUnitMode.PHRASE) {
        1f
    } else {
        (1f + (liveScale - 1f) * 0.16f).coerceIn(1f, 1.07f)
    }
    val activeSizeSp = baseFontSizeSp * gentleScale

    return buildAnnotatedString {
        append(source)
        if (highlightStyle == LiveHighlightStyle.FOCUS_GLOW) {
            addStyle(
                SpanStyle(color = colors.mutedText.copy(alpha = 0.56f)),
                start = 0,
                end = length
            )
        }
        if (prevForElement != null) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Medium,
                    color = colors.text
                ),
                start = prevForElement.startInElement.coerceIn(0, length),
                end = prevForElement.endInElement.coerceIn(0, length)
            )
        }
        if (activeForElement != null) {
            val span = when (highlightStyle) {
                LiveHighlightStyle.LIQUID_TRAIL -> SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = activeSizeSp.sp,
                    color = colors.liveWordInk,
                    background = colors.liveWordBg,
                    textDecoration = TextDecoration.Underline
                )
                LiveHighlightStyle.BOLD_LARGE -> SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = activeSizeSp.sp,
                    color = colors.liveWordInk,
                    background = colors.liveWordBg.copy(alpha = 0.52f)
                )
                LiveHighlightStyle.FOCUS_GLOW -> SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = activeSizeSp.sp,
                    color = colors.liveWordInk,
                    background = colors.liveWordBg
                )
            }
            addStyle(
                span,
                start = activeForElement.startInElement.coerceIn(0, length),
                end = activeForElement.endInElement.coerceIn(0, length)
            )
        }
    }
}

@Composable
private fun TopCenterLiveControlPill(
    livePlaying: Boolean,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    currentStep: Int,
    totalSteps: Int,
    onToggleLivePlay: () -> Unit,
    onRestart: () -> Unit,
    onOpenLiveSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eqTransition = rememberInfiniteTransition(label = "liveEq")
    val eqBar1 by eqTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val eqBar2 by eqTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val eqBar3 by eqTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq3"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = colors.barSurface,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onRestart,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = "Reiniciar lectura Live",
                            tint = colors.mutedText,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Pretty circular Play/Pause hero button in Top-Center
                    Surface(
                        color = colors.accent,
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleLivePlay)
                            .testTag("live_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = livePlaying,
                                transitionSpec = {
                                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.82f)) togetherWith
                                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.82f))
                                },
                                label = "livePlayPauseIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Pausar Modo Live" else "Continuar Modo Live",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Animated equalizer + mode/speed badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenLiveSettings)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.height(14.dp)
                        ) {
                            val heights = if (livePlaying) listOf(eqBar1, eqBar2, eqBar3) else listOf(0.35f, 0.55f, 0.35f)
                            heights.forEach { frac ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height((14f * frac).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (livePlaying) colors.accent else colors.mutedText)
                                )
                            }
                        }
                        val capsuleLabel = if (preferences.liveUnitMode == LiveUnitMode.PHRASE) {
                            "Frases · ${"%.1f".format(preferences.livePhraseTempoMultiplier)}x"
                        } else {
                            "${preferences.liveWpm} ppm"
                        }
                        Text(
                            text = capsuleLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text
                        )
                    }
                }

                if (totalSteps > 0) {
                    val progress = ((currentStep + 1).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = colors.accent,
                        trackColor = colors.barBorder.copy(alpha = 0.4f),
                        modifier = Modifier
                            .width(172.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

/**
 * Spotify / Apple-Music Lyrics-Style Phrase Stage for Modo Live ("Por frases").
 * Keeps the active phrase centered on screen, crisp and bright, while previous and upcoming phrases
 * fade to gentle transparency and soft blur.
 */
@Composable
private fun LiveLyricsStageView(
    chapter: EpubChapter,
    totalChapters: Int,
    initialProgressIndex: Int,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    livePlaying: Boolean,
    liveResetSignal: Int,
    showTopToolbar: Boolean,
    onToggleLivePlay: () -> Unit,
    onRestartLive: () -> Unit,
    onOpenLiveSettings: () -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onUpdateExcerpt: (String) -> Unit,
    onUpdateProgress: (Int, Int) -> Unit,
    onNextChapter: () -> Unit,
    onTriggerCompletionStamp: () -> Unit
) {
    val fontFamily = resolveFontFamily(preferences.readerFont)
    val listState = rememberLazyListState()

    val phrases = remember(chapter) {
        val all = mutableListOf<LiveToken>()
        chapter.blocks.forEachIndexed { blockIdx, block ->
            val str = when (block) {
                is ReaderBlock.Heading -> block.text
                is ReaderBlock.Paragraph -> block.text
                is ReaderBlock.Quote -> block.text
                else -> ""
            }
            if (str.isNotEmpty()) {
                all.addAll(extractPhrasesFromText(str, blockIdx))
            }
        }
        all
    }

    var activeIdx by remember(chapter.index, liveResetSignal) {
        mutableIntStateOf(initialProgressIndex.coerceIn(0, (phrases.size - 1).coerceAtLeast(0)))
    }

    LaunchedEffect(activeIdx, phrases.size) {
        if (phrases.isNotEmpty()) {
            onUpdateExcerpt(phrases[activeIdx.coerceIn(0, phrases.lastIndex)].text)
            onUpdateProgress(activeIdx, phrases.size)
        }
    }

    LaunchedEffect(activeIdx, phrases.size) {
        if (phrases.isNotEmpty() && activeIdx in phrases.indices) {
            listState.animateScrollToItem(index = activeIdx)
        }
    }

    LaunchedEffect(
        livePlaying,
        preferences.liveWpm,
        preferences.livePhraseTempoMultiplier,
        preferences.livePunctuationPause,
        preferences.liveAutoTurnPages,
        chapter.index,
        phrases,
        liveResetSignal
    ) {
        if (!livePlaying || phrases.isEmpty()) return@LaunchedEffect
        delay(240L)
        while (activeIdx < phrases.size) {
            val token = phrases[activeIdx]
            val stepMs = computeLiveTokenDelayMs(
                token = token,
                unitMode = LiveUnitMode.PHRASE,
                wpm = preferences.liveWpm,
                phraseTempoMultiplier = preferences.livePhraseTempoMultiplier,
                punctuationPause = preferences.livePunctuationPause
            )
            delay(stepMs)
            if (activeIdx + 1 < phrases.size) {
                activeIdx++
            } else if (preferences.liveAutoTurnPages) {
                delay(320L)
                if (chapter.index < totalChapters - 1) {
                    onNextChapter()
                } else {
                    onTriggerCompletionStamp()
                }
                break
            } else {
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onToggleControls() }
                )
            }
    ) {
        if (phrases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hay frases de texto en este capítulo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mutedText
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val centerPad = (maxHeight / 2f - 54.dp).coerceAtLeast(88.dp)
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = preferences.horizontalMarginDp.dp,
                        end = preferences.horizontalMarginDp.dp,
                        top = centerPad,
                        bottom = centerPad
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(phrases) { idx, phrase ->
                        val distance = abs(idx - activeIdx)
                        val isActive = distance == 0
                        val targetAlpha = when (distance) {
                            0 -> 1.0f
                            1 -> 0.28f
                            2 -> 0.14f
                            else -> 0.08f
                        }
                        val targetBlur = when (distance) {
                            0 -> 0.dp
                            1 -> 2.4.dp
                            2 -> 4.6.dp
                            else -> 6.5.dp
                        }
                        val targetScale = if (isActive) 1.04f else 0.94f

                        val animAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            label = "lyricsAlpha"
                        )
                        val animBlur by animateDpAsState(
                            targetValue = targetBlur,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            label = "lyricsBlur"
                        )
                        val animScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                            label = "lyricsScale"
                        )

                        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && animBlur > 0.2.dp) {
                            Modifier.blur(animBlur)
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = animAlpha
                                    scaleX = animScale
                                    scaleY = animScale
                                }
                                .then(blurModifier)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { activeIdx = idx }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = phrase.text,
                                fontFamily = fontFamily,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isActive) (preferences.fontSizeSp + 3f).coerceIn(17f, 44f).sp else (preferences.fontSizeSp - 1f).coerceAtLeast(13f).sp,
                                lineHeight = ((preferences.fontSizeSp + 3f) * preferences.lineSpacingMultiplier.coerceAtLeast(1.38f)).sp,
                                color = if (isActive) colors.text else colors.mutedText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Top-Center Live Play/Pause Control Pill (always top-center when toolbar isn't open)
        AnimatedVisibility(
            visible = !showTopToolbar,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            TopCenterLiveControlPill(
                livePlaying = livePlaying,
                preferences = preferences,
                colors = colors,
                currentStep = activeIdx,
                totalSteps = phrases.size,
                onToggleLivePlay = onToggleLivePlay,
                onRestart = {
                    activeIdx = 0
                    onRestartLive()
                },
                onOpenLiveSettings = onOpenLiveSettings
            )
        }

        // Subtle Footer chapter & phrase progress
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = preferences.horizontalMarginDp.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${chapter.title} · Cap. ${chapter.index + 1}/$totalChapters",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (phrases.isNotEmpty()) {
                Text(
                    text = "Frase ${activeIdx + 1}/${phrases.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
private fun ScrollEpubReaderView(
    chapter: EpubChapter,
    totalChapters: Int,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    livePlaying: Boolean,
    liveResetSignal: Int,
    showTopToolbar: Boolean,
    initialPageIndex: Int,
    onUpdatePage: (Int, Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onToggleLivePlay: () -> Unit,
    onOpenLiveSettings: () -> Unit,
    onUpdateExcerpt: (String) -> Unit
) {
    val fontFamily = resolveFontFamily(preferences.readerFont)
    val align = if (preferences.textAlign == ReaderTextAlign.JUSTIFY) TextAlign.Justify else TextAlign.Start
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPageIndex.coerceIn(0, (chapter.blocks.size - 1).coerceAtLeast(0))
    )
    var visibleBlockIndices by remember(chapter.index) { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(listState, chapter.index) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet() }
            .collect { visibleBlockIndices = it }
    }

    LaunchedEffect(listState, chapter.index, chapter.blocks.size) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { visibleBlockIndex ->
            onUpdatePage(visibleBlockIndex, chapter.blocks.size.coerceAtLeast(1))
        }
    }

    val scrollTokens = remember(chapter, preferences.liveUnitMode) {
        val list = mutableListOf<LiveToken>()
        chapter.blocks.forEachIndexed { blockIdx, block ->
            val str = when (block) {
                is ReaderBlock.Heading -> block.text
                is ReaderBlock.Paragraph -> block.text
                is ReaderBlock.Quote -> block.text
                else -> ""
            }
            if (str.isNotEmpty()) {
                if (preferences.liveUnitMode == LiveUnitMode.PHRASE) {
                    list.addAll(extractPhrasesFromText(str, blockIdx))
                } else {
                    WORD_REGEX.findAll(str).forEach { m ->
                        list.add(
                            LiveToken(
                                elementIndex = blockIdx,
                                startInElement = m.range.first,
                                endInElement = m.range.last + 1,
                                text = m.value,
                                wordCount = 1
                            )
                        )
                    }
                }
            }
        }
        list
    }

    var activeScrollTokenIdx by remember(
        chapter.index,
        preferences.liveEnabled,
        preferences.liveUnitMode,
        liveResetSignal
    ) {
        mutableIntStateOf(0)
    }

    val activeToken = if (preferences.liveEnabled) scrollTokens.getOrNull(activeScrollTokenIdx) else null

    // Automatically keep active word/phrase visible in Continuous Scroll mode
    LaunchedEffect(activeToken?.elementIndex, preferences.liveEnabled) {
        val targetBlock = activeToken?.elementIndex
        if (preferences.liveEnabled && targetBlock != null) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetBlock }
            if (!isVisible || targetBlock != listState.firstVisibleItemIndex) {
                listState.animateScrollToItem(index = targetBlock)
            }
        }
    }

    LaunchedEffect(
        preferences.liveEnabled,
        livePlaying,
        preferences.liveUnitMode,
        preferences.liveWpm,
        preferences.livePhraseTempoMultiplier,
        preferences.liveAutoTurnPages,
        preferences.livePunctuationPause,
        chapter.index,
        scrollTokens,
        liveResetSignal
    ) {
        if (!preferences.liveEnabled || !livePlaying || scrollTokens.isEmpty()) return@LaunchedEffect
        delay(220L)
        while (activeScrollTokenIdx < scrollTokens.size) {
            val token = scrollTokens[activeScrollTokenIdx]
            val stepMs = computeLiveTokenDelayMs(
                token = token,
                unitMode = preferences.liveUnitMode,
                wpm = preferences.liveWpm,
                phraseTempoMultiplier = preferences.livePhraseTempoMultiplier,
                punctuationPause = preferences.livePunctuationPause
            )
            delay(stepMs)
            if (activeScrollTokenIdx + 1 < scrollTokens.size) {
                activeScrollTokenIdx++
            } else {
                if (preferences.liveAutoTurnPages && chapter.index < totalChapters - 1) onNextChapter()
                break
            }
        }
    }

    LaunchedEffect(chapter.index) {
        val firstText = chapter.blocks.firstOrNull { it is ReaderBlock.Paragraph }
            ?.let { (it as ReaderBlock.Paragraph).text }
            ?: chapter.title
        onUpdateExcerpt(firstText)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = preferences.horizontalMarginDp.dp,
                end = preferences.horizontalMarginDp.dp,
                top = if (preferences.liveEnabled) 84.dp else 36.dp,
                bottom = 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = { onToggleControls() }
                    )
                }
        ) {
            itemsIndexed(chapter.blocks) { blockIdx, block ->
                val blockActiveToken = if (activeToken?.elementIndex == blockIdx) activeToken else null
                val highlightRange = blockActiveToken?.let { it.startInElement until it.endInElement }

                when (block) {
                    is ReaderBlock.Heading -> {
                        val annotated = remember(
                            block.text,
                            highlightRange,
                            preferences.fontSizeSp,
                            preferences.liveScaleFactor,
                            preferences.liveUnitMode,
                            colors
                        ) {
                            val baseSize = if (block.level == 1) preferences.fontSizeSp + 7f else preferences.fontSizeSp + 3f
                            buildLiveScrollText(
                                raw = block.text,
                                highlightRange = highlightRange,
                                baseSizeSp = baseSize,
                                scale = if (preferences.liveUnitMode == LiveUnitMode.PHRASE) 1f else preferences.liveScaleFactor,
                                colors = colors
                            )
                        }
                        Text(
                            text = annotated,
                            fontFamily = LoraFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (block.level == 1) (preferences.fontSizeSp + 7f).sp else (preferences.fontSizeSp + 3f).sp,
                            lineHeight = (preferences.fontSizeSp * 1.4f).sp,
                            color = colors.text,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    is ReaderBlock.Paragraph -> {
                        val annotated = remember(
                            block.text,
                            highlightRange,
                            preferences.fontSizeSp,
                            preferences.liveScaleFactor,
                            preferences.liveUnitMode,
                            colors
                        ) {
                            buildLiveScrollText(
                                raw = block.text,
                                highlightRange = highlightRange,
                                baseSizeSp = preferences.fontSizeSp,
                                scale = if (preferences.liveUnitMode == LiveUnitMode.PHRASE) 1f else preferences.liveScaleFactor,
                                colors = colors
                            )
                        }
                        Text(
                            text = annotated,
                            fontFamily = fontFamily,
                            fontSize = preferences.fontSizeSp.sp,
                            lineHeight = (preferences.fontSizeSp * preferences.lineSpacingMultiplier).sp,
                            color = colors.text,
                            textAlign = align
                        )
                    }
                    is ReaderBlock.Quote -> {
                        Text(
                            text = "“${block.text}”",
                            fontFamily = LoraFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = preferences.fontSizeSp.sp,
                            lineHeight = (preferences.fontSizeSp * preferences.lineSpacingMultiplier).sp,
                            color = colors.mutedText,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    is ReaderBlock.ImageBlock -> {
                        InlineBookImage(
                            imagePath = block.imagePath,
                            caption = block.caption,
                            colors = colors
                        )
                    }
                    is ReaderBlock.InteractiveDemo -> {
                        InteractiveBookDemoView(
                            demo = block,
                            isVisible = blockIdx in visibleBlockIndices,
                            livePlaying = preferences.liveEnabled && livePlaying,
                            onPauseLive = { if (preferences.liveEnabled && livePlaying) onToggleLivePlay() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is ReaderBlock.YouTubeVideo -> {
                        YouTubeBookVideoView(
                            video = block,
                            isVisible = blockIdx in visibleBlockIndices,
                            livePlaying = preferences.liveEnabled && livePlaying,
                            onPauseLive = { if (preferences.liveEnabled && livePlaying) onToggleLivePlay() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ReaderBlock.Divider -> {
                        HorizontalDivider(
                            color = colors.mutedText.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (chapter.index > 0) {
                        OutlinedButton(onClick = onPreviousChapter) { Text("Capítulo anterior") }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Button(onClick = onNextChapter) {
                        Text(if (chapter.index < totalChapters - 1) "Siguiente capítulo" else "Finalizar libro")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = preferences.liveEnabled && !showTopToolbar,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            TopCenterLiveControlPill(
                livePlaying = livePlaying,
                preferences = preferences,
                colors = colors,
                currentStep = activeScrollTokenIdx,
                totalSteps = scrollTokens.size,
                onToggleLivePlay = onToggleLivePlay,
                onRestart = { activeScrollTokenIdx = 0 },
                onOpenLiveSettings = onOpenLiveSettings
            )
        }
    }
}

private fun buildLiveScrollText(
    raw: String,
    highlightRange: IntRange?,
    baseSizeSp: Float,
    scale: Float,
    colors: ReaderColors
): AnnotatedString {
    if (highlightRange == null) return AnnotatedString(raw)
    val gentleScale = (1f + (scale - 1f) * 0.16f).coerceIn(1f, 1.07f)
    return buildAnnotatedString {
        append(raw)
        addStyle(
            SpanStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = (baseSizeSp * gentleScale).sp,
                color = colors.liveWordInk,
                background = colors.liveWordBg,
                textDecoration = TextDecoration.Underline
            ),
            start = highlightRange.first.coerceIn(0, raw.length),
            end = (highlightRange.last + 1).coerceIn(0, raw.length)
        )
    }
}

@Composable
private fun PdfOriginalReaderView(
    pdfFile: File,
    colors: ReaderColors,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit
) {
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }

    val rendererHolder = remember(pdfFile.absolutePath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
            pfd to renderer
        }.getOrNull()
    }

    DisposableEffect(rendererHolder) {
        onDispose {
            rendererHolder?.second?.close()
            rendererHolder?.first?.close()
        }
    }

    val pageBitmap by produceState<Bitmap?>(initialValue = null, currentPage, rendererHolder) {
        value = withContext(Dispatchers.IO) {
            val renderer = rendererHolder?.second ?: return@withContext null
            if (currentPage !in 0 until renderer.pageCount) return@withContext null
            renderer.openPage(currentPage).use { page ->
                val width = 1080
                val height = (width * (page.height.toFloat() / page.width.coerceAtLeast(1))).toInt()
                    .coerceIn(400, 2400)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AndroidColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onToggleControls() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Página PDF ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                CircularProgressIndicator(color = colors.accent)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text("Anterior")
            }

            Text(
                text = "PDF · Pág. ${currentPage + 1} / ${pageCount.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text
            )

            FilledTonalButton(
                onClick = { if (currentPage < pageCount - 1) currentPage++ },
                enabled = currentPage < pageCount - 1
            ) {
                Text("Siguiente")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}
