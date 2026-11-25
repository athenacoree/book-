package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import com.example.data.isHtmlGame
import com.example.engine.AppThemeMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.example.engine.EpubParser
import com.example.engine.ReaderBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.data.BookEntity
import com.example.engine.ReaderTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Provides gentle real device accelerometer tilt angles (in degrees) for 3D parallax book covers.
 */
@Composable
fun rememberDeviceTilt(): Pair<Float, Float> {
    val context = LocalContext.current
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accel == null) {
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val ay = ((event.values[1] - 4.5f) / 9.81f).coerceIn(-1f, 1f)
                    tiltY = -ax * 8f
                    tiltX = ay * 6f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val smoothX by animateFloatAsState(targetValue = tiltX, animationSpec = spring(), label = "tiltX")
    val smoothY by animateFloatAsState(targetValue = tiltY, animationSpec = spring(), label = "tiltY")
    return smoothX to smoothY
}

/**
 * #4 Interactive 3D Cover Tilt + Passive Finger-Slide Spotlight Wrapper.
 * Uses PointerEventPass.Initial WITHOUT consuming events so LazyVerticalGrid scrolling stays 100% smooth.
 */
@Composable
fun BookCover3DTiltContainer(
    sensorTiltX: Float,
    sensorTiltY: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var isTouched by remember { mutableStateOf(false) }
    var normX by remember { mutableFloatStateOf(0.5f) }
    var normY by remember { mutableFloatStateOf(0.5f) }

    val touchScale by animateFloatAsState(
        targetValue = if (isTouched) 1.045f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "touchScale"
    )
    val touchTiltX by animateFloatAsState(
        targetValue = if (isTouched) ((0.5f - normY) * 14f) else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "touchTiltX"
    )
    val touchTiltY by animateFloatAsState(
        targetValue = if (isTouched) ((normX - 0.5f) * 14f) else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "touchTiltY"
    )
    val spotlightAlpha by animateFloatAsState(
        targetValue = if (isTouched) 0.34f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "spotlightAlpha"
    )

    val finalRotX = (sensorTiltX + touchTiltX).coerceIn(-11f, 11f)
    val finalRotY = (sensorTiltY + touchTiltY).coerceIn(-11f, 11f)

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pos = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Press,
                            PointerEventType.Move,
                            PointerEventType.Enter -> {
                                val w = boxSize.width.coerceAtLeast(1).toFloat()
                                val h = boxSize.height.coerceAtLeast(1).toFloat()
                                if (pos != null && pos.x in -12f..(w + 12f) && pos.y in -12f..(h + 12f)) {
                                    isTouched = true
                                    normX = (pos.x / w).coerceIn(0f, 1f)
                                    normY = (pos.y / h).coerceIn(0f, 1f)
                                } else if (event.type == PointerEventType.Move) {
                                    isTouched = false
                                }
                            }
                            PointerEventType.Release,
                            PointerEventType.Exit -> {
                                isTouched = false
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = touchScale
                scaleY = touchScale
                rotationX = finalRotX
                rotationY = finalRotY
                cameraDistance = 22f * this.density
            }
    ) {
        content()

        // Diagonal Satin Light Sheen reactive to 3D angle + radial finger spotlight
        val sheenShift = (finalRotY / 8f) * 0.42f
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        0.0f to Color.Transparent,
                        (0.38f + sheenShift).coerceIn(0.05f, 0.85f) to Color.White.copy(alpha = 0.14f),
                        (0.55f + sheenShift).coerceIn(0.15f, 0.95f) to Color.Transparent
                    )
                )
        )

        if (spotlightAlpha > 0.01f && boxSize.width > 0 && boxSize.height > 0) {
            val center = Offset(boxSize.width * normX, boxSize.height * normY)
            val radius = (maxOf(boxSize.width, boxSize.height) * 0.72f).coerceAtLeast(80f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF8E1).copy(alpha = spotlightAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        )
                    )
            )
        }
    }
}

