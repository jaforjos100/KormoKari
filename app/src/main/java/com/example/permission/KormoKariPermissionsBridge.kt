package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.location.WorkerLocationService
import com.example.util.BatteryOptimizationHelper
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Native JavaScript Interface Bridge exposing canonical permission and dispatch controls
 * to kormokari-permission-engine.js and the KormoKari PWA.
 */
class KormoKariPermissionsBridge(
    private val activity: Activity,
    private val webViewProvider: () -> WebView?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val PERMISSION_LOCATION_FOREGROUND = "ACCESS_FINE_LOCATION"
        const val PERMISSION_LOCATION_BACKGROUND = "ACCESS_BACKGROUND_LOCATION"
        const val PERMISSION_NOTIFICATIONS = "POST_NOTIFICATIONS"
        const val PERMISSION_CAMERA = "CAMERA"
        const val PERMISSION_MICROPHONE = "RECORD_AUDIO"

        const val STATUS_GRANTED = "GRANTED"
        const val STATUS_DENIED = "DENIED"
        const val STATUS_PERMANENTLY_DENIED = "PERMANENTLY_DENIED"

        const val REQ_CODE_MICROPHONE = 9101
        const val REQ_CODE_CAMERA = 9102
        const val REQ_CODE_LOCATION_FOREGROUND = 9103
        const val REQ_CODE_LOCATION_BACKGROUND = 9104
        const val REQ_CODE_NOTIFICATIONS = 9105

        // Map request codes to active pending permission callbacks
        internal val pendingCallbacks = mutableMapOf<Int, String>()

        fun onPermissionResult(requestCode: Int, grantResults: IntArray, webView: WebView?) {
            val callbackId = pendingCallbacks.remove(requestCode)
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val status = if (isGranted) STATUS_GRANTED else STATUS_DENIED

            if (!callbackId.isNullOrBlank()) {
                Handler(Looper.getMainLooper()).post {
                    webView?.evaluateJavascript(
                        "if (typeof window['$callbackId'] === 'function') { window['$callbackId']('$status'); }",
                        null
                    )
                }
            }

            // Also dispatch real-time events upon grant
            if (isGranted) {
                Handler(Looper.getMainLooper()).post {
                    when (requestCode) {
                        REQ_CODE_LOCATION_FOREGROUND, REQ_CODE_LOCATION_BACKGROUND -> {
                            webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('kormokari:locationReady'));", null)
                        }
                        REQ_CODE_MICROPHONE -> {
                            webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('kormokari:microphoneGranted'));", null)
                        }
                        REQ_CODE_CAMERA -> {
                            webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('kormokari:cameraGranted'));", null)
                        }
                        REQ_CODE_NOTIFICATIONS -> {
                            webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('kormokari:notificationGranted'));", null)
                        }
                    }
                }
            }
        }
    }

    private fun mapPermissionKey(key: String): String {
        return when (key.trim().uppercase()) {
            "LOCATION", "LOCATION_FOREGROUND", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> Manifest.permission.ACCESS_FINE_LOCATION
            "BACKGROUND_LOCATION", "LOCATION_BACKGROUND", "ACCESS_BACKGROUND_LOCATION" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACCESS_BACKGROUND_LOCATION else Manifest.permission.ACCESS_FINE_LOCATION
            }
            "NOTIFICATIONS", "NOTIFICATION", "POST_NOTIFICATIONS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else ""
            }
            "CAMERA" -> Manifest.permission.CAMERA
            "MICROPHONE", "AUDIO", "RECORD_AUDIO" -> Manifest.permission.RECORD_AUDIO
            else -> key
        }
    }

    private fun getRequestCodeForKey(key: String): Int {
        return when (key.trim().uppercase()) {
            "MICROPHONE", "AUDIO", "RECORD_AUDIO" -> REQ_CODE_MICROPHONE
            "CAMERA" -> REQ_CODE_CAMERA
            "BACKGROUND_LOCATION", "LOCATION_BACKGROUND", "ACCESS_BACKGROUND_LOCATION" -> REQ_CODE_LOCATION_BACKGROUND
            "NOTIFICATIONS", "NOTIFICATION", "POST_NOTIFICATIONS" -> REQ_CODE_NOTIFICATIONS
            else -> REQ_CODE_LOCATION_FOREGROUND
        }
    }

    @JavascriptInterface
    fun getPermissionStatus(key: String): String {
        val upper = key.trim().uppercase()
        if (upper == "BATTERY" || upper == "BATTERY_OPTIMIZATION" || upper == "IGNORE_BATTERY_OPTIMIZATIONS" || upper == "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS") {
            return if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) STATUS_GRANTED else STATUS_DENIED
        }

        val mapped = mapPermissionKey(key)
        if (mapped.isEmpty()) {
            return STATUS_GRANTED // Implicitly granted on Android versions where permission doesn't exist
        }

        val permissionState = ContextCompat.checkSelfPermission(activity, mapped)
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            return STATUS_GRANTED
        }

        return STATUS_DENIED
    }

    @JavascriptInterface
    fun isIgnoringBatteryOptimizations(): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)
    }

    @JavascriptInterface
    fun isBatteryOptimizationExempted(): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)
    }

    @JavascriptInterface
    fun getAllPermissionStatuses(): String {
        val json = JSONObject()
        json.put(PERMISSION_LOCATION_FOREGROUND, getPermissionStatus(PERMISSION_LOCATION_FOREGROUND))
        json.put(PERMISSION_LOCATION_BACKGROUND, getPermissionStatus(PERMISSION_LOCATION_BACKGROUND))
        json.put(PERMISSION_NOTIFICATIONS, getPermissionStatus(PERMISSION_NOTIFICATIONS))
        json.put(PERMISSION_CAMERA, getPermissionStatus(PERMISSION_CAMERA))
        json.put(PERMISSION_MICROPHONE, getPermissionStatus(PERMISSION_MICROPHONE))
        val isBatteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)
        json.put("BATTERY", if (isBatteryExempt) STATUS_GRANTED else STATUS_DENIED)
        json.put("BATTERY_OPTIMIZATION", if (isBatteryExempt) STATUS_GRANTED else STATUS_DENIED)
        json.put("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", if (isBatteryExempt) STATUS_GRANTED else STATUS_DENIED)
        return json.toString()
    }

    @JavascriptInterface
    fun getDeviceInstallationId(): String {
        val prefs = activity.getSharedPreferences("kormokari_device_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_installation_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_installation_id", deviceId).apply()
        }
        return deviceId
    }

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean = true

    @JavascriptInterface
    fun onPushRegistrationChallenge(challenge: String, authUserId: String? = null): String {
        if (challenge.length != 64) {
            return JSONObject().put("success", false).put("message", "Invalid challenge length").toString()
        }

        val context = activity.applicationContext
        val fcmTokenManager = com.example.fcm.FcmTokenManager(context)
        val deviceId = getDeviceInstallationId()
        val appVersion = com.example.config.KormoKariConfig.VERSION_NAME
        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
        } else "granted"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var token = fcmTokenManager.getCachedToken()
                if (token.isNullOrBlank()) {
                    val fetchResult = fcmTokenManager.fetchFreshToken()
                    token = fetchResult.getOrNull()
                }

                if (!token.isNullOrBlank()) {
                    val payload = JSONObject().apply {
                        put("challenge", challenge)
                        put("token", token)
                        put("platform", "android")
                        put("device_id", deviceId)
                        put("device_name", deviceName)
                        put("app_version", appVersion)
                        put("notification_permission", notifPermission)
                    }

                    val url = "${com.example.config.KormoKariConfig.PRODUCTION_URL}api/v1/push-tokens/exchange-challenge"
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    android.util.Log.i("KormoKariPush", "Challenge exchange response code: ${response.code}")
                    response.close()
                }
            } catch (e: Exception) {
                android.util.Log.w("KormoKariPush", "Failed exchanging push registration challenge: ${e.message}")
            }
        }

        return JSONObject().put("success", true).toString()
    }

    @JavascriptInterface
    fun onPushUnlinkChallenge(challenge: String): String {
        val context = activity.applicationContext
        val deviceId = getDeviceInstallationId()
        val token = com.example.fcm.FcmTokenManager(context).getCachedToken()

        // Stop background location tracking immediately on logout
        WorkerLocationService.stop(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("challenge", challenge)
                    put("device_id", deviceId)
                    if (!token.isNullOrBlank()) put("token", token)
                }

                val url = "${com.example.config.KormoKariConfig.PRODUCTION_URL}api/v1/push-tokens/exchange-unlink-challenge"
                val client = OkHttpClient()
                val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {}
        }
        return JSONObject().put("success", true).toString()
    }

    @JavascriptInterface
    fun requestRuntimePermission(key: String): String {
        requestRuntimePermissionWithCallback(key, "")
        return getPermissionStatus(key)
    }

    @JavascriptInterface
    fun requestRuntimePermissionWithCallback(key: String, callbackId: String) {
        val mapped = mapPermissionKey(key)
        if (mapped.isEmpty() || ContextCompat.checkSelfPermission(activity, mapped) == PackageManager.PERMISSION_GRANTED) {
            if (callbackId.isNotBlank()) {
                mainHandler.post {
                    webViewProvider()?.evaluateJavascript(
                        "if (typeof window['$callbackId'] === 'function') { window['$callbackId']('$STATUS_GRANTED'); }",
                        null
                    )
                }
            }
            return
        }

        val requestCode = getRequestCodeForKey(key)
        if (callbackId.isNotBlank()) {
            pendingCallbacks[requestCode] = callbackId
        }

        mainHandler.post {
            if (mapped == Manifest.permission.ACCESS_FINE_LOCATION) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    requestCode
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mapped == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                // Guarded: foreground location must be granted first
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQ_CODE_LOCATION_FOREGROUND
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        requestCode
                    )
                }
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(mapped), requestCode)
            }
        }
    }

    @JavascriptInterface
    fun isLocationServiceEnabled(): Boolean {
        val lm = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @JavascriptInterface
    fun isTrackingActive(): Boolean {
        return WorkerLocationService.isTracking
    }

    @JavascriptInterface
    fun getTrackingStatus(): String {
        return WorkerLocationService.getTrackingStatusJson()
    }

    @JavascriptInterface
    fun startTracking(authToken: String): Boolean {
        return WorkerLocationService.start(activity, authToken)
    }

    @JavascriptInterface
    fun stopTracking(): Boolean {
        return WorkerLocationService.stop(activity)
    }

    @JavascriptInterface
    fun openAppSettings() {
        mainHandler.post {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun openNotificationSettings() {
        mainHandler.post {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                activity.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun openLocationSettings() {
        mainHandler.post {
            try {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun openBatteryOptimizationSettings() {
        mainHandler.post {
            BatteryOptimizationHelper.openBatteryOptimizationSettings(activity)
        }
    }

    @JavascriptInterface
    fun requestBatteryOptimizationExemption(): Boolean {
        mainHandler.post {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimization(activity)
        }
        return true
    }
}
