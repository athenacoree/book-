package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ReaderPreferences
import com.example.engine.GameNotificationHelper
import com.example.engine.HtmlOrientation
import com.example.engine.ReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

private const val VIRTUAL_ORIGIN = "https://luminabook.local/"
private fun hardenGameHtml(rawHtml: String): String {
    // Remove game-supplied CSP first: multiple policies are intersected,
    // so an older connect-src self-only policy would still block GitHub.
    val withoutGameCsp = Regex("""(?is)<meta\b(?=[^>]*http-equiv\s*=\s*["\x27]?Content-Security-Policy["\x27]?)[^>]*>""").replace(rawHtml, "")
    val policy = """<meta http-equiv="Content-Security-Policy" content="default-src 'self' data: blob:; img-src 'self' data: blob: https:; media-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src 'self' https://raw.githubusercontent.com https://*.supabase.co wss://*.supabase.co; frame-src 'none'; child-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'">"""
    val headPattern = Regex("(?i)<head(?:\\s[^>]*)?>")
    val headMatch = headPattern.find(withoutGameCsp)
    if (headMatch != null) {
        return withoutGameCsp.replaceRange(headMatch.range, headMatch.value + policy)
    }
    val htmlPattern = Regex("(?i)<html(?:\\s[^>]*)?>")
    val htmlMatch = htmlPattern.find(withoutGameCsp)
    return if (htmlMatch != null) {
        withoutGameCsp.replaceRange(htmlMatch.range, htmlMatch.value + "<head>$policy</head>")
    } else {
        "<!doctype html><html><head>$policy</head><body>$withoutGameCsp</body></html>"
    }
}

data class HtmlInAppNotification(
    val id: Long,
    val title: String,
    val body: String
)

data class HtmlConsoleEntry(
    val id: Long,
    val level: String,
    val message: String,
    val line: Int
)

private sealed class JsDialogState {
    data class Alert(val message: String, val result: JsResult) : JsDialogState()
    data class Confirm(val message: String, val result: JsResult) : JsDialogState()
    data class Prompt(val message: String, val defaultValue: String, val result: JsPromptResult) : JsDialogState()
}

private class HtmlBridgeSnapshot {
    @Volatile var themeName: String = "paper"
    @Volatile var isDark: Boolean = false
    @Volatile var notificationsPermitted: Boolean = true
    @Volatile var isPaused: Boolean = false
    @Volatile var isMuted: Boolean = false
    @Volatile var savedStateJson: String = "{}"
}

