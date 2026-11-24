package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.example.data.GutendexBook
import com.example.data.SupabaseCatalogItem
import com.example.engine.AppThemeMode
import com.example.engine.BookMetadataDetector
import com.example.ui.CatalogLoadState
import com.example.ui.DiscoverUiState
import com.example.ui.components.AppThemeToggleChip
import com.example.ui.components.BookCover3DTiltContainer
import com.example.ui.components.BookCoverArt
import com.example.ui.components.LiquidInkDownloadOverlay
import com.example.ui.components.rememberDeviceTilt

@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    appThemeMode: AppThemeMode,
    onSetAppThemeMode: (AppThemeMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onTopicSelected: (String) -> Unit,
    onDownloadBook: (GutendexBook) -> Unit,
    onDownloadGame: (SupabaseCatalogItem) -> Unit,
    onOpenDownloadedBook: (String) -> Unit,
    onOpenDownloadedGame: (String) -> Unit,
    onCatalogSectionSelected: (String) -> Unit,
    onGoToOfflineLibrary: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val (sensorTiltX, sensorTiltY) = rememberDeviceTilt()
    var searchExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Explorar Catálogo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (uiState.isOnline) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (uiState.isOnline) "En línea · EPUB gratuitos" else "Sin conexión",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) keyboard?.hide()
                    },
                    modifier = Modifier.testTag("discover_search_toggle")
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchExpanded) "Ocultar buscador" else "Mostrar buscador"
                    )
                }
                AppThemeToggleChip(
                    appThemeMode = appThemeMode,
                    onToggleTheme = onSetAppThemeMode,
                    modifier = Modifier.testTag("discover_theme_toggle")
                )
            }
        }

        AnimatedVisibility(visible = uiState.downloadFeedback != null) {
            uiState.downloadFeedback?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearFeedback) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = searchExpanded) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar por título o autor (Cervantes, Verne…)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        keyboard?.hide()
                        onSearchSubmit()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Buscar")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSearchSubmit()
                }),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("discover_search_input")
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedCatalogSection == "books",
                    onClick = { onCatalogSectionSelected("books") },
                    label = { Text("Libros EPUB") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedCatalogSection == "games",
                    onClick = { onCatalogSectionSelected("games") },
                    label = { Text("GITBOOK PLAY") }
                )
            }
        }

        if (uiState.selectedCatalogSection == "books") {
            // Language + Topic Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val languages = listOf("es" to "Español", "en" to "Inglés", "" to "Todos")
                items(languages) { (code, label) ->
                    FilterChip(
                        selected = uiState.selectedLanguage == code,
                        onClick = { onLanguageSelected(code) },
                        label = { Text(label) }
                    )
                }
                val topics = listOf("fiction" to "Ficción", "adventure" to "Aventura", "philosophy" to "Filosofía", "history" to "Historia")
                items(topics) { (topic, label) ->
                    FilterChip(
                        selected = uiState.selectedTopic == topic,
                        onClick = { onTopicSelected(topic) },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (uiState.selectedCatalogSection == "games") {
            when {
                !uiState.isOnline -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Conéctate a Internet para explorar los juegos.")
                    }
                }
                uiState.gamesLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.gamesError != null -> {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No se pudo cargar el catálogo de juegos")
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.gamesError, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSearchSubmit) { Text("Reintentar") }
                    }
                }
                uiState.games.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No hay juegos para esa búsqueda todavía.")
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.games, key = { it.id }) { game ->
                            val libraryId = "lbg-${game.id}"
                            OnlineCatalogGameRow(
                                game = game,
                                isDownloaded = uiState.downloadedLibraryIds.contains(libraryId),
                                installedVersion = uiState.installedGameVersions[game.id],
                                isDownloading = uiState.downloadingGameIds.contains(game.id),
                                onDownload = { onDownloadGame(game) },
                                onOpen = { onOpenDownloadedGame(libraryId) }
                            )
                        }
                    }
                }
            }
        } else {
            when (val state = uiState.loadState) {
            CatalogLoadState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Conectando con catálogo EPUB…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            CatalogLoadState.Offline -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Modo sin conexión activo",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El catálogo en línea requiere conexión a Internet, pero todos tus libros en la Biblioteca y el Conversor funcionan 100% offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSearchSubmit) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                        Button(onClick = onGoToOfflineLibrary) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ir a Biblioteca")
                        }
                    }
                }
            }

            is CatalogLoadState.Error -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No se pudo cargar el catálogo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onSearchSubmit) {
                        Text("Reintentar")
                    }
                }
            }

            is CatalogLoadState.Success -> {
                if (state.books.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron libros EPUB para esa búsqueda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            val libraryId = "gut-${book.id}"
                            val isDownloaded = uiState.downloadedLibraryIds.contains(libraryId)
                            val isDownloading = uiState.downloadingIds.contains(book.id)

                            OnlineCatalogBookRow(
                                book = book,
                                sensorTiltX = sensorTiltX,
                                sensorTiltY = sensorTiltY,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                onDownload = { onDownloadBook(book) },
                                onOpen = { onOpenDownloadedBook(libraryId) }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun OnlineCatalogBookRow(
    book: GutendexBook,
    sensorTiltX: Float,
    sensorTiltY: Float,
    isDownloaded: Boolean,
    installedVersion: String?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    val langLabel = BookMetadataDetector.mapLanguageCode(book.languages.firstOrNull())
    val topicLabel = BookMetadataDetector.resolveGenre(book.subjects, book.title, emptyList())

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.75.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover3DTiltContainer(
                sensorTiltX = sensorTiltX,
                sensorTiltY = sensorTiltY
            ) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (!book.coverImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = book.coverImageUrl,
                            contentDescription = "Portada de ${book.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        BookCoverArt(
                            title = book.title,
                            author = book.authorDisplay,
                            originalFormat = "EPUB",
                            isConverted = false,
                            coverPath = null,
                            showFormatBadge = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isDownloading) {
                        LiquidInkDownloadOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = langLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = topicLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "EPUB · ${book.downloadCount} descargas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloaded) {
                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Leer", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EPUB", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}


@Composable
private fun OnlineCatalogGameRow(
    game: SupabaseCatalogItem,
    isDownloaded: Boolean,
    installedVersion: String?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().border(0.75.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 104.dp, height = 126.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                if (!game.coverPath.isNullOrBlank()) {
                    AsyncImage(model = game.coverPath, contentDescription = "Portada de ${game.title}",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎮", style = MaterialTheme.typography.headlineLarge)
                        Text("GITBOOK", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("PLAY", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (game.description.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(game.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(7.dp))
                if (installedVersion != null && installedVersion != game.gameVersion) {
                    Text("Actualización disponible · v${game.gameVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    if (game.releaseNotes.isNotBlank()) {
                        Text(game.releaseNotes, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                } else if (installedVersion != null) {
                    Text("Instalado · v${installedVersion}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                } else {
                    Text("Versión ${game.gameVersion}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text("${game.fileFormat.uppercase()} · ${game.languageCode?.uppercase() ?: "Juego"} · ${game.downloadCount} descargas",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (isDownloaded && installedVersion == game.gameVersion) {
                    OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Jugar")
                    }
                } else {
                    Button(onClick = onDownload, enabled = !isDownloading,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                        if (isDownloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isDownloading) "Preparando" else if (isDownloaded) "Actualizar" else "Instalar")
                    }
                }
            }
        }
    }
}
