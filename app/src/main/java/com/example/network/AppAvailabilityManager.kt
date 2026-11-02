package com.example.network

import android.net.Uri
import android.util.Log
import com.example.config.KormoKariConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Result of validating the live Availability API payload.
 */
sealed interface AvailabilityValidationResult {
    data object Valid : AvailabilityValidationResult
    data class Failed(val reason: String) : AvailabilityValidationResult
}

/**
 * Result of validating the actual live homepage content.
 */
sealed interface HomepageValidationResult {
    data object Valid : HomepageValidationResult
    data class Failed(val reason: String) : HomepageValidationResult
}

/**
 * Validated model representing the server availability payload from
 * GET https://kormokari.com/api/app/availability.
 */
data class AvailabilityPayload(
    val status: String,
    val service: String,
    val app: String,
    val version: String,
    val available: Boolean,
    val mode: String,
    val message: String? = null
) {
    val isOperational: Boolean
        get() = status.equals("ready", ignoreCase = true) &&
            service.equals("kormokari-core", ignoreCase = true) &&
            app.equals("KormoKari", ignoreCase = true) &&
            version.equals("ready-v1", ignoreCase = true) &&
            available &&
            mode.equals("normal", ignoreCase = true)
}

/**
 * Dedicated manager responsible for orchestrating the KormoKari application availability checks
 * before opening the WebView.
 *
 * Implements a strict two-stage verification process:
 * 1. Availability API Verification (`GET https://kormokari.com/api/app/availability`):
 *    - Validates HTTP 200 and well-formed JSON.
 *    - Validates status="ready", service="kormokari-core", app="KormoKari",
 *      version="ready-v1", available=true, mode="normal".
 *
 * 2. Homepage Health Verification (`GET https://kormokari.com/`):
 *    - Rejects directory listings (`Index of /`, `<h1>Index of /</h1>`, etc.).
 *    - Rejects ZIP directory listings (`.zip` links in directory indexes).
 *    - Rejects raw PHP/SQL/Laravel server errors (Fatal error, SQLSTATE, Stack trace, 500/502/504).
 *    - Validates minimum HTML structure and KormoKari brand identity.
 *    - Validates that redirects do not leak to untrusted domains or error routes.
 */
