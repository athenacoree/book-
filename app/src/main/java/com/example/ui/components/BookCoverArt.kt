package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.compose.AsyncImage
import com.example.data.BookEntity
import com.example.data.isHtmlGame
import com.example.ui.theme.LoraFontFamily
import java.io.File
import kotlin.math.abs

private data class LinenPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color
)

private val linenPalettes = listOf(
    LinenPalette(Color(0xFF5C2B29), Color(0xFF3A1816), Color(0xFFE7B874)), // Burgundy & Gold
    LinenPalette(Color(0xFF234237), Color(0xFF142821), Color(0xFFD6BE8A)), // Forest Sage
    LinenPalette(Color(0xFF24344D), Color(0xFF162030), Color(0xFFE2C07D)), // Midnight Navy
    LinenPalette(Color(0xFF6B4226), Color(0xFF422715), Color(0xFFF0D29C)), // Saddle Leather
    LinenPalette(Color(0xFF43324D), Color(0xFF281D2F), Color(0xFFE5C496)), // Deep Plum
    LinenPalette(Color(0xFF2E383C), Color(0xFF1B2124), Color(0xFFD8B982))  // Slate Charcoal
)

@Composable
fun BookCoverArt(
    title: String,
    author: String,
    originalFormat: String,
    isConverted: Boolean,
    coverPath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    showFormatBadge: Boolean = true
) {
    val shape = RoundedCornerShape(cornerRadius)
    val context = LocalContext.current
    val coverImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    val coverModel: Any? = coverPath?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
        if (path.startsWith("https://", ignoreCase = true)) path else File(path).takeIf { it.exists() }
    }

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .border(0.75.dp, Color.Black.copy(alpha = 0.14f), shape)
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = "Portada de $title",
                imageLoader = coverImageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle book spine highlight on left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            val palette = linenPalettes[abs(title.hashCode()) % linenPalettes.size]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(palette.top, palette.bottom))
                    )
            ) {
                // Book spine crease
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Embossed inner frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp)
                        .border(0.75.dp, palette.accent.copy(alpha = 0.38f), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(palette.accent)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = title,
                                fontFamily = LoraFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFFFAF6F0),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (showFormatBadge) {
            val badgeText = if (isConverted && !originalFormat.equals("EPUB", ignoreCase = true)) {
                "$originalFormat → EPUB"
            } else {
                originalFormat.uppercase()
            }
            Surface(
                color = Color(0xCC1C1917),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFFFAF6F0),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun shareEpubFile(context: Context, book: BookEntity) {
    val htmlOriginal = book.originalPath?.takeIf { book.isHtmlGame && it.isNotBlank() && File(it).exists() }
    val isHtml = htmlOriginal != null
    val file = if (htmlOriginal != null) File(htmlOriginal) else File(book.epubPath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (isHtml) "text/html" else "application/epub+zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, if (isHtml) "${book.title}.html" else "${book.title}.epub")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, if (isHtml) "Compartir juego HTML" else "Compartir libro EPUB")
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        "${kb.toInt().coerceAtLeast(1)} KB"
    } else {
        String.format("%.1f MB", kb / 1024.0)
    }
}