/**
 * #2 Two-stage 3D Book Preview & Opening Transition:
 * Stage 1: Library behind blurs out; book + full extracted metadata (detected language, editable genre,
 * estimated pages, word count, reading time, images, publisher/year, synopsis) is presented in the center.
 * Stage 2: User taps the book -> opaque stage rises (zero library bleed-through) and cover swings open into Reader.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookOpening3DOverlay(
    book: BookEntity?,
    onDismissPreview: () -> Unit,
    onUpdateGenre: (BookEntity, String) -> Unit = { _, _ -> },
    onAnimationFinished: () -> Unit
) {
    if (book == null) return

    androidx.activity.compose.BackHandler(onBack = onDismissPreview)

    var isExpanding by remember(book.id) { mutableStateOf(false) }
    var showGenreEditor by remember(book.id) { mutableStateOf(false) }
    var localGenre by remember(book.id, book.genre) { mutableStateOf(book.genre) }
    var expandDescription by remember(book.id) { mutableStateOf(false) }
    var previewPageIndex by remember(book.id) { mutableStateOf(-1) }
    var previewPages by remember(book.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(book.id, book.epubPath) {
        previewPageIndex = -1
        previewPages = withContext(Dispatchers.IO) {
            if (book.isHtmlGame) emptyList() else runCatching {
                val parsed = EpubParser.parse(File(book.epubPath))
                val text = parsed.chapters.flatMap { chapter ->
                    chapter.blocks.mapNotNull { block ->
                        when (block) {
                            is ReaderBlock.Heading -> block.text
                            is ReaderBlock.Paragraph -> block.text
                            is ReaderBlock.Quote -> block.text
                            else -> null
                        }
                    }
                }.joinToString("\n\n")
                text.chunked(720).map { it.trim() }.filter { it.isNotBlank() }.take(80)
            }.getOrDefault(emptyList())
        }
    }
    val entryProgress = remember(book.id) { Animatable(0f) }
    val openProgress = remember(book.id) { Animatable(0f) }

    LaunchedEffect(book.id) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(book.id, isExpanding) {
        if (isExpanding) {
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
            onAnimationFinished()
        }
    }

    val e = entryProgress.value.coerceIn(0f, 1f)
    val o = openProgress.value.coerceIn(0f, 1f)

    // The catalog is blurred by LibraryScreen, but the overlay curtain stays fully opaque.
    // This prevents any book cards or scrolling content from bleeding through the preview.
    val fullPaperRevealAlpha = if (!isExpanding) 0f else ((o - 0.55f) / 0.35f).coerceIn(0f, 1f)

    val cardScale = if (!isExpanding) {
        0.88f + 0.12f * e
    } else {
        1.0f + o * 1.45f
    }

    val coverFlipAngle = if (!isExpanding) 0f else -(o * 112f)
    val coverFrontAlpha = when {
        coverFlipAngle > -76f -> 1f
        coverFlipAngle > -94f -> 1f - ((-coverFlipAngle - 76f) / 18f)
        else -> 0f
    }.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                enabled = !isExpanding,
                onClick = onDismissPreview
            ),
        contentAlignment = Alignment.Center
    ) {
        if (fullPaperRevealAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = fullPaperRevealAlpha))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .graphicsLayer { alpha = e }
        ) {
            // Centered 3D Book Object
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .height(254.dp)
                    .pointerInput(book.id, previewPages.size) {
                        var accumulatedDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragEnd = {
                                if (kotlin.math.abs(accumulatedDrag) > 42f && previewPages.isNotEmpty()) {
                                    previewPageIndex = if (accumulatedDrag < 0f) {
                                        (previewPageIndex + 1).coerceAtMost(previewPages.lastIndex)
                                    } else {
                                        (previewPageIndex - 1).coerceAtLeast(-1)
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                accumulatedDrag += dragAmount
                                change.consume()
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        cameraDistance = 28f * this.density
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        enabled = !isExpanding
                    ) {
                        isExpanding = true
                    }
            ) {
                Surface(
                    color = if (book.isHtmlGame) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (book.isHtmlGame) {
                            Icon(
                                imageVector = Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (book.isHtmlGame) Color(0xFFFAF6F0) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (book.isHtmlGame) "Juego HTML · Pantalla completa" else book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (book.isHtmlGame) Color(0xFFFDE68A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (previewPageIndex >= 0 && previewPageIndex < previewPages.size) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = book.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2
                        )
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = previewPages[previewPageIndex],
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 14.sp,
                            maxLines = 13,
                            overflow = TextOverflow.Clip
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Página " + (previewPageIndex + 1) + " de " + previewPages.size + " · desliza para hojear",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (coverFrontAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = coverFlipAngle
                                cameraDistance = 28f * this.density
                                alpha = coverFrontAlpha
                            }
                    ) {
                        BookCoverArt(
                            title = book.title,
                            author = book.author,
                            originalFormat = book.originalFormat,
                            isConverted = book.isConvertedFromOtherFormat,
                            coverPath = book.coverPath,
                            showFormatBadge = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (previewPages.isNotEmpty() && previewPageIndex == -1) {
                Text(
                    text = "Desliza sobre la portada para hojear las páginas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Full Extracted Metadata & Interactive Genre Panel
            val promptAlpha = if (!isExpanding) e else (1f - o * 5f).coerceIn(0f, 1f)
            if (promptAlpha > 0.01f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .graphicsLayer { alpha = promptAlpha }
                ) {
                    if (book.isHtmlGame) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.24f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "JUEGO INTERACTIVO · HTML",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Glass Metadata Sheet
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* absorb clicks so tapping details doesn't accidentally dismiss */ }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Language + Editable Genre Pills
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Language,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Idioma: ${book.language}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(0xFFD97706).copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(0.75.dp, Color(0xFFFBBF24).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                        .clickable { showGenreEditor = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "Género: $localGenre",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFFDE68A),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Editar género",
                                            tint = Color(0xFFFDE68A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            if (book.isHtmlGame) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    PreviewStatBadge(
                                        label = "Formato",
                                        value = "HTML5"
                                    )
                                    PreviewStatBadge(
                                        label = "Récord",
                                        value = book.gameBestRecord.ifBlank { "Sin récord" }
                                    )
                                    PreviewStatBadge(
                                        label = "Notif.",
                                        value = "Activas"
                                    )
                                    PreviewStatBadge(
                                        label = "Tamaño",
                                        value = formatFileSize(book.fileSizeBytes)
                                    )
                                }
                            } else {
                                // Pages, Chapters, Reading Time, Word Count, Images
                                val estReadMinutes = ((book.wordCount.coerceAtLeast(200)) / 200).coerceAtLeast(1)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    PreviewStatBadge(
                                        label = "Páginas",
                                        value = "~${book.estimatedPages.coerceAtLeast(1)}"
                                    )
                                    PreviewStatBadge(
                                        label = "Capítulos",
                                        value = "${book.totalChapters}"
                                    )
                                    PreviewStatBadge(
                                        label = "Palabras",
                                        value = formatCompactCount(book.wordCount)
                                    )
                                    PreviewStatBadge(
                                        label = "Lectura",
                                        value = "~${estReadMinutes}m"
                                    )
                                }
                            }

                            val hasSavedGameData = book.gameSaveJson.isNotBlank() && book.gameSaveJson != "{}"
                            val extraMeta = buildList {
                                if (book.isHtmlGame) {
                                    add("Juego ejecutable HTML")
                                    add(if (hasSavedGameData) "Partida guardada lista" else "Sin partida guardada")
                                } else {
                                    if (book.totalImages > 0) add("${book.totalImages} ilustraciones")
                                    if (book.publisher.isNotBlank()) add(book.publisher)
                                    if (book.publicationYear.isNotBlank()) add(book.publicationYear)
                                    add(formatFileSize(book.fileSizeBytes))
                                }
                            }
                            if (extraMeta.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            book.isHtmlGame -> Icons.Filled.NotificationsActive
                                            book.totalImages > 0 -> Icons.Filled.Image
                                            else -> Icons.Filled.Schedule
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFFB8AFA3),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = extraMeta.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCFC7BC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (book.description.isNotBlank()) {
                                Text(
                                    text = book.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE7DFD3),
                                    maxLines = if (expandDescription) 8 else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { expandDescription = !expandDescription }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { isExpanding = true },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (book.isHtmlGame) Icons.Filled.SportsEsports else Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                book.isHtmlGame -> "Toca para jugar"
                                book.progressPercent > 0 -> "Continuar leyendo (${book.progressPercent}%)"
                                else -> "Toca para abrir libro"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onDismissPreview,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Volver a la biblioteca",
                            color = Color(0xFFE7DFD3)
                        )
                    }
                }
            }
        }
    }

    if (showGenreEditor) {
        var genreDraft by remember(book.id, localGenre) { mutableStateOf(localGenre) }
        val presets = if (book.isHtmlGame) {
            listOf(
                "Mini Juego HTML",
                "Arcade",
                "Puzzle",
                "Aventura",
                "Estrategia",
                "Trivia",
                "Simulación",
                "RPG"
            )
        } else {
            listOf(
                "Novela clásica",
                "Aventura",
                "Fantasía",
                "Ciencia ficción",
                "Misterio",
                "Romance",
                "Historia",
                "Filosofía y ensayo",
                "Poesía",
                "Ciencia y técnica",
                "Terror"
            )
        }
        AlertDialog(
            onDismissRequest = { showGenreEditor = false },
            title = { Text(if (book.isHtmlGame) "Editar categoría del juego" else "Editar género del libro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = genreDraft,
                        onValueChange = { genreDraft = it },
                        label = { Text(if (book.isHtmlGame) "Categoría o género" else "Género literario") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = genreDraft.equals(preset, ignoreCase = true),
                                onClick = { genreDraft = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = genreDraft.trim().ifBlank { "Lectura general" }
                        localGenre = cleaned
                        onUpdateGenre(book, cleaned)
                        showGenreEditor = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenreEditor = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PreviewStatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB8AFA3)
        )
    }
}

private fun formatCompactCount(count: Int): String {
    return when {
        count <= 0 -> "—"
        count >= 1000 -> "${"%.1f".format(count / 1000f)}k"
        else -> count.toString()
    }
}

/**
 * #5 Animated 2D/3D Book-Binding Conversion Graphic for ConverterScreen.
 */
@Composable
fun ConverterBookBinding3DAnimation(
    isConverting: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "converter3d")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConverting) 1800 else 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.72f
        val cy = h * 0.54f

        // Floating incoming document sheets spiraling into the bound EPUB
        val sheetCount = 5
        for (i in 0 until sheetCount) {
            val phase = (orbit + i / sheetCount.toFloat()) % 1f
            val startX = w * 0.14f + phase * (cx - w * 0.18f)
            val waveY = cy - sin(phase * PI.toFloat()) * (h * 0.28f) + (i - 2) * 8f
            val sheetScale = 1f - phase * 0.35f
            val sheetW = 34f * sheetScale
            val sheetH = 46f * sheetScale
            val angle = (1f - phase) * 28f - 10f
            val alpha = (sin(phase * PI.toFloat())).coerceIn(0.15f, 0.92f)

            rotate(degrees = angle, pivot = Offset(startX, waveY)) {
                drawRoundRect(
                    color = Color(0xFFFAF6F0).copy(alpha = alpha),
                    topLeft = Offset(startX - sheetW / 2f, waveY - sheetH / 2f),
                    size = Size(sheetW, sheetH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Mini lines on floating page
                for (line in 0..2) {
                    drawLine(
                        color = Color(0xFFB45309).copy(alpha = alpha * 0.55f),
                        start = Offset(startX - sheetW * 0.3f, waveY - sheetH * 0.2f + line * 9f),
                        end = Offset(startX + sheetW * 0.3f, waveY - sheetH * 0.2f + line * 9f),
                        strokeWidth = 2.2f
                    )
                }
            }
        }

        // Bound Golden EPUB Book with 3D pulse
        val pulse = 1f + sin(orbit * 2f * PI.toFloat()) * (if (isConverting) 0.06f else 0.025f)
        val bookW = 76f * pulse
        val bookH = 102f * pulse

        // Glowing halo behind EPUB
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFF59E0B).copy(alpha = if (isConverting) 0.45f else 0.24f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = bookH * 0.95f
            ),
            radius = bookH * 0.95f,
            center = Offset(cx, cy)
        )

        // Book pages thickness edge (3D depth)
        drawRoundRect(
            color = Color(0xFFE7DEC8),
            topLeft = Offset(cx - bookW / 2f + 6f, cy - bookH / 2f + 6f),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(10f, 10f)
        )

        // Book front cover
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFD97706), Color(0xFF92400E)),
                start = Offset(cx - bookW / 2f, cy - bookH / 2f),
                end = Offset(cx + bookW / 2f, cy + bookH / 2f)
            ),
            topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(10f, 10f)
        )

        // Spine highlight
        drawRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(cx - bookW / 2f + 6f, cy - bookH / 2f + 4f),
            size = Size(4f, bookH - 8f)
        )

        // Progress arc when converting
        if (isConverting) {
            drawArc(
                color = Color(0xFFFDE68A),
                startAngle = -90f,
                sweepAngle = 360f * progressFraction.coerceIn(0.05f, 1f),
                useCenter = false,
                topLeft = Offset(cx - bookH * 0.65f, cy - bookH * 0.65f),
                size = Size(bookH * 1.3f, bookH * 1.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
            )
        }
    }
}

