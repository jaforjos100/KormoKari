package com.example

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fcm.FcmTokenManager
import com.example.fcm.KormoKariFirebaseMessagingService
import com.example.fcm.NotificationChannelHelper
import com.example.network.NetworkMonitor
import com.example.network.NetworkState
import com.example.twa.KormoKariTwaLauncher
import com.example.ui.startup.KormoKariStartupScreen
import com.example.ui.startup.StartupViewModel
import com.example.ui.theme.KormoKariTheme

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var twaLauncher: KormoKariTwaLauncher
    private lateinit var fcmTokenManager: FcmTokenManager
    private lateinit var offlineJobRepository: com.example.data.local.OfflineJobRepository
    lateinit var appUpdateManager: com.example.update.KormoKariAppUpdateManager

    private val handoffTokenPattern = Regex("^kkh_[a-f0-9]{64}$", RegexOption.IGNORE_CASE)
    private var pendingHandoffToken: String? = null
    private var lastConsumedHandoffToken: String? = null
    private var handoffAttempts = 0

    var pendingAuthCallbackUrl: String? = null

    var activeWebView: android.webkit.WebView? = null
        set(value) {
            field = value
            if (value != null) {
                if (pendingHandoffToken != null && isTrustedKormoKariUrl(value.url)) {
                    val token = pendingHandoffToken!!
                    value.post {
                        startHandoffExchange(value, token)
                    }
                } else if (!pendingAuthCallbackUrl.isNullOrBlank()) {
                    val callbackUrl = pendingAuthCallbackUrl!!
                    pendingAuthCallbackUrl = null
                    value.post {
                        android.webkit.CookieManager.getInstance().flush()
                        value.loadUrl(callbackUrl)
                    }
                }
            }
        }

    val activeWebViewInstance: android.webkit.WebView?
        get() = activeWebView
    var fileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    val pickVisualMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris: Array<android.net.Uri>? = when {
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            fileChooserCallback?.onReceiveValue(uris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    private val startupViewModel: StartupViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StartupViewModel(
                    networkMonitor,
                    twaLauncher,
                    fcmTokenManager,
                    offlineJobRepository
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Initialize AndroidX SplashScreen API before super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        val deepLinkHandled = handleAuthDeepLink(intent)
        if (!deepLinkHandled) {
            handleOAuthCallback(intent)
        }
        // Keep Android navigation bar and status bar styled to match Android theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.parseColor("#FBF9FB"),
                android.graphics.Color.parseColor("#111827")
            )
        )

        // 2. Initialize Core Services, FCM Channels & Token Manager
        KormoKariApplication.ensureFirebaseInitialized(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        twaLauncher = KormoKariTwaLauncher(this)
        fcmTokenManager = FcmTokenManager(applicationContext)
        offlineJobRepository = com.example.data.local.OfflineJobRepository(applicationContext)
        NotificationChannelHelper.createNotificationChannels(applicationContext)

        // Initialize Google Play In-App Update flow
        appUpdateManager = com.example.update.KormoKariAppUpdateManager(this)
        appUpdateManager.registerLauncher()
        appUpdateManager.checkForUpdate(isImmediate = false)

        // 3. Keep the native splash screen visible until TWA content is ready for interaction
        splashScreen.setKeepOnScreenCondition {
            startupViewModel.shouldKeepSplashScreenOnScreen()
        }

        // 4. Initiate verification and container launch immediately on startup (with deep link & OAuth callback support)
        val initialDestinationUrl = extractTargetUrl(intent)
        startupViewModel.attemptLaunch(this, initialDestinationUrl)

        // 4. Handle smooth exit transition matching KormoKari brand design requirements
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashView = splashScreenViewProvider.view
            val iconView = try { splashScreenViewProvider.iconView } catch (_: Exception) { null }

            val alphaAnimator = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
            val animators = mutableListOf<Animator>(alphaAnimator)

            if (iconView != null) {
                animators.add(ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.12f))
                animators.add(ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.12f))
            }

            AnimatorSet().apply {
                duration = 320L
                interpolator = AccelerateDecelerateInterpolator()
                playTogether(animators)
                doOnEnd {
                    splashScreenViewProvider.remove()
                }
                start()
            }
        }

        setContent {
            KormoKariTheme {
                val state by startupViewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var previousNetworkState by remember { mutableStateOf<NetworkState?>(null) }
                var showExitConfirmationDialog by remember { mutableStateOf(false) }

                // Real-time connectivity listener that displays a Material 3 Snackbar when the device loses internet access
                LaunchedEffect(state.networkState) {
                    val currentNet = state.networkState
                    if (previousNetworkState != null && previousNetworkState != currentNet) {
                        if (currentNet is NetworkState.Disconnected) {
                            val reasonDetail = if (currentNet.reason.isNotBlank()) " (${currentNet.reason})" else ""
                            val message = "Internet connection lost$reasonDetail"

                            snackbarHostState.showSnackbar(
                                message = message,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                        } else if (currentNet is NetworkState.Connected && previousNetworkState is NetworkState.Disconnected) {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "Internet connection restored. Live features are back online.",
                                duration = SnackbarDuration.Short
                            )
                            // Background auto-retry: automatically check and restore live website without manual button clicks
                            if (state.uiState !is com.example.ui.startup.StartupUiState.PwaWebView) {
                                startupViewModel.retry(this@MainActivity)
                            }
                        }
                    }
                    previousNetworkState = currentNet
                }

                // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        val isGranted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!isGranted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Custom BackHandler intercepting system back press to show Material3 confirmation dialog and prevent accidental exits
                BackHandler(enabled = true) {
                    if (state.selectedJobDetail != null) {
                        startupViewModel.selectJobDetail(null)
                    } else {
                        showExitConfirmationDialog = true
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFFBF9FB),
                    contentWindowInsets = WindowInsets.systemBars,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .testTag("connectivity_snackbar_host")
                                .padding(12.dp)
                        ) { snackbarData ->
                            Snackbar(
                                snackbarData = snackbarData,
                                containerColor = Color(0xFF0F172A),
                                contentColor = Color(0xFFF8FAFC),
                                actionColor = Color(0xFFE91069),
                                dismissActionContentColor = Color(0xFF94A3B8),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        KormoKariStartupScreen(
                            state = state,
                            onLaunchRequested = {
                                startupViewModel.attemptLaunch(this@MainActivity)
                            },
                            onRetry = {
                                startupViewModel.retry(this@MainActivity)
                            },
                            onRefresh = {
                                startupViewModel.refresh(this@MainActivity)
                            },
                            onOpenExternal = {
                                startupViewModel.openExternalBrowser(this@MainActivity)
                            },
                            onOpenSettings = {
                                startupViewModel.openNetworkSettings(this@MainActivity)
                            },
                            onBrowseOffline = {
                                startupViewModel.openOfflineMode()
                            },
                            onBackFromOffline = {
                                startupViewModel.closeOfflineMode(this@MainActivity)
                            },
                            onExitRequested = {
                                showExitConfirmationDialog = true
                            },
                            onSearchQueryChanged = { query ->
                                startupViewModel.setOfflineSearchQuery(query)
                            },
                            onCategorySelected = { category ->
                                startupViewModel.setOfflineCategory(category)
                            },
                            onSelectJob = { job ->
                                startupViewModel.selectJobDetail(job)
                            },
                            onRequestBatteryExemption = {
                                startupViewModel.requestIgnoreBatteryOptimization(this@MainActivity)
                            },
                            onOpenBatterySettings = {
                                com.example.util.BatteryOptimizationHelper.openBatteryOptimizationSettings(this@MainActivity)
                            },
                            onServerNotReady = { healthState ->
                                startupViewModel.reportHealthState(healthState)
                            }
                        )
                    }
                }

                // Material 3 confirmation dialog before closing the app to prevent accidental exits
                if (showExitConfirmationDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirmationDialog = false },
                        icon = {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFF0F5),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = stringResource(R.string.exit_dialog_title),
                                        tint = Color(0xFFE91069),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.exit_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.exit_dialog_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExitConfirmationDialog = false
                                    this@MainActivity.finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE91069),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .testTag("exit_dialog_confirm_button")
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.exit_dialog_confirm),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showExitConfirmationDialog = false },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF0F172A)
                                ),
                                modifier = Modifier
                                    .testTag("exit_dialog_cancel_button")
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.exit_dialog_cancel),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("exit_confirmation_dialog")
                    )
                }
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingAuthIntent(intent)
    }

    fun processIncomingAuthIntent(intent: Intent): Boolean {
        val deepLinkHandled = handleAuthDeepLink(intent)
        if (!deepLinkHandled) {
            val handled = handleOAuthCallback(intent)
            if (!handled) {
                val targetUrl = extractTargetUrl(intent)
                if (!targetUrl.isNullOrBlank()) {
                    android.webkit.CookieManager.getInstance().flush()
                    startupViewModel.attemptLaunch(this, targetUrl, forceRelaunch = true)
                    return true
                }
                return false
            }
            return true
        }
        return true
    }

    private fun handleAuthDeepLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host?.lowercase() ?: ""
        if (scheme != "kormokari" || host != "auth-callback") return false

        val rawToken = uri.getQueryParameter("token")
            ?: uri.getQueryParameter("handoff_token")
            ?: uri.getQueryParameter("code")
        val cleanToken = rawToken?.trim()

        val normalizedToken = when {
            cleanToken == null -> null
            cleanToken.startsWith("kkh_", ignoreCase = true) -> cleanToken.lowercase()
            cleanToken.length == 64 && cleanToken.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> "kkh_${cleanToken.lowercase()}"
            else -> cleanToken.lowercase()
        }

        if (normalizedToken.isNullOrBlank() || !handoffTokenPattern.matches(normalizedToken)) {
            val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")
            if (!error.isNullOrBlank()) {
                val webView = activeWebView
                if (webView != null) {
                    webView.post { webView.loadUrl("https://kormokari.com/login") }
                } else {
                    startupViewModel.attemptLaunch(this, "https://kormokari.com/login", forceRelaunch = true)
                }
            } else {
                loadLoginPage()
            }
            return true
        }

        if (normalizedToken == lastConsumedHandoffToken || normalizedToken == pendingHandoffToken) {
            return true // duplicate delivery, do nothing
        }

        pendingHandoffToken = normalizedToken
        handoffAttempts = 0
        android.webkit.CookieManager.getInstance().flush()

        val webView = activeWebView
        if (webView != null && isTrustedKormoKariUrl(webView.url)) {
            webView.post { startHandoffExchange(webView, normalizedToken) }
        } else if (webView != null) {
            webView.post { webView.loadUrl("https://kormokari.com/login") }
            // onPageFinished will retry the exchange once page completes
        }
        // Cold start: keep the token pending. The normal app launch creates the WebView;
        // the WebView-ready hook (onWebViewPageFinished) starts the exchange.
        return true
    }

    private fun isTrustedKormoKariUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val u = Uri.parse(url)
            val host = u.host?.lowercase()
            u.scheme == "https" && (host == "kormokari.com" || host == "www.kormokari.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun loadLoginPage() {
        val webView = activeWebView
        if (webView != null) {
            webView.post { webView.loadUrl("https://kormokari.com/login?handoff_error=1") }
        } else {
            startupViewModel.attemptLaunch(this, "https://kormokari.com/login?handoff_error=1", forceRelaunch = true)
        }
    }

    private fun startHandoffExchange(webView: android.webkit.WebView, token: String) {
        lastConsumedHandoffToken = token
        handoffAttempts++
        if (handoffAttempts > 3) {
            pendingHandoffToken = null
            webView.loadUrl("https://kormokari.com/login")
            return
        }

        webView.evaluateJavascript(buildHandoffExchangeJs(token)) { result ->
            val status = result?.trim()?.removeSurrounding("\"")
            if (status == "need_trusted_page") {
                webView.loadUrl("https://kormokari.com/login") // retried from onPageFinished
            } else {
                pendingHandoffToken = null
                android.webkit.CookieManager.getInstance().flush()
            }
        }
    }

    private fun buildHandoffExchangeJs(token: String): String = """
        (function() {
            var token = '$token';
            var meta = document.querySelector('meta[name="csrf-token"]');
            var csrfToken = meta ? (meta.content || meta.getAttribute('content') || '') : '';
            if (!csrfToken) {
                var input = document.querySelector('input[name="_token"]');
                if (input && input.value) {
                    csrfToken = input.value;
                }
            }
            if (!csrfToken) {
                var cookieMatch = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
                if (cookieMatch) {
                    csrfToken = decodeURIComponent(cookieMatch[1]);
                }
            }
            var reqHeaders = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            };
            if (csrfToken) {
                reqHeaders['X-CSRF-TOKEN'] = csrfToken;
                reqHeaders['X-XSRF-TOKEN'] = csrfToken;
            }
            var payload = { token: token };
            fetch('https://kormokari.com/auth/handoff', {
                method: 'POST',
                credentials: 'same-origin',
                headers: reqHeaders,
                body: JSON.stringify(payload)
            }).then(function (r) {
                return r.json().catch(function () { return { success: false }; });
            }).then(function (d) {
                if (d && d.success && typeof d.redirect_url === 'string') {
                    var target = d.redirect_url.trim();
                    if (target.indexOf('https://kormokari.com') === 0 || target.indexOf('https://www.kormokari.com') === 0) {
                        window.location.replace(target);
                    } else if (target.charAt(0) === '/' && target.charAt(1) !== '/') {
                        window.location.replace(target);
                    } else {
                        window.location.replace('https://kormokari.com/login?handoff_error=1');
                    }
                } else {
                    window.location.replace('https://kormokari.com/login?handoff_error=1');
                }
            }).catch(function () {
                window.location.replace('https://kormokari.com/login?handoff_error=1');
            });
            return 'posted';
        })();
    """.trimIndent()

    fun onWebViewPageFinished(url: String?) {
        val token = pendingHandoffToken ?: return
        val webView = activeWebView ?: return
        if (!isTrustedKormoKariUrl(url)) return
        webView.post { startHandoffExchange(webView, token) }
    }

    private fun handleOAuthCallback(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        val host = uri.host?.lowercase() ?: ""
        val path = uri.path ?: ""

        if ((host == "kormokari.com" || host == "www.kormokari.com") && (
            path.startsWith("/auth/google/callback") ||
            path.startsWith("/auth/facebook/callback")
        )) {
            val token = uri.getQueryParameter("token") ?: uri.getQueryParameter("handoff_token")
            if (!token.isNullOrBlank()) {
                val webView = activeWebViewInstance
                if (webView != null) {
                    startHandoffExchange(webView, token)
                } else {
                    pendingHandoffToken = token
                }
                return true
            }
            return false
        }
        return false
    }

    private fun extractTargetUrl(intent: Intent?): String? {
        if (intent == null) return null
        val dataString = intent.dataString
        if (!dataString.isNullOrBlank()) {
            if (dataString.startsWith("kormokari://")) {
                return null
            }
            return dataString
        }
        return intent.getStringExtra(KormoKariFirebaseMessagingService.EXTRA_TARGET_URL)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        com.example.permission.KormoKariPermissionsBridge.onPermissionResult(requestCode, grantResults, activeWebView)
        com.example.permission.KormoKariWebMessageProtocolHandler.onPermissionResult(requestCode, grantResults, activeWebView)
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.onResume()
        }
        if (::networkMonitor.isInitialized) {
            startupViewModel.onResumeActivity(this)
        }
        activeWebView?.evaluateJavascript(
            """
            (function() {
                try {
                    window.dispatchEvent(new CustomEvent('kormokari:batteryOptimizationChanged'));
                    if (typeof renderCentralPermissionsCard === 'function') {
                        renderCentralPermissionsCard();
                    }
                    if (typeof updatePermissionStatuses === 'function') {
                        updatePermissionStatuses();
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.onDestroy()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.example.update.KormoKariAppUpdateManager.UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                android.util.Log.w("MainActivity", "In-app update flow failed or was cancelled by user. Result code: $resultCode")
            }
        }
    }
}
