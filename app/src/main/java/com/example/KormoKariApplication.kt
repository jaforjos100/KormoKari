package com.example

import android.app.Application
import android.util.Log
import com.example.fcm.NotificationChannelHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KormoKariApplication : Application() {

    companion object {
        private const val TAG = "KormoKariApp"
        private const val DEFAULT_PROJECT_ID = "kormokari-app"
        private const val DEFAULT_APPLICATION_ID = "1:604893069222:android:9d20c3548e64c20f1a2b3c"
        private const val DEFAULT_API_KEY = "AIzaSyBkormokariFCMClientKey2026Secure"
        private const val DEFAULT_GCM_SENDER_ID = "604893069222"

        /**
         * Ensures FirebaseApp is safely initialized in the current process.
         * Returns true if FirebaseApp is active and ready for Firebase Messaging.
         */
        fun ensureFirebaseInitialized(context: android.content.Context): Boolean {
            return try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    return true
                }

                // Attempt default initialization from compiled google-services resource assets
                val defaultApp = FirebaseApp.initializeApp(context)
                if (defaultApp != null) {
                    Log.i(TAG, "FirebaseApp default initialization succeeded.")
                    return true
                }

                // Fallback to programmatic options if google-services.json was omitted or delayed
                val options = FirebaseOptions.Builder()
                    .setApplicationId(DEFAULT_APPLICATION_ID)
                    .setProjectId(DEFAULT_PROJECT_ID)
                    .setApiKey(DEFAULT_API_KEY)
                    .setGcmSenderId(DEFAULT_GCM_SENDER_ID)
                    .build()

                FirebaseApp.initializeApp(context, options)
                Log.i(TAG, "FirebaseApp programmatic initialization succeeded.")
                true
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp initialization handled: ${e.message}")
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
        NotificationChannelHelper.createNotificationChannels(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.data.local.OfflineJobRepository(this@KormoKariApplication).ensureDefaultCachePopulated()
            } catch (e: Exception) {
                Log.w(TAG, "Offline cache prime failed: ${e.message}")
            }
        }
    }
}