/**
 * #6 Elastic 2D Bookmark Ribbon dropping from top edge with spring physics.
 */
@Composable
fun ElasticBookmarkRibbon(
    isBookmarked: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val dropProgress by animateFloatAsState(
        targetValue = if (isBookmarked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bookmarkDrop"
    )

    if (dropProgress <= 0.02f) return

    val infinite = rememberInfiniteTransition(label = "ribbonSway")
    val sway by infinite.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 54.dp.toPx() }

    Canvas(
        modifier = modifier
            .width(26.dp)
            .height(62.dp)
            .offset { IntOffset(0, ((-1f + dropProgress) * maxOffsetPx).roundToInt()) }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationZ = sway * dropProgress
            }
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - 14f)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(accentColor, accentColor.copy(alpha = 0.85f))
            )
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(4f, 0f),
            end = Offset(4f, h - 6f),
            strokeWidth = 2f
        )
    }
}

/**
 * #8 2D Liquid Ink Wave Download Progress Overlay for DiscoverScreen.
 */
@Composable
fun LiquidInkDownloadOverlay(
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "liquidInk")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val level by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * (1f - level)
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                val y = baseY + sin(x * 0.08f + wavePhase) * 7f
                lineTo(x, y)
                x += 6f
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xD9D97706),
                    Color(0xE692400E)
                )
            )
        )
    }
}

