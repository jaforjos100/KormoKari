package com.example.ui.startup

import com.example.network.NetworkState
import com.example.twa.BrowserDiagnostics

sealed interface StartupUiState {
    /**
     * Phase 3 Native Animated Splash with two orbital rings, central radial glow,
     * KormoKari wordmark ("Kormo" in pink, "Kari" in navy), tagline, and 3 clockwise orbital dots.
     */
    data object AnimatedSplash : StartupUiState

    /**
     * TWA/Custom Tab is active in foreground.
     */
    data class TwaLaunched(val packageUsed: String) : StartupUiState

    /**
     * Actionable error/recovery state shown only if connection fails.
     */
    data class Error(
        val title: String,
        val message: String,
        val canRetry: Boolean = true,
        val showBrowserOption: Boolean = false,
        val isNetworkError: Boolean = false
    ) : StartupUiState

    /**
     * Dedicated local read-only offline mode powered by Room cache.
     */
    data object OfflineMode : StartupUiState

    /**
     * Interactive in-app PWA WebView component with integrated pull-to-refresh mechanism.
     */
    data class PwaWebView(
        val url: String = "https://kormokari.com/",
        val title: String = "KormoKari"
    ) : StartupUiState

    /**
     * Authoritative website health status / server readiness gate.
     */
    data class HealthStatus(val healthState: com.example.network.ServerHealthState) : StartupUiState
}

data class StartupScreenState(
    val uiState: StartupUiState = StartupUiState.AnimatedSplash,
    val networkState: NetworkState = NetworkState.Checking,
    val serverHealthState: com.example.network.ServerHealthState = com.example.network.ServerHealthState.Checking,
    val diagnostics: BrowserDiagnostics? = null,
    val isRefreshing: Boolean = false,
    val fcmToken: String? = null,
    val targetUrl: String = "https://kormokari.com/",
    val cachedJobs: List<com.example.data.local.CachedJob> = emptyList(),
    val cachedApplications: List<com.example.data.local.CachedApplication> = emptyList(),
    val selectedOfflineCategory: String? = null,
    val offlineSearchQuery: String = "",
    val isIgnoringBatteryOptimizations: Boolean = false,
    val selectedJobDetail: com.example.data.local.CachedJob? = null,
    val webViewProgress: Int = 0,
    val isPwaLoading: Boolean = false,
    val canGoBackInPwa: Boolean = false,
    val pwaError: String? = null
)
