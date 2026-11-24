package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import com.example.data.isHtmlGame
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BookEntity
import com.example.engine.AppThemeMode
import com.example.ui.LibraryUiState
import com.example.ui.components.BookCover3DTiltContainer
import com.example.ui.components.BookCoverArt
import com.example.ui.components.BookOpening3DOverlay
import com.example.ui.components.formatFileSize
import com.example.ui.components.rememberDeviceTilt
import com.example.ui.components.shareEpubFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onOpenBook: (BookEntity) -> Unit,
    onImportClicked: () -> Unit,
    onToggleLayout: () -> Unit,
    onSetAppThemeMode: (AppThemeMode) -> Unit,
    onToggleFavorite: (BookEntity) -> Unit,
    onUpdateBookGenre: (BookEntity, String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBookForMenu by remember { mutableStateOf<BookEntity?>(null) }
    var openingBookId by remember { mutableStateOf<String?>(null) }
    val openingBook = remember(openingBookId, uiState.allBooks) {
        openingBookId?.let { id -> uiState.allBooks.firstOrNull { it.id == id } }
    }
    val (sensorTiltX, sensorTiltY) = rememberDeviceTilt()
    val backdropBlur by animateDpAsState(
        targetValue = if (openingBook != null) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "libraryBlur"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier)
        ) {
            // Conversion Progress / Completion / Error Banners
            AnimatedVisibility(visible = uiState.activeConversion != null) {
                uiState.activeConversion?.let { conv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Convirtiendo ${conv.sourceFormat.label} → EPUB",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${conv.fileName} · ${conv.stageMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { conv.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.lastConvertedBook != null) {
                uiState.lastConvertedBook?.let { book ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (book.isHtmlGame) "¡Juego HTML listo para jugar!" else "¡Listo en formato EPUB!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                onDismissBanner()
                                onOpenBook(book)
                            }) {
                                Text(if (book.isHtmlGame) "Jugar" else "Leer")
                            }
                            IconButton(onClick = onDismissBanner) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.conversionError != null) {
                uiState.conversionError?.let { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissBanner) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                }
            }

            val displayBooks = uiState.allBooks
            if (displayBooks.isEmpty()) {
                EmptyLibraryState(
                    isSearching = false,
                    onImportClicked = onImportClicked,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else if (uiState.isGridLayout) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 120.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    uiState.continueReadingBook?.let { featured ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueReadingHeroCard(
                                book = featured,
                                sensorTiltX = sensorTiltX,
                                sensorTiltY = sensorTiltY,
                                onOpen = { openingBookId = featured.id }
                            )
                        }
                    }

                    items(displayBooks, key = { it.id }) { book ->
                        BookGridItem(
                            book = book,
                            sensorTiltX = sensorTiltX,
                            sensorTiltY = sensorTiltY,
                            onClick = { openingBookId = book.id },
                            onMoreClick = { selectedBookForMenu = book }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayBooks, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            onClick = { openingBookId = book.id },
                            onMoreClick = { selectedBookForMenu = book }
                        )
                    }
                }
            }
        }

        // Appearance and layout controls moved to the lower edge
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 5.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                val sysDark = isSystemInDarkTheme()
                val isDarkEffective = when (uiState.appThemeMode) {
                    AppThemeMode.SYSTEM -> sysDark
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                }
                IconButton(onClick = { onSetAppThemeMode(if (isDarkEffective) AppThemeMode.LIGHT else AppThemeMode.DARK) }, modifier = Modifier.testTag("library_theme_toggle")) {
                    Icon(if (isDarkEffective) Icons.Filled.DarkMode else Icons.Filled.LightMode, contentDescription = "Cambiar apariencia", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onToggleLayout, modifier = Modifier.testTag("toggle_layout_button")) {
                    Icon(if (uiState.isGridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, contentDescription = "Cambiar cuadrícula o lista")
                }
            }
        }

        // Primary Import FAB
        FloatingActionButton(
            onClick = onImportClicked,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("import_book_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = "Importar",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // #2 Blurred Book Preview + Full Metadata + Opaque 3D Book Opening Transition Overlay
        BookOpening3DOverlay(
            book = openingBook,
            onDismissPreview = { openingBookId = null },
            onUpdateGenre = onUpdateBookGenre,
            onAnimationFinished = {
                val target = openingBook
                if (target != null) {
                    onOpenBook(target)
                    scope.launch {
                        delay(380)
                        openingBookId = null
                    }
                }
            }
        )
    }

    // Book Action Bottom Sheet
    selectedBookForMenu?.let { book ->
        ModalBottomSheet(onDismissRequest = { selectedBookForMenu = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCoverArt(
                        title = book.title,
                        author = book.author,
                        originalFormat = book.originalFormat,
                        isConverted = book.isConvertedFromOtherFormat,
                        coverPath = book.coverPath,
                        showFormatBadge = false,
                        modifier = Modifier.width(56.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${book.author} · ${book.totalChapters} cap. · ${formatFileSize(book.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                SheetActionRow(
                    icon = if (book.isHtmlGame) Icons.Filled.SportsEsports else Icons.AutoMirrored.Filled.MenuBook,
                    label = if (book.isHtmlGame) "Jugar en pantalla completa (HTML)" else "Leer en formato libro EPUB",
                    onClick = {
                        selectedBookForMenu = null
                        onOpenBook(book)
                    }
                )
                SheetActionRow(
                    icon = Icons.Filled.Share,
                    label = if (book.isHtmlGame) "Compartir / Exportar juego .html" else "Compartir / Exportar archivo .epub (iPhone / Apple Books)",
                    onClick = {
                        selectedBookForMenu = null
                        shareEpubFile(context, book)
                    }
                )
                SheetActionRow(
                    icon = if (book.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (book.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    onClick = {
                        selectedBookForMenu = null
                        onToggleFavorite(book)
                    }
                )
                SheetActionRow(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Eliminar de la biblioteca",
                    isDestructive = true,
                    onClick = {
                        selectedBookForMenu = null
                        onDeleteBook(book)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ContinueReadingHeroCard(
    book: BookEntity,
    sensorTiltX: Float,
    sensorTiltY: Float,
    onOpen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("continue_reading_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BookCover3DTiltContainer(
                    sensorTiltX = sensorTiltX,
                    sensorTiltY = sensorTiltY
                ) {
                    BookCoverArt(
                        title = book.title,
                        author = book.author,
                        originalFormat = book.originalFormat,
                        isConverted = book.isConvertedFromOtherFormat,
                        coverPath = book.coverPath,
                        modifier = Modifier.width(86.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (book.lastReadTimestamp > 0) "CONTINUAR LEYENDO" else "LECTURA DESTACADA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = book.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = book.genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                book.isHtmlGame -> "Juego HTML5 · Pantalla completa"
                                book.progressPercent > 0 -> "${book.progressPercent}% leído"
                                else -> "~${book.estimatedPages} págs. · ${book.totalChapters} cap."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpen,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (book.isHtmlGame) Icons.Filled.SportsEsports else Icons.Filled.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (book.isHtmlGame) "Jugar" else "Leer", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookGridItem(
    book: BookEntity,
    sensorTiltX: Float,
    sensorTiltY: Float,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.75.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                BookCover3DTiltContainer(
                    sensorTiltX = sensorTiltX,
                    sensorTiltY = sensorTiltY
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
                        BookCoverArt(
                            title = book.title,
                            author = book.author,
                            originalFormat = book.originalFormat,
                            isConverted = book.isConvertedFromOtherFormat,
                            coverPath = book.coverPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (book.isFavorite) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Favorito",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opciones del libro",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = book.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = book.genre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (book.progressPercent > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { book.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_list_item_${book.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverArt(
                title = book.title,
                author = book.author,
                originalFormat = book.originalFormat,
                isConverted = book.isConvertedFromOtherFormat,
                coverPath = book.coverPath,
                showFormatBadge = false,
                modifier = Modifier.width(62.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                book.isHtmlGame -> "HTML"
                                book.isConvertedFromOtherFormat -> "${book.originalFormat} → EPUB"
                                else -> "EPUB"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${book.totalChapters} cap. · ${formatFileSize(book.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isSearching: Boolean,
    onImportClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_library_1795491459614),
            contentDescription = "Ilustración de biblioteca",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isSearching) "Sin resultados" else "Tu estantería está lista",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSearching) {
                "Prueba con otro término o limpia los filtros."
            } else {
                "Importa PDF, MOBI, EPUB, FB2 o TXT. Todos se convierten automáticamente al formato libro EPUB."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(onClick = onImportClicked) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importar primer libro")
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
