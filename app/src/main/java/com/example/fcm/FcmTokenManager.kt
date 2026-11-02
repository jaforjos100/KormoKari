package com.example.fcm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class FcmTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "FcmTokenManager"
        private const val PREFS_NAME = "kormokari_fcm_prefs"
        private const val KEY_FCM_TOKEN = "key_fcm_registration_token"
        private const val KEY_TOKEN_TIMESTAMP = "key_token_timestamp"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentToken = MutableStateFlow<String?>(getCachedToken())
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    fun getCachedToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_FCM_TOKEN, token)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
        _currentToken.value = token
        Log.i(TAG, "FCM registration token successfully persisted.")
    }

    fun clearToken() {
        prefs.edit().remove(KEY_FCM_TOKEN).remove(KEY_TOKEN_TIMESTAMP).apply()
        _currentToken.value = null
    }

    /**
     * Asynchronously retrieves the current Firebase registration token from the SDK.
     */
    suspend fun fetchFreshToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val isInitialized = com.example.KormoKariApplication.ensureFirebaseInitialized(context)
            if (!isInitialized && com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                Log.w(TAG, "FirebaseApp is not configured. FCM token retrieval deferred.")
                continuation.resume(Result.failure(IllegalStateException("FirebaseApp not initialized")))
                return@suspendCancellableCoroutine
            }

            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        saveToken(token)
                        continuation.resume(Result.success(token))
                    } else {
                        val exception = task.exception ?: IllegalStateException("Failed to fetch FCM token")
                        Log.w(TAG, "Fetching FCM registration token deferred: ${exception.message}")
                        continuation.resume(Result.failure(exception))
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseMessaging token retrieval deferred: ${e.message}")
            continuation.resume(Result.failure(e))
        }
    }
}
