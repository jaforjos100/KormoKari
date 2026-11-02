package com.example.network

/**
 * Authoritative Server Readiness & Health States for KormoKari.
 * Strictly distinguishes between client-side internet errors and true server-side maintenance states.
 */
sealed interface ServerHealthState {
    /**
     * Actively checking website availability and backend readiness contract.
     */
    data object Checking : ServerHealthState

    /**
     * Authoritative website readiness confirmed via `X-KormoKari-Health: ready-v1` or HTTP 200 OK.
     */
    data class Ready(
        val checkedAtMillis: Long = System.currentTimeMillis(),
        val healthHeader: String? = "ready-v1"
    ) : ServerHealthState

    /**
     * Backend is unavailable or reports maintenance (`SERVER_NOT_READY` / HTTP 503).
     */
    data class ServerNotReady(
        val checkedAtMillis: Long = System.currentTimeMillis(),
        val httpCode: Int = 503,
        val details: String? = null
    ) : ServerHealthState

    /**
     * Device has no active internet connection (Wi-Fi/Cellular off).
     */
    data class NoInternet(
        val reason: String = "No internet connection detected",
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : ServerHealthState

    /**
     * Server connection failed (DNS resolution failure, TCP connection refused, HTTP 500/502/504).
     */
    data class ConnectionError(
        val message: String,
        val httpCode: Int? = null,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : ServerHealthState

    /**
     * Health check exceeded its timeout threshold (e.g. 6 seconds).
     */
    data class Timeout(
        val timeoutMs: Long,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : ServerHealthState

    /**
     * HTTPS certificate verification or TLS handshake failed. Security barrier.
     */
    data class TlsError(
        val message: String,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : ServerHealthState

    /**
     * Helper to retrieve the timestamp of the last authoritative check.
     */
    val timestamp: Long
        get() = when (this) {
            is Checking -> System.currentTimeMillis()
            is Ready -> checkedAtMillis
            is ServerNotReady -> checkedAtMillis
            is NoInternet -> checkedAtMillis
            is ConnectionError -> checkedAtMillis
            is Timeout -> checkedAtMillis
            is TlsError -> checkedAtMillis
        }
}