/**
 * #9 Subtle Ambient 2D Particles Layer for Reader Background.
 */
@Composable
fun AmbientReaderParticles(
    theme: ReaderTheme,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "ambientParticles")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    val isDark = theme == ReaderTheme.NIGHT || theme == ReaderTheme.BLACK
    val particleColor = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309)
    val maxAlpha = if (isDark) 0.18f else 0.09f

    val seeds = remember {
        List(16) { idx ->
            Triple(
                ((idx * 67 + 19) % 100) / 100f,
                ((idx * 43 + 31) % 100) / 100f,
                2.5f + (idx % 4) * 1.4f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        seeds.forEachIndexed { i, (sx, sy, radius) ->
            val driftY = (sy - phase * (0.4f + (i % 3) * 0.15f) + 1f) % 1f
            val driftX = (sx + sin(phase * 2f * PI + i).toFloat() * 0.03f + 1f) % 1f
            val alpha = maxAlpha * (0.5f + 0.5f * cos(phase * 2f * PI + i * 0.7).toFloat())
            drawCircle(
                color = particleColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(driftX * w, driftY * h)
            )
        }
    }
}

/**
 * #10 Editorial Ink Stamp Celebration Overlay when reaching the end of a chapter or book.
 */
@Composable
fun ChapterCompletionStampOverlay(
    visible: Boolean,
    chapterTitle: String,
    isFinalChapter: Boolean,
    pagesInChapter: Int,
    onNextChapter: () -> Unit,
    onBackToLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFinalChapter) Icons.Filled.EmojiEvents else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isFinalChapter) "¡Libro completado!" else "Capítulo completado",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$pagesInChapter páginas leídas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (!isFinalChapter) {
                        Button(
                            onClick = onNextChapter,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continuar al siguiente capítulo")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Button(
                            onClick = onBackToLibrary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Volver a la Biblioteca")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seguir en esta página")
                    }
                }
            }
        }
    }
}

/**
 * Animated Claro / Oscuro header pill toggle for outer Library and Catalog screens.
 */
@Composable
fun AppThemeToggleChip(
    appThemeMode: AppThemeMode,
    onToggleTheme: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val sysDark = isSystemInDarkTheme()
    val isDarkEffective = when (appThemeMode) {
        AppThemeMode.SYSTEM -> sysDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable {
                onToggleTheme(if (isDarkEffective) AppThemeMode.LIGHT else AppThemeMode.DARK)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            AnimatedContent(targetState = isDarkEffective, label = "themeIcon") { dark ->
                Icon(
                    imageVector = if (dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    contentDescription = if (dark) "Modo oscuro activo" else "Modo claro activo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (isDarkEffective) "Oscuro" else "Claro",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
