package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookmarkEntity
import com.example.data.ReaderPreferences
import com.example.engine.LiveHighlightStyle
import com.example.engine.LiveUnitMode
import com.example.engine.PageTransitionStyle
import com.example.engine.ParsedEpub
import com.example.engine.ReaderFont
import com.example.engine.ReaderTextAlign
import com.example.engine.ReaderTheme
import com.example.engine.ReadingMode
import com.example.engine.TapNavigationMode
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.LoraFontFamily
import com.example.ui.theme.MerriweatherFontFamily
import com.example.ui.theme.NunitoFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.ReaderBlackBg
import com.example.ui.theme.ReaderBlackInk
import com.example.ui.theme.ReaderNightBg
import com.example.ui.theme.ReaderNightInk
import com.example.ui.theme.ReaderPaperBg
import com.example.ui.theme.ReaderPaperInk
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaInk
import com.example.ui.theme.ReaderWhiteBg
import com.example.ui.theme.ReaderWhiteInk
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReaderColors(
    val background: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    val barSurface: Color,
    val barBorder: Color,
    val liveWordInk: Color,
    val liveWordBg: Color
)

fun resolveReaderColors(theme: ReaderTheme): ReaderColors = when (theme) {
    ReaderTheme.WHITE -> ReaderColors(
        background = ReaderWhiteBg,
        text = ReaderWhiteInk,
        mutedText = Color(0xFF646464),
        accent = Color(0xFFB45309),
        barSurface = Color(0xF7FFFFFF),
        barBorder = Color(0xFFE5E5E5),
        liveWordInk = Color(0xFF000000),
        liveWordBg = Color(0x33F59E0B)
    )
    ReaderTheme.PAPER -> ReaderColors(
        background = ReaderPaperBg,
        text = ReaderPaperInk,
        mutedText = Color(0xFF736B60),
        accent = Color(0xFFB45309),
        barSurface = Color(0xF7FAF6F0),
        barBorder = Color(0xFFE5DDD0),
        liveWordInk = Color(0xFF0E0B08),
        liveWordBg = Color(0x3DF59E0B)
    )
    ReaderTheme.SEPIA -> ReaderColors(
        background = ReaderSepiaBg,
        text = ReaderSepiaInk,
        mutedText = Color(0xFF7A654F),
        accent = Color(0xFF9A4612),
        barSurface = Color(0xF7FBF3E4),
        barBorder = Color(0xFFDCCCAE),
        liveWordInk = Color(0xFF1F1308),
        liveWordBg = Color(0x40C2592B)
    )
    ReaderTheme.NIGHT -> ReaderColors(
        background = ReaderNightBg,
        text = ReaderNightInk,
        mutedText = Color(0xFF9A9186),
        accent = Color(0xFFF59E0B),
        barSurface = Color(0xF7262320),
        barBorder = Color(0xFF3A352F),
        liveWordInk = Color(0xFFFFFFFF),
        liveWordBg = Color(0x4DF59E0B)
    )
    ReaderTheme.BLACK -> ReaderColors(
        background = ReaderBlackBg,
        text = ReaderBlackInk,
        mutedText = Color(0xFF9E9E9E),
        accent = Color(0xFFFBBF24),
        barSurface = Color(0xF7121212),
        barBorder = Color(0xFF2B2B2B),
        liveWordInk = Color(0xFFFFFFFF),
        liveWordBg = Color(0x59FBBF24)
    )
}

fun resolveFontFamily(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.LITERATA -> LiterataFontFamily
    ReaderFont.LORA -> LoraFontFamily
    ReaderFont.MERRIWEATHER -> MerriweatherFontFamily
    ReaderFont.SANS -> PlusJakartaFontFamily
    ReaderFont.NUNITO -> NunitoFontFamily
}

/**
 * Floating Long-Press Quick Control Overlay shown when user presses and holds inside a book.
 */
