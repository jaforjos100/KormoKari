package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.util.BatteryOptimizationHelper
import org.json.JSONObject

/**
 * Handles WebMessage protocol and native permission bridging between https://kormokari.com
 * and Android's native ActivityCompat.requestPermissions.
 *
 * Supports native prompts and status reporting for:
 * - Camera (Manifest.permission.CAMERA)
 * - Microphone (Manifest.permission.RECORD_AUDIO)
 * - Push Notifications (Manifest.permission.POST_NOTIFICATIONS)
 * - Foreground & Background GPS Location (Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION)
 * - Battery Optimization exemption (Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 */
class KormoKariWebMessageProtocolHandler(
    private val activity: Activity,
    private val webViewProvider: () -> WebView?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "KormoKariWebMessage"
        const val CANONICAL_ORIGIN = "https://kormokari.com"

        // Request Codes matching KormoKari canonical specs
        const val REQ_CODE_MICROPHONE = 9101
        const val REQ_CODE_CAMERA = 9102
        const val REQ_CODE_LOCATION_FOREGROUND = 9103
        const val REQ_CODE_LOCATION_BACKGROUND = 9104
        const val REQ_CODE_NOTIFICATIONS = 9105

        const val STATUS_GRANTED = "GRANTED"
        const val STATUS_DENIED = "DENIED"

        internal val pendingCallbacks = mutableMapOf<Int, String>()

        const val JS_FACADE_BOOTSTRAP = """
        (function() {
            try {
                if (!window.KormoKariAndroid) {
                    window.KormoKariAndroid = {
                        architecture: 'webview',
                        platform: 'android',
                        version: '3.10.0',
                        package: 'com.kormokari.app',
                        origin: 'https://kormokari.com'
                    };
                }

                // WebMessage listener forwarding messages from https://kormokari.com to native handler
                if (!window._kormokariWebMessageBridgeBound) {
                    window._kormokariWebMessageBridgeBound = true;
                    window.addEventListener('message', function(event) {
                        try {
                            if (event.origin && event.origin.indexOf('kormokari.com') === -1 && event.origin !== 'null') {
                                return;
                            }
                            var data = event.data;
                            if (data && typeof data === 'object') {
                                if (data.type === 'KORMOKARI_PERMISSION_REQUEST' || data.action === 'REQUEST_PERMISSION') {
                                    var perm = data.permission || data.type;
                                    var cb = data.callbackId || '';
                                    if (window.KormoKariWebMessageBridge && window.KormoKariWebMessageBridge.requestPermissionWithCallback) {
                                        window.KormoKariWebMessageBridge.requestPermissionWithCallback(perm, cb);
                                    } else if (window.KormoKariAndroidPermissionsBridge) {
                                        window.KormoKariAndroidPermissionsBridge.requestRuntimePermissionWithCallback(perm, cb);
                                    }
                                } else if (window.KormoKariWebMessageBridge && window.KormoKariWebMessageBridge.postMessage) {
                                    window.KormoKariWebMessageBridge.postMessage(JSON.stringify(data));
                                }
                            }
                        } catch (e) {
                            console.error('[KormoKariWebMessage] Bridge dispatch error:', e);
                        }
                    });
                }
            } catch (e) {}
        })();
        """

        /**
         * Injects the KormoKariAndroid facade and WebMessage forwarder into the specified WebView.
         */
        fun injectFacade(webView: WebView?) {
            webView?.evaluateJavascript(JS_FACADE_BOOTSTRAP, null)
        }

        /**
         * Called from MainActivity.onRequestPermissionsResult to resolve pending callbacks.
         */
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

    /**
     * Handles JSON WebMessage string passed from window.postMessage or JavaScript.
     */
    @JavascriptInterface
    fun postMessage(messageJson: String) {
        try {
            val json = JSONObject(messageJson)
            val type = json.optString("type", json.optString("action", ""))
            val permission = json.optString("permission", "")
            val callbackId = json.optString("callbackId", "")

            when {
                type.equals("REQUEST_PERMISSION", ignoreCase = true) ||
                type.startsWith("PERMISSION_", ignoreCase = true) -> {
                    val targetPermission = if (permission.isNotBlank()) permission else type.substringAfter("PERMISSION_")
                    requestPermissionWithCallback(targetPermission, callbackId)
                }
                type.equals("BATTERY_OPTIMIZATION", ignoreCase = true) ||
                permission.contains("BATTERY", ignoreCase = true) -> {
                    requestBatteryOptimizationExemption()
                }
                type.equals("GET_STATUS", ignoreCase = true) -> {
                    val status = getPermissionStatus(permission)
                    if (callbackId.isNotBlank()) {
                        mainHandler.post {
                            webViewProvider()?.evaluateJavascript(
                                "if (typeof window['$callbackId'] === 'function') { window['$callbackId']('$status'); }",
                                null
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WebMessage: ${e.message}")
        }
    }

    /**
     * Bridges JavaScript permission requests directly to native ActivityCompat.requestPermissions.
     */
    @JavascriptInterface
    fun requestPermission(type: String): String {
        requestPermissionWithCallback(type, "")
        return getPermissionStatus(type)
    }

    /**
     * Bridges JavaScript permission requests with a callback function name.
     */
    @JavascriptInterface
    fun requestPermissionWithCallback(type: String, callbackId: String) {
        val normalized = type.trim().uppercase()
        when {
            normalized.contains("CAMERA") -> requestCameraPermission(callbackId)
            normalized.contains("MIC") || normalized.contains("AUDIO") -> requestMicrophonePermission(callbackId)
            normalized.contains("NOTIF") -> requestNotificationPermission(callbackId)
            normalized.contains("BACKGROUND_LOC") -> requestBackgroundLocationPermission(callbackId)
            normalized.contains("LOC") || normalized.contains("GPS") -> requestLocationPermission(callbackId)
            normalized.contains("BATTERY") -> {
                requestBatteryOptimizationExemption()
                if (callbackId.isNotBlank()) {
                    val status = if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) STATUS_GRANTED else STATUS_DENIED
                    mainHandler.post {
                        webViewProvider()?.evaluateJavascript(
                            "if (typeof window['$callbackId'] === 'function') { window['$callbackId']('$status'); }",
                            null
                        )
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown permission requested: $type")
            }
        }
    }

    /**
     * Triggers native ActivityCompat.requestPermissions for CAMERA.
     */
    @JavascriptInterface
    fun requestCameraPermission(callbackId: String = "") {
        executeRequest(Manifest.permission.CAMERA, REQ_CODE_CAMERA, callbackId)
    }

    /**
     * Triggers native ActivityCompat.requestPermissions for RECORD_AUDIO.
     */
    @JavascriptInterface
    fun requestMicrophonePermission(callbackId: String = "") {
        executeRequest(Manifest.permission.RECORD_AUDIO, REQ_CODE_MICROPHONE, callbackId)
    }

    /**
     * Triggers native ActivityCompat.requestPermissions for POST_NOTIFICATIONS (API 33+).
     */
    @JavascriptInterface
    fun requestNotificationPermission(callbackId: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            executeRequest(Manifest.permission.POST_NOTIFICATIONS, REQ_CODE_NOTIFICATIONS, callbackId)
        } else {
            notifyGranted(callbackId)
        }
    }

    /**
     * Triggers native ActivityCompat.requestPermissions for fine & coarse GPS location.
     */
    @JavascriptInterface
    fun requestLocationPermission(callbackId: String = "") {
        if (callbackId.isNotBlank()) {
            pendingCallbacks[REQ_CODE_LOCATION_FOREGROUND] = callbackId
            KormoKariPermissionsBridge.pendingCallbacks[REQ_CODE_LOCATION_FOREGROUND] = callbackId
        }
        mainHandler.post {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_CODE_LOCATION_FOREGROUND
            )
        }
    }

    /**
     * Triggers native ActivityCompat.requestPermissions for ACCESS_BACKGROUND_LOCATION (API 29+).
     * Guarded: requires foreground location first.
     */
    @JavascriptInterface
    fun requestBackgroundLocationPermission(callbackId: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFg = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFg) {
                requestLocationPermission(callbackId)
            } else {
                executeRequest(Manifest.permission.ACCESS_BACKGROUND_LOCATION, REQ_CODE_LOCATION_BACKGROUND, callbackId)
            }
        } else {
            notifyGranted(callbackId)
        }
    }

    /**
     * Launches Android system dialog for battery optimization exemption.
     */
    @JavascriptInterface
    fun requestBatteryOptimizationExemption(): Boolean {
        mainHandler.post {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimization(activity)
        }
        return true
    }

    /**
     * Queries permission status for camera, microphone, notifications, GPS, or battery.
     */
    @JavascriptInterface
    fun getPermissionStatus(type: String): String {
        val normalized = type.trim().uppercase()
        return when {
            normalized.contains("CAMERA") -> checkPermission(Manifest.permission.CAMERA)
            normalized.contains("MIC") || normalized.contains("AUDIO") -> checkPermission(Manifest.permission.RECORD_AUDIO)
            normalized.contains("NOTIF") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    STATUS_GRANTED
                }
            }
            normalized.contains("BACKGROUND_LOC") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    STATUS_GRANTED
                }
            }
            normalized.contains("LOC") || normalized.contains("GPS") -> checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            normalized.contains("BATTERY") -> {
                if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) STATUS_GRANTED else STATUS_DENIED
            }
            else -> STATUS_DENIED
        }
    }

    private fun checkPermission(permission: String): String {
        return if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            STATUS_GRANTED
        } else {
            STATUS_DENIED
        }
    }

    private fun executeRequest(permission: String, requestCode: Int, callbackId: String) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            notifyGranted(callbackId)
            return
        }

        if (callbackId.isNotBlank()) {
            pendingCallbacks[requestCode] = callbackId
            KormoKariPermissionsBridge.pendingCallbacks[requestCode] = callbackId
        }

        mainHandler.post {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        }
    }

    private fun notifyGranted(callbackId: String) {
        if (callbackId.isNotBlank()) {
            mainHandler.post {
                webViewProvider()?.evaluateJavascript(
                    "if (typeof window['$callbackId'] === 'function') { window['$callbackId']('$STATUS_GRANTED'); }",
                    null
                )
            }
        }
    }
}
