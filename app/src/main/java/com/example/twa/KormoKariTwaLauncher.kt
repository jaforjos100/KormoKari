package com.example.twa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import com.example.R
import com.example.config.KormoKariConfig
import com.google.androidbrowserhelper.trusted.TwaLauncher

sealed interface LaunchResult {
    data class Success(val packageUsed: String, val isTwaCapable: Boolean) : LaunchResult
    data class FallbackCustomTabs(val packageUsed: String, val message: String) : LaunchResult
    data class Failure(val exception: Throwable, val userMessage: String) : LaunchResult
}

data class BrowserDiagnostics(
    val defaultBrowserPackage: String?,
    val isCustomTabsSupported: Boolean,
    val targetUrl: String = KormoKariConfig.PRODUCTION_URL,
    val digitalAssetLinksStatus: String = KormoKariConfig.currentAssetLinksStatus.description
)

class KormoKariTwaLauncher(private val context: Context) {

    companion object {
        private const val TAG = "KormoKariTwaLauncher"
    }

    private var twaLauncher: TwaLauncher? = null

    /**
     * Inspects browser capabilities on the device.
     */
    fun getBrowserDiagnostics(): BrowserDiagnostics {
        val customTabsPackage = CustomTabsClient.getPackageName(
            context,
            null
        )
        return BrowserDiagnostics(
            defaultBrowserPackage = customTabsPackage,
            isCustomTabsSupported = !customTabsPackage.isNullOrEmpty(),
            targetUrl = KormoKariConfig.PRODUCTION_URL,
            digitalAssetLinksStatus = KormoKariConfig.currentAssetLinksStatus.description
        )
    }

    /**
     * Launches the KormoKari production origin using Trusted Web Activity with Custom Tabs fallback.
     */
    fun launch(url: String = KormoKariConfig.PRODUCTION_URL): LaunchResult {
        return try {
            val uri = Uri.parse(url)
            val customTabsPackage = CustomTabsClient.getPackageName(context, null)

            val primaryColor = context.getColor(R.color.kormokari_primary)
            val surfaceColor = context.getColor(R.color.kormokari_surface)

            if (!customTabsPackage.isNullOrEmpty()) {
                // Initialize Android Browser Helper TwaLauncher
                if (twaLauncher == null) {
                    twaLauncher = TwaLauncher(context)
                }

                val builder = TrustedWebActivityIntentBuilder(uri)
                    .setNavigationBarColor(surfaceColor)
                    .setToolbarColor(primaryColor)
                    .setAdditionalTrustedOrigins(listOf("https://kormokari.com"))

                val validationCallback = object : CustomTabsCallback() {
                    override fun onRelationshipValidationResult(
                        relation: Int,
                        requestedOrigin: Uri,
                        result: Boolean,
                        extras: Bundle?
                    ) {
                        Log.i(TAG, "TWA relationship validation for $requestedOrigin: $result (relation=$relation)")
                        KormoKariConfig.updateAssetLinksVerification(result)
                    }
                }

                // Launch through Android Browser Helper with CCT fallback
                twaLauncher?.launch(builder, validationCallback, null, null, TwaLauncher.CCT_FALLBACK_STRATEGY)

                Log.i(TAG, "Launched TWA/Custom Tab via package: $customTabsPackage for $url")
                LaunchResult.Success(
                    packageUsed = customTabsPackage,
                    isTwaCapable = true
                )
            } else {
                // Fallback to standard ACTION_VIEW if no custom tabs provider is found
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(fallbackIntent)
                    LaunchResult.FallbackCustomTabs(
                        packageUsed = "System Default Browser",
                        message = "Opened in standard browser because no Custom Tabs provider was detected."
                    )
                } catch (e: Exception) {
                    LaunchResult.Failure(
                        exception = e,
                        userMessage = "No compatible web browser was found on your device to open KormoKari."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch TWA: ${e.message}", e)
            // Attempt emergency browser launch
            try {
                val emergencyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(emergencyIntent)
                LaunchResult.FallbackCustomTabs(
                    packageUsed = "Emergency Intent Fallback",
                    message = "Opened via system browser fallback."
                )
            } catch (fallbackError: Exception) {
                LaunchResult.Failure(
                    exception = e,
                    userMessage = e.localizedMessage ?: "Failed to launch KormoKari."
                )
            }
        }
    }

    fun destroy() {
        twaLauncher?.destroy()
        twaLauncher = null
    }
}
