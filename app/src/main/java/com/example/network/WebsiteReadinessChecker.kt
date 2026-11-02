package com.example.network

import com.example.config.KormoKariConfig
import okhttp3.OkHttpClient

/**
 * Authoritative Website Startup Readiness & Health Checker for KormoKari.
 * Delegates to [AppAvailabilityManager] for availability API contract verification
 * and homepage health validation before opening the WebView.
 */
class WebsiteReadinessChecker(
    client: OkHttpClient = AppAvailabilityManager.defaultClient()
) {
    private val availabilityManager = AppAvailabilityManager(client)

    /**
     * Checks if a new health check should be executed based on the cooldown window.
     */
    fun shouldCheckOnResume(cooldownMs: Long = KormoKariConfig.HEALTH_CHECK_COOLDOWN_MS): Boolean {
        return availabilityManager.shouldCheckOnResume(cooldownMs)
    }

    /**
     * Executes the authoritative two-stage availability check against KormoKari backend:
     * 1. GET https://kormokari.com/api/app/availability (Availability API)
     * 2. GET https://kormokari.com/ (Homepage Content Validation)
     */
    suspend fun checkReadiness(
        availabilityUrl: String = KormoKariConfig.AVAILABILITY_API_URL,
        homepageUrl: String = KormoKariConfig.HOMEPAGE_URL
    ): ServerHealthState {
        return availabilityManager.checkAvailability(availabilityUrl, homepageUrl)
    }

    /**
     * Fresh live check without reading cached state, executed before every navigation and reload.
     */
    suspend fun checkFreshAvailability(
        availabilityUrl: String = KormoKariConfig.AVAILABILITY_API_URL,
        homepageUrl: String = KormoKariConfig.HOMEPAGE_URL
    ): ServerHealthState {
        return availabilityManager.checkFreshAvailability(availabilityUrl, homepageUrl)
    }

    companion object {
        /**
         * Parses and validates the JSON payload returned by GET https://kormokari.com/api/app/availability.
         */
        fun parseAndValidateAvailabilityJson(jsonString: String): AvailabilityValidationResult {
            return AppAvailabilityManager.parseAndValidateAvailabilityJson(jsonString)
        }

        /**
         * Inspects and validates live homepage HTML content.
         */
        fun validateHomepageContent(httpCode: Int, body: String, finalUrl: String? = null): HomepageValidationResult {
            return AppAvailabilityManager.validateHomepageContent(httpCode, body, finalUrl)
        }
    }
}
