package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.config.KormoKariConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play In-App Updates (Play Core library) for KormoKari.
 * 
 * Supports both:
 * 1. Flexible updates: downloads in the background while user continues browsing jobs/dashboard,
 *    prompting to restart once the download completes.
 * 2. Immediate updates: full-screen flow for critical/mandatory releases.
 * 3. Graceful fallback for non-Play Store sideloaded environments and emulators.
 */
class KormoKariAppUpdateManager(
    private val activity: ComponentActivity
) {
    companion object {
        private const val TAG = "KormoKariUpdateMgr"
        const val UPDATE_REQUEST_CODE = 4096
    }

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    private val _updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()

    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                Log.d(TAG, "Update downloading: $progress% ($bytesDownloaded / $totalBytes)")
                _updateState.value = AppUpdateUiState.Downloading(progress)
            }
            InstallStatus.DOWNLOADED -> {
                Log.i(TAG, "Update download complete, ready for installation")
                _updateState.value = AppUpdateUiState.Downloaded
                notifyUpdateDownloaded()
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "Update installation failed with error code: ${state.installErrorCode()}")
                _updateState.value = AppUpdateUiState.Error("Update failed (code ${state.installErrorCode()})")
            }
            InstallStatus.CANCELED -> {
                Log.i(TAG, "Update was canceled")
                _updateState.value = AppUpdateUiState.Idle
            }
            else -> {
                Log.d(TAG, "Install status: ${state.installStatus()}")
            }
        }
    }

    init {
        appUpdateManager.registerListener(installStateUpdatedListener)
    }

    /**
     * Registers the ActivityResultLauncher to start update intents via Activity Result API.
     * Must be called in Activity.onCreate.
     */
    fun registerLauncher() {
        updateLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) {
                Log.w(TAG, "Update flow failed or was canceled by user. Result code: ${result.resultCode}")
                _updateState.value = AppUpdateUiState.Idle
            } else {
                Log.i(TAG, "Update flow started successfully")
            }
        }
    }

    /**
     * Checks Google Play for available in-app updates.
     * 
     * @param isImmediate Whether to enforce an immediate/blocking update instead of a flexible one.
     * @param onCheckComplete Optional callback with update availability status.
     */
    fun checkForUpdate(
        isImmediate: Boolean = false,
        onCheckComplete: ((isAvailable: Boolean, availableVersionCode: Int) -> Unit)? = null
    ) {
        activity.runOnUiThread {
            try {
                _updateState.value = AppUpdateUiState.Checking
                val appUpdateInfoTask = appUpdateManager.appUpdateInfo

                appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                    val availability = appUpdateInfo.updateAvailability()
                    val availableVersion = appUpdateInfo.availableVersionCode()
                    Log.i(TAG, "Play update check completed: availability=$availability, availableVersionCode=$availableVersion, currentVersion=${KormoKariConfig.VERSION_CODE}")

                    val isAvailable = availability == UpdateAvailability.UPDATE_AVAILABLE
                    onCheckComplete?.invoke(isAvailable, availableVersion)

                    if (isAvailable) {
                        val requestedType = if (isImmediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
                        val updateTypeAllowed = appUpdateInfo.isUpdateTypeAllowed(requestedType)

                        if (updateTypeAllowed) {
                            startUpdateFlow(appUpdateInfo, requestedType)
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            // Fallback to immediate if flexible is not allowed
                            startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE)
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            // Fallback to flexible
                            startUpdateFlow(appUpdateInfo, AppUpdateType.FLEXIBLE)
                        } else {
                            Log.w(TAG, "Update available, but neither IMMEDIATE nor FLEXIBLE update type is allowed")
                            _updateState.value = AppUpdateUiState.Idle
                        }
                    } else {
                        _updateState.value = AppUpdateUiState.UpToDate
                    }
                }.addOnFailureListener { exception ->
                    Log.w(TAG, "Play In-App Update check failed (expected in sideloaded/debug builds without Play Store connection): ${exception.message}")
                    _updateState.value = AppUpdateUiState.Idle
                    onCheckComplete?.invoke(false, KormoKariConfig.VERSION_CODE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during check for update: ${e.message}")
                _updateState.value = AppUpdateUiState.Idle
                onCheckComplete?.invoke(false, KormoKariConfig.VERSION_CODE)
            }
        }
    }

    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo, updateType: Int) {
        val launcher = updateLauncher
        val options = AppUpdateOptions.newBuilder(updateType).build()

        if (launcher != null) {
            try {
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, options)
                Log.i(TAG, "Started in-app update flow (type=$updateType) with Activity Result Launcher")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start update flow with launcher: ${e.message}, falling back to legacy startUpdateFlowForResult")
                tryLegacyStart(appUpdateInfo, options)
            }
        } else {
            tryLegacyStart(appUpdateInfo, options)
        }
    }

    private fun tryLegacyStart(appUpdateInfo: AppUpdateInfo, options: AppUpdateOptions) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                options,
                UPDATE_REQUEST_CODE
            )
            Log.i(TAG, "Started in-app update flow with legacy startUpdateFlowForResult")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch in-app update flow: ${e.message}")
            _updateState.value = AppUpdateUiState.Error("Unable to open update dialog")
        }
    }

    /**
     * Resumes in-progress updates in Activity.onResume.
     * If an immediate update was triggered, resumes the prompt.
     * If a flexible update finished downloading in the background, shows completion prompt.
     */
    fun onResume() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    _updateState.value = AppUpdateUiState.Downloaded
                    notifyUpdateDownloaded()
                } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // Resume immediate update
                    Log.i(TAG, "Resuming developer-triggered immediate update in progress")
                    startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking resume status: ${e.message}")
        }
    }

    /**
     * Displays a non-intrusive prompt/Snackbar to complete the downloaded update.
     */
    fun notifyUpdateDownloaded() {
        activity.runOnUiThread {
            try {
                android.app.AlertDialog.Builder(activity)
                    .setTitle("Update Downloaded")
                    .setMessage("An update for KormoKari has been downloaded. Restart the app now to complete the update.")
                    .setPositiveButton("Restart") { _, _ ->
                        completeUpdate()
                    }
                    .setNegativeButton("Later", null)
                    .show()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to show AlertDialog, falling back to Toast: ${e.message}")
                android.widget.Toast.makeText(
                    activity,
                    "An update for KormoKari has been downloaded. Restart to complete update.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Finalizes installation and restarts the app with the newly installed version.
     */
    fun completeUpdate() {
        Log.i(TAG, "Invoking completeUpdate() to restart application with new version")
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking completeUpdate(): ${e.message}")
        }
    }

    /**
     * Unregisters the listener to prevent memory leaks on Activity destroy.
     */
    fun onDestroy() {
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (_: Exception) {}
    }
}

/**
 * UI State representing the status of Google Play In-App Updates.
 */
sealed class AppUpdateUiState {
    object Idle : AppUpdateUiState()
    object Checking : AppUpdateUiState()
    data class Downloading(val progressPercent: Int) : AppUpdateUiState()
    object Downloaded : AppUpdateUiState()
    object UpToDate : AppUpdateUiState()
    data class Error(val message: String) : AppUpdateUiState()
}