@Composable
fun LongPressQuickMenuOverlay(
    visible: Boolean,
    bookTitle: String,
    chapterTitle: String,
    preferences: ReaderPreferences,
    livePlaying: Boolean,
    onDismiss: () -> Unit,
    onToggleLiveEnabled: (Boolean) -> Unit,
    onToggleLivePlay: () -> Unit,
    onSetLiveUnitMode: (LiveUnitMode) -> Unit,
    onAdjustLiveWpm: (Int) -> Unit,
    onAdjustLivePhraseTempo: (Float) -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetPageTransitionStyle: (PageTransitionStyle) -> Unit,
    onAddBookmark: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenFullSettings: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("long_press_quick_menu")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Menú rápido de lectura",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$bookTitle · $chapterTitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar menú rápido")
                        }
                    }

                    // Modo Live Quick Box
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text("Modo Live", style = MaterialTheme.typography.titleSmall)
                                        val unitLabel = if (preferences.liveUnitMode == LiveUnitMode.PHRASE) {
                                            "Frases · ${"%.1f".format(preferences.livePhraseTempoMultiplier)}x"
                                        } else {
                                            "${preferences.liveWpm} ppm"
                                        }
                                        Text(
                                            text = "$unitLabel · Auto-página",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = preferences.liveEnabled,
                                    onCheckedChange = onToggleLiveEnabled
                                )
                            }

                            if (preferences.liveEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                // Word vs Phrase selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiveUnitMode.entries.forEach { unitMode ->
                                        val selected = preferences.liveUnitMode == unitMode
                                        Surface(
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onSetLiveUnitMode(unitMode) }
                                        ) {
                                            Text(
                                                text = unitMode.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                textAlign = TextAlign.Center,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (preferences.liveUnitMode == LiveUnitMode.PHRASE) {
                                        OutlinedButton(
                                            onClick = {
                                                onAdjustLivePhraseTempo((preferences.livePhraseTempoMultiplier - 0.2f).coerceIn(0.6f, 2.4f))
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Más rápido")
                                        }
                                        FilledTonalIconButton(onClick = onToggleLivePlay) {
                                            Icon(
                                                imageVector = if (livePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (livePlaying) "Pausar Live" else "Iniciar Live"
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onAdjustLivePhraseTempo((preferences.livePhraseTempoMultiplier + 0.2f).coerceIn(0.6f, 2.4f))
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Más tiempo")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onAdjustLiveWpm(preferences.liveWpm - 30) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("−30 ppm")
                                        }
                                        FilledTonalIconButton(onClick = onToggleLivePlay) {
                                            Icon(
                                                imageVector = if (livePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (livePlaying) "Pausar Live" else "Iniciar Live"
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onAdjustLiveWpm(preferences.liveWpm + 30) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("+30 ppm")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Quick Color Themes
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Color de pantalla", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderTheme.entries.forEach { theme ->
                                val rc = resolveReaderColors(theme)
                                val selected = preferences.readerTheme == theme
                                Surface(
                                    color = rc.background,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else rc.barBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSetTheme(theme) }
                                ) {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = rc.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick 3D Transition Picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Efecto 3D al pasar página", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageTransitionStyle.entries.forEach { style ->
                                val selected = preferences.pageTransitionStyle == style
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSetPageTransitionStyle(style) }
                                ) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onAddBookmark()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Marcador", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenToc()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Índice", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenFullSettings(0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Centro de lectura completo")
                    }
                }
            }
        }
    }
}

/**
 * Unified 4-tab Reading Center Sheet (Apariencia • Páginas 3D • Modo Live • Efectos).
 */
@Composable
fun UnifiedReaderSettingsSheet(
    initialTab: Int,
    preferences: ReaderPreferences,
    livePlaying: Boolean,
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
    onToggleLivePlay: () -> Unit,
    onRestartLivePage: () -> Unit,
    onSetLiveUnitMode: (LiveUnitMode) -> Unit,
    onSetLiveWpm: (Int) -> Unit,
    onSetLivePhraseTempoMultiplier: (Float) -> Unit,
    onSetLiveScaleFactor: (Float) -> Unit,
    onSetLiveHighlightStyle: (LiveHighlightStyle) -> Unit,
    onSetLiveAutoTurnPages: (Boolean) -> Unit,
    onSetLivePunctuationPause: (Boolean) -> Unit,
    onExportEpub: () -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Centro de lectura",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Apariencia") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Páginas 3D") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Modo Live") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Efectos") })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 310.dp, max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // 1. Themes (Blanco, Papel, Sepia, Noche, Negro)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Color de página", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderTheme.entries.forEach { theme ->
                                val c = resolveReaderColors(theme)
                                val selected = preferences.readerTheme == theme
                                Surface(
                                    color = c.background,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(50.dp)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else c.barBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSetTheme(theme) }
                                        .testTag("theme_${theme.name.lowercase()}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = theme.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = c.text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Font Style Picker
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estilo de letra", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFont.entries.forEach { font ->
                                val selected = preferences.readerFont == font
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetFont(font) }
                                        .testTag("font_${font.name.lowercase()}")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Aa",
                                            fontFamily = resolveFontFamily(font),
                                            fontSize = 18.sp,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = font.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Font Size (12..40 pt)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tamaño en pantalla", style = MaterialTheme.typography.labelMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSetFontSize(preferences.fontSizeSp - 2f) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text("A−") }
                                Text(
                                    text = "${preferences.fontSizeSp.toInt()} pt",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OutlinedButton(
                                    onClick = { onSetFontSize(preferences.fontSizeSp + 2f) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text("A+") }
                            }
                        }
                        Slider(
                            value = preferences.fontSizeSp,
                            onValueChange = { onSetFontSize(it.roundToInt().toFloat()) },
                            valueRange = 12f..40f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Margins & Line Spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val margins = listOf(10 to "Pantalla llena", 22 to "Clásico", 34 to "Amplio")
                        margins.forEach { (dpVal, label) ->
                            val selected = abs(preferences.horizontalMarginDp - dpVal) <= 3
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSetMargin(dpVal) }
                            ) {
                                Text(
                                    text = "Margen\n$label",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val spacings = listOf(1.35f to "Compacto", 1.6f to "Equilibrado", 1.9f to "Abierto")
                        spacings.forEach { (valFloat, label) ->
                            val selected = abs(preferences.lineSpacingMultiplier - valFloat) < 0.1f
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSetLineSpacing(valFloat) }
                            ) {
                                Text(
                                    text = "Líneas\n$label",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Page Layout Direction
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Disposición de páginas", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modeOptions = listOf(
                                Triple(ReadingMode.HORIZONTAL_PAGES, Icons.Filled.SwapHoriz, "Lado a lado"),
                                Triple(ReadingMode.VERTICAL_PAGES, Icons.Filled.SwapVert, "Arriba / Abajo"),
                                Triple(ReadingMode.CONTINUOUS_SCROLL, Icons.Filled.ViewStream, "Continuo")
                            )
                            modeOptions.forEach { (mode, icon, label) ->
                                val selected = preferences.readingMode == mode
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSetReadingMode(mode) }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                    ) {
                                        Icon(icon, contentDescription = null)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3D Page Transition Effect
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Animación 3D entre páginas", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageTransitionStyle.entries.forEach { style ->
                                val selected = preferences.pageTransitionStyle == style
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetPageTransitionStyle(style) }
                                ) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Page Turn Smoothness / Duration
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Fluidez al pasar página", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val speeds = listOf(300 to "Ágil", 460 to "Fluida", 640 to "Suave cine")
                            speeds.forEach { (ms, label) ->
                                val selected = abs(preferences.pageTransitionDurationMs - ms) <= 60
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSetPageTransitionDurationMs(ms) }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Tap Navigation Zones
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Toque en pantalla para pasar página", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TapNavigationMode.entries.forEach { tapMode ->
                                val selected = preferences.tapNavigationMode == tapMode
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetTapNavigationMode(tapMode) }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = tapMode.displayName,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (tapMode == TapNavigationMode.VERTICAL_ZONES) {
                                                "Arriba: anterior · Abajo: siguiente"
                                            } else {
                                                "Izq: anterior · Der: siguiente"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Text Alignment
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Alineación del texto", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val aligns = listOf(
                                Triple(ReaderTextAlign.JUSTIFY, Icons.Filled.FormatAlignJustify, "Justificado"),
                                Triple(ReaderTextAlign.LEFT, Icons.AutoMirrored.Filled.FormatAlignLeft, "Izquierda")
                            )
                            aligns.forEach { (alignVal, icon, label) ->
                                val selected = preferences.textAlign == alignVal
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetTextAlign(alignVal) }
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Enriched Modo Live Tab
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Activar Modo Live", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Lectura guiada fluida que resalta cada palabra y pasa páginas automáticamente",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.liveEnabled,
                                onCheckedChange = onSetLiveEnabled,
                                modifier = Modifier.testTag("live_mode_switch")
                            )
                        }
                    }

                    // Play / Pause / Restart Row
                    if (preferences.liveEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onToggleLivePlay,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (livePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (livePlaying) "Pausar Live" else "Reanudar Live")
                            }
                            OutlinedButton(onClick = onRestartLivePage) {
                                Text("Reiniciar pág.")
                            }
                        }
                    }

                    // Word vs Phrase Unit Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Resaltado en Modo Live", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiveUnitMode.entries.forEach { unitMode ->
                                val selected = preferences.liveUnitMode == unitMode
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetLiveUnitMode(unitMode) }
                                        .testTag("live_unit_${unitMode.name.lowercase()}")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = unitMode.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (unitMode == LiveUnitMode.WORD) {
                                                "Palabra a palabra"
                                            } else {
                                                "Frase hasta puntuación"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Speed Slider + Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Velocidad base", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${preferences.liveWpm} palabras/min",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = preferences.liveWpm.toFloat(),
                            onValueChange = { onSetLiveWpm(it.roundToInt()) },
                            valueRange = 60f..480f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(120 to "Relax", 180 to "Natural", 260 to "Fluido", 380 to "Turbo")
                            presets.forEach { (wpmVal, label) ->
                                val selected = abs(preferences.liveWpm - wpmVal) <= 20
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSetLiveWpm(wpmVal) }
                                ) {
                                    Text(
                                        text = "$label\n$wpmVal",
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Phrase Dwell Time Adjustment (highlighted when in PHRASE mode)
                    if (preferences.liveUnitMode == LiveUnitMode.PHRASE) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tiempo extra para leer cada frase", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "${"%.1f".format(preferences.livePhraseTempoMultiplier)}x",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = preferences.livePhraseTempoMultiplier,
                                onValueChange = {
                                    val snapped = (it * 10f).roundToInt() / 10f
                                    onSetLivePhraseTempoMultiplier(snapped)
                                },
                                valueRange = 0.6f..2.4f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val tempos = listOf(0.8f to "Ágil", 1.15f to "Cómodo", 1.5f to "Pausado", 2.0f to "Estudio")
                                tempos.forEach { (mult, label) ->
                                    val selected = abs(preferences.livePhraseTempoMultiplier - mult) < 0.12f
                                    Surface(
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSetLivePhraseTempoMultiplier(mult) }
                                    ) {
                                        Text(
                                            text = "$label\n${"%.1f".format(mult)}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Visual Highlight Style
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estilo visual del modo Live", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiveHighlightStyle.entries.forEach { style ->
                                val selected = preferences.liveHighlightStyle == style
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSetLiveHighlightStyle(style) }
                                ) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Active Word Scale (in Word mode)
                    if (preferences.liveUnitMode == LiveUnitMode.WORD) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tamaño de la palabra activa", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val scales = listOf(1.12f to "Sutil", 1.24f to "Grande", 1.36f to "Extra")
                                scales.forEach { (scaleVal, label) ->
                                    val selected = abs(preferences.liveScaleFactor - scaleVal) < 0.05f
                                    Surface(
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSetLiveScaleFactor(scaleVal) }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Auto-turn page & Natural punctuation pause switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pasar página automáticamente", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Cambia a la página o capítulo siguiente al llegar al final",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.liveAutoTurnPages,
                            onCheckedChange = onSetLiveAutoTurnPages
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pausa natural en puntuación", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Respira brevemente en comas y puntos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.livePunctuationPause,
                            onCheckedChange = onSetLivePunctuationPause
                        )
                    }
                }

                3 -> {
                    // Ambient 2D Particles & Export
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Ambiente 2D animado", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Partículas suaves cálidas o nocturnas según el color de página",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = preferences.ambientParticlesEnabled,
                                onCheckedChange = onSetAmbientParticlesEnabled
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onExportEpub,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir / Exportar libro .epub")
                    }
                }
            }
        }
    }
}

@Composable
fun TocAndBookmarksSheetContent(
    epub: ParsedEpub,
    currentChapterIndex: Int,
    bookmarks: List<BookmarkEntity>,
    onSelectChapter: (Int) -> Unit,
    onSelectBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Índice (${epub.chapters.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Marcadores (${bookmarks.size})") }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(epub.chapters) { idx, ch ->
                    val isCurrent = idx == currentChapterIndex
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectChapter(idx) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        } else {
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tienes marcadores guardados en este libro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(bookmarks) { _, bm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectBookmark(bm) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${bm.chapterTitle} · Pág. ${bm.pageIndex + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bm.excerpt,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Eliminar marcador"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
