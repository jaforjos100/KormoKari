package com.example.ui.startup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.config.KormoKariConfig
import com.example.fcm.FcmTokenManager
import com.example.network.NetworkMonitor
import com.example.network.NetworkState
import com.example.twa.KormoKariTwaLauncher
import com.example.twa.LaunchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StartupViewModel(
    private val networkMonitor: NetworkMonitor,
    private val twaLauncher: KormoKariTwaLauncher,
    private val fcmTokenManager: FcmTokenManager? = null,
    private val offlineJobRepository: com.example.data.local.OfflineJobRepository? = null,
    private val readinessChecker: com.example.network.WebsiteReadinessChecker = com.example.network.WebsiteReadinessChecker()
) : ViewModel() {

    private val _state = MutableStateFlow(StartupScreenState())
    val state: StateFlow<StartupScreenState> = _state.asStateFlow()

    private var launchJob: Job? = null
    private var hasInitiatedLaunch = false
    private var targetDestinationUrl: String = KormoKariConfig.PRODUCTION_URL

    var isReadyForTransition: Boolean = false
        private set

    init {
        // Probe current network synchronously and stream continuous updates
        viewModelScope.launch {
            val initialNet = networkMonitor.checkCurrentNetwork()
            _state.update { it.copy(networkState = initialNet) }
            isReadyForTransition = true

            networkMonitor.networkState.collect { netState ->
                _state.update { it.copy(networkState = netState) }
            }
        }

        // Initialize FCM token listener and fetch fresh registration token
        if (fcmTokenManager != null) {
            viewModelScope.launch {
                fcmTokenManager.currentToken.collect { token ->
                    _state.update { it.copy(fcmToken = token) }
                }
            }
            viewModelScope.launch {
                fcmTokenManager.fetchFreshToken()
            }
        }

        // Initialize local Room cache for offline read-only capabilities
        if (offlineJobRepository != null) {
            viewModelScope.launch {
                offlineJobRepository.ensureDefaultCachePopulated()
            }
            viewModelScope.launch {
                offlineJobRepository.allJobs.collect { jobs ->
                    _state.update { it.copy(cachedJobs = jobs) }
                }
            }
            viewModelScope.launch {
                offlineJobRepository.allApplications.collect { apps ->
                    _state.update { it.copy(cachedApplications = apps) }
                }
            }
        }

        // Load device browser diagnostics
        _state.update {
            it.copy(diagnostics = twaLauncher.getBrowserDiagnostics())
        }
    }

    /**
     * Determines whether the native androidx.core.splashscreen should remain visible.
     * Implements the keepOnScreenCondition to ensure the native splash remains visible
     * until the TWA content is ready for interaction, or an error state occurs.
     */
    fun shouldKeepSplashScreenOnScreen(): Boolean {
        return _state.value.uiState is StartupUiState.AnimatedSplash
    }

    /**
     * Initiates the verification and TWA launch flow without artificial delay.
     * Supports opening specific deep linked destinations (e.g., job postings from push notifications).
     */
    fun attemptLaunch(
        context: Context,
        destinationUrl: String? = null,
        forceRelaunch: Boolean = false
    ) {
        if (!destinationUrl.isNullOrBlank()) {
            targetDestinationUrl = destinationUrl
            _state.update { it.copy(targetUrl = destinationUrl) }
        }

        if (forceRelaunch) {
            hasInitiatedLaunch = false
            launchJob?.cancel()
        } else if (hasInitiatedLaunch || launchJob?.isActive == true) {
            return
        }

        launchJob = viewModelScope.launch {
            val currentNetwork = networkMonitor.checkCurrentNetwork()
            _state.update { it.copy(networkState = currentNetwork) }

            when (currentNetwork) {
                is NetworkState.Disconnected -> {
                    val noInternetState = com.example.network.ServerHealthState.NoInternet(
                        reason = context.getString(R.string.no_connection_subtitle)
                    )
                    _state.update {
                        it.copy(
                            serverHealthState = noInternetState,
                            uiState = StartupUiState.HealthStatus(noInternetState),
                            isRefreshing = false
                        )
                    }
                    return@launch
                }
                is NetworkState.Connected,
                is NetworkState.Checking -> {
                    // Pre-launch authoritative readiness check enforcing X-KormoKari-Health: ready-v1
                    _state.update {
                        it.copy(
                            serverHealthState = com.example.network.ServerHealthState.Checking
                        )
                    }

                    val healthResult = readinessChecker.checkReadiness()
                    _state.update { it.copy(serverHealthState = healthResult) }

                    when (healthResult) {
                        is com.example.network.ServerHealthState.Ready -> {
                            hasInitiatedLaunch = true
                            _state.update {
                                it.copy(
                                    uiState = StartupUiState.PwaWebView(targetDestinationUrl),
                                    isRefreshing = false
                                )
                            }
                        }
                        is com.example.network.ServerHealthState.Checking -> {
                            // Probe in progress, retain checking state
                        }
                        is com.example.network.ServerHealthState.ServerNotReady,
                        is com.example.network.ServerHealthState.NoInternet,
                        is com.example.network.ServerHealthState.ConnectionError,
                        is com.example.network.ServerHealthState.Timeout,
                        is com.example.network.ServerHealthState.TlsError -> {
                            _state.update {
                                it.copy(
                                    uiState = StartupUiState.HealthStatus(healthResult),
                                    isRefreshing = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * User-triggered retry from the health status or error surface.
     */
    fun retry(context: Context) {
        hasInitiatedLaunch = false
        _state.update {
            it.copy(
                isRefreshing = true,
                serverHealthState = com.example.network.ServerHealthState.Checking,
                uiState = StartupUiState.HealthStatus(com.example.network.ServerHealthState.Checking)
            )
        }
        attemptLaunch(context, targetDestinationUrl, forceRelaunch = true)
    }

    /**
     * User-triggered pull-to-refresh to reload PWA content and re-check readiness.
     */
    fun refresh(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val currentNetwork = networkMonitor.checkCurrentNetwork()
            _state.update { it.copy(networkState = currentNetwork) }

            if (currentNetwork is NetworkState.Disconnected) {
                val noInternetState = com.example.network.ServerHealthState.NoInternet(
                    reason = context.getString(R.string.no_connection_subtitle)
                )
                _state.update {
                    it.copy(
                        serverHealthState = noInternetState,
                        uiState = StartupUiState.HealthStatus(noInternetState),
                        isRefreshing = false
                    )
                }
            } else {
                val healthResult = readinessChecker.checkReadiness()
                _state.update { it.copy(serverHealthState = healthResult) }

                when (healthResult) {
                    is com.example.network.ServerHealthState.Ready -> {
                        hasInitiatedLaunch = true
                        _state.update {
                            it.copy(
                                uiState = StartupUiState.PwaWebView(targetDestinationUrl),
                                isRefreshing = false
                            )
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                uiState = StartupUiState.HealthStatus(healthResult),
                                isRefreshing = false
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Report an active session health failure (e.g. from active-session-health.js or HTTP 503).
     */
    fun reportServerNotReady(details: String? = null, httpCode: Int = 503) {
        val notReady = com.example.network.ServerHealthState.ServerNotReady(
            checkedAtMillis = System.currentTimeMillis(),
            httpCode = httpCode,
            details = details
        )
        _state.update {
            it.copy(
                serverHealthState = notReady,
                uiState = StartupUiState.HealthStatus(notReady),
                isRefreshing = false
            )
        }
    }

    /**
     * External health state update dispatcher.
     */
    fun reportHealthState(healthState: com.example.network.ServerHealthState) {
        _state.update {
            it.copy(
                serverHealthState = healthState,
                uiState = if (healthState is com.example.network.ServerHealthState.Ready) {
                    StartupUiState.PwaWebView(targetDestinationUrl)
                } else {
                    StartupUiState.HealthStatus(healthState)
                },
                isRefreshing = false
            )
        }
    }

    /**
     * Fallback to direct external browser intent if custom tabs failed.
     */
    fun openExternalBrowser(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetDestinationUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    uiState = StartupUiState.Error(
                        title = "Browser Unavailable",
                        message = "Could not locate a browser application to open $targetDestinationUrl",
                        canRetry = true
                    )
                )
            }
        }
    }

    /**
     * Launches Android device wireless or Wi-Fi settings to assist user in reconnecting.
     */
    fun openNetworkSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(wifiIntent)
            } catch (_: Exception) {
                // Silently ignore if neither settings intent resolves
            }
        }
    }

    /**
     * Activates the in-app PWA WebView container with pull-to-refresh capabilities.
     */
    fun launchPwaWebView(url: String = targetDestinationUrl) {
        hasInitiatedLaunch = true
        _state.update {
            it.copy(
                uiState = StartupUiState.PwaWebView(url),
                isRefreshing = false
            )
        }
    }

    /**
     * Activates the local read-only offline experience showing cached jobs and applications.
     */
    fun openOfflineMode() {
        _state.update { it.copy(uiState = StartupUiState.OfflineMode) }
    }

    /**
     * Closes offline mode and attempts to reconnect to the live web application.
     */
    fun closeOfflineMode(context: Context) {
        _state.update { it.copy(uiState = StartupUiState.AnimatedSplash) }
        attemptLaunch(context, forceRelaunch = true)
    }

    fun setOfflineSearchQuery(query: String) {
        _state.update { it.copy(offlineSearchQuery = query) }
    }

    fun setOfflineCategory(category: String?) {
        _state.update { it.copy(selectedOfflineCategory = category) }
    }

    fun selectJobDetail(job: com.example.data.local.CachedJob?) {
        _state.update { it.copy(selectedJobDetail = job) }
    }

    fun refreshBatteryOptimizationStatus(context: Context) {
        val isIgnored = com.example.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        _state.update { it.copy(isIgnoringBatteryOptimizations = isIgnored) }
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        com.example.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context)
        refreshBatteryOptimizationStatus(context)
    }

    fun onResumeActivity(context: Context) {
        refreshBatteryOptimizationStatus(context)
        // Respect authoritative readiness check cooldown on resume
        if (readinessChecker.shouldCheckOnResume()) {
            val currentState = _state.value.uiState
            // If in non-ready health state, or error, or initial launch hasn't occurred, re-verify readiness
            if (currentState is StartupUiState.HealthStatus || currentState is StartupUiState.Error || !hasInitiatedLaunch) {
                attemptLaunch(context, targetDestinationUrl, forceRelaunch = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        twaLauncher.destroy()
    }
}
