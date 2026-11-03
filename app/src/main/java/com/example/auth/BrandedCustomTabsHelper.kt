package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import com.example.R

/**
 * Utility for launching visually connected, branded Android Chrome Custom Tabs
 * for external identity-provider authentication (Facebook OAuth, Google fallback, etc.).
 *
 * Security & UX Guarantees:
 * 1. Brand Consistency: Matches KormoKari primary pink (#E91069) and surface palette.
 * 2. In-App Visual Connection: Never drops the user into an untethered, disconnected browser tab.
 * 3. Anti-Phishing: Displays the trusted provider URL bar securely via Chrome Custom Tabs.
 * 4. Policy Compliance: Never uses embedded WebView for Facebook or OAuth authentication.
 * 5. Graceful Fallback: Safely falls back to ACTION_VIEW if no Custom Tabs provider is available.
 */
object BrandedCustomTabsHelper {
    private const val TAG = "BrandedCustomTabs"

    /**
     * Launches a branded Custom Tab with KormoKari styling.
     * Returns true if successfully launched, false otherwise.
     */
    fun launchBrandedCustomTab(context: Context, url: Uri): Boolean {
        return try {
            val primaryColor = context.getColor(R.color.kormokari_primary)
            val surfaceColor = context.getColor(R.color.kormokari_surface)

            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(primaryColor)
                .setNavigationBarColor(surfaceColor)
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()

            val customTabsPackage = CustomTabsClient.getPackageName(context, null)
            if (!customTabsPackage.isNullOrEmpty()) {
                customTabsIntent.intent.setPackage(customTabsPackage)
            }

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val finalUri = normalizeOAuthUrl(url)

            customTabsIntent.launchUrl(context, finalUri)
            Log.i(TAG, "Launched branded Custom Tab for in-app authentication: $finalUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Custom Tab, falling back to browser intent", e)
            try {
                val finalUri = normalizeOAuthUrl(url)
                val fallbackIntent = Intent(Intent.ACTION_VIEW, finalUri).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallbackIntent)
                true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed fallback browser launch", fallbackError)
                false
            }
        }
    }

    /**
     * Normalizes social auth URLs to the required backend redirect endpoint with ?source=app.
     * Prevents calling non-existent bare paths like /auth/google or /auth/facebook.
     */
    fun normalizeOAuthUrl(url: Uri): Uri {
        val host = url.host?.lowercase() ?: ""
        val path = url.path?.lowercase() ?: ""
        val isKormoKari = host == "kormokari.com" || host == "www.kormokari.com" || host.isEmpty()
        if (!isKormoKari) return url

        val provider = when {
            path.contains("facebook") || url.toString().lowercase().contains("facebook") -> "facebook"
            path.contains("google") || url.toString().lowercase().contains("google") -> "google"
            else -> null
        } ?: return url

        val builder = Uri.parse("https://kormokari.com/auth/$provider/redirect").buildUpon()
        for (paramName in url.queryParameterNames) {
            if (paramName != "source") {
                builder.appendQueryParameter(paramName, url.getQueryParameter(paramName))
            }
        }
        builder.appendQueryParameter("source", "app")
        return builder.build()
    }

    /**
     * Checks if the URL is an external authentication / OAuth URL that requires
     * in-app branded Custom Tab treatment rather than embedded WebView or generic browser.
     */
    fun isSocialAuthUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        if (isAuthCallbackUrl(urlString)) return false
        val lower = urlString.lowercase()
        return isFacebookAuthUrl(urlString) ||
                isGoogleAuthUrl(urlString) ||
                (lower.contains("google") && (lower.contains("auth") || lower.contains("login") || lower.contains("signin") || lower.contains("redirect") || lower.contains("oauth"))) ||
                (lower.contains("facebook") && (lower.contains("auth") || lower.contains("login") || lower.contains("signin") || lower.contains("redirect") || lower.contains("oauth")))
    }

    /**
     * Checks if the URL is an OAuth callback / handoff destination intended to return
     * session credentials directly into the main application container.
     */
    fun isAuthCallbackUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        val lower = urlString.lowercase()
        return lower.contains("/auth/google/callback") ||
                lower.contains("/auth/facebook/callback") ||
                lower.contains("/auth/handoff") ||
                lower.contains("auth-callback")
    }

    /**
     * Identifies Facebook OAuth and Socialite authentication entry points.
     * Meta strictly forbids embedded WebViews for Facebook login.
     */
    fun isFacebookAuthUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        val lower = urlString.lowercase()
        return lower.contains("facebook.com") ||
                lower.contains("fb.com") ||
                lower.contains("/auth/facebook") ||
                lower.contains("/login/facebook") ||
                lower.contains("/oauth/facebook") ||
                lower.contains("/signin/facebook") ||
                lower.contains("/sign-in/facebook") ||
                lower.contains("/signup/facebook") ||
                lower.contains("/sign-up/facebook") ||
                lower.contains("/facebook/redirect") ||
                lower.contains("/redirect/facebook") ||
                lower.contains("provider=facebook") ||
                lower.contains("driver=facebook")
    }

    /**
     * Identifies Google OAuth endpoints and web fallbacks.
     */
    fun isGoogleAuthUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        val lower = urlString.lowercase()
        return lower.contains("accounts.google.com") ||
                lower.contains("/auth/google") ||
                lower.contains("/login/google") ||
                lower.contains("/oauth/google") ||
                lower.contains("/signin/google") ||
                lower.contains("/sign-in/google") ||
                lower.contains("/signup/google") ||
                lower.contains("/sign-up/google") ||
                lower.contains("/google/redirect") ||
                lower.contains("/redirect/google") ||
                lower.contains("provider=google") ||
                lower.contains("driver=google")
    }
}