private class LuminaHtmlJsBridge(
    private val snapshot: HtmlBridgeSnapshot,
    private val onNotify: (String, String) -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onSaveState: (String, String?) -> Unit
) {
    @JavascriptInterface
    fun postNotification(title: String?, body: String?) {
        onNotify(title.orEmpty().take(100), body.orEmpty().take(500))
    }

    @JavascriptInterface
    fun requestNotificationPermission(): String {
        val alreadyGranted = snapshot.notificationsPermitted
        if (!alreadyGranted) {
            onRequestPermission()
        }
        return if (alreadyGranted) "granted" else "default"
    }

    @JavascriptInterface
    fun hasNotificationPermission(): Boolean = snapshot.notificationsPermitted

    @JavascriptInterface
    fun getThemeName(): String = snapshot.themeName

    @JavascriptInterface
    fun isDark(): Boolean = snapshot.isDark

    @JavascriptInterface
    fun isPaused(): Boolean = snapshot.isPaused

    @JavascriptInterface
    fun isMuted(): Boolean = snapshot.isMuted

    @JavascriptInterface
    fun getInitialSaveJson(): String = snapshot.savedStateJson

    @JavascriptInterface
    fun syncSaveState(jsonStr: String?, explicitRecord: String?) {
        val safeJson = jsonStr?.takeIf { it.isNotBlank() } ?: "{}"
        if (safeJson.length > 512 * 1024) return
        snapshot.savedStateJson = safeJson
        onSaveState(safeJson, explicitRecord?.takeIf { it.isNotBlank() })
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlGameReaderView(
    htmlFile: File,
    bookTitle: String,
    savedGameJson: String,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    reloadSignal: Int,
    testNotificationSignal: Int,
    showTopToolbar: Boolean,
    isExternalOverlayOpen: Boolean,
    onToggleControls: () -> Unit,
    onOpenSettings: (Int) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onEnableSafeSoftwareMode: () -> Unit,
    onSaveGameState: (String, String?) -> Unit,
    onConsoleMessage: (HtmlConsoleEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findHostActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var renderCrashCount by remember(htmlFile.absolutePath) { mutableIntStateOf(0) }
    var showCrashRecoveryOverlay by remember(htmlFile.absolutePath, reloadSignal) { mutableStateOf(false) }
    var isPageLoading by remember(
        htmlFile.absolutePath,
        reloadSignal,
        renderCrashCount,
        preferences.htmlGameSafeSoftwareMode
    ) { mutableStateOf(true) }
    var loadProgress by remember(
        htmlFile.absolutePath,
        reloadSignal,
        renderCrashCount,
        preferences.htmlGameSafeSoftwareMode
    ) { mutableIntStateOf(0) }
    var activeBanner by remember { mutableStateOf<HtmlInAppNotification?>(null) }
    var jsDialog by remember { mutableStateOf<JsDialogState?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLifecycleResumed by remember { mutableStateOf(true) }

    val latestPrefs by rememberUpdatedState(preferences)
    val latestColors by rememberUpdatedState(colors)
    val latestBookTitle by rememberUpdatedState(bookTitle)
    val latestOnSaveGameState by rememberUpdatedState(onSaveGameState)
    val latestOnConsoleMessage by rememberUpdatedState(onConsoleMessage)

    val effectivePaused = showTopToolbar || isExternalOverlayOpen || showCrashRecoveryOverlay || !isLifecycleResumed
    val latestPaused by rememberUpdatedState(effectivePaused)

    val bridgeSnapshot = remember(htmlFile.absolutePath) {
        HtmlBridgeSnapshot().apply {
            savedStateJson = savedGameJson.ifBlank { "{}" }
        }
    }

    bridgeSnapshot.themeName = preferences.readerTheme.name.lowercase()
    bridgeSnapshot.isDark = preferences.readerTheme == ReaderTheme.NIGHT ||
        preferences.readerTheme == ReaderTheme.BLACK
    bridgeSnapshot.notificationsPermitted = preferences.htmlGameNotificationsEnabled &&
        GameNotificationHelper.hasNotificationPermission(context)
    bridgeSnapshot.isPaused = effectivePaused
    bridgeSnapshot.isMuted = preferences.htmlGameMuted
    if (savedGameJson.isNotBlank() && savedGameJson != "{}" && bridgeSnapshot.savedStateJson == "{}") {
        bridgeSnapshot.savedStateJson = savedGameJson
    }

    // Real Android Immersive System Bars Management
    DisposableEffect(activity, view, preferences.htmlGameFullscreenImmersive, showTopToolbar, isExternalOverlayOpen) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        if (controller != null) {
            if (preferences.htmlGameFullscreenImmersive && !showTopToolbar && !isExternalOverlayOpen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Screen Orientation Enforcement for HTML Game
    DisposableEffect(activity, preferences.htmlGameOrientation) {
        val targetOrientation = when (preferences.htmlGameOrientation) {
            HtmlOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            HtmlOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            HtmlOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        activity?.requestedOrientation = targetOrientation
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val stateStr = if (granted) "granted" else "denied"
        webViewRef?.post {
            webViewRef?.evaluateJavascript(
                "window.__luminaOnPermissionResult && window.__luminaOnPermissionResult('$stateStr');",
                null
            )
        }
    }

    fun triggerNotification(rawTitle: String, rawBody: String) {
        if (!latestPrefs.htmlGameNotificationsEnabled) return
        val title = rawTitle.ifBlank { latestBookTitle }
        val body = rawBody.ifBlank { latestBookTitle }
        activeBanner = HtmlInAppNotification(
            id = System.currentTimeMillis(),
            title = title,
            body = body
        )
        GameNotificationHelper.postGameNotification(
            context = context,
            gameTitle = latestBookTitle,
            title = title,
            body = body
        )
    }

    LaunchedEffect(activeBanner?.id) {
        val current = activeBanner ?: return@LaunchedEffect
        delay(4200)
        if (activeBanner?.id == current.id) {
            activeBanner = null
        }
    }

    LaunchedEffect(testNotificationSignal) {
        if (testNotificationSignal > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !GameNotificationHelper.hasNotificationPermission(context)
            ) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            triggerNotification(
                rawTitle = bookTitle,
                rawBody = "Notificaciones del juego activas en modo HTML."
            )
        }
    }

    val preparedHtml = remember(
        htmlFile.absolutePath,
        reloadSignal,
        renderCrashCount,
        preferences.htmlGameSafeSoftwareMode
    ) {
        val raw = runCatching { htmlFile.readText(Charsets.UTF_8) }
            .getOrElse { "<html><body><h3>No se pudo cargar el juego HTML</h3></body></html>" }
        val permitted = GameNotificationHelper.hasNotificationPermission(context) &&
            preferences.htmlGameNotificationsEnabled
        injectLuminaGameBridge(
            rawHtml = hardenGameHtml(raw),
            initiallyGranted = permitted,
            initiallyMuted = preferences.htmlGameMuted,
            initiallyPaused = effectivePaused
        )
    }

    // Sync theme, zoom, mute & pause dynamically without reloading WebView
    LaunchedEffect(preferences.readerTheme, preferences.htmlGameZoomPercent, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.settings.textZoom = preferences.htmlGameZoomPercent
        wv.setBackgroundColor(colors.background.toArgb())
        val bgHex = colorToCssHex(colors.background)
        val inkHex = colorToCssHex(colors.text)
        val accentHex = colorToCssHex(colors.accent)
        val themeName = preferences.readerTheme.name.lowercase()
        val js = """
            (function(){
              var r = document.documentElement;
              if (r) {
                r.dataset.luminaTheme = ${JSONObject.quote(themeName)};
                r.style.setProperty('--lumina-bg', ${JSONObject.quote(bgHex)});
                r.style.setProperty('--lumina-ink', ${JSONObject.quote(inkHex)});
                r.style.setProperty('--lumina-accent', ${JSONObject.quote(accentHex)});
              }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    LaunchedEffect(effectivePaused, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (effectivePaused) {
            wv.evaluateJavascript(
                "window.__luminaFlushStorage && window.__luminaFlushStorage();",
                null
            )
        }
        wv.evaluateJavascript(
            "window.__luminaSetPaused && window.__luminaSetPaused($effectivePaused);",
            null
        )
    }

    LaunchedEffect(preferences.htmlGameMuted, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript(
            "window.__luminaSetMuted && window.__luminaSetMuted(${preferences.htmlGameMuted});",
            null
        )
    }

    // Also flush saved data when cleared from Settings
    LaunchedEffect(savedGameJson, webViewRef) {
        if (savedGameJson == "{}") {
            bridgeSnapshot.savedStateJson = "{}"
            webViewRef?.evaluateJavascript(
                "try{localStorage.clear();}catch(_){}",
                null
            )
        }
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webViewRef
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    wv?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isLifecycleResumed = false
                    wv?.evaluateJavascript(
                        "window.__luminaFlushStorage && window.__luminaFlushStorage();",
                        null
                    )
                    wv?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val insetModifier = if (preferences.htmlGameFullscreenImmersive) {
        Modifier
    } else {
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(100f)
        val maxHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(100f)
        val safeTopPx = with(density) { 44.dp.toPx() }
        val safeBottomPx = with(density) { 86.dp.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }

        key(htmlFile.absolutePath, reloadSignal, renderCrashCount, preferences.htmlGameSafeSoftwareMode) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(latestColors.background.toArgb())
                        if (latestPrefs.htmlGameSafeSoftwareMode || renderCrashCount > 0) {
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            javaScriptCanOpenWindowsAutomatically = false
                            databaseEnabled = false
                            setSupportMultipleWindows(false)
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                            textZoom = latestPrefs.htmlGameZoomPercent
                        }
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        addJavascriptInterface(
                            LuminaHtmlJsBridge(
                                snapshot = bridgeSnapshot,
                                onNotify = { title, body ->
                                    scope.launch { triggerNotification(title, body) }
                                },
                                onRequestPermission = {
                                    scope.launch {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            !GameNotificationHelper.hasNotificationPermission(ctx)
                                        ) {
                                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            evaluateJavascript(
                                                "window.__luminaOnPermissionResult && window.__luminaOnPermissionResult('granted');",
                                                null
                                            )
                                        }
                                    }
                                },
                                onSaveState = { jsonStr, explicitRecord ->
                                    scope.launch {
                                        latestOnSaveGameState(jsonStr, explicitRecord)
                                    }
                                }
                            ),
                            "LuminaGameBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                if (url.scheme == "https" && url.host == "luminabook.local") {
                                    val relPath = url.path?.trimStart('/').orEmpty()
                                    val parentDir = htmlFile.parentFile
                                    if (relPath.isNotEmpty() && parentDir != null) {
                                        val target = File(parentDir, relPath).canonicalFile
                                        if (target.path.startsWith(parentDir.canonicalPath) && target.isFile) {
                                            val mime = guessMimeType(target.name)
                                            return runCatching {
                                                WebResourceResponse(mime, "UTF-8", FileInputStream(target))
                                            }.getOrNull()
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri: Uri = request?.url ?: return true
                                if (request.isForMainFrame) {
                                    return !(uri.scheme == "https" && uri.host == "luminabook.local")
                                }
                                return uri.scheme !in setOf("https", "http", "data", "blob")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                val bgHex = colorToCssHex(latestColors.background)
                                val inkHex = colorToCssHex(latestColors.text)
                                val accentHex = colorToCssHex(latestColors.accent)
                                val themeName = latestPrefs.readerTheme.name.lowercase()
                                view?.evaluateJavascript(
                                    """
                                    (function(){
                                      var r = document.documentElement;
                                      if (r) {
                                        r.dataset.luminaTheme = ${JSONObject.quote(themeName)};
                                        r.style.setProperty('--lumina-bg', ${JSONObject.quote(bgHex)});
                                        r.style.setProperty('--lumina-ink', ${JSONObject.quote(inkHex)});
                                        r.style.setProperty('--lumina-accent', ${JSONObject.quote(accentHex)});
                                      }
                                      if (window.__luminaSetMuted) window.__luminaSetMuted(${latestPrefs.htmlGameMuted});
                                      if (window.__luminaSetPaused) window.__luminaSetPaused($latestPaused);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                scope.launch {
                                    latestOnConsoleMessage(
                                        HtmlConsoleEntry(
                                            id = System.currentTimeMillis(),
                                            level = "CRASH",
                                            message = "El proceso gráfico del WebView se reinició.",
                                            line = 0
                                        )
                                    )
                                    if (renderCrashCount < 1 && !latestPrefs.htmlGameSafeSoftwareMode) {
                                        renderCrashCount++
                                        onEnableSafeSoftwareMode()
                                    } else {
                                        isPageLoading = false
                                        showCrashRecoveryOverlay = true
                                    }
                                }
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress >= 95) isPageLoading = false
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage != null) {
                                    val levelLabel = when (consoleMessage.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                        ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                        ConsoleMessage.MessageLevel.LOG -> "LOG"
                                        ConsoleMessage.MessageLevel.TIP -> "TIP"
                                        ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                                        else -> "LOG"
                                    }
                                    latestOnConsoleMessage(
                                        HtmlConsoleEntry(
                                            id = System.nanoTime(),
                                            level = levelLabel,
                                            message = consoleMessage.message().orEmpty(),
                                            line = consoleMessage.lineNumber()
                                        )
                                    )
                                }
                                return true
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                jsDialog = JsDialogState.Alert(message.orEmpty(), result)
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                jsDialog = JsDialogState.Confirm(message.orEmpty(), result)
                                return true
                            }

                            override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: JsPromptResult?
                            ): Boolean {
                                if (result == null) return false
                                jsDialog = JsDialogState.Prompt(message.orEmpty(), defaultValue.orEmpty(), result)
                                return true
                            }
                        }

                        tag = reloadSignal
                        loadDataWithBaseURL(VIRTUAL_ORIGIN, preparedHtml, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    if (wv.tag != reloadSignal) {
                        wv.tag = reloadSignal
                        isPageLoading = true
                        wv.loadDataWithBaseURL(VIRTUAL_ORIGIN, preparedHtml, "text/html", "UTF-8", null)
                    }
                    wv.settings.textZoom = preferences.htmlGameZoomPercent
                },
                onRelease = { wv ->
                    if (webViewRef === wv) {
                        webViewRef = null
                    }
                    wv.stopLoading()
                    wv.onPause()
                    wv.removeJavascriptInterface("LuminaGameBridge")
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(insetModifier)
                    .testTag("html_game_webview")
            )
        }

        if (isPageLoading && loadProgress < 90 && !showCrashRecoveryOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = when {
                            preferences.htmlGameSafeSoftwareMode -> "Abriendo en modo de compatibilidad…"
                            loadProgress < 25 -> "Preparando el juego y sus archivos…"
                            loadProgress < 60 -> "Cargando recursos visuales y sonido…"
                            else -> "Restaurando tu partida…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                }
            }
        }

        // Crash / Safe-Mode Recovery Overlay
        if (showCrashRecoveryOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("html_recovery_overlay")
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "El motor HTML necesitó reiniciarse",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tu progreso guardado sigue protegido. Puedes recargar el juego o usar el Modo Seguro por software.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                showCrashRecoveryOverlay = false
                                isPageLoading = true
                                renderCrashCount++
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reiniciar juego")
                        }
                        if (!preferences.htmlGameSafeSoftwareMode) {
                            OutlinedButton(
                                onClick = {
                                    onEnableSafeSoftwareMode()
                                    showCrashRecoveryOverlay = false
                                    isPageLoading = true
                                    renderCrashCount++
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Activar Modo Seguro (Software)")
                            }
                        }
                        TextButton(
                            onClick = { onOpenSettings(4) }
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver consola / errores JS")
                        }
                    }
                }
            }
        }

        // Draggable, Snap-to-Edge, Auto-Dimming Floating Pill
        AnimatedVisibility(
            visible = !showTopToolbar && !showCrashRecoveryOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            var pillSize by remember { mutableStateOf(IntSize(420, 128)) }
            var hasInitializedPosition by remember { mutableStateOf(false) }
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(safeTopPx) }
            var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var isDragging by remember { mutableStateOf(false) }
            var isAwake by remember { mutableStateOf(true) }

            LaunchedEffect(maxWidthPx, pillSize.width) {
                if (pillSize.width > 0) {
                    val maxX = (maxWidthPx - pillSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                    if (!hasInitializedPosition) {
                        offsetX.snapTo(maxX)
                        offsetY.snapTo(safeTopPx)
                        hasInitializedPosition = true
                    } else {
                        offsetX.snapTo(offsetX.value.coerceIn(edgeMarginPx, maxX))
                    }
                }
            }

            LaunchedEffect(lastInteractionAt, isDragging, preferences.htmlGameMuted) {
                isAwake = true
                if (!isDragging) {
                    delay(2600)
                    isAwake = false
                }
            }

            val pillAlpha by animateFloatAsState(
                targetValue = if (isAwake || isDragging) 0.95f else 0.34f,
                animationSpec = tween(durationMillis = 320),
                label = "pillAlpha"
            )

            Surface(
                shape = CircleShape,
                color = colors.barSurface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .onSizeChanged { pillSize = it }
                    .graphicsLayer { alpha = pillAlpha }
                    .border(0.75.dp, colors.barBorder, CircleShape)
                    .pointerInput(maxWidthPx, maxHeightPx, pillSize) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                lastInteractionAt = System.currentTimeMillis()
                            },
                            onDragEnd = {
                                isDragging = false
                                lastInteractionAt = System.currentTimeMillis()
                                scope.launch {
                                    val maxX = (maxWidthPx - pillSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                                    val centerX = offsetX.value + pillSize.width / 2f
                                    val targetX = if (centerX < maxWidthPx / 2f) edgeMarginPx else maxX
                                    offsetX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                lastInteractionAt = System.currentTimeMillis()
                                val maxX = (maxWidthPx - pillSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                                val maxY = (maxHeightPx - pillSize.height - safeBottomPx).coerceAtLeast(safeTopPx)
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(edgeMarginPx, maxX))
                                    offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(safeTopPx, maxY))
                                }
                            }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragIndicator,
                        contentDescription = "Arrastrar menú flotante",
                        tint = colors.mutedText,
                        modifier = Modifier.size(16.dp)
                    )
                    IconButton(
                        onClick = {
                            lastInteractionAt = System.currentTimeMillis()
                            onToggleMute(!preferences.htmlGameMuted)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("html_game_mute_pill")
                    ) {
                        Icon(
                            imageVector = if (preferences.htmlGameMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (preferences.htmlGameMuted) "Activar sonido" else "Silenciar juego",
                            tint = if (preferences.htmlGameMuted) MaterialTheme.colorScheme.error else colors.text
                        )
                    }
                    IconButton(
                        onClick = {
                            lastInteractionAt = System.currentTimeMillis()
                            onToggleControls()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("html_game_menu_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = "Controles del juego",
                            tint = colors.accent
                        )
                    }
                    IconButton(
                        onClick = {
                            lastInteractionAt = System.currentTimeMillis()
                            onOpenSettings(4)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("html_game_settings_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Ajustes del juego y consola",
                            tint = colors.text
                        )
                    }
                }
            }
        }

        // Floating In-App Game Notification Banner
        AnimatedVisibility(
            visible = activeBanner != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            activeBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeBanner = null }
                        .testTag("html_inapp_notification_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = banner.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = banner.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { activeBanner = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cerrar notificación",
                                tint = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Native M3 JS Alert / Confirm / Prompt Dialogs
    when (val dialog = jsDialog) {
        is JsDialogState.Alert -> {
            AlertDialog(
                onDismissRequest = {
                    dialog.result.confirm()
                    jsDialog = null
                },
                title = { Text(bookTitle) },
                text = { Text(dialog.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            dialog.result.confirm()
                            jsDialog = null
                        }
                    ) {
                        Text("Aceptar")
                    }
                }
            )
        }

        is JsDialogState.Confirm -> {
            AlertDialog(
                onDismissRequest = {
                    dialog.result.cancel()
                    jsDialog = null
                },
                title = { Text(bookTitle) },
                text = { Text(dialog.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            dialog.result.confirm()
                            jsDialog = null
                        }
                    ) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialog.result.cancel()
                            jsDialog = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        is JsDialogState.Prompt -> {
            var inputText by remember(dialog) { mutableStateOf(dialog.defaultValue) }
            AlertDialog(
                onDismissRequest = {
                    dialog.result.cancel()
                    jsDialog = null
                },
                title = { Text(bookTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dialog.message.isNotBlank()) {
                            Text(dialog.message)
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dialog.result.confirm(inputText)
                            jsDialog = null
                        }
                    ) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialog.result.cancel()
                            jsDialog = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        null -> Unit
    }
}

private fun guessMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}

private fun colorToCssHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

internal fun injectLuminaGameBridge(
    rawHtml: String,
    initiallyGranted: Boolean,
    initiallyMuted: Boolean = false,
    initiallyPaused: Boolean = false
): String {
    val permLiteral = if (initiallyGranted) "granted" else "default"
    val bridgeScript = """
<script>
(function() {
  if (window.__luminaBridgeInstalled) return;
  window.__luminaBridgeInstalled = true;
  var _perm = '$permLiteral';
  var _pendingResolvers = [];
  window.__luminaPaused = $initiallyPaused;
  window.__luminaMuted = $initiallyMuted;
  var _pausedRafQueue = [];
  var _audioContexts = [];
  var _explicitRecord = null;
  var _nativeRaf = window.requestAnimationFrame ? window.requestAnimationFrame.bind(window) : null;

  // 1. Auto-Pause requestAnimationFrame Gate
  if (_nativeRaf) {
    window.requestAnimationFrame = function(cb) {
      if (window.__luminaPaused) {
        if (_pausedRafQueue.length < 120) _pausedRafQueue.push(cb);
        return -1;
      }
      return _nativeRaf(cb);
    };
  }

  // 2. Track AudioContext instances for true Pause & Mute
  function wrapAudioCtx(OrigCtx) {
    if (!OrigCtx) return OrigCtx;
    var Wrapped = function() {
      var ctx = new OrigCtx();
      _audioContexts = _audioContexts.filter(function(existing) { return existing && existing.state !== 'closed'; });
      if (_audioContexts.length >= 24) _audioContexts.shift();
      _audioContexts.push(ctx);
      if (window.__luminaPaused || window.__luminaMuted) {
        try { ctx.suspend(); } catch (_) {}
      }
      return ctx;
    };
    Wrapped.prototype = OrigCtx.prototype;
    return Wrapped;
  }
  if (window.AudioContext) window.AudioContext = wrapAudioCtx(window.AudioContext);
  if (window.webkitAudioContext) window.webkitAudioContext = wrapAudioCtx(window.webkitAudioContext);

  function applyMediaAudioState() {
    var shouldSilence = !!(window.__luminaPaused || window.__luminaMuted);
    for (var i = 0; i < _audioContexts.length; i++) {
      var ctx = _audioContexts[i];
      if (!ctx) continue;
      try {
        if (shouldSilence && ctx.state === 'running') ctx.suspend();
        else if (!shouldSilence && ctx.state === 'suspended') ctx.resume();
      } catch (_) {}
    }
    try {
      var els = document.querySelectorAll('audio, video');
      for (var j = 0; j < els.length; j++) {
        els[j].muted = window.__luminaMuted;
        if (window.__luminaPaused && !els[j].paused) els[j].pause();
      }
    } catch (_) {}
  }

  window.__luminaSetPaused = function(paused) {
    var next = !!paused;
    if (window.__luminaPaused === next) return;
    window.__luminaPaused = next;
    applyMediaAudioState();
    try {
      window.dispatchEvent(new CustomEvent(next ? 'lumina:pause' : 'lumina:resume'));
    } catch (_) {}
    if (!next && _nativeRaf && _pausedRafQueue.length > 0) {
      var q = _pausedRafQueue.splice(0, _pausedRafQueue.length);
      for (var i = 0; i < q.length; i++) {
        _nativeRaf(q[i]);
      }
    }
  };

  window.__luminaSetMuted = function(muted) {
    window.__luminaMuted = !!muted;
    applyMediaAudioState();
    try {
      window.dispatchEvent(new CustomEvent('lumina:mute', { detail: { muted: window.__luminaMuted } }));
    } catch (_) {}
  };

  // 2.5. Lightweight interaction audio for HTML games without their own sound controls.
  var _uiAudio = null;
  var _lastUiSound = 0;
  function playUiTick() {
    if (window.__luminaMuted || document.hidden) return;
    var now = Date.now();
    if (now - _lastUiSound < 75) return;
    _lastUiSound = now;
    try {
      _uiAudio = _uiAudio || new (window.AudioContext || window.webkitAudioContext)();
      if (_uiAudio.state === 'suspended') _uiAudio.resume();
      var osc = _uiAudio.createOscillator();
      var gain = _uiAudio.createGain();
      var t = _uiAudio.currentTime;
      osc.type = 'sine';
      osc.frequency.setValueAtTime(620, t);
      osc.frequency.exponentialRampToValueAtTime(410, t + 0.055);
      gain.gain.setValueAtTime(0.0001, t);
      gain.gain.exponentialRampToValueAtTime(0.012, t + 0.008);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.065);
      osc.connect(gain); gain.connect(_uiAudio.destination);
      osc.start(t); osc.stop(t + 0.07);
    } catch (_) {}
  }
  document.addEventListener('click', function(event) {
    var target = event.target && event.target.closest ? event.target.closest('button,[role="button"],input[type="button"],input[type="submit"]') : null;
    if (!target) return;
    if (target.id === 'sound' || target.id === 'soundBtn' || target.id === 'soundToggle' || /mute|volume|sound/i.test(target.getAttribute('aria-label') || '')) return;
    if (document.querySelector('#soundBtn,#soundToggle,#sound')) return;
    playUiTick();
  }, true);

  // 3. Persistent Room-Backed localStorage Hydrate & Auto-Sync
  try {
    if (window.LuminaGameBridge && window.localStorage) {
      var initRaw = window.LuminaGameBridge.getInitialSaveJson();
      if (initRaw && initRaw !== '{}') {
        var initObj = JSON.parse(initRaw);
        var keys = Object.keys(initObj || {});
        for (var k = 0; k < keys.length; k++) {
          var key = keys[k];
          if (window.localStorage.getItem(key) === null) {
            window.localStorage.setItem(key, String(initObj[key]));
          }
        }
      }
    }
  } catch (_) {}

  function dumpLocalStorage() {
    var out = {};
    try {
      for (var i = 0; i < window.localStorage.length; i++) {
        var k = window.localStorage.key(i);
        if (k !== null) out[k] = window.localStorage.getItem(k);
      }
    } catch (_) {}
    var serialized = JSON.stringify(out);
    return serialized.length <= 524288 ? serialized : '';
  }

  var _syncTimer = null;
  window.__luminaFlushStorage = function() {
    if (_syncTimer) { clearTimeout(_syncTimer); _syncTimer = null; }
    try {
      if (window.LuminaGameBridge) {
        var payload = dumpLocalStorage();
        if (payload) window.LuminaGameBridge.syncSaveState(payload, _explicitRecord || '');
      }
    } catch (_) {}
  };

  function scheduleStorageSync() {
    if (_syncTimer) clearTimeout(_syncTimer);
    _syncTimer = setTimeout(window.__luminaFlushStorage, 180);
  }

  try {
    var origSetItem = Storage.prototype.setItem;
    var origRemoveItem = Storage.prototype.removeItem;
    var origClear = Storage.prototype.clear;
    Storage.prototype.setItem = function(k, v) {
      origSetItem.apply(this, arguments);
      if (this === window.localStorage) scheduleStorageSync();
    };
    Storage.prototype.removeItem = function(k) {
      origRemoveItem.apply(this, arguments);
      if (this === window.localStorage) scheduleStorageSync();
    };
    Storage.prototype.clear = function() {
      origClear.apply(this, arguments);
      if (this === window.localStorage) scheduleStorageSync();
    };
  } catch (_) {}

  window.__luminaOnPermissionResult = function(res) {
    _perm = (res === 'granted') ? 'granted' : 'denied';
    while (_pendingResolvers.length > 0) {
      var cb = _pendingResolvers.shift();
      try { cb(_perm); } catch (_) {}
    }
  };

  function LuminaNotification(title, options) {
    var body = (options && options.body) ? String(options.body) : '';
    try {
      if (window.LuminaGameBridge) {
        window.LuminaGameBridge.postNotification(String(title || ''), body);
      }
    } catch (_) {}
    this.title = String(title || '');
    this.body = body;
  }
  LuminaNotification.permission = _perm;
  Object.defineProperty(LuminaNotification, 'permission', {
    get: function() {
      try {
        if (window.LuminaGameBridge && window.LuminaGameBridge.hasNotificationPermission()) {
          return 'granted';
        }
      } catch (_) {}
      return _perm;
    }
  });
  LuminaNotification.requestPermission = function(callback) {
    return new Promise(function(resolve) {
      try {
        if (window.LuminaGameBridge) {
          var immediate = window.LuminaGameBridge.requestNotificationPermission();
          if (immediate === 'granted') {
            _perm = 'granted';
            if (typeof callback === 'function') callback('granted');
            resolve('granted');
            return;
          }
          var resolver = function(val) {
            var index = _pendingResolvers.indexOf(resolver);
            if (index >= 0) _pendingResolvers.splice(index, 1);
            if (typeof callback === 'function') callback(val);
            resolve(val);
          };
          _pendingResolvers.push(resolver);
          setTimeout(function() {
            var index = _pendingResolvers.indexOf(resolver);
            if (index >= 0) {
              _pendingResolvers.splice(index, 1);
              var p = _perm === 'granted' ? 'granted' : 'default';
              if (typeof callback === 'function') callback(p);
              resolve(p);
            }
          }, 1200);
          return;
        }
      } catch (_) {}
      if (typeof callback === 'function') callback(_perm);
      resolve(_perm);
    });
  };
  window.Notification = LuminaNotification;

  window.LuminaBook = {
    notify: function(title, body) {
      if (window.LuminaGameBridge) {
        window.LuminaGameBridge.postNotification(String(title || ''), String(body || ''));
      }
    },
    getTheme: function() {
      return window.LuminaGameBridge ? window.LuminaGameBridge.getThemeName() : 'paper';
    },
    isDark: function() {
      return window.LuminaGameBridge ? window.LuminaGameBridge.isDark() : false;
    },
    isPaused: function() {
      return !!window.__luminaPaused;
    },
    isMuted: function() {
      return !!window.__luminaMuted;
    },
    saveData: function(key, value) {
      try {
        window.localStorage.setItem(String(key), String(value));
        window.__luminaFlushStorage();
      } catch (_) {}
    },
    loadData: function(key) {
      try { return window.localStorage.getItem(String(key)); } catch (_) { return null; }
    },
    setScore: function(recordLabel) {
      _explicitRecord = String(recordLabel || '');
      window.__luminaFlushStorage();
    }
  };
})();
</script>
""".trimIndent()

    val headIndex = rawHtml.indexOf("<head>", ignoreCase = true)
    if (headIndex >= 0) {
        val insertAt = headIndex + "<head>".length
        return rawHtml.substring(0, insertAt) + "\n" + bridgeScript + "\n" + rawHtml.substring(insertAt)
    }
    val htmlIndex = rawHtml.indexOf("<html", ignoreCase = true)
    if (htmlIndex >= 0) {
        val tagEnd = rawHtml.indexOf('>', htmlIndex)
        if (tagEnd >= 0) {
            val insertAt = tagEnd + 1
            return rawHtml.substring(0, insertAt) + "\n<head>$bridgeScript</head>\n" + rawHtml.substring(insertAt)
        }
    }
    return bridgeScript + "\n" + rawHtml
}
