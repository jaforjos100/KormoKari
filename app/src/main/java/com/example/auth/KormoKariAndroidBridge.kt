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
                val googleAuthUrl = "https://kormokari.com/auth/google/redirect"
                val uri = Uri.parse(googleAuthUrl)

                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(activity, uri)
                Log.d(TAG, "Successfully launched Chrome Custom Tab for Google OAuth")
            } catch (e: Exception) {
                Log.w(TAG, "CustomTabs launch failed, falling back to system browser: ${e.message}")
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kormokari.com/auth/google/redirect")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Fatal: unable to launch Google OAuth intent: ${ex.message}")
                }
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
                val facebookAuthUrl = "https://kormokari.com/auth/facebook/redirect"
                val uri = Uri.parse(facebookAuthUrl)

                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(activity, uri)
                Log.d(TAG, "Successfully launched Chrome Custom Tab for Facebook OAuth")
            } catch (e: Exception) {
                Log.w(TAG, "CustomTabs launch failed, falling back to system browser: ${e.message}")
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kormokari.com/auth/facebook/redirect")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Fatal: unable to launch Facebook OAuth intent: ${ex.message}")
                }
            }
        }
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
