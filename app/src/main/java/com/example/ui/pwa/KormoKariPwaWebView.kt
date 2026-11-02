package com.example.ui.pwa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.auth.KormoKariAuthBridge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.config.KormoKariConfig
import com.example.network.NetworkState
import com.example.ui.theme.KormoKariNavy
import com.example.ui.theme.KormoKariPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PWA WebView Component featuring an integrated Material 3 Pull-to-Refresh mechanism,
 * connection awareness, and automatic/manual reload capabilities when returning online.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KormoKariPwaWebView(
    url: String,
    networkState: NetworkState,
    onNavigateOffline: () -> Unit,
    onBack: () -> Unit,
    onServerNotReady: ((com.example.network.ServerHealthState) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isProgressVisible by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (isLoading) loadProgress.coerceIn(0.06f, 1f) else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "webViewProgressAnimation"
    )

    // Smooth fade-out animation for LinearProgressIndicator once loading completes
    val progressIndicatorAlpha by animateFloatAsState(
        targetValue = if (isProgressVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "progressIndicatorFadeOutAlpha"
    )

    var canGoBack by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isContentReady by remember { mutableStateOf(false) }
    var showBackOnlineBanner by remember { mutableStateOf(false) }

    // Smooth content fade-in animation when transitioning from cached/skeleton to live network content
    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentReady || networkState is NetworkState.Disconnected) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "pwaWebViewFadeInAnimation"
    )

    fun triggerReload() {
        if (networkState is NetworkState.Disconnected) {
            // Offline Pull-to-Refresh Guard: immediately stop spinner, reload from cache without error
            isRefreshing = false
            isLoading = false
            hasError = false
            webViewInstance?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            webViewInstance?.reload()
            return
        }

        isRefreshing = true
        isLoading = true
        loadProgress = 0.08f
        isProgressVisible = true
        hasError = false

        coroutineScope.launch {
            val availabilityManager = com.example.network.AppAvailabilityManager()
            val healthResult = availabilityManager.checkFreshAvailability()
            withContext(Dispatchers.Main) {
                if (healthResult is com.example.network.ServerHealthState.Ready) {
                    webViewInstance?.reload()
                } else {
                    isRefreshing = false
                    isLoading = false
                    isProgressVisible = false
                    onServerNotReady?.invoke(healthResult)
                }
            }
        }
    }

    // Track network transition to alert user when back online
    var wasOffline by remember { mutableStateOf(networkState is NetworkState.Disconnected) }

    LaunchedEffect(networkState) {
        val isOnline = networkState is NetworkState.Connected
        webViewInstance?.settings?.cacheMode = if (isOnline) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        if (isOnline && wasOffline) {
            // Auto-sync on reconnect: switch back to live data and reload
            triggerReload()
        }
        wasOffline = (networkState is NetworkState.Disconnected)
    }

    // Background auto-checker when offline: periodically checks online status and auto-reconnects
    LaunchedEffect(networkState) {
        if (networkState is NetworkState.Disconnected) {
            while (true) {
                delay(3000)
                val hasInternet = withContext(Dispatchers.IO) {
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 2000)
                        socket.close()
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (hasInternet) {
                    triggerReload()
                    break
                }
            }
        }
    }

    // Intercept hardware and gesture back presses inside the WebView
    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFBF9FB))
            .testTag("pwa_webview_container")
    ) {
        // Linear Progress Indicator at the top of the WebView with smooth fade-out animation
        androidx.compose.animation.AnimatedVisibility(
            visible = isProgressVisible || progressIndicatorAlpha > 0.01f,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .alpha(progressIndicatorAlpha)
                    .testTag("webview_linear_progress_indicator"),
                color = KormoKariPrimary,
                trackColor = Color(0xFFE91069).copy(alpha = 0.12f)
            )
        }

        // Compact "No Internet" status banner when offline, allowing user to browse cached pages below
        AnimatedVisibility(
            visible = networkState is NetworkState.Disconnected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("no_internet_status_banner"),
                color = Color(0xFFEF4444)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No Internet Connection — Showing Cached Content",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. Native SwipeRefreshLayout Pull-to-Refresh Container around the WebView
        var previousLoadedUrl by remember { mutableStateOf(url) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("pwa_swipe_refresh_box")
        ) {
            // Live WebView wrapped inside native SwipeRefreshLayout for smooth, scroll-aware pull-to-refresh
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha)
                    .testTag("pwa_webview"),
                factory = { ctx ->
                    val triggerDistancePx = (200 * ctx.resources.displayMetrics.density).toInt()
                    var initialTouchY = -1f

                    val swipeRefreshLayout = SwipeRefreshLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setColorSchemeColors(
                            ctx.getColor(R.color.kormokari_primary),
                            ctx.getColor(R.color.kormokari_primary_dark)
                        )
                        setProgressBackgroundColorSchemeColor(
                            android.graphics.Color.WHITE
                        )
                        setDistanceToTriggerSync(triggerDistancePx)
                        setOnRefreshListener {
                            triggerReload()
                        }
                    }

                    val webView = WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Track initial ACTION_DOWN Y-coordinate for top-zone pull detection
                        setOnTouchListener { _, event ->
                            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                                initialTouchY = event.y
                            }
                            false
                        }

                        // Enable modern PWA WebSettings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = if (networkState is NetworkState.Connected) {
                                WebSettings.LOAD_DEFAULT
                            } else {
                                WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportZoom(true)
                            builtInZoomControls = false
                            displayZoomControls = false
                            userAgentString = "${settings.userAgentString} KormoKariApp/3.9.7"
                        }

                        // Register secure native JavaScript interface bridge for Google Sign-In
                        val activity = ctx.findActivity()
                        if (activity != null) {
                            val authBridge = KormoKariAuthBridge(
                                activity = activity,
                                webViewProvider = { webViewInstance ?: this },
                                coroutineScope = coroutineScope
                            ).apply {
                                onServerNotReadyListener = { reason ->
                                    onServerNotReady?.invoke(
                                        com.example.network.ServerHealthState.ServerNotReady(
                                            checkedAtMillis = System.currentTimeMillis(),
                                            httpCode = 503,
                                            details = reason
                                        )
                                    )
                                }
                            }
                            addJavascriptInterface(authBridge, KormoKariAuthBridge.JAVASCRIPT_INTERFACE_NAME)
                            addJavascriptInterface(authBridge, KormoKariAuthBridge.JAVASCRIPT_INTERFACE_ALIAS)

                            // Register native runtime permissions & live GPS dispatch bridge
                            val permissionsBridge = com.example.permission.KormoKariPermissionsBridge(
                                activity = activity,
                                webViewProvider = { webViewInstance ?: this }
                            )
                            addJavascriptInterface(permissionsBridge, "KormoKariAndroidPermissionsBridge")
                            addJavascriptInterface(permissionsBridge, "AndroidPermissionsBridge")

                            val webMessageHandler = com.example.permission.KormoKariWebMessageProtocolHandler(
                                activity = activity,
                                webViewProvider = { webViewInstance ?: this }
                            )
                            addJavascriptInterface(webMessageHandler, "KormoKariWebMessageBridge")
                            addJavascriptInterface(webMessageHandler, "KormoKariWebMessageProtocolHandler")

                            if (activity is com.example.MainActivity) {
                                activity.activeWebView = this
                            }
                        }

                        // Enable cookies
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                val progressFraction = (newProgress.coerceIn(0, 100)) / 100f
                                loadProgress = progressFraction
                                if (newProgress < 100) {
                                    isLoading = true
                                    isProgressVisible = true
                                } else {
                                    isLoading = false
                                    isRefreshing = false
                                    isContentReady = true
                                    // Allow progress bar to visibly reach 100% before smoothly fading out
                                    coroutineScope.launch {
                                        delay(240)
                                        isProgressVisible = false
                                    }
                                }
                            }

                            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                if (request == null) return
                                val origin = request.origin?.toString() ?: ""
                                if (origin.contains("kormokari.com") || origin.startsWith("https://kormokari.com")) {
                                    request.grant(request.resources)
                                } else {
                                    request.deny()
                                }
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: android.webkit.GeolocationPermissions.Callback?
                            ) {
                                val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    ctx,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasLocationPermission) {
                                    callback?.invoke(origin, true, false)
                                } else {
                                    val act = ctx.findActivity()
                                    if (act != null) {
                                        androidx.core.app.ActivityCompat.requestPermissions(
                                            act,
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            ),
                                            com.example.permission.KormoKariPermissionsBridge.REQ_CODE_LOCATION_FOREGROUND
                                        )
                                    }
                                    callback?.invoke(origin, true, false)
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                val activity = ctx.findActivity() as? com.example.MainActivity ?: return false
                                activity.fileChooserCallback?.onReceiveValue(null)
                                activity.fileChooserCallback = filePathCallback

                                val acceptTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
                                val isOnlyImages = acceptTypes.isNotEmpty() && acceptTypes.all { it.startsWith("image/") }

                                try {
                                    if (isOnlyImages && androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(activity)) {
                                        activity.pickVisualMediaLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                        return true
                                    }

                                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = if (acceptTypes.size == 1) acceptTypes[0] else "*/*"
                                        if (acceptTypes.size > 1) {
                                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                                        }
                                    }
                                    activity.filePickerLauncher.launch(intent)
                                    return true
                                } catch (_: Exception) {
                                    activity.fileChooserCallback?.onReceiveValue(null)
                                    activity.fileChooserCallback = null
                                    return false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                com.example.permission.KormoKariWebMessageProtocolHandler.injectFacade(view)
                                isLoading = true
                                loadProgress = 0.08f
                                isProgressVisible = true
                                hasError = false
                                // Smooth Reconnect / refresh: do NOT set isContentReady = false if content is already displayed
                                canGoBack = canGoBack()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                com.example.permission.KormoKariWebMessageProtocolHandler.injectFacade(view)
                                isLoading = false
                                loadProgress = 1f
                                isRefreshing = false
                                isContentReady = true
                                canGoBack = canGoBack()

                                // Offline page cleanup script: remove manual Try Again / Retry buttons while preserving footer navigation
                                val offlinePageCleanupScript = """
                                    (function() {
                                        try {
                                            var styleId = 'kormokari-offline-cleanup-style';
                                            if (!document.getElementById(styleId)) {
                                                var style = document.createElement('style');
                                                style.id = styleId;
                                                style.textContent = `
                                                    /* Remove Try Again / Retry buttons from offline page */
                                                    .kormokari-modern-offline-btn,
                                                    button[class*="retry"],
                                                    button[class*="try"],
                                                    a[class*="retry"],
                                                    a[class*="try"] {
                                                        display: none !important;
                                                    }
                                                `;
                                                document.head.appendChild(style);
                                            }

                                            function cleanOfflineButtons() {
                                                // Remove Try Again / Retry / Try Home buttons on offline page
                                                var btns = document.querySelectorAll('button, a, .btn, [role="button"], input[type="button"], input[type="submit"]');
                                                btns.forEach(function(btn) {
                                                    var text = (btn.innerText || btn.textContent || btn.value || '').trim().toLowerCase();
                                                    if (text.indexOf('try') !== -1 || text.indexOf('retry') !== -1 || text.indexOf('আবার চেষ্টা') !== -1 || text.indexOf('চেষ্টা করুন') !== -1 || text.indexOf('try হোম') !== -1 || text.indexOf('try এগেইন') !== -1) {
                                                        // Ensure we only hide retry/try buttons, never the bottom footer navigation
                                                        if (!btn.closest('nav, footer, [class*="bottom"], [class*="nav"], [class*="menu"], [class*="bar"], [class*="tab"]')) {
                                                            btn.style.setProperty('display', 'none', 'important');
                                                        }
                                                    }
                                                });
                                            }

                                            cleanOfflineButtons();
                                            var observer = new MutationObserver(function() {
                                                cleanOfflineButtons();
                                            });
                                            observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

                                            // Auto-detect online status in background inside WebView
                                            window.addEventListener('online', function() {
                                                window.location.reload();
                                            });
                                        } catch (e) {}
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(offlinePageCleanupScript, null)

                                // Trigger smooth fade-out animation once the page completes loading
                                coroutineScope.launch {
                                    delay(240)
                                    isProgressVisible = false
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    val statusCode = errorResponse?.statusCode ?: 0
                                    if (statusCode == 503) {
                                        onServerNotReady?.invoke(
                                            com.example.network.ServerHealthState.ServerNotReady(
                                                checkedAtMillis = System.currentTimeMillis(),
                                                httpCode = 503,
                                                details = "HTTP 503 Service Unavailable"
                                            )
                                        )
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    isRefreshing = false
                                    isProgressVisible = false
                                    if (networkState !is NetworkState.Disconnected) {
                                        isContentReady = false
                                        hasError = true
                                    } else {
                                        hasError = false
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                val targetUrlString = targetUri.toString()

                                // 1. In-App Social Authentication & Identity Provider Routing:
                                // Facebook OAuth and Google web OAuth flows must use branded Chrome Custom Tabs,
                                // NEVER loaded in an embedded WebView (protecting credentials & adhering to provider policies)
                                if (com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl(targetUrlString)) {
                                    val launched = com.example.auth.BrandedCustomTabsHelper.launchBrandedCustomTab(ctx, targetUri)
                                    return launched
                                }

                                // 2. Keep kormokari.com internal pages inside the WebView
                                if (targetUrlString.contains("kormokari.com")) {
                                    return false
                                }

                                // External intents (tel, mailto, whatsapp, external browsers)
                                return try {
                                    val externalIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    ctx.startActivity(externalIntent)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }

                        loadUrl(url)
                        webViewInstance = this
                    }

                    swipeRefreshLayout.addView(webView)

                    // Strict, intentional pull-to-refresh trigger:
                    // 1. Only allow when touch initiated in the very top zone (top 15% of WebView height)
                    // 2. Only allow when page is at the very top (scrollY == 0 and cannot scroll up)
                    swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
                        val viewHeight = webView.height
                        val isTouchInTopZone = if (viewHeight > 0 && initialTouchY >= 0f) {
                            initialTouchY <= (viewHeight * 0.15f)
                        } else {
                            false
                        }
                        val isAtTop = webView.scrollY == 0 && !webView.canScrollVertically(-1)

                        // Return true to BLOCK refresh: block if touch wasn't in top 15% zone or page not at top
                        !isTouchInTopZone || !isAtTop
                    }

                    swipeRefreshLayout
                },
                update = { swipeRefresh ->
                    swipeRefresh.isRefreshing = isRefreshing
                    val webView = swipeRefresh.getChildAt(0) as? WebView
                    if (webView != null) {
                        webViewInstance = webView
                        val targetCacheMode = if (networkState is NetworkState.Connected) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        if (webView.settings.cacheMode != targetCacheMode) {
                            webView.settings.cacheMode = targetCacheMode
                        }

                        // Handle new URL updates (e.g. from OAuth redirect deep links or notifications)
                        if (!url.isNullOrBlank() && url != webView.url && url != previousLoadedUrl) {
                            previousLoadedUrl = url
                            android.webkit.CookieManager.getInstance().flush()
                            webView.loadUrl(url)
                        }
                    }
                }
            )

            // High-fidelity full screen loading animation during initial load or transition from cache
            androidx.compose.animation.AnimatedVisibility(
                visible = !isContentReady && !hasError && networkState !is NetworkState.Disconnected,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
            ) {
                com.example.ui.loading.FullScreenLoadingAnimation(
                    progress = if (loadProgress > 0f) loadProgress else null,
                    statusText = "Loading KormoKari…",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("full_screen_loading_animation")
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance = null
        }
    }
}

/**
 * Creates an infinite sweeping shimmer gradient brush using KormoKari's brand off-white/pink palette.
 */
@Composable
fun rememberKormoKariShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "kormokariShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -350f,
        targetValue = 1350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "kormokariShimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFF1F2F4),
            Color(0xFFFCE7F3).copy(alpha = 0.55f),
            Color(0xFFF1F2F4)
        ),
        start = Offset(translateAnim, translateAnim * 0.25f),
        end = Offset(translateAnim + 340f, translateAnim * 0.25f + 340f)
    )
}

/**
 * Basic shimmering placeholder block for skeleton UI elements.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = rememberKormoKariShimmerBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = brush)
    )
}

/**
 * High-fidelity Skeleton Screen displayed when transitioning from cached state to live network-loaded content.
 * Mimics KormoKari's real job listing layout: search bar, category chips, and job card placeholders.
 */
@Composable
fun KormoKariPwaSkeletonScreen(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberKormoKariShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFBF9FB))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("pwa_skeleton_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Shimmer Search Bar Placeholder
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8EE)),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    brush = shimmerBrush
                )
                Spacer(modifier = Modifier.width(12.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    brush = shimmerBrush
                )
                Spacer(modifier = Modifier.width(12.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .size(24.dp),
                    shape = RoundedCornerShape(6.dp),
                    brush = shimmerBrush
                )
            }
        }

        // 2. Horizontal Category Filter Pills Placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(72.dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                brush = shimmerBrush
            )
            SkeletonBlock(
                modifier = Modifier
                    .width(86.dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                brush = shimmerBrush
            )
            SkeletonBlock(
                modifier = Modifier
                    .width(80.dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                brush = shimmerBrush
            )
            SkeletonBlock(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                brush = shimmerBrush
            )
        }

        // 3. Status Bar Notice Placeholder (e.g. "Loading latest jobs from kormokari.com...")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFDF2F8),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    brush = shimmerBrush
                )
                Spacer(modifier = Modifier.width(10.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .width(200.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                    brush = shimmerBrush
                )
            }
        }

        // 4. Multiple Job Card Skeletons
        repeat(3) {
            KormoKariJobCardSkeleton(brush = shimmerBrush)
        }
    }
}

/**
 * Individual job card skeleton mirroring KormoKari's job cards.
 */
@Composable
fun KormoKariJobCardSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8EE)),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Company Logo Placeholder + Title & Company
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    brush = brush
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        brush = brush
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        brush = brush
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges / Tags (Salary, Employment Type, Location)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(90.dp)
                        .height(24.dp),
                    shape = RoundedCornerShape(6.dp),
                    brush = brush
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(70.dp)
                        .height(24.dp),
                    shape = RoundedCornerShape(6.dp),
                    brush = brush
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp),
                    shape = RoundedCornerShape(6.dp),
                    brush = brush
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body text lines
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                shape = RoundedCornerShape(3.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(10.dp),
                shape = RoundedCornerShape(3.dp),
                brush = brush
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Footer: Timestamp + Apply button placeholder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(64.dp)
                        .height(11.dp),
                    shape = RoundedCornerShape(3.dp),
                    brush = brush
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(82.dp)
                        .height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    brush = brush
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

