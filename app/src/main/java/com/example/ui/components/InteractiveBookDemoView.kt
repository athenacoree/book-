package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current
    val pendingDeviceRequest = remember(demo.id) { mutableStateOf<PermissionRequest?>(null) }
    val devicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val request = pendingDeviceRequest.value ?: return@rememberLauncherForActivityResult
        val allowed = request.resources.all { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    "microphone" in demo.permissions &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    "camera" in demo.permissions &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                else -> false
            }
        }
        if (allowed) request.grant(request.resources) else request.deny()
        pendingDeviceRequest.value = null
    }
    val virtualHost = remember(demo.sourceEpubPath, demo.id) {
        "demo-${(demo.sourceEpubPath + "|" + demo.id).hashCode().toUInt()}.gitbook.local"
    }
    val activeWebView = remember(demo.id, demo.entryPath) { mutableStateOf<WebView?>(null) }

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
                        activeWebView.value?.apply {
                            stopLoading()
                            loadUrl("about:blank")
                            destroy()
                        }
                        activeWebView.value = null
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
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest) {
                                    val androidPermissions = mutableListOf<String>()
                                    val declared = request.resources.all { resource ->
                                        when (resource) {
                                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                                if ("microphone" in demo.permissions) {
                                                    androidPermissions.add(Manifest.permission.RECORD_AUDIO)
                                                    true
                                                } else false
                                            }
                                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                                if ("camera" in demo.permissions) {
                                                    androidPermissions.add(Manifest.permission.CAMERA)
                                                    true
                                                } else false
                                            }
                                            else -> false
                                        }
                                    }
                                    if (!declared || androidPermissions.isEmpty()) {
                                        request.deny()
                                        return
                                    }
                                    val missing = androidPermissions.distinct().filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missing.isEmpty()) {
                                        request.grant(request.resources)
                                    } else {
                                        pendingDeviceRequest.value = request
                                        devicePermissionLauncher.launch(missing.toTypedArray())
                                    }
                                }

                                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                                    if (pendingDeviceRequest.value == request) pendingDeviceRequest.value = null
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    val uri = request.url
                                    val isLocalDemo = uri.scheme == "https" && uri.host == virtualHost &&
                                        uri.path.orEmpty().startsWith(virtualPrefix)
                                    val isPermittedRemote = "network" in demo.permissions && uri.scheme == "https"
                                    return !(isLocalDemo || isPermittedRemote)
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
                                    if ("network" in demo.permissions && uri.scheme == "https") return null
                                    return blockedResponse(403)
                                }
                            }
                            activeWebView.value = this
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


/** Official YouTube iframe player, instantiated only when the video block is visible. */
@Composable
fun YouTubeBookVideoView(
    video: ReaderBlock.YouTubeVideo,
    isVisible: Boolean,
    livePlaying: Boolean,
    onPauseLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(isVisible, livePlaying) { if (isVisible && livePlaying) onPauseLive() }
    val webViewRef = remember(video.videoId) { mutableStateOf<WebView?>(null) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(video.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            if (isVisible) {
                DisposableEffect(video.videoId) {
                    onDispose {
                        webViewRef.value?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
                        webViewRef.value = null
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(AndroidColor.BLACK)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mediaPlaybackRequiresUserGesture = true
                                loadsImagesAutomatically = true
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            val hosts = listOf("youtube.com", "youtube-nocookie.com", "youtu.be", "ytimg.com", "googlevideo.com", "google.com", "googleapis.com", "gstatic.com", "ggpht.com")
                            fun allowed(host: String?): Boolean {
                                val h = host.orEmpty().lowercase()
                                return hosts.any { h == it || h.endsWith(".$it") }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.isForMainFrame) return false
                                    return request.url.scheme != "https" || !allowed(request.url.host)
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val uri = request.url
                                    if (uri.scheme == "https" && allowed(uri.host)) return null
                                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                }
                            }
                            webViewRef.value = this
                            val html = """
                                <!doctype html><html><head>
                                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                                <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
                                iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
                                </head><body>
                                <iframe src="https://www.youtube-nocookie.com/embed/VIDEO_ID_HERE?playsinline=1&amp;rel=0"
                                title="YouTube video player"
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
                                </body></html>
                            """.trimIndent().replace("VIDEO_ID_HERE", video.videoId)
                            loadDataWithBaseURL("https://www.youtube-nocookie.com/", html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { }
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(112.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Text("El vídeo se cargará al llegar a esta parte del libro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