class AppAvailabilityManager(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private const val TAG = "AppAvailabilityManager"

        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; KormoKariApp/1.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /**
         * Parses and validates the JSON payload returned by GET https://kormokari.com/api/app/availability.
         */
        fun parseAndValidateAvailabilityJson(jsonString: String): AvailabilityValidationResult {
            if (jsonString.isBlank()) {
                return AvailabilityValidationResult.Failed("Empty response body from availability API")
            }
            return try {
                val json = JSONObject(jsonString)
                val status = json.optString("status")
                val service = json.optString("service")
                val app = json.optString("app")
                val version = json.optString("version")
                val available = json.optBoolean("available", false)
                val mode = json.optString("mode")

                val payload = AvailabilityPayload(
                    status = status,
                    service = service,
                    app = app,
                    version = version,
                    available = available,
                    mode = mode,
                    message = json.optString("message", null)
                )

                if (!status.equals("ready", ignoreCase = true)) {
                    return AvailabilityValidationResult.Failed("Invalid status: expected 'ready', found '$status'")
                }
                if (!service.equals("kormokari-core", ignoreCase = true)) {
                    return AvailabilityValidationResult.Failed("Invalid service: expected 'kormokari-core', found '$service'")
                }
                if (!app.equals("KormoKari", ignoreCase = true)) {
                    return AvailabilityValidationResult.Failed("Invalid app: expected 'KormoKari', found '$app'")
                }
                if (!version.equals("ready-v1", ignoreCase = true)) {
                    return AvailabilityValidationResult.Failed("Invalid version: expected 'ready-v1', found '$version'")
                }
                if (!available) {
                    return AvailabilityValidationResult.Failed("Application is marked unavailable (available=false)")
                }
                if (!mode.equals("normal", ignoreCase = true)) {
                    return AvailabilityValidationResult.Failed("Application mode is '$mode', expected 'normal'")
                }

                if (!payload.isOperational) {
                    return AvailabilityValidationResult.Failed("Availability contract validation failed")
                }

                AvailabilityValidationResult.Valid
            } catch (e: Exception) {
                AvailabilityValidationResult.Failed("Malformed JSON: ${e.message}")
            }
        }

        /**
         * Inspects and validates live homepage HTML content.
         * Enforces rejection of directory listings, ZIP listings, and server error signatures.
         */
        fun validateHomepageContent(httpCode: Int, body: String, finalUrl: String? = null): HomepageValidationResult {
            if (httpCode != 200) {
                return HomepageValidationResult.Failed("Server returned HTTP $httpCode")
            }

            // Verify redirect destination
            if (finalUrl != null) {
                val uri = try { Uri.parse(finalUrl) } catch (_: Exception) { null }
                val host = uri?.host?.lowercase()
                if (host != null && host != "kormokari.com" && host != "www.kormokari.com") {
                    return HomepageValidationResult.Failed("Unexpected redirect to untrusted host: $host")
                }
                val path = uri?.path?.lowercase() ?: ""
                if (path.contains("maintenance") || path.contains("error") || path.contains("server-down")) {
                    return HomepageValidationResult.Failed("Redirected to maintenance/error page: $path")
                }
            }

            if (body.isBlank()) {
                return HomepageValidationResult.Failed("Homepage content is empty")
            }

            val lowerBody = body.lowercase()

            // 1. Reject ZIP archive directory listings
            val zipPattern = Regex("""href=["'][^"']*\.zip["']""", RegexOption.IGNORE_CASE)
            if (zipPattern.containsMatchIn(body) &&
                (lowerBody.contains("parent directory") || lowerBody.contains("index of") || lowerBody.contains("[dir]") || lowerBody.contains("<pre>"))
            ) {
                return HomepageValidationResult.Failed("ZIP directory listing detected")
            }

            // 2. Reject Directory Listings (Index of /)
            if (lowerBody.contains("index of /") ||
                lowerBody.contains("<title>index of") ||
                lowerBody.contains("<h1>index of") ||
                lowerBody.contains("directory listing for") ||
                (lowerBody.contains("parent directory") && (lowerBody.contains("<pre>") || lowerBody.contains("<table>")))
            ) {
                return HomepageValidationResult.Failed("Directory listing detected (Index of /)")
            }

            // 3. Reject Raw Server Errors (PHP, SQL, Laravel, Nginx/Apache)
            val rawErrorSignatures = listOf(
                "fatal error:",
                "parse error:",
                "sqlstate[",
                "stack trace:",
                "whoops! there was an error",
                "500 internal server error",
                "502 bad gateway",
                "504 gateway time-out",
                "503 service unavailable",
                "database connection error",
                "uncaught exception",
                "access denied for user"
            )
            for (signature in rawErrorSignatures) {
                if (lowerBody.contains(signature)) {
                    return HomepageValidationResult.Failed("Raw server error detected: $signature")
                }
            }

            // 4. Validate Minimum Content Length
            if (body.length < 300) {
                return HomepageValidationResult.Failed("Homepage content is empty or incomplete")
            }

            // 5. Validate HTML Structure
            if (!lowerBody.contains("<html") || (!lowerBody.contains("<body") && !lowerBody.contains("<head"))) {
                return HomepageValidationResult.Failed("Content is not valid HTML")
            }

            // 6. Verify KormoKari brand identity
            if (!lowerBody.contains("kormokari")) {
                return HomepageValidationResult.Failed("Missing KormoKari brand identity in content")
            }

            return HomepageValidationResult.Valid
        }
    }

    private val isChecking = AtomicBoolean(false)
    private var lastCheckTimeMillis: Long = 0L

    /**
     * Checks if a new availability check should be performed on app resume based on the cooldown.
     */
    fun shouldCheckOnResume(cooldownMs: Long = KormoKariConfig.HEALTH_CHECK_COOLDOWN_MS): Boolean {
        if (isChecking.get()) return false
        val now = System.currentTimeMillis()
        return (now - lastCheckTimeMillis) >= cooldownMs
    }

    /**
     * Executes the comprehensive availability check validating both the Availability API
     * and the Homepage health before allowing the WebView to load.
     */
    suspend fun checkAvailability(
        availabilityUrl: String = KormoKariConfig.AVAILABILITY_API_URL,
        homepageUrl: String = KormoKariConfig.HOMEPAGE_URL
    ): ServerHealthState = withContext(Dispatchers.IO) {
        if (!isChecking.compareAndSet(false, true)) {
            Log.d(TAG, "Availability check already in progress. Ignoring duplicate request.")
            return@withContext ServerHealthState.Checking
        }

        try {
            // Stage 1: Validate live Availability API
            val apiResult = executeAvailabilityCheck(availabilityUrl)
            if (apiResult !is ServerHealthState.Ready) {
                lastCheckTimeMillis = System.currentTimeMillis()
                return@withContext apiResult
            }

            // Stage 2: Validate live Homepage Content
            val homepageResult = executeHomepageCheck(homepageUrl)
            lastCheckTimeMillis = System.currentTimeMillis()
            homepageResult
        } finally {
            isChecking.set(false)
        }
    }

    /**
     * Executes fresh check without reading cached states, verifying the live Availability API
     * before each new navigation or reload.
     */
    suspend fun checkFreshAvailability(
        availabilityUrl: String = KormoKariConfig.AVAILABILITY_API_URL,
        homepageUrl: String = KormoKariConfig.HOMEPAGE_URL
    ): ServerHealthState = withContext(Dispatchers.IO) {
        // Stage 1: Validate live Availability API
        val apiResult = executeAvailabilityCheck(availabilityUrl)
        if (apiResult !is ServerHealthState.Ready) {
            return@withContext apiResult
        }

        // Stage 2: Validate live Homepage Content
        executeHomepageCheck(homepageUrl)
    }

    private fun executeAvailabilityCheck(targetUrl: String): ServerHealthState {
        val uri = try {
            Uri.parse(targetUrl)
        } catch (_: Exception) {
            return ServerHealthState.ConnectionError("Invalid target URL")
        }

        if (uri.scheme?.lowercase() != "https") {
            return ServerHealthState.TlsError("Insecure HTTP scheme rejected")
        }

        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .build()

        var response: Response? = null
        return try {
            response = client.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            when {
                code in 500..599 -> {
                    Log.w(TAG, "Availability API returned server error HTTP $code")
                    ServerHealthState.ServerNotReady(
                        checkedAtMillis = System.currentTimeMillis(),
                        httpCode = code,
                        details = "Server error HTTP $code"
                    )
                }
                code != 200 -> {
                    Log.w(TAG, "Availability API returned non-200 HTTP $code")
                    ServerHealthState.ConnectionError(
                        message = "Server connection unavailable",
                        httpCode = code
                    )
                }
                else -> {
                    when (val validation = parseAndValidateAvailabilityJson(bodyString)) {
                        is AvailabilityValidationResult.Valid -> {
                            Log.i(TAG, "Availability API contract verified READY")
                            ServerHealthState.Ready(
                                checkedAtMillis = System.currentTimeMillis(),
                                healthHeader = response.header(KormoKariConfig.HEALTH_CHECK_HEADER) ?: "ready-v1"
                            )
                        }
                        is AvailabilityValidationResult.Failed -> {
                            Log.w(TAG, "Availability API contract validation failed: ${validation.reason}")
                            ServerHealthState.ServerNotReady(
                                checkedAtMillis = System.currentTimeMillis(),
                                httpCode = code,
                                details = validation.reason
                            )
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Availability API timed out: ${e.message}")
            ServerHealthState.Timeout(timeoutMs = KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS)
        } catch (e: InterruptedIOException) {
            Log.w(TAG, "Availability API I/O timed out: ${e.message}")
            ServerHealthState.Timeout(timeoutMs = KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS)
        } catch (e: SSLHandshakeException) {
            Log.e(TAG, "TLS handshake failed: ${e.message}")
            ServerHealthState.TlsError("HTTPS TLS handshake failed")
        } catch (e: SSLException) {
            Log.e(TAG, "SSL/TLS verification failed: ${e.message}")
            ServerHealthState.TlsError("HTTPS certificate verification failed")
        } catch (e: CertificateException) {
            Log.e(TAG, "Certificate verification error: ${e.message}")
            ServerHealthState.TlsError("Certificate validation failed")
        } catch (e: UnknownHostException) {
            Log.w(TAG, "DNS resolution failed: ${e.message}")
            ServerHealthState.ConnectionError("Unable to resolve server address")
        } catch (e: ConnectException) {
            Log.w(TAG, "TCP connection failed: ${e.message}")
            ServerHealthState.ConnectionError("Connection to server was refused")
        } catch (e: IOException) {
            Log.w(TAG, "I/O connection error: ${e.message}")
            ServerHealthState.ConnectionError(e.message ?: "Server connection error")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            ServerHealthState.ConnectionError("Unexpected error connecting to server")
        } finally {
            response?.close()
        }
    }

    private fun executeHomepageCheck(targetUrl: String): ServerHealthState {
        val uri = try {
            Uri.parse(targetUrl)
        } catch (_: Exception) {
            return ServerHealthState.ConnectionError("Invalid target URL")
        }

        if (uri.scheme?.lowercase() != "https") {
            return ServerHealthState.TlsError("Insecure HTTP scheme rejected")
        }

        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Cache-Control", "no-cache")
            .build()

        var response: Response? = null
        return try {
            response = client.newCall(request).execute()
            val code = response.code
            val finalUrl = response.request.url.toString()
            val bodyString = response.body?.string() ?: ""

            when {
                code in 500..599 -> {
                    Log.w(TAG, "Homepage returned server error HTTP $code")
                    ServerHealthState.ServerNotReady(
                        checkedAtMillis = System.currentTimeMillis(),
                        httpCode = code,
                        details = "Homepage returned HTTP $code"
                    )
                }
                code != 200 -> {
                    Log.w(TAG, "Homepage returned HTTP $code")
                    ServerHealthState.ConnectionError(
                        message = "Homepage returned HTTP $code",
                        httpCode = code
                    )
                }
                else -> {
                    when (val validation = validateHomepageContent(code, bodyString, finalUrl)) {
                        is HomepageValidationResult.Valid -> {
                            Log.i(TAG, "Homepage content verified valid and healthy")
                            ServerHealthState.Ready(
                                checkedAtMillis = System.currentTimeMillis(),
                                healthHeader = response.header(KormoKariConfig.HEALTH_CHECK_HEADER) ?: "ready-v1"
                            )
                        }
                        is HomepageValidationResult.Failed -> {
                            Log.w(TAG, "Homepage validation rejected: ${validation.reason}")
                            ServerHealthState.ServerNotReady(
                                checkedAtMillis = System.currentTimeMillis(),
                                httpCode = code,
                                details = validation.reason
                            )
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Homepage check timed out: ${e.message}")
            ServerHealthState.Timeout(timeoutMs = KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS)
        } catch (e: InterruptedIOException) {
            Log.w(TAG, "Homepage check I/O timed out: ${e.message}")
            ServerHealthState.Timeout(timeoutMs = KormoKariConfig.HEALTH_CHECK_TIMEOUT_MS)
        } catch (e: SSLHandshakeException) {
            Log.e(TAG, "Homepage TLS handshake failed: ${e.message}")
            ServerHealthState.TlsError("HTTPS TLS handshake failed")
        } catch (e: SSLException) {
            Log.e(TAG, "Homepage SSL verification failed: ${e.message}")
            ServerHealthState.TlsError("HTTPS certificate verification failed")
        } catch (e: CertificateException) {
            Log.e(TAG, "Homepage certificate error: ${e.message}")
            ServerHealthState.TlsError("Certificate validation failed")
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Homepage DNS failed: ${e.message}")
            ServerHealthState.ConnectionError("Unable to resolve server address")
        } catch (e: ConnectException) {
            Log.w(TAG, "Homepage TCP connection failed: ${e.message}")
            ServerHealthState.ConnectionError("Connection to server was refused")
        } catch (e: IOException) {
            Log.w(TAG, "Homepage I/O error: ${e.message}")
            ServerHealthState.ConnectionError(e.message ?: "Server connection error")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            ServerHealthState.ConnectionError("Unexpected error connecting to homepage")
        } finally {
            response?.close()
        }
    }
}
