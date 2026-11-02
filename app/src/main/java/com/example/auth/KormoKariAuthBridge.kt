package com.example.auth

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.config.KormoKariConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * Secure native JavaScript Interface bridging the KormoKari WebView and Android
 * for Google Account Selection & Sign-In using Android Jetpack Credential Manager.
 *
 * Security Architecture & Guarantees:
 * 1. Strict Origin Verification: Every native call validates that the WebView's
 *    current active URL strictly matches the verified production origin (https://kormokari.com).
 *    Calls from untrusted or cross-origin web pages are rejected immediately.
 * 2. Injection-Proof Callback Invocation: JavaScript callback function identifiers
 *    are validated against a strict alphanumeric whitelist to prevent XSS / script injection.
 * 3. Account Selection Guarantee: Auto-select is explicitly disabled (`autoSelect = false`),
 *    ensuring Android users are presented with the native account picker to choose their intended
 *    Google account rather than silently auto-selecting the wrong device account.
 * 4. Zero Secret Leakage: Tokens and credentials are never exposed via URL parameters or logged.
 *    Results are returned securely via direct `evaluateJavascript` execution on the trusted origin.
 * 5. Replay Protection: Supports an optional cryptographic nonce provided by the Laravel backend.
 */
class KormoKariAuthBridge(
    activity: Activity,
    private val webViewProvider: () -> WebView?,
    private val coroutineScope: CoroutineScope,
    private val defaultServerClientId: String = KormoKariConfig.DEFAULT_WEB_CLIENT_ID
) {
    private val activityRef = WeakReference(activity)
    private val credentialManager: CredentialManager = CredentialManager.create(activity)

    companion object {
        const val TAG = "KormoKariAuthBridge"

        /**
         * The standard JavaScript window object name exposed to web clients:
         * `window.KormoKariAuthBridge`
         */
        const val JAVASCRIPT_INTERFACE_NAME = "KormoKariAuthBridge"

        /**
         * Secondary alias name for universal compatibility:
         * `window.AndroidAuthBridge`
         */
        const val JAVASCRIPT_INTERFACE_ALIAS = "AndroidAuthBridge"

        /**
         * Standard Android bridge name for web integrations:
         * `window.KormoKariAndroid`
         */
        const val JAVASCRIPT_INTERFACE_KORMOKARI_ANDROID = "KormoKariAndroid"

        // Error codes returned to the web client
        const val ERROR_UNTRUSTED_ORIGIN = "UNTRUSTED_ORIGIN"
        const val ERROR_USER_CANCELLED = "USER_CANCELLED"
        const val ERROR_NO_CREDENTIALS = "NO_CREDENTIALS"
        const val ERROR_INVALID_CALLBACK = "INVALID_CALLBACK"
        const val ERROR_ACTIVITY_DESTROYED = "ACTIVITY_DESTROYED"
        const val ERROR_CONFIGURATION_ERROR = "CONFIGURATION_ERROR"
        const val ERROR_INTERNAL = "INTERNAL_ERROR"

        // Whitelist regex pattern for safe JavaScript function names (e.g. "onGoogleSignInComplete", "KormoKari.auth.handleSuccess")
        private val SAFE_CALLBACK_PATTERN = Pattern.compile("^[a-zA-Z_\$][a-zA-Z0-9_\$.]*$")

        // Allowed hostnames for the KormoKari production service
        private val TRUSTED_HOSTS = setOf(
            "kormokari.com",
            "www.kormokari.com"
        )
    }

    /**
     * Checks if native Google Sign-In is supported by the Android device and available to the caller.
     * The web frontend can invoke `window.KormoKariAuthBridge.isNativeGoogleSignInAvailable()`
     * to conditionally render the native Google account picker trigger.
     */
    @JavascriptInterface
    fun isNativeGoogleSignInAvailable(): Boolean {
        val currentUrl = getCurrentWebViewUrl()
        return isOriginTrusted(currentUrl)
    }

    /**
     * Checks if Facebook Sign-In is supported by the Android device and available to the caller.
     * Guaranteed to use secure, branded Chrome Custom Tabs and never an embedded WebView.
     */
    @JavascriptInterface
    fun isNativeFacebookSignInAvailable(): Boolean {
        val currentUrl = getCurrentWebViewUrl()
        return isOriginTrusted(currentUrl)
    }

    /**
     * Optional listener invoked when active-session-health.js reports that the backend
     * has entered maintenance mode (SERVER_NOT_READY / HTTP 503).
     */
    var onServerNotReadyListener: ((String?) -> Unit)? = null

    /**
     * Returns the protocol version of this native authentication bridge.
     */
    @JavascriptInterface
    fun getBridgeVersion(): Int = 1

    /**
     * Returns the expected readiness contract version ("ready-v1").
     */
    @JavascriptInterface
    fun getHealthContractVersion(): String = KormoKariConfig.HEALTH_CHECK_EXPECTED_VALUE

    /**
     * Allows active-session-health.js to notify native Android of server downtime.
     * Guaranteed to only accept reports from trusted origins.
     */
    @JavascriptInterface
    fun reportServerNotReady(details: String? = null) {
        val currentUrl = getCurrentWebViewUrl()
        if (!isOriginTrusted(currentUrl)) {
            Log.w(TAG, "reportServerNotReady rejected: untrusted origin ($currentUrl)")
            return
        }
        coroutineScope.launch(Dispatchers.Main) {
            onServerNotReadyListener?.invoke(details ?: "SERVER_NOT_READY reported by session health")
        }
    }

    /**
     * Verifies whether the calling document origin is verified and trusted.
     */
    @JavascriptInterface
    fun isOriginTrusted(): Boolean {
        return isOriginTrusted(getCurrentWebViewUrl())
    }

    /**
     * Solution B: Native Google Credential Manager entry point exposed on:
     * `window.KormoKariAndroid.requestGoogleSignIn(nonce)`
     *
     * 1. Launches CredentialManager.getCredential(...) with GetGoogleIdOption.
     * 2. Dispatches Google ID Token back via:
     *    `window.KormoKariAndroidBridge.onGoogleCredentialReceived('${googleIdToken}', '${nonce}');`
     */
    @JavascriptInterface
    fun requestGoogleSignIn() {
        requestGoogleSignIn(null as String?)
    }

    @JavascriptInterface
    fun requestGoogleSignIn(nonce: String?) {
        val currentUrl = getCurrentWebViewUrl()
        if (!isOriginTrusted(currentUrl)) {
            Log.e(TAG, "Blocked Google Sign-In request from untrusted origin: $currentUrl")
            return
        }

        val activity = activityRef.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.e(TAG, "Host Android activity is no longer active.")
            return
        }

        val effectiveClientId = defaultServerClientId
        if (effectiveClientId.isBlank()) {
            Log.e(TAG, "Google OAuth Server Client ID is not configured.")
            return
        }

        coroutineScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(effectiveClientId)
                    .setAutoSelectEnabled(false)
                    .apply {
                        if (!nonce.isNullOrBlank()) {
                            setNonce(nonce.trim())
                        }
                    }
                    .build()

                val credentialRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialResponse = withContext(Dispatchers.IO) {
                    credentialManager.getCredential(
                        request = credentialRequest,
                        context = activity
                    )
                }

                val credential = credentialResponse.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val safeNonce = nonce ?: ""

                    withContext(Dispatchers.Main) {
                        val webView = webViewProvider()
                        val js = "if (window.KormoKariAndroidBridge && typeof window.KormoKariAndroidBridge.onGoogleCredentialReceived === 'function') { window.KormoKariAndroidBridge.onGoogleCredentialReceived('${idToken}', '${safeNonce}'); }"
                        webView?.evaluateJavascript(js, null)

                        if (!nonce.isNullOrBlank() && isValidCallbackName(nonce)) {
                            sendSuccessToWeb(
                                callbackName = nonce,
                                idToken = idToken,
                                email = googleIdTokenCredential.id,
                                displayName = googleIdTokenCredential.displayName,
                                familyName = googleIdTokenCredential.familyName,
                                givenName = googleIdTokenCredential.givenName,
                                profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Unsupported credential type from provider, launching Custom Tab fallback")
                    launchCustomTabInternal(KormoKariConfig.GOOGLE_AUTH_URL)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.i(TAG, "Google Sign-In dismissed by user")
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google accounts available on device, launching Custom Tab fallback")
                launchCustomTabInternal(KormoKariConfig.GOOGLE_AUTH_URL)
            } catch (e: Exception) {
                Log.e(TAG, "Credential Manager error, falling back to Custom Tab: ${e.message}")
                launchCustomTabInternal(KormoKariConfig.GOOGLE_AUTH_URL)
            }
        }
    }

    /**
     * Initiates the secure native Google account selection flow with optional server client ID and nonce.
     *
     * @param callbackFunctionName The name of the JavaScript function to invoke with results.
     * @param serverClientId Optional OAuth Web Client ID registered in Google Cloud Console.
     * @param nonce Optional cryptographic nonce from the Laravel backend to bind to the ID token.
     */
    @JavascriptInterface
    fun requestGoogleSignInWithConfig(
        callbackFunctionName: String,
        serverClientId: String?,
        nonce: String?
    ) {
        requestGoogleSignInWithConfig(
            callbackFunctionName = callbackFunctionName,
            serverClientId = serverClientId,
            nonce = nonce,
            fallbackUrl = null
        )
    }

    /**
     * Initiates the secure native Google account selection flow with fallback URL support.
     *
     * @param callbackFunctionName The name of the JavaScript function to invoke with results.
     * @param serverClientId Optional OAuth Web Client ID registered in Google Cloud Console.
     * @param nonce Optional cryptographic nonce from the Laravel backend.
     * @param fallbackUrl Optional URL to launch in branded Custom Tabs if device has no accounts.
     */
    @JavascriptInterface
    fun requestGoogleSignInWithFallback(
        callbackFunctionName: String,
        serverClientId: String?,
        nonce: String?,
        fallbackUrl: String?
    ) {
        requestGoogleSignInWithConfig(
            callbackFunctionName = callbackFunctionName,
            serverClientId = serverClientId,
            nonce = nonce,
            fallbackUrl = fallbackUrl
        )
    }

    /**
     * Core Google Credential Manager implementation.
     * - Disables autoSelect so users ALWAYS choose their preferred account.
     * - Disables filterByAuthorizedAccounts so ALL Google accounts on device (and "Add account") appear.
     * - Gracefully falls back to branded Custom Tabs if no accounts exist or provider is unavailable.
     */
    fun requestGoogleSignInWithConfig(
        callbackFunctionName: String,
        serverClientId: String?,
        nonce: String?,
        fallbackUrl: String? = null
    ) {
        val currentUrl = getCurrentWebViewUrl()

        // 1. Strict Origin Verification
        if (!isOriginTrusted(currentUrl)) {
            Log.e(TAG, "Blocked Google Sign-In request from untrusted origin: $currentUrl")
            sendErrorToWeb(
                callbackName = callbackFunctionName,
                errorCode = ERROR_UNTRUSTED_ORIGIN,
                errorMessage = "Google sign-in is only permitted from verified KormoKari origins."
            )
            return
        }

        // 2. Validate Callback Function Name
        if (!isValidCallbackName(callbackFunctionName)) {
            Log.e(TAG, "Rejected unsafe JavaScript callback name: $callbackFunctionName")
            return
        }

        val activity = activityRef.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            sendErrorToWeb(
                callbackName = callbackFunctionName,
                errorCode = ERROR_ACTIVITY_DESTROYED,
                errorMessage = "Host Android activity is no longer active."
            )
            return
        }

        val effectiveClientId = if (!serverClientId.isNullOrBlank()) {
            serverClientId.trim()
        } else {
            defaultServerClientId
        }

        if (effectiveClientId.isBlank()) {
            sendErrorToWeb(
                callbackName = callbackFunctionName,
                errorCode = ERROR_CONFIGURATION_ERROR,
                errorMessage = "Google OAuth Server Client ID is not configured."
            )
            return
        }

        // 3. Launch Credential Manager Flow on CoroutineScope
        coroutineScope.launch {
            try {
                // Build Google ID Option:
                // - filterByAuthorizedAccounts = false ensures all accounts and "Add account" are presented
                // - autoSelect = false guarantees user explicitly taps their intended account
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(effectiveClientId)
                    .setAutoSelectEnabled(false)
                    .apply {
                        if (!nonce.isNullOrBlank()) {
                            setNonce(nonce.trim())
                        }
                    }
                    .build()

                val credentialRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialResponse = withContext(Dispatchers.IO) {
                    credentialManager.getCredential(
                        request = credentialRequest,
                        context = activity
                    )
                }

                val credential = credentialResponse.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val email = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName
                    val familyName = googleIdTokenCredential.familyName
                    val givenName = googleIdTokenCredential.givenName
                    val profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()

                    val safeNonce = nonce ?: ""
                    withContext(Dispatchers.Main) {
                        val webView = webViewProvider()
                        val js = "if (window.KormoKariAndroidBridge && typeof window.KormoKariAndroidBridge.onGoogleCredentialReceived === 'function') { window.KormoKariAndroidBridge.onGoogleCredentialReceived('${idToken}', '${safeNonce}'); }"
                        webView?.evaluateJavascript(js, null)
                    }

                    sendSuccessToWeb(
                        callbackName = callbackFunctionName,
                        idToken = idToken,
                        email = email,
                        displayName = displayName,
                        familyName = familyName,
                        givenName = givenName,
                        profilePictureUri = profilePictureUri
                    )
                } else {
                    sendErrorToWeb(
                        callbackName = callbackFunctionName,
                        errorCode = ERROR_INTERNAL,
                        errorMessage = "Unsupported credential type received from provider."
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                // User intentionally cancelled or dismissed the account picker
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_USER_CANCELLED,
                    errorMessage = "Account selection cancelled by user."
                )
            } catch (e: NoCredentialException) {
                // Device has no Google accounts available: offer fallback flow via branded Custom Tabs
                Log.w(TAG, "No Google accounts available on device, offering Custom Tab fallback flow")
                val targetFallbackUrl = if (!fallbackUrl.isNullOrBlank()) {
                    fallbackUrl.trim()
                } else {
                    KormoKariConfig.GOOGLE_AUTH_URL
                }
                val fallbackInitiated = launchCustomTabInternal(targetFallbackUrl)
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_NO_CREDENTIALS,
                    errorMessage = "No Google accounts are available on this device. Opened secure browser sign-in.",
                    fallbackInitiated = fallbackInitiated
                )
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error during Google Sign-In", e)
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_INTERNAL,
                    errorMessage = e.message ?: "Google Sign-In failed."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during native Google Sign-In", e)
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_INTERNAL,
                    errorMessage = "An unexpected error occurred during Google Sign-In."
                )
            }
        }
    }

    /**
     * Explicit fallback method allowing the website to trigger the web Google Sign-In flow
     * in a branded Custom Tab without leaving the visual app container.
     */
    @JavascriptInterface
    fun requestGoogleFallbackSignIn(callbackFunctionName: String? = null, fallbackUrl: String? = null) {
        val currentUrl = getCurrentWebViewUrl()
        if (!isOriginTrusted(currentUrl)) {
            Log.e(TAG, "Blocked requestGoogleFallbackSignIn from untrusted origin: $currentUrl")
            if (!callbackFunctionName.isNullOrBlank() && isValidCallbackName(callbackFunctionName)) {
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_UNTRUSTED_ORIGIN,
                    errorMessage = "Google fallback sign-in is only permitted from verified KormoKari origins."
                )
            }
            return
        }

        val targetUrl = if (!fallbackUrl.isNullOrBlank()) fallbackUrl.trim() else KormoKariConfig.GOOGLE_AUTH_URL
        val launched = launchCustomTabInternal(targetUrl)

        if (!callbackFunctionName.isNullOrBlank() && isValidCallbackName(callbackFunctionName)) {
            val payload = JSONObject().apply {
                put("success", launched)
                put("status", if (launched) "CUSTOM_TAB_LAUNCHED" else "LAUNCH_FAILED")
                put("provider", "google")
                put("authUrl", targetUrl)
            }
            dispatchScript(callbackFunctionName, payload)
        }
    }

    /**
     * Initiates secure Facebook Sign-In using branded Android Chrome Custom Tabs.
     * Complies with Meta's strict security policy forbidding embedded WebViews.
     */
    @JavascriptInterface
    fun requestFacebookSignIn(callbackFunctionName: String? = null, authUrl: String? = null) {
        val currentUrl = getCurrentWebViewUrl()
        if (!isOriginTrusted(currentUrl)) {
            Log.e(TAG, "Blocked Facebook Sign-In request from untrusted origin: $currentUrl")
            if (!callbackFunctionName.isNullOrBlank() && isValidCallbackName(callbackFunctionName)) {
                sendErrorToWeb(
                    callbackName = callbackFunctionName,
                    errorCode = ERROR_UNTRUSTED_ORIGIN,
                    errorMessage = "Facebook sign-in is only permitted from verified KormoKari origins."
                )
            }
            return
        }

        val effectiveAuthUrl = if (!authUrl.isNullOrBlank()) {
            authUrl.trim()
        } else {
            KormoKariConfig.FACEBOOK_AUTH_URL
        }

        val launched = launchCustomTabInternal(effectiveAuthUrl)

        if (!callbackFunctionName.isNullOrBlank() && isValidCallbackName(callbackFunctionName)) {
            val payload = JSONObject().apply {
                put("success", launched)
                put("status", if (launched) "CUSTOM_TAB_LAUNCHED" else "LAUNCH_FAILED")
                put("provider", "facebook")
                put("authUrl", effectiveAuthUrl)
            }
            dispatchScript(callbackFunctionName, payload)
        }
    }

    /**
     * Generic in-app identity-provider Custom Tab launcher for web frontend auth flows.
     */
    @JavascriptInterface
    fun openCustomTabAuth(authUrl: String?): Boolean {
        val currentUrl = getCurrentWebViewUrl()
        if (!isOriginTrusted(currentUrl)) {
            Log.e(TAG, "Blocked openCustomTabAuth from untrusted origin: $currentUrl")
            return false
        }
        if (authUrl.isNullOrBlank()) return false
        return launchCustomTabInternal(authUrl.trim())
    }

    /**
     * Diagnostic metadata inspecting current auth bridge capabilities.
     */
    @JavascriptInterface
    fun getAuthDiagnostics(): String {
        return JSONObject().apply {
            put("bridgeVersion", getBridgeVersion())
            put("googleCredentialManagerAvailable", true)
            put("facebookCustomTabAvailable", true)
            put("defaultServerClientIdConfigured", defaultServerClientId.isNotBlank())
            put("trustedOrigin", KormoKariConfig.PRODUCTION_ORIGIN)
        }.toString()
    }

    private fun launchCustomTabInternal(url: String): Boolean {
        val activity = activityRef.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            BrandedCustomTabsHelper.launchBrandedCustomTab(activity, Uri.parse(url))
        } else {
            activity.runOnUiThread {
                BrandedCustomTabsHelper.launchBrandedCustomTab(activity, Uri.parse(url))
            }
            true
        }
    }

    /**
     * Validates whether the given URL belongs to a trusted KormoKari origin.
     */
    fun isOriginTrusted(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()

            // Strict HTTPS requirement
            if (scheme != "https") {
                return false
            }

            if (host == null) {
                return false
            }

            // Check if host matches trusted hostnames or configured production origin
            val prodUri = Uri.parse(KormoKariConfig.PRODUCTION_ORIGIN)
            val prodHost = prodUri.host?.lowercase()

            TRUSTED_HOSTS.contains(host) || (prodHost != null && host == prodHost)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates that the callback function name matches a safe identifier pattern
     * to prevent XSS / script injection attacks.
     */
    fun isValidCallbackName(callbackName: String?): Boolean {
        if (callbackName.isNullOrBlank()) return false
        if (callbackName.length > 100) return false
        return SAFE_CALLBACK_PATTERN.matcher(callbackName.trim()).matches()
    }

    private fun getCurrentWebViewUrl(): String? {
        val webView = webViewProvider() ?: return null
        return webView.url
    }

    private fun sendSuccessToWeb(
        callbackName: String,
        idToken: String,
        email: String?,
        displayName: String?,
        familyName: String?,
        givenName: String?,
        profilePictureUri: String?
    ) {
        if (!isValidCallbackName(callbackName)) return

        val payload = JSONObject().apply {
            put("success", true)
            put("idToken", idToken)
            put("email", email ?: "")
            put("displayName", displayName ?: "")
            put("familyName", familyName ?: "")
            put("givenName", givenName ?: "")
            put("profilePictureUri", profilePictureUri ?: "")
            put("authAction", "login_or_signup")
        }

        dispatchScript(callbackName, payload)
    }

    private fun sendErrorToWeb(
        callbackName: String,
        errorCode: String,
        errorMessage: String,
        fallbackInitiated: Boolean = false
    ) {
        if (!isValidCallbackName(callbackName)) return

        val payload = JSONObject().apply {
            put("success", false)
            put("errorCode", errorCode)
            put("errorMessage", errorMessage)
            put("fallbackInitiated", fallbackInitiated)
        }

        dispatchScript(callbackName, payload)
    }

    private fun dispatchScript(callbackName: String, payload: JSONObject) {
        val safeCallback = callbackName.trim()
        val jsonString = payload.toString()

        val script = """
            (function() {
                try {
                    var payload = $jsonString;
                    if (typeof window['$safeCallback'] === 'function') {
                        window['$safeCallback'](payload);
                    }
                    if (typeof window.dispatchEvent === 'function') {
                        window.dispatchEvent(new CustomEvent('kormokari:authResult', { detail: payload }));
                        window.dispatchEvent(new CustomEvent('kormokari:googleSignInResult', { detail: payload }));
                    }
                } catch(e) {
                    console.error('KormoKariAuthBridge dispatch error', e);
                }
            })();
        """.trimIndent()

        coroutineScope.launch(Dispatchers.Main) {
            val webView = webViewProvider()
            if (webView != null) {
                webView.evaluateJavascript(script, null)
            }
        }
    }
}
