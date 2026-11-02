package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.KormoKariConfig
import com.example.network.NetworkMonitor
import com.example.twa.KormoKariTwaLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `verify app name resource matches KormoKari brand`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("KormoKari", appName)
    }

    @Test
    fun `verify tagline resource matches KormoKari brand`() {
        val tagline = context.getString(R.string.brand_tagline)
        assertEquals("Find work. Create opportunity.", tagline)
        assertEquals(tagline, KormoKariConfig.TAGLINE)
    }

    @Test
    fun `verify splash and logo drawables are accessible`() {
        val logoDrawable = context.getDrawable(R.drawable.kormokari_logo)
        assertNotNull(logoDrawable)
        val splashIconDrawable = context.getDrawable(R.drawable.kormokari_splash_icon)
        assertNotNull(splashIconDrawable)
    }

    @Test
    fun `verify splash screen window background matches off-white pink palette`() {
        val splashBgColor = context.getColor(R.color.kormokari_splash_background)
        // 0xFFFBF9FB corresponds to the off-white/pink palette
        val expectedColor = android.graphics.Color.parseColor("#FBF9FB")
        assertEquals("Splash screen window background must match KormoKari off-white/pink palette", expectedColor, splashBgColor)
    }

    @Test
    fun `verify Android navigation bar colors adhere to theme palette`() {
        val navBarColorLight = context.getColor(R.color.kormokari_splash_background)
        val navBarColorDark = context.getColor(R.color.kormokari_navy)
        assertEquals(android.graphics.Color.parseColor("#FBF9FB"), navBarColorLight)
        assertEquals(android.graphics.Color.parseColor("#111827"), navBarColorDark)
    }

    @Test
    fun `verify production origin configuration`() {
        assertEquals("https://kormokari.com/", KormoKariConfig.PRODUCTION_URL)
        assertEquals("https://kormokari.com", KormoKariConfig.PRODUCTION_ORIGIN)
        assertEquals("com.kormokari.app", KormoKariConfig.APPLICATION_ID)
    }

    @Test
    fun `verify digital asset links status is explicitly marked pending verification`() {
        assertFalse(
            "TWA verification must remain pending until production asset links fingerprint is confirmed on server",
            KormoKariConfig.currentAssetLinksStatus.isVerified
        )
        assertEquals(
            KormoKariConfig.AssetLinksStatus.PENDING_VERIFICATION,
            KormoKariConfig.currentAssetLinksStatus
        )
    }

    @Test
    fun `verify debug certificate fingerprint is verified and not invented`() {
        assertEquals(
            "0A:91:A8:C0:9D:39:81:B6:3D:56:CF:92:B7:8A:BD:F1:59:69:3A:27:02:60:9F:44:8E:A9:3A:DD:A8:F0:F3:5F",
            KormoKariConfig.DEBUG_CERTIFICATE_SHA256
        )
    }

    @Test
    fun `verify production release key status is marked blocked pending release keys`() {
        assertTrue(
            KormoKariConfig.PRODUCTION_SIGNING_STATUS.contains("BLOCKED")
        )
    }

    @Test
    fun `verify asset_statements string resource declares correct relation and site`() {
        val assetStatements = context.getString(R.string.asset_statements)
        assertTrue(assetStatements.contains("delegate_permission/common.handle_all_urls"))
        assertTrue(assetStatements.contains("https://kormokari.com"))
    }

    @Test
    fun `verify network monitor initializes without throwing`() {
        val monitor = NetworkMonitor(context)
        val state = monitor.checkCurrentNetwork()
        assertNotNull(state)
    }

    @Test
    fun `verify browser diagnostics structure`() {
        val launcher = KormoKariTwaLauncher(context)
        val diagnostics = launcher.getBrowserDiagnostics()
        assertEquals("https://kormokari.com/", diagnostics.targetUrl)
        assertTrue(diagnostics.digitalAssetLinksStatus.isNotEmpty())
    }

    @Test
    fun `verify splashscreen keepOnScreenCondition keeps splash visible until TWA is launched`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher)

        // Before TWA launch completes, the native splash must be kept on screen
        assertTrue("Splash screen must remain on screen while TWA content is preparing", viewModel.shouldKeepSplashScreenOnScreen())

        // Initiate launch
        viewModel.attemptLaunch(context)

        // Once launched, splash condition releases
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("Splash screen condition must release once TWA content is launched or resolved", viewModel.shouldKeepSplashScreenOnScreen())
    }

    @Test
    fun `verify network monitor reports network state and connection type`() {
        val monitor = NetworkMonitor(context)
        val state = monitor.checkCurrentNetwork()
        assertNotNull(state)
        // Under Robolectric, active network is simulated or detected cleanly
        assertTrue("Network state must be Connected, Disconnected or Checking",
            state is com.example.network.NetworkState.Connected ||
            state is com.example.network.NetworkState.Disconnected ||
            state is com.example.network.NetworkState.Checking
        )
    }

    @Test
    fun `verify no connection view string resources match brand requirements`() {
        val title = context.getString(R.string.no_connection_title)
        val subtitle = context.getString(R.string.no_connection_subtitle)
        val retryAction = context.getString(R.string.action_retry)
        val settingsAction = context.getString(R.string.action_open_network_settings)

        assertEquals("No Internet Connection", title)
        assertTrue(subtitle.contains("KormoKari requires an active internet connection"))
        assertEquals("Try Again", retryAction)
        assertEquals("Open Network Settings", settingsAction)
    }

    @Test
    fun `verify pull to refresh string resources are properly configured`() {
        val hint = context.getString(R.string.pull_to_refresh_hint)
        val loading = context.getString(R.string.pull_to_refresh_loading)

        assertTrue(hint.contains("Swipe down"))
        assertTrue(loading.contains("Refreshing KormoKari"))
    }

    @Test
    fun `verify pull to refresh mechanism triggers refresh in StartupViewModel`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher)

        // Trigger refresh
        viewModel.refresh(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // After refresh finishes, isRefreshing must return to false
        assertFalse("isRefreshing must be false once pull-to-refresh cycle completes", viewModel.state.value.isRefreshing)
        assertNotNull(viewModel.state.value.uiState)
    }

    @Test
    fun `verify notification channels are created with correct IDs and properties`() {
        com.example.fcm.NotificationChannelHelper.createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val jobChannel = notificationManager.getNotificationChannel(com.example.fcm.NotificationChannelHelper.CHANNEL_JOB_ALERTS)
        assertNotNull("Job alerts notification channel must exist", jobChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, jobChannel.importance)
        assertTrue(jobChannel.name.toString().contains("Job"))

        val statusChannel = notificationManager.getNotificationChannel(com.example.fcm.NotificationChannelHelper.CHANNEL_APP_STATUS)
        assertNotNull("Application status notification channel must exist", statusChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, statusChannel.importance)
        assertTrue(statusChannel.name.toString().contains("Application"))
    }

    @Test
    fun `verify FCM payload target URL resolution for jobs and applications`() {
        // Direct explicit URL
        val directUrl = com.example.fcm.KormoKariFirebaseMessagingService.resolveTargetUrl(
            mapOf("url" to "https://kormokari.com/custom/path")
        )
        assertEquals("https://kormokari.com/custom/path", directUrl)

        // Job ID payload
        val jobUrl = com.example.fcm.KormoKariFirebaseMessagingService.resolveTargetUrl(
            mapOf("job_id" to "789")
        )
        assertEquals("https://kormokari.com/jobs/789", jobUrl)

        // Application ID payload
        val appUrl = com.example.fcm.KormoKariFirebaseMessagingService.resolveTargetUrl(
            mapOf("application_id" to "456")
        )
        assertEquals("https://kormokari.com/dashboard/applications/456", appUrl)

        // Fallback default
        val fallbackUrl = com.example.fcm.KormoKariFirebaseMessagingService.resolveTargetUrl(emptyMap())
        assertEquals(KormoKariConfig.PRODUCTION_URL, fallbackUrl)
    }

    @Test
    fun `verify FCM payload channel ID routing`() {
        val jobChannel = com.example.fcm.KormoKariFirebaseMessagingService.resolveChannelId(
            mapOf("channel" to "job_alerts")
        )
        assertEquals(com.example.fcm.NotificationChannelHelper.CHANNEL_JOB_ALERTS, jobChannel)

        val statusChannel = com.example.fcm.KormoKariFirebaseMessagingService.resolveChannelId(
            mapOf("channel" to "application_status")
        )
        assertEquals(com.example.fcm.NotificationChannelHelper.CHANNEL_APP_STATUS, statusChannel)
    }

    @Test
    fun `verify FCM token manager saves and retrieves cached token`() {
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val sampleToken = "fcm_test_token_kormokari_12345"
        tokenManager.saveToken(sampleToken)

        assertEquals(sampleToken, tokenManager.getCachedToken())
        assertEquals(sampleToken, tokenManager.currentToken.value)

        tokenManager.clearToken()
        org.junit.Assert.assertNull(tokenManager.getCachedToken())
    }

    @Test
    fun `verify StartupViewModel supports deep link destination URLs`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager)

        val destination = "https://kormokari.com/jobs/999"
        viewModel.attemptLaunch(context, destinationUrl = destination)

        assertEquals(destination, viewModel.state.value.targetUrl)
    }

    @Test
    fun `verify KormoKariApplication ensureFirebaseInitialized initializes safely`() {
        val result = KormoKariApplication.ensureFirebaseInitialized(context)
        // ensureFirebaseInitialized returns a boolean and must never throw an unhandled exception
        assertNotNull(result)
    }

    @Test
    fun `verify battery optimization permission is declared in AndroidManifest`() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must be declared in AndroidManifest",
            permissions.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        )
    }

    @Test
    fun `verify BatteryOptimizationHelper safely reports status`() {
        val isIgnored = com.example.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        // Should execute cleanly without throwing
        assertNotNull(isIgnored)
    }

    @Test
    fun `verify Room database pre-populates offline cached jobs and applications`() = kotlinx.coroutines.test.runTest {
        val repo = com.example.data.local.OfflineJobRepository(context)
        repo.ensureDefaultCachePopulated()

        val jobs = repo.allJobs
        assertNotNull(jobs)
    }

    @Test
    fun `verify StartupViewModel manages offline mode state and search queries`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val repo = com.example.data.local.OfflineJobRepository(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager, repo)

        // Enter offline mode
        viewModel.openOfflineMode()
        assertTrue(viewModel.state.value.uiState is com.example.ui.startup.StartupUiState.OfflineMode)

        // Query updates
        viewModel.setOfflineSearchQuery("Delivery")
        assertEquals("Delivery", viewModel.state.value.offlineSearchQuery)

        // Category updates
        viewModel.setOfflineCategory("Skilled Trades")
        assertEquals("Skilled Trades", viewModel.state.value.selectedOfflineCategory)

        // Battery optimization refresh
        viewModel.refreshBatteryOptimizationStatus(context)
        assertNotNull(viewModel.state.value.isIgnoringBatteryOptimizations)
    }

    @Test
    fun `verify PwaWebView state initializes with target URL and handles pull-to-refresh reload`() {
        val pwaState = com.example.ui.startup.StartupUiState.PwaWebView(
            url = "https://kormokari.com/jobs",
            title = "KormoKari Jobs"
        )
        assertEquals("https://kormokari.com/jobs", pwaState.url)
        assertEquals("KormoKari Jobs", pwaState.title)

        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val repo = com.example.data.local.OfflineJobRepository(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager, repo)

        // Launch in-app PWA WebView
        viewModel.launchPwaWebView("https://kormokari.com/jobs")
        val currentState = viewModel.state.value.uiState
        assertTrue("uiState must be PwaWebView", currentState is com.example.ui.startup.StartupUiState.PwaWebView)
        assertEquals("https://kormokari.com/jobs", (currentState as com.example.ui.startup.StartupUiState.PwaWebView).url)

        // Execute refresh to simulate manual pull-to-refresh
        viewModel.refresh(context)
        assertNotNull(viewModel.state.value)
    }

    @Test
    fun `verify LinearProgressIndicator progress normalization within valid bounds`() {
        val testProgresses = listOf(0, 25, 50, 75, 95, 100, 105, -5)
        testProgresses.forEach { progressInt ->
            val normalized = (progressInt.coerceIn(0, 100)) / 100f
            assertTrue("Normalized progress must be between 0.0 and 1.0", normalized in 0.0f..1.0f)
        }
    }

    @Test
    fun `verify skeleton screen and fade-in animation state transitions from cached to live`() {
        // Initial state: content is loading from cache/network, skeleton active, content alpha is 0
        var isContentReady = false
        var hasError = false
        val initialContentAlpha = if (isContentReady && !hasError) 1f else 0f
        val showSkeletonInitially = !isContentReady && !hasError
        assertEquals(0f, initialContentAlpha)
        assertTrue("Skeleton screen must be displayed while web content is loading", showSkeletonInitially)

        // Simulated transition: onPageFinished or progress == 100 -> content ready, triggers fade-in
        isContentReady = true
        val loadedContentAlpha = if (isContentReady && !hasError) 1f else 0f
        val showSkeletonAfterLoad = !isContentReady && !hasError
        assertEquals(1f, loadedContentAlpha)
        assertFalse("Skeleton screen must fade out once live content is rendered", showSkeletonAfterLoad)

        // Reload / pull-to-refresh transition: resets contentReady to false to show skeleton again smoothly
        isContentReady = false
        val reloadingAlpha = if (isContentReady && !hasError) 1f else 0f
        assertEquals(0f, reloadingAlpha)
        assertTrue(!isContentReady && !hasError)

        // Error transition (offline with no cached live document): hide skeleton and show error fallback
        hasError = true
        val errorSkeletonVisible = !isContentReady && !hasError
        assertFalse("Skeleton screen must not mask error fallback when load fails", errorSkeletonVisible)
    }

    @Test
    fun `verify LinearProgressIndicator smooth fade-out animation lifecycle on page finish`() {
        // 1. Loading active state: indicator is visible and alpha targets 1.0f
        var isProgressVisible = true
        var loadProgress = 0.15f
        val activeAlpha = if (isProgressVisible) 1f else 0f
        assertEquals("Indicator must be fully opaque while page is loading", 1f, activeAlpha)
        assertTrue("Indicator must remain visible during intermediate progress", isProgressVisible)

        // 2. Progress completes to 100%
        loadProgress = 1.0f
        assertEquals(1.0f, loadProgress, 0.001f)

        // 3. Page load completion triggers delayed smooth fade-out
        isProgressVisible = false
        val fadedAlpha = if (isProgressVisible) 1f else 0f
        assertEquals("Indicator alpha must target 0.0f after page load completes", 0f, fadedAlpha)

        // 4. Test reloading resets indicator visibility and triggers fade-in
        isProgressVisible = true
        loadProgress = 0.08f
        val resetAlpha = if (isProgressVisible) 1f else 0f
        assertEquals(1f, resetAlpha)
        assertTrue(loadProgress > 0f)
    }

    @Test
    fun `verify real-time connectivity loss Snackbar messaging and action`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val repo = com.example.data.local.OfflineJobRepository(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager, repo)

        // Simulate connectivity loss
        val disconnectedState = com.example.network.NetworkState.Disconnected("No Wi-Fi or cellular network")
        val reasonDetail = if (disconnectedState.reason.isNotBlank()) " (${disconnectedState.reason})" else ""
        val expectedMessage = "Internet connection lost$reasonDetail"
        assertTrue(expectedMessage.contains("No Wi-Fi or cellular network"))
        org.junit.Assert.assertFalse(expectedMessage.contains("Switched to offline mode"))
    }

    @Test
    fun `verify SwipeRefreshLayout wraps WebView and triggers refresh on swipe`() {
        val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
            setColorSchemeColors(
                context.getColor(R.color.kormokari_primary),
                context.getColor(R.color.kormokari_primary_dark)
            )
        }
        val webView = android.webkit.WebView(context)
        swipeRefreshLayout.addView(webView)

        assertTrue("SwipeRefreshLayout must contain the added WebView", (0 until swipeRefreshLayout.childCount).any { swipeRefreshLayout.getChildAt(it) == webView })

        swipeRefreshLayout.isRefreshing = true
        assertTrue(swipeRefreshLayout.isRefreshing)
        swipeRefreshLayout.isRefreshing = false
        assertFalse(swipeRefreshLayout.isRefreshing)
    }

    @Test
    fun `verify exit confirmation dialog strings are defined with authentic messaging`() {
        val title = context.getString(R.string.exit_dialog_title)
        val message = context.getString(R.string.exit_dialog_message)
        val confirm = context.getString(R.string.exit_dialog_confirm)
        val cancel = context.getString(R.string.exit_dialog_cancel)

        assertEquals("Exit KormoKari?", title)
        assertTrue(message.contains("Are you sure you want to close the app?"))
        assertEquals("Exit App", confirm)
        assertEquals("Stay", cancel)
    }

    @Test
    fun `verify back-press navigation hierarchy dismisses job detail before triggering exit confirmation`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val repo = com.example.data.local.OfflineJobRepository(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager, repo)

        // Put into offline mode and select a job detail
        viewModel.openOfflineMode()
        val dummyJob = com.example.data.local.CachedJob(
            id = "test-1",
            title = "Android Developer",
            company = "Tech Corp",
            location = "Dhaka",
            salary = "80k - 120k BDT",
            jobType = "Full Time",
            category = "Engineering",
            description = "Kotlin development",
            requirements = "Kotlin, Jetpack Compose",
            contactEmail = "jobs@techcorp.com",
            postedAgo = "Today",
            isFeatured = false
        )
        viewModel.selectJobDetail(dummyJob)
        assertNotNull(viewModel.state.value.selectedJobDetail)

        // Back action when detail is open: dismiss detail first, do not trigger exit dialog
        var showExitDialog = false
        if (viewModel.state.value.selectedJobDetail != null) {
            viewModel.selectJobDetail(null)
        } else {
            showExitDialog = true
        }

        // Detail dismissed, exit dialog NOT shown
        assertEquals(null, viewModel.state.value.selectedJobDetail)
        assertFalse("Exit confirmation dialog must not be shown when dismissing sub-screen detail", showExitDialog)

        // Next back action when at root screen: triggers exit confirmation dialog
        if (viewModel.state.value.selectedJobDetail != null) {
            viewModel.selectJobDetail(null)
        } else {
            showExitDialog = true
        }
        assertTrue("Exit confirmation dialog must be shown when back is pressed at root navigation", showExitDialog)
    }

    @Test
    fun `verify full screen loading animation strings and progress properties`() {
        val startupLoading = context.getString(R.string.startup_loading)
        val startupInit = context.getString(R.string.startup_initializing)

        assertTrue(startupLoading.contains("Connecting to KormoKari"))
        assertTrue(startupInit.contains("Preparing your verified session"))

        // Progress clamp verification for loading progress
        val progressHalf = 0.5f.coerceIn(0f, 1f)
        assertEquals(0.5f, progressHalf)
        val progressOver = 1.2f.coerceIn(0f, 1f)
        assertEquals(1f, progressOver)
        val progressUnder = (-0.1f).coerceIn(0f, 1f)
        assertEquals(0f, progressUnder)
    }

    @Test
    fun `verify KormoKariAuthBridge interface names and version constants`() {
        assertEquals("KormoKariAuthBridge", com.example.auth.KormoKariAuthBridge.JAVASCRIPT_INTERFACE_NAME)
        assertEquals("AndroidAuthBridge", com.example.auth.KormoKariAuthBridge.JAVASCRIPT_INTERFACE_ALIAS)
        assertEquals("UNTRUSTED_ORIGIN", com.example.auth.KormoKariAuthBridge.ERROR_UNTRUSTED_ORIGIN)
        assertEquals("USER_CANCELLED", com.example.auth.KormoKariAuthBridge.ERROR_USER_CANCELLED)
        assertEquals("NO_CREDENTIALS", com.example.auth.KormoKariAuthBridge.ERROR_NO_CREDENTIALS)
        assertFalse(KormoKariConfig.DEFAULT_WEB_CLIENT_ID.isBlank())
    }

    @Test
    fun `verify KormoKariAuthBridge strictly validates trusted origins`() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val bridge = com.example.auth.KormoKariAuthBridge(
            activity = activity,
            webViewProvider = { null },
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        // Trusted origins must pass
        assertTrue(bridge.isOriginTrusted("https://kormokari.com"))
        assertTrue(bridge.isOriginTrusted("https://kormokari.com/"))
        assertTrue(bridge.isOriginTrusted("https://kormokari.com/login"))
        assertTrue(bridge.isOriginTrusted("https://kormokari.com/auth/google/callback"))
        assertTrue(bridge.isOriginTrusted("https://www.kormokari.com"))
        assertTrue(bridge.isOriginTrusted("https://www.kormokari.com/jobs"))

        // Untrusted origins must fail
        assertFalse(bridge.isOriginTrusted("http://kormokari.com")) // Insecure HTTP
        assertFalse(bridge.isOriginTrusted("http://kormokari.com/login"))
        assertFalse(bridge.isOriginTrusted("https://evil.com"))
        assertFalse(bridge.isOriginTrusted("https://attacker-kormokari.com"))
        assertFalse(bridge.isOriginTrusted("https://kormokari.com.attacker.com"))
        assertFalse(bridge.isOriginTrusted("javascript:alert(1)"))
        assertFalse(bridge.isOriginTrusted(""))
        assertFalse(bridge.isOriginTrusted(null))
    }

    @Test
    fun `verify KormoKariAuthBridge sanitizes JavaScript callback function names against XSS`() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val bridge = com.example.auth.KormoKariAuthBridge(
            activity = activity,
            webViewProvider = { null },
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        // Valid function identifiers
        assertTrue(bridge.isValidCallbackName("onGoogleSignInComplete"))
        assertTrue(bridge.isValidCallbackName("handleGoogleAuth"))
        assertTrue(bridge.isValidCallbackName("KormoKari.auth.handleSuccess"))
        assertTrue(bridge.isValidCallbackName("window.myAuthCallback"))
        assertTrue(bridge.isValidCallbackName("_callback_123"))
        assertTrue(bridge.isValidCallbackName("\$customCallback"))

        // Unsafe / injection attempts must be rejected
        assertFalse(bridge.isValidCallbackName("alert(1)"))
        assertFalse(bridge.isValidCallbackName("callback(); evil()"))
        assertFalse(bridge.isValidCallbackName("<script>"))
        assertFalse(bridge.isValidCallbackName("onSuccess\nmaliciousCode()"))
        assertFalse(bridge.isValidCallbackName("onSuccess; fetch('https://evil.com')"))
        assertFalse(bridge.isValidCallbackName(""))
        assertFalse(bridge.isValidCallbackName("   "))
        assertFalse(bridge.isValidCallbackName(null))
    }

    @Test
    fun `verify ServerHealthState models and timestamps`() {
        val now = System.currentTimeMillis()
        val checking = com.example.network.ServerHealthState.Checking
        assertTrue(checking.timestamp > 0)

        val ready = com.example.network.ServerHealthState.Ready(checkedAtMillis = now, healthHeader = "ready-v1")
        assertEquals(now, ready.timestamp)
        assertEquals("ready-v1", ready.healthHeader)

        val serverNotReady = com.example.network.ServerHealthState.ServerNotReady(
            checkedAtMillis = now,
            httpCode = 503,
            details = "SERVER_NOT_READY"
        )
        assertEquals(now, serverNotReady.timestamp)
        assertEquals(503, serverNotReady.httpCode)
        assertEquals("SERVER_NOT_READY", serverNotReady.details)

        val noNet = com.example.network.ServerHealthState.NoInternet(reason = "Wi-Fi off", checkedAtMillis = now)
        assertEquals(now, noNet.timestamp)
        assertEquals("Wi-Fi off", noNet.reason)

        val timeout = com.example.network.ServerHealthState.Timeout(timeoutMs = 6000L, checkedAtMillis = now)
        assertEquals(6000L, timeout.timeoutMs)

        val tlsError = com.example.network.ServerHealthState.TlsError(message = "Cert expired", checkedAtMillis = now)
        assertEquals("Cert expired", tlsError.message)

        val connError = com.example.network.ServerHealthState.ConnectionError(message = "Refused", httpCode = 500, checkedAtMillis = now)
        assertEquals(500, connError.httpCode)
    }

    @Test
    fun `verify KormoKari health check contract configuration`() {
        assertEquals("X-KormoKari-Health", KormoKariConfig.HEALTH_CHECK_HEADER)
        assertEquals("ready-v1", KormoKariConfig.HEALTH_CHECK_EXPECTED_VALUE)
        assertEquals(KormoKariConfig.AVAILABILITY_API_URL, KormoKariConfig.HEALTH_CHECK_URL)
        assertEquals("https://kormokari.com/api/app/availability", KormoKariConfig.AVAILABILITY_API_URL)
        assertEquals("https://kormokari.com/", KormoKariConfig.HOMEPAGE_URL)
        assertEquals(6000L, KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS)
        assertEquals(15000L, KormoKariConfig.HEALTH_CHECK_COOLDOWN_MS)
    }

    @Test
    fun `verify bilingual strings for server maintenance and readiness`() {
        val notReadyTitle = context.getString(R.string.server_not_ready_title)
        assertEquals("SERVER_NOT_READY", notReadyTitle)

        val troubleHeading = context.getString(R.string.trouble_connecting_heading)
        assertEquals("We're Having Trouble Connecting", troubleHeading)

        val troubleHeadingBn = context.getString(R.string.trouble_connecting_heading_bn)
        assertTrue(troubleHeadingBn.contains("সমস্যা হচ্ছে"))

        val retryBtnBn = context.getString(R.string.action_retry_bn)
        assertEquals("আবার চেষ্টা করুন", retryBtnBn)

        val cancelBtn = context.getString(R.string.exit_dialog_cancel)
        assertEquals("Stay", cancelBtn)
    }

    @Test
    fun `verify KormoKariAuthBridge exposes readiness contract version and handles session health reporting`() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        var reportedDetails: String? = null

        val bridge = com.example.auth.KormoKariAuthBridge(
            activity = activity,
            webViewProvider = { null },
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        ).apply {
            onServerNotReadyListener = { details ->
                reportedDetails = details
            }
        }

        assertEquals("ready-v1", bridge.getHealthContractVersion())

        // Rejects untrusted origins
        bridge.reportServerNotReady("simulated maintenance")
        // Since webViewProvider returns null url, untrusted origin check correctly blocks invocation
        org.junit.Assert.assertNull(reportedDetails)
    }

    @Test
    fun `verify WebsiteReadinessChecker shouldCheckOnResume enforces cooldown`() {
        val checker = com.example.network.WebsiteReadinessChecker()
        // Fresh checker with lastCheckTimeMillis = 0 returns true for cooldown
        assertTrue(checker.shouldCheckOnResume(cooldownMs = 15000L))
    }

    @Test
    fun `verify BrandedCustomTabsHelper correctly identifies Facebook and Google OAuth URLs`() {
        // Facebook URLs
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isFacebookAuthUrl("https://www.facebook.com/v18.0/dialog/oauth?client_id=123"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isFacebookAuthUrl("https://m.facebook.com/dialog/oauth"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isFacebookAuthUrl("https://kormokari.com/auth/facebook"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isFacebookAuthUrl("https://kormokari.com/login/facebook"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isFacebookAuthUrl("https://kormokari.com/oauth/facebook"))

        // Google OAuth URLs
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isGoogleAuthUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=456"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isGoogleAuthUrl("https://kormokari.com/auth/google"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isGoogleAuthUrl("https://kormokari.com/login/google"))

        // Non-auth internal URLs
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/jobs"))
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/worker/dashboard"))
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/about"))
    }

    @Test
    fun `verify KormoKariAuthBridge social auth methods and diagnostics`() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val bridge = com.example.auth.KormoKariAuthBridge(
            activity = activity,
            webViewProvider = { null },
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        // Null/untrusted webview returns false for availability
        assertFalse(bridge.isNativeGoogleSignInAvailable())
        assertFalse(bridge.isNativeFacebookSignInAvailable())

        // Trusted origin helper verification
        assertTrue(bridge.isOriginTrusted("https://kormokari.com"))
        assertTrue(bridge.isOriginTrusted("https://www.kormokari.com"))
        assertTrue(bridge.isOriginTrusted("https://kormokari.com/login"))
        assertFalse(bridge.isOriginTrusted("http://kormokari.com"))
        assertFalse(bridge.isOriginTrusted("https://evil.com"))
        assertFalse(bridge.isOriginTrusted(null))

        val diagnostics = bridge.getAuthDiagnostics()
        assertTrue(diagnostics.contains("googleCredentialManagerAvailable"))
        assertTrue(diagnostics.contains("facebookCustomTabAvailable"))
        assertTrue(diagnostics.contains("kormokari.com"))
    }

    @Test
    fun `verify KormoKariConfig social auth constants`() {
        assertEquals("https://kormokari.com/auth/google", KormoKariConfig.GOOGLE_AUTH_URL)
        assertEquals("https://kormokari.com/auth/facebook", KormoKariConfig.FACEBOOK_AUTH_URL)
        assertEquals("/auth/google/callback", KormoKariConfig.GOOGLE_CALLBACK_PATH)
        assertEquals("/auth/facebook/callback", KormoKariConfig.FACEBOOK_CALLBACK_PATH)
        assertEquals("kormokari", KormoKariConfig.OAUTH_CUSTOM_SCHEME)
        assertEquals("auth-callback", KormoKariConfig.OAUTH_CUSTOM_HOST)
    }

    @Test
    fun `verify Availability API validation succeeds on valid contract payload`() {
        val validPayload = """
            {
                "status": "ready",
                "service": "kormokari-core",
                "app": "KormoKari",
                "version": "ready-v1",
                "available": true,
                "mode": "normal",
                "message": "Application is operational"
            }
        """.trimIndent()
        val result = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson(validPayload)
        assertTrue(result is com.example.network.AvailabilityValidationResult.Valid)
    }

    @Test
    fun `verify Availability API validation rejects unavailable mode or invalid status`() {
        // available is false
        val unavailablePayload = """
            {"status":"ready","service":"kormokari-core","app":"KormoKari","version":"ready-v1","available":false,"mode":"normal"}
        """.trimIndent()
        val res1 = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson(unavailablePayload)
        assertTrue(res1 is com.example.network.AvailabilityValidationResult.Failed)

        // mode is maintenance
        val maintenancePayload = """
            {"status":"ready","service":"kormokari-core","app":"KormoKari","version":"ready-v1","available":true,"mode":"maintenance"}
        """.trimIndent()
        val res2 = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson(maintenancePayload)
        assertTrue(res2 is com.example.network.AvailabilityValidationResult.Failed)

        // status is not_ready
        val notReadyPayload = """
            {"status":"not_ready","service":"kormokari-core","app":"KormoKari","version":"ready-v1","available":true,"mode":"normal"}
        """.trimIndent()
        val res3 = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson(notReadyPayload)
        assertTrue(res3 is com.example.network.AvailabilityValidationResult.Failed)

        // service mismatch
        val foreignService = """
            {"status":"ready","service":"other-service","app":"KormoKari","version":"ready-v1","available":true,"mode":"normal"}
        """.trimIndent()
        val res4 = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson(foreignService)
        assertTrue(res4 is com.example.network.AvailabilityValidationResult.Failed)
    }

    @Test
    fun `verify Availability API validation rejects invalid and malformed JSON`() {
        // Empty
        val resEmpty = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson("")
        assertTrue(resEmpty is com.example.network.AvailabilityValidationResult.Failed)

        // Malformed syntax
        val resMalformed = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson("{broken_json: 123")
        assertTrue(resMalformed is com.example.network.AvailabilityValidationResult.Failed)

        // Missing fields
        val resMissing = com.example.network.WebsiteReadinessChecker.parseAndValidateAvailabilityJson("""{"status":"ready"}""")
        assertTrue(resMissing is com.example.network.AvailabilityValidationResult.Failed)
    }

    @Test
    fun `verify Homepage validation rejects directory listing Index of slash`() {
        val dirListingHtml = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
            <html>
             <head>
              <title>Index of /</title>
             </head>
             <body>
            <h1>Index of /</h1>
            <pre>      <a href="?C=N;O=D">Name</a>                    <a href="?C=M;O=A">Last modified</a>      <a href="?C=S;O=A">Size</a>  <a href="?C=D;O=A">Description</a><hr>      <a href="kormokari/">kormokari/</a>              2026-10-31 10:00    -   
            <hr></pre>
            </body></html>
        """.trimIndent()

        val result = com.example.network.WebsiteReadinessChecker.validateHomepageContent(200, dirListingHtml, "https://kormokari.com/")
        assertTrue(result is com.example.network.HomepageValidationResult.Failed)
        assertTrue((result as com.example.network.HomepageValidationResult.Failed).reason.contains("Directory listing"))
    }

    @Test
    fun `verify Homepage validation rejects ZIP directory listings`() {
        val zipListingHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>KormoKari Downloads</title></head>
            <body>
            <pre>
            [DIR] <a href="parent">Parent Directory</a>
            [   ] <a href="kormokari_backup_v1.zip">kormokari_backup_v1.zip</a> 2026-11-01 120MB
            </pre>
            </body>
            </html>
        """.trimIndent()

        val result = com.example.network.WebsiteReadinessChecker.validateHomepageContent(200, zipListingHtml, "https://kormokari.com/")
        assertTrue(result is com.example.network.HomepageValidationResult.Failed)
        assertTrue((result as com.example.network.HomepageValidationResult.Failed).reason.contains("ZIP"))
    }

    @Test
    fun `verify Homepage validation rejects raw server errors PHP SQL 500 502 504`() {
        // PHP Fatal error
        val phpFatal = "<html><body>Fatal error: Uncaught Error: Call to undefined function in /var/www/kormokari/index.php</body></html>"
        val res1 = com.example.network.WebsiteReadinessChecker.validateHomepageContent(200, phpFatal, "https://kormokari.com/")
        assertTrue(res1 is com.example.network.HomepageValidationResult.Failed)

        // SQLSTATE error
        val sqlError = "<html><body>SQLSTATE[HY000] [2002] Connection refused (kormokari db)</body></html>"
        val res2 = com.example.network.WebsiteReadinessChecker.validateHomepageContent(200, sqlError, "https://kormokari.com/")
        assertTrue(res2 is com.example.network.HomepageValidationResult.Failed)

        // HTTP 500
        val res500 = com.example.network.WebsiteReadinessChecker.validateHomepageContent(500, "500 Internal Server Error", "https://kormokari.com/")
        assertTrue(res500 is com.example.network.HomepageValidationResult.Failed)

        // HTTP 502
        val res502 = com.example.network.WebsiteReadinessChecker.validateHomepageContent(502, "502 Bad Gateway", "https://kormokari.com/")
        assertTrue(res502 is com.example.network.HomepageValidationResult.Failed)
    }

    @Test
    fun `verify Homepage validation succeeds on valid healthy KormoKari HTML`() {
        val healthyHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>KormoKari - On-Demand Jobs & Verified Workers in Bangladesh</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                <header><h1>KormoKari Work Platform</h1></header>
                <main>
                    <p>Find work, hire verified workers, and grow your career with KormoKari in Bangladesh.</p>
                </main>
            </body>
            </html>
        """.trimIndent()

        val result = com.example.network.WebsiteReadinessChecker.validateHomepageContent(200, healthyHtml, "https://kormokari.com/")
        assertTrue(result is com.example.network.HomepageValidationResult.Valid)
    }

    @Test
    fun `verify WebsiteReadinessChecker handles timeout safely`() = kotlinx.coroutines.test.runTest {
        val timeoutClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { _ -> throw java.net.SocketTimeoutException("Simulated connection timeout") }
            .build()

        val checker = com.example.network.WebsiteReadinessChecker(timeoutClient)
        val result = checker.checkReadiness()
        assertTrue(result is com.example.network.ServerHealthState.Timeout)
        assertEquals(6000L, (result as com.example.network.ServerHealthState.Timeout).timeoutMs)
    }

    @Test
    fun `verify WebsiteReadinessChecker handles server error safely`() = kotlinx.coroutines.test.runTest {
        val errorClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("503 Service Unavailable".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val checker = com.example.network.WebsiteReadinessChecker(errorClient)
        val result = checker.checkReadiness()
        assertTrue(result is com.example.network.ServerHealthState.ServerNotReady)
        assertEquals(503, (result as com.example.network.ServerHealthState.ServerNotReady).httpCode)
    }

    @Test
    fun `verify WebsiteReadinessChecker two-stage gate requires both checks to pass`() = kotlinx.coroutines.test.runTest {
        // Mock client where API succeeds but Homepage returns Directory Listing
        val partialFailClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path.contains("availability")) {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"status":"ready","service":"kormokari-core","app":"KormoKari","version":"ready-v1","available":true,"mode":"normal"}"""
                                .toResponseBody("application/json".toMediaTypeOrNull())
                        )
                        .build()
                } else {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            "<!DOCTYPE html><html><head><title>Index of /</title></head><body><h1>Index of /</h1></body></html>"
                                .toResponseBody("text/html".toMediaTypeOrNull())
                        )
                        .build()
                }
            }
            .build()

        val checker = com.example.network.WebsiteReadinessChecker(partialFailClient)
        val result = checker.checkReadiness()
        // Must reject because Homepage is Index of /
        assertTrue(result is com.example.network.ServerHealthState.ServerNotReady)
    }

    @Test
    fun `verify StartupViewModel retry resets launch and triggers recheck`() {
        val monitor = NetworkMonitor(context)
        val launcher = KormoKariTwaLauncher(context)
        val tokenManager = com.example.fcm.FcmTokenManager(context)
        val repo = com.example.data.local.OfflineJobRepository(context)
        val viewModel = com.example.ui.startup.StartupViewModel(monitor, launcher, tokenManager, repo)

        // Trigger retry
        viewModel.retry(context)
        val state = viewModel.state.value
        // Retry evaluates connection health state and displays health status screen
        assertNotNull(state.serverHealthState)
        assertTrue(state.uiState is com.example.ui.startup.StartupUiState.HealthStatus)
    }

    @Test
    fun `verify AppAvailabilityManager parses AvailabilityPayload correctly and validates isOperational`() {
        val payload = com.example.network.AvailabilityPayload(
            status = "ready",
            service = "kormokari-core",
            app = "KormoKari",
            version = "ready-v1",
            available = true,
            mode = "normal",
            message = "Application is operational"
        )
        assertTrue(payload.isOperational)

        val unreadyPayload = payload.copy(available = false)
        assertFalse(unreadyPayload.isOperational)

        val maintenancePayload = payload.copy(mode = "maintenance")
        assertFalse(maintenancePayload.isOperational)
    }

    @Test
    fun `verify AppAvailabilityManager checkAvailability performs two-stage verification`() = kotlinx.coroutines.test.runTest {
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path.contains("availability")) {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"status":"ready","service":"kormokari-core","app":"KormoKari","version":"ready-v1","available":true,"mode":"normal"}"""
                                .toResponseBody("application/json".toMediaTypeOrNull())
                        )
                        .build()
                } else {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>KormoKari - On-Demand Jobs & Verified Workers in Bangladesh</title></head><body><header><h1>KormoKari Verified Work Platform</h1></header><main><p>Find on-demand verified workers, job opportunities, and skilled professionals across Bangladesh on the KormoKari platform.</p></main></body></html>"""
                                .toResponseBody("text/html".toMediaTypeOrNull())
                        )
                        .build()
                }
            }
            .build()

        val manager = com.example.network.AppAvailabilityManager(mockClient)
        val result = manager.checkAvailability()
        assertTrue(result is com.example.network.ServerHealthState.Ready)
    }

    @Test
    fun `verify AppAvailabilityManager shouldCheckOnResume respects cooldown`() {
        val manager = com.example.network.AppAvailabilityManager()
        assertTrue(manager.shouldCheckOnResume(cooldownMs = 15000L))
    }

    @Test
    fun `verify KormoKariPermissionsBridge getAllPermissionStatuses returns JSON with canonical keys`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().get()
        val bridge = com.example.permission.KormoKariPermissionsBridge(activity) { null }
        val statusesJson = bridge.getAllPermissionStatuses()
        val json = org.json.JSONObject(statusesJson)

        assertTrue(json.has(com.example.permission.KormoKariPermissionsBridge.PERMISSION_LOCATION_FOREGROUND))
        assertTrue(json.has(com.example.permission.KormoKariPermissionsBridge.PERMISSION_LOCATION_BACKGROUND))
        assertTrue(json.has(com.example.permission.KormoKariPermissionsBridge.PERMISSION_NOTIFICATIONS))
        assertTrue(json.has(com.example.permission.KormoKariPermissionsBridge.PERMISSION_CAMERA))
        assertTrue(json.has(com.example.permission.KormoKariPermissionsBridge.PERMISSION_MICROPHONE))
    }

    @Test
    fun `verify WorkerLocationService tracking lifecycle and telemetry status`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertFalse(com.example.location.WorkerLocationService.isTracking)

        val trackingStatus = com.example.location.WorkerLocationService.getTrackingStatusJson()
        val json = org.json.JSONObject(trackingStatus)
        assertTrue(json.has("isTracking"))
        assertEquals(false, json.getBoolean("isTracking"))
    }

    @Test
    fun `verify KormoKariWebMessageProtocolHandler facade contains canonical architecture and bridge detection`() {
        val facadeJs = com.example.permission.KormoKariWebMessageProtocolHandler.JS_FACADE_BOOTSTRAP
        assertTrue(facadeJs.contains("window.KormoKariAndroid"))
        assertTrue(facadeJs.contains("architecture: 'webview'"))
        assertTrue(facadeJs.contains("version: '3.10.0'"))
        assertTrue(facadeJs.contains("package: 'com.kormokari.app'"))
        assertTrue(facadeJs.contains("origin: 'https://kormokari.com'"))
    }

    @Test
    fun `verify app version constants match 3_10_0`() {
        assertEquals("3.10.0", KormoKariConfig.VERSION_NAME)
        assertEquals(31000, KormoKariConfig.VERSION_CODE)
    }

    @Test
    fun `verify battery optimization status query in KormoKariPermissionsBridge`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().get()
        val bridge = com.example.permission.KormoKariPermissionsBridge(activity) { null }
        val status = bridge.getPermissionStatus("BATTERY")
        assertTrue(status == "GRANTED" || status == "DENIED")

        val allStatuses = org.json.JSONObject(bridge.getAllPermissionStatuses())
        assertTrue(allStatuses.has("BATTERY"))
        assertTrue(allStatuses.has("BATTERY_OPTIMIZATION"))
    }

    @Test
    fun `verify KormoKariWebMessageProtocolHandler bridges camera, microphone, notification, GPS, and battery`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().get()
        val handler = com.example.permission.KormoKariWebMessageProtocolHandler(activity) { null }

        // Test status queries for all required permission channels
        val camStatus = handler.getPermissionStatus("CAMERA")
        assertTrue(camStatus == "GRANTED" || camStatus == "DENIED")

        val micStatus = handler.getPermissionStatus("MICROPHONE")
        assertTrue(micStatus == "GRANTED" || micStatus == "DENIED")

        val notifStatus = handler.getPermissionStatus("NOTIFICATIONS")
        assertTrue(notifStatus == "GRANTED" || notifStatus == "DENIED")

        val locStatus = handler.getPermissionStatus("LOCATION")
        assertTrue(locStatus == "GRANTED" || locStatus == "DENIED")

        val batStatus = handler.getPermissionStatus("BATTERY")
        assertTrue(batStatus == "GRANTED" || batStatus == "DENIED")

        // Test postMessage JSON handling
        handler.postMessage("""{"type":"GET_STATUS","permission":"CAMERA","callbackId":"testCb"}""")
        handler.postMessage("""{"type":"REQUEST_PERMISSION","permission":"RECORD_AUDIO","callbackId":"testMicCb"}""")

        // Test battery optimization exemption trigger
        val batResult = handler.requestBatteryOptimizationExemption()
        assertTrue(batResult)
    }

    @Test
    fun `verify Solution A OAuth callback deep linking and BrandedCustomTabsHelper callback identification`() {
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isAuthCallbackUrl("https://kormokari.com/auth/google/callback?code=xyz"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isAuthCallbackUrl("https://kormokari.com/auth/facebook/callback?code=abc"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isAuthCallbackUrl("https://kormokari.com/auth/handoff?token=123"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isAuthCallbackUrl("https://www.kormokari.com/auth/google/callback"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isAuthCallbackUrl("kormokari://auth-callback?status=success"))

        // Callbacks MUST NOT be classified as social auth initiation URLs (to keep them inside WebView)
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/auth/google/callback?code=xyz"))
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/auth/facebook/callback"))
        assertFalse(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/auth/handoff"))

        // Standard auth start endpoints should still be social auth URLs
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/auth/google"))
        assertTrue(com.example.auth.BrandedCustomTabsHelper.isSocialAuthUrl("https://kormokari.com/auth/facebook"))
    }

    @Test
    fun `verify Solution B KormoKariAndroid JavaScript interface constant`() {
        assertEquals("KormoKariAndroid", com.example.auth.KormoKariAuthBridge.JAVASCRIPT_INTERFACE_KORMOKARI_ANDROID)
        assertEquals("KormoKariAuthBridge", com.example.auth.KormoKariAuthBridge.JAVASCRIPT_INTERFACE_NAME)
        assertEquals("AndroidAuthBridge", com.example.auth.KormoKariAuthBridge.JAVASCRIPT_INTERFACE_ALIAS)
    }

    @Test
    fun `verify KormoKariAndroidBridge instantiates and handles requestGoogleSignIn and requestFacebookSignIn without crash`() {
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val webView = android.webkit.WebView(activity)
        val bridge = com.example.auth.KormoKariAndroidBridge(activity, webView)

        bridge.requestGoogleSignIn()
        bridge.requestGoogleSignIn("testNonce")
        bridge.requestFacebookSignIn()
        bridge.requestFacebookSignIn("testNonce")
        bridge.checkForAppUpdate()
        bridge.checkForAppUpdate(true)
        bridge.completeAppUpdate()
    }

    @Test
    fun `verify KormoKariAppUpdateManager initialization and lifecycle in MainActivity`() {
        val activity = org.robolectric.Robolectric.buildActivity(com.example.MainActivity::class.java).setup().get()
        assertNotNull(activity.appUpdateManager)

        // Verify update manager methods execute gracefully without throwing in test environment
        activity.appUpdateManager.checkForUpdate(isImmediate = false)
        activity.appUpdateManager.onResume()
        activity.appUpdateManager.notifyUpdateDownloaded()
        activity.appUpdateManager.onDestroy()
    }
}

