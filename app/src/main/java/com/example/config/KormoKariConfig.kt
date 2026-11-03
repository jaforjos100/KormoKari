package com.example.config

/**
 * Central configuration, signing identity audit, and metadata for KormoKari Android application.
 * Defines the verified production origin, package identity, and TWA association state.
 */
object KormoKariConfig {
    /**
     * The verified production web origin hosting the Laravel backend and KormoKari PWA.
     */
    const val PRODUCTION_ORIGIN = "https://kormokari.com"
    const val PRODUCTION_URL = "https://kormokari.com/"

    /**
     * Permanent application ID established for this release.
     */
    const val APPLICATION_ID = "com.kormokari.app"

    /**
     * Application display name and branding tagline.
     */
    const val APP_NAME = "KormoKari"
    const val TAGLINE = "Find work. Create opportunity."
    const val VERSION_NAME = "3.10.0"
    const val VERSION_CODE = 31000

    /**
     * Default Google OAuth Web Client ID for Credential Manager token requests.
     * Can be overridden by the web client or server during sign-in initialization.
     */
    const val DEFAULT_WEB_CLIENT_ID = "604893069222-kormokari-web.apps.googleusercontent.com"

    /**
     * Verified local Debug certificate SHA-256 fingerprint.
     * Extracted from debug.keystore (Alias: androiddebugkey).
     * Suitable ONLY for local emulator and development testing.
     */
    const val DEBUG_CERTIFICATE_SHA256 =
        "AF:AE:A1:ED:6F:F6:5E:3D:C9:38:B4:CC:43:3D:51:24:76:7F:62:3D:5C:88:B2:A5:36:15:BA:AE:99:5B:7F:68"

    /**
     * Status of the production release signing identity.
     * Marked BLOCKED per Phase 2 mandate until production release keystore or Google Play App Signing is configured.
     */
    const val PRODUCTION_SIGNING_STATUS = "BLOCKED (Awaiting production release keystore or Google Play App Signing key)"

    /**
     * Digital Asset Links status tracker.
     * Note: Digital Asset Links verification is pending until the release signing key's
     * SHA-256 fingerprint is published on https://kormokari.com/.well-known/assetlinks.json.
     * In the meantime, the app launches through Custom Tabs / TWA intent with standard browser chrome,
     * transitioning into full standalone mode once assetlinks.json is matched.
     */
    enum class AssetLinksStatus(val description: String, val isVerified: Boolean) {
        VERIFIED("Verified standalone TWA association active (Fullscreen enabled)", true),
        PENDING_VERIFICATION("Pending domain association on live server (.well-known/assetlinks.json returned 404)", false),
        FAILED("Domain verification failed or fingerprint mismatch (Fallback to Custom Tab toolbar)", false)
    }

    var currentAssetLinksStatus: AssetLinksStatus = AssetLinksStatus.PENDING_VERIFICATION

    fun updateAssetLinksVerification(isVerified: Boolean) {
        currentAssetLinksStatus = if (isVerified) {
            AssetLinksStatus.VERIFIED
        } else {
            AssetLinksStatus.FAILED
        }
    }

    /**
     * Timeout for initial connectivity check before offering retry / diagnostic options (in milliseconds).
     */
    const val STARTUP_TIMEOUT_MS = 6000L

    /**
     * Native launch splash transition animation duration (in milliseconds).
     */
    const val SPLASH_ANIMATION_DURATION_MS = 650

    /**
     * Authoritative backend availability & startup readiness contract specifications:
     * - Primary Endpoint: GET https://kormokari.com/api/app/availability
     * - Expected JSON Fields:
     *   status="ready", service="kormokari-core", app="KormoKari", version="ready-v1", available=true, mode="normal"
     * - Homepage Verification: GET https://kormokari.com/
     */
    const val AVAILABILITY_API_URL = "https://kormokari.com/api/app/availability"
    const val HOMEPAGE_URL = "https://kormokari.com/"
    const val HEALTH_CHECK_HEADER = "X-KormoKari-Health"
    const val HEALTH_CHECK_EXPECTED_VALUE = "ready-v1"
    const val HEALTH_CHECK_URL = AVAILABILITY_API_URL
    const val HEALTH_CHECK_FALLBACK_URL = HOMEPAGE_URL
    const val HEALTH_CHECK_TIMEOUT_MS = 6000L
    const val HEALTH_CHECK_COOLDOWN_MS = 15000L

    /**
     * Social Authentication Endpoints on the KormoKari Laravel backend.
     * Reused for Google fallback and Facebook OAuth Custom Tab flows.
     */
    const val GOOGLE_AUTH_URL = "https://kormokari.com/auth/google/redirect?source=app"
    const val FACEBOOK_AUTH_URL = "https://kormokari.com/auth/facebook/redirect?source=app"
    const val GOOGLE_REDIRECT_URL = "https://kormokari.com/auth/google/redirect?source=app"
    const val FACEBOOK_REDIRECT_URL = "https://kormokari.com/auth/facebook/redirect?source=app"
    const val GOOGLE_CALLBACK_PATH = "/auth/google/callback"
    const val FACEBOOK_CALLBACK_PATH = "/auth/facebook/callback"
    const val OAUTH_CUSTOM_SCHEME = "kormokari"
    const val OAUTH_CUSTOM_HOST = "auth-callback"
}
