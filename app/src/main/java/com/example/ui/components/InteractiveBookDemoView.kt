package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.engine.InteractiveDemoPackage
import com.example.engine.ReaderBlock
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Extracts and runs a publisher-bundled example only while its block is visible.
 * Each demo uses a virtual local origin; it has no file access or network access.
 */
@Composable
fun InteractiveBookDemoView(
    demo: ReaderBlock.InteractiveDemo,
    isVisible: Boolean,
    livePlaying: Boolean,
    onPauseLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(isVisible, livePlaying) {
        if (isVisible && livePlaying) onPauseLive()
    }

    val extractionState by produceState(
        initialValue = false to null as InteractiveDemoPackage.ExtractedBundle?,
        demo.id,
        demo.sourceEpubPath,
        demo.entryPath,
        demo.cacheDirPath,
        isVisible
    ) {
        value = if (!isVisible || demo.cacheDirPath.isBlank()) {
            false to null
        } else {
            true to InteractiveDemoPackage.extract(
                sourceEpub = File(demo.sourceEpubPath),
                entryPath = demo.entryPath,
                cacheDir = File(demo.cacheDirPath),
                demoId = demo.id
            )
        }
    }
    val extractedBundle = extractionState.second
    val virtualHost = remember(demo.sourceEpubPath, demo.id) {
        "demo-${(demo.sourceEpubPath + "|" + demo.id).hashCode().toUInt()}.gitbook.local"
    }
    var activeWebView by remember(demo.id, demo.entryPath) { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = demo.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            if (isVisible && extractedBundle != null) {
                val bundle = extractedBundle
                DisposableEffect(demo.id, bundle.entryFile.absolutePath) {
                    onDispose {
                        activeWebView?.apply {
                            stopLoading()
                            loadUrl("about:blank")
                            destroy()
                        }
                        activeWebView = null
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            setBackgroundColor(AndroidColor.WHITE)
                            overScrollMode = WebView.OVER_SCROLL_NEVER
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                allowFileAccessFromFileURLs = false
                                allowUniversalAccessFromFileURLs = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mediaPlaybackRequiresUserGesture = true
                                loadsImagesAutomatically = true
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            val canonicalRoot = bundle.rootDir.canonicalFile
                            val virtualPrefix = "/${demo.id}/"
                            fun resolveLocalAsset(rawPath: String?): File? {
                                if (rawPath.isNullOrBlank()) return null
                                return runCatching {
                                    val decoded = android.net.Uri.decode(rawPath)
                                    if (!decoded.startsWith(virtualPrefix)) return@runCatching null
                                    val relative = decoded.removePrefix(virtualPrefix)
                                    if (relative.isBlank() || relative.split('/').any { it == ".." || it.isBlank() }) {
                                        return@runCatching null
                                    }
                                    val candidate = File(canonicalRoot, relative).canonicalFile
                                    if (candidate.path.startsWith(canonicalRoot.path + File.separator) && candidate.isFile) candidate else null
                                }.getOrNull()
                            }
                            fun mimeType(file: File): String {
                                return when (file.extension.lowercase()) {
                                    "html", "htm" -> "text/html"
                                    "css" -> "text/css"
                                    "js", "mjs" -> "application/javascript"
                                    "json" -> "application/json"
                                    "svg" -> "image/svg+xml"
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "webp" -> "image/webp"
                                    "gif" -> "image/gif"
                                    "woff" -> "font/woff"
                                    "woff2" -> "font/woff2"
                                    "ttf" -> "font/ttf"
                                    "otf" -> "font/otf"
                                    else -> "application/octet-stream"
                                }
                            }
                            fun blockedResponse(status: Int = 403) = WebResourceResponse(
                                "text/plain", "UTF-8", status,
                                if (status == 404) "Not found" else "Blocked by GITBOOK",
                                emptyMap(), ByteArrayInputStream(ByteArray(0))
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    val uri = request.url
                                    return !(uri.scheme == "https" && uri.host == virtualHost &&
                                        uri.path.orEmpty().startsWith(virtualPrefix))
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val uri = request.url
                                    if (uri.scheme == "https" && uri.host == virtualHost) {
                                        val asset = resolveLocalAsset(uri.path)
                                            ?: return blockedResponse(404)
                                        return runCatching {
                                            val mime = mimeType(asset)
                                            WebResourceResponse(
                                                mime,
                                                if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "UTF-8" else null,
                                                200, "OK", emptyMap(), asset.inputStream()
                                            )
                                        }.getOrElse { blockedResponse(404) }
                                    }
                                    if (uri.scheme == "data" || uri.scheme == "blob" || uri.scheme == "about") return null
                                    return blockedResponse(403)
                                }
                            }
                            activeWebView = this
                            tag = bundle.entryFile.absolutePath
                            loadUrl("https://$virtualHost/${demo.id}/${bundle.entryFile.name}")
                        }
                    },
                    update = { view ->
                        if (view.tag != bundle.entryFile.absolutePath) {
                            view.tag = bundle.entryFile.absolutePath
                            view.loadUrl("https://$virtualHost/${demo.id}/${bundle.entryFile.name}")
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVisible && !extractionState.first) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = if (isVisible) "No se pudo preparar esta demostración." else "La demostración se activará al llegar a esta parte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
