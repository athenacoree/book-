package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.net.Uri
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.engine.ReaderBlock
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Runs a publisher-bundled HTML example in a restricted local WebView.
 * The view is only created while its block is visible in the reader.
 */
@Composable
fun InteractiveBookDemoView(
    demo: ReaderBlock.InteractiveDemo,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeWebView by remember(demo.id, demo.htmlFilePath) { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
            if (isVisible && File(demo.htmlFilePath).isFile && File(demo.bundleRootPath).isDirectory) {
                DisposableEffect(demo.id, demo.htmlFilePath) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            setBackgroundColor(AndroidColor.WHITE)
                            overScrollMode = WebView.OVER_SCROLL_NEVER
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = false
                                allowFileAccessFromFileURLs = false
                                allowUniversalAccessFromFileURLs = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mediaPlaybackRequiresUserGesture = true
                                blockNetworkLoads = true
                                loadsImagesAutomatically = true
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            val root = File(demo.bundleRootPath).canonicalFile
                            fun isInsideBundle(rawPath: String?): Boolean {
                                if (rawPath.isNullOrBlank()) return false
                                return runCatching {
                                    val candidate = File(Uri.decode(rawPath)).canonicalFile
                                    candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
                                }.getOrDefault(false)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    val uri = request.url
                                    return when (uri.scheme?.lowercase()) {
                                        "file" -> !isInsideBundle(uri.path)
                                        "about" -> uri.toString() != "about:blank"
                                        else -> true
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val uri = request.url
                                    val allowed = when (uri.scheme?.lowercase()) {
                                        "file" -> isInsideBundle(uri.path)
                                        "data", "blob", "about" -> true
                                        else -> false
                                    }
                                    return if (allowed) null else WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        403,
                                        "Blocked by GITBOOK",
                                        emptyMap(),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                            }
                            activeWebView = this
                            tag = demo.htmlFilePath
                            loadUrl(Uri.fromFile(File(demo.htmlFilePath)).toString())
                        }
                    },
                    update = { view ->
                        if (view.tag != demo.htmlFilePath) {
                            view.tag = demo.htmlFilePath
                            view.loadUrl(Uri.fromFile(File(demo.htmlFilePath)).toString())
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
                    Text(
                        text = if (isVisible) "Esta demostración no se pudo cargar." else "La demostración se activará al llegar a esta parte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
