package com.example

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
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

    var activeWebView: android.webkit.WebView? = null
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetUrl = extractTargetUrl(intent)
        if (!targetUrl.isNullOrBlank()) {
            android.webkit.CookieManager.getInstance().flush()
            startupViewModel.attemptLaunch(this, targetUrl, forceRelaunch = true)
        }
    }

    private fun extractTargetUrl(intent: Intent?): String? {
        if (intent == null) return null
        val dataString = intent.dataString
        if (!dataString.isNullOrBlank()) {
            return dataString
        }
        return intent.getStringExtra(KormoKariFirebaseMessagingService.EXTRA_TARGET_URL)
    }

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
}
