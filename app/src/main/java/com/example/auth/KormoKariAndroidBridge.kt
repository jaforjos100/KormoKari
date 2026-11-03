package com.example.auth

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent

class KormoKariAndroidBridge(
    private val activity: ComponentActivity,
    private val webView: android.webkit.WebView
) {
    companion object {
        private const val TAG = "KormoKariAuthBridge"
    }

    @JavascriptInterface
    fun requestGoogleSignIn() {
        requestGoogleSignIn(null)
    }

    /**
     * Safely initiates Google Sign-In via Chrome Custom Tabs
     * Executed on UI Thread to prevent JavaBridge thread crashes
     */
    @JavascriptInterface
    fun requestGoogleSignIn(nonce: String?) {
        activity.runOnUiThread {
            try {
                val googleAuthUrl = "https://kormokari.com/auth/google/redirect?source=app"
                val uri = Uri.parse(googleAuthUrl)
                val launched = BrandedCustomTabsHelper.launchBrandedCustomTab(activity, uri)
                Log.d(TAG, "Launched Chrome Custom Tab for Google OAuth: $googleAuthUrl, success: $launched")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal: unable to launch Google OAuth intent: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun requestFacebookSignIn() {
        requestFacebookSignIn(null)
    }

    /**
     * Safely initiates Facebook Sign-In via Chrome Custom Tabs
     */
    @JavascriptInterface
    fun requestFacebookSignIn(nonce: String?) {
        activity.runOnUiThread {
            try {
                val facebookAuthUrl = "https://kormokari.com/auth/facebook/redirect?source=app"
                val uri = Uri.parse(facebookAuthUrl)
                val launched = BrandedCustomTabsHelper.launchBrandedCustomTab(activity, uri)
                Log.d(TAG, "Launched Chrome Custom Tab for Facebook OAuth: $facebookAuthUrl, success: $launched")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal: unable to launch Facebook OAuth intent: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun openOAuthCustomTab(provider: String?) {
        val prov = provider?.trim()?.lowercase() ?: "google"
        if (prov.contains("facebook")) {
            requestFacebookSignIn()
        } else {
            requestGoogleSignIn()
        }
    }

    @JavascriptInterface
    fun signInWithGoogle() {
        requestGoogleSignIn()
    }

    @JavascriptInterface
    fun signInWithFacebook() {
        requestFacebookSignIn()
    }

    @JavascriptInterface
    fun loginWithGoogle() {
        requestGoogleSignIn()
    }

    @JavascriptInterface
    fun loginWithFacebook() {
        requestFacebookSignIn()
    }

    @JavascriptInterface
    fun checkForAppUpdate() {
        checkForAppUpdate(false)
    }

    @JavascriptInterface
    fun checkForAppUpdate(isImmediate: Boolean) {
        if (activity is com.example.MainActivity) {
            activity.appUpdateManager.checkForUpdate(isImmediate)
        }
    }

    @JavascriptInterface
    fun completeAppUpdate() {
        if (activity is com.example.MainActivity) {
            activity.appUpdateManager.completeUpdate()
        }
    }

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean = true
}
