package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.DiscoverViewModel
import com.example.ui.LibraryViewModel
import com.example.ui.ReaderViewModel
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.LuminaBookTheme

private object Routes {
    const val LIBRARY = "library"
    const val CONVERTER = "converter"
    const val DISCOVER = "discover"
    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/${Uri.encode(bookId)}"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    TopLevelTab(
        route = Routes.LIBRARY,
        label = "Biblioteca",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        testTag = "nav_tab_library"
    ),
    TopLevelTab(
        route = Routes.CONVERTER,
        label = "Conversor",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_converter"
    ),
    TopLevelTab(
        route = Routes.DISCOVER,
        label = "Explorar",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_tab_discover"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val incomingUri = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent {
            LuminaBookApp(incomingFileUri = incomingUri)
        }
    }
}

@Composable
fun LuminaBookApp(incomingFileUri: Uri? = null) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel()
    val discoverViewModel: DiscoverViewModel = viewModel()
    val readerViewModel: ReaderViewModel = viewModel()

    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()

    var showStartup by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1450)
        showStartup = false
    }

    LuminaBookTheme(appThemeMode = libraryState.appThemeMode) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
    val isReaderActive = currentRoute?.startsWith("reader/") == true

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importBooks(uris)
        }
    }

    val singleConvertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            libraryViewModel.importBooks(listOf(uri)) { convertedBook ->
                navController.navigate(Routes.reader(convertedBook.id))
            }
        }
    }

    val supportedMimeTypes = arrayOf(
        "application/epub+zip",
        "application/pdf",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/octet-stream",
        "text/plain",
        "text/html",
        "application/xml",
        "*/*"
    )

    LaunchedEffect(incomingFileUri) {
        if (incomingFileUri != null) {
            libraryViewModel.importBooks(listOf(incomingFileUri)) { book ->
                navController.navigate(Routes.reader(book.id))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && !isReaderActive

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!isReaderActive && !useRail) {
                    AnimatedFloatingBottomBar(
                        tabs = topLevelTabs,
                        currentRoute = currentRoute,
                        onSelectTab = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(Routes.LIBRARY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.LIBRARY) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.LIBRARY,
                    enterTransition = { fadeIn(animationSpec = tween(260)) },
                    exitTransition = { fadeOut(animationSpec = tween(260)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(240)) },
                    popExitTransition = { fadeOut(animationSpec = tween(240)) },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            uiState = libraryState,
                            onOpenBook = { book ->
                                navController.navigate(Routes.reader(book.id))
                            },
                            onImportClicked = {
                                filePickerLauncher.launch(supportedMimeTypes)
                            },
                            onToggleLayout = libraryViewModel::toggleLayoutMode,
                            onSetAppThemeMode = libraryViewModel::setAppThemeMode,
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            onUpdateBookGenre = { book, genre ->
                                libraryViewModel.updateBookGenre(book.id, genre)
                            },
                            onDeleteBook = libraryViewModel::deleteBook,
                            onDismissBanner = libraryViewModel::dismissConversionBanner
                        )
                    }

                    composable(Routes.CONVERTER) {
                        ConverterScreen(
                            uiState = libraryState,
                            onPickFileToConvert = {
                                singleConvertLauncher.launch(supportedMimeTypes)
                            },
                            onOpenBook = { book ->
                                navController.navigate(Routes.reader(book.id))
                            }
                        )
                    }

                    composable(Routes.DISCOVER) {
                        DiscoverScreen(
                            uiState = discoverState,
                            appThemeMode = libraryState.appThemeMode,
                            onSetAppThemeMode = libraryViewModel::setAppThemeMode,
                            onSearchQueryChange = discoverViewModel::setSearchQuery,
                            onSearchSubmit = discoverViewModel::refreshCatalog,
                            onLanguageSelected = discoverViewModel::setLanguage,
                            onTopicSelected = discoverViewModel::setTopic,
                            onDownloadBook = { book ->
                                discoverViewModel.downloadBook(book)
                            },
                            onDownloadGame = { game ->
                                discoverViewModel.downloadGame(game)
                            },
                            onOpenDownloadedBook = { libraryId ->
                                navController.navigate(Routes.reader(libraryId))
                            },
                            onOpenDownloadedGame = { libraryId ->
                                navController.navigate(Routes.reader(libraryId))
                            },
                            onCatalogSectionSelected = discoverViewModel::setCatalogSection,
                            onGoToOfflineLibrary = {
                                navController.navigate(Routes.LIBRARY) {
                                    popUpTo(Routes.LIBRARY) { inclusive = true }
                                }
                            },
                            onClearFeedback = discoverViewModel::clearFeedback
                        )
                    }

                    composable(
                        route = Routes.READER,
                        arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                    ) { entry ->
                        val bookId = Uri.decode(entry.arguments?.getString("bookId").orEmpty())
                        LaunchedEffect(bookId) {
                            readerViewModel.loadBook(bookId)
                        }

                        val loadState by readerViewModel.loadState.collectAsStateWithLifecycle()
                        val prefs by readerViewModel.preferences.collectAsStateWithLifecycle()
                        val chapterIndex by readerViewModel.currentChapterIndex.collectAsStateWithLifecycle()
                        val pageIndex by readerViewModel.currentPageIndex.collectAsStateWithLifecycle()
                        val bookmarks by readerViewModel.bookmarks.collectAsStateWithLifecycle()
                        val isPdfOriginal by readerViewModel.isPdfOriginalMode.collectAsStateWithLifecycle()

                        ReaderScreen(
                            loadState = loadState,
                            preferences = prefs,
                            currentChapterIndex = chapterIndex,
                            currentPageIndex = pageIndex,
                            bookmarks = bookmarks,
                            isPdfOriginalMode = isPdfOriginal,
                            onBack = { navController.popBackStack() },
                            onSelectChapter = readerViewModel::selectChapter,
                            onUpdatePage = readerViewModel::updatePage,
                            onAddBookmark = readerViewModel::addBookmark,
                            onDeleteBookmark = readerViewModel::deleteBookmark,
                            onTogglePdfMode = readerViewModel::togglePdfOriginalMode,
                            onSetTheme = readerViewModel::setTheme,
                            onSetFont = readerViewModel::setFont,
                            onSetFontSize = readerViewModel::setFontSize,
                            onSetLineSpacing = readerViewModel::setLineSpacing,
                            onSetMargin = readerViewModel::setMargin,
                            onSetReadingMode = readerViewModel::setReadingMode,
                            onSetPageTransitionStyle = readerViewModel::setPageTransitionStyle,
                            onSetPageTransitionDurationMs = readerViewModel::setPageTransitionDurationMs,
                            onSetTapNavigationMode = readerViewModel::setTapNavigationMode,
                            onSetTextAlign = readerViewModel::setTextAlign,
                            onSetAmbientParticlesEnabled = readerViewModel::setAmbientParticlesEnabled,
                            onSetLiveEnabled = readerViewModel::setLiveEnabled,
                            onSetLiveUnitMode = readerViewModel::setLiveUnitMode,
                            onSetLiveWpm = readerViewModel::setLiveWpm,
                            onSetLivePhraseTempoMultiplier = readerViewModel::setLivePhraseTempoMultiplier,
                            onSetLiveScaleFactor = readerViewModel::setLiveScaleFactor,
                            onSetLiveHighlightStyle = readerViewModel::setLiveHighlightStyle,
                            onSetLiveAutoTurnPages = readerViewModel::setLiveAutoTurnPages,
                            onSetLivePunctuationPause = readerViewModel::setLivePunctuationPause,
                            onSetHtmlGameNotificationsEnabled = readerViewModel::setHtmlGameNotificationsEnabled,
                            onSetHtmlGameFullscreenImmersive = readerViewModel::setHtmlGameFullscreenImmersive,
                            onSetHtmlGameZoomPercent = readerViewModel::setHtmlGameZoomPercent,
                            onSetHtmlGameMuted = readerViewModel::setHtmlGameMuted,
                            onSetHtmlGameSafeSoftwareMode = readerViewModel::setHtmlGameSafeSoftwareMode,
                            onSetHtmlGameOrientation = readerViewModel::setHtmlGameOrientation,
                            onSetHtmlGameConfirmBackExit = readerViewModel::setHtmlGameConfirmBackExit,
                            onSaveHtmlGameState = readerViewModel::saveHtmlGameState,
                            onClearHtmlGameSaveData = readerViewModel::clearHtmlGameSaveData
                        )
                    }
                }
            }
        }
        }
        if (showStartup) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("G", style = MaterialTheme.typography.displayLarge,
                        color = Color(0xFF18A9B8), fontWeight = FontWeight.Black)
                    Text("GITBOOK", style = MaterialTheme.typography.headlineLarge,
                        color = Color(0xFF172033), fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(12.dp))
                    Text("Estamos creando tu experiencia",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF526174), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(22.dp))
                    CircularProgressIndicator(color = Color(0xFF18A9B8), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun AnimatedFloatingBottomBar(
    tabs: List<TopLevelTab>,
    currentRoute: String?,
    onSelectTab: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(260),
                        label = "tabPillColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(220),
                        label = "tabContentColor"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabIconScale"
                    )
                    val iconBounceY by animateFloatAsState(
                        targetValue = if (selected) -1.5f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabIconBounce"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab.route) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                            translationY = iconBounceY * density
                                        }
                                )
                                AnimatedVisibility(
                                    visible = selected,
                                    enter = fadeIn(tween(180)) + expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ),
                                    exit = fadeOut(tween(140)) + shrinkHorizontally()
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (selected) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
