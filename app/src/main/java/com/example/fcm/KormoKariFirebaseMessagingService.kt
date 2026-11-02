package com.example.fcm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.config.KormoKariConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.atomic.AtomicInteger

class KormoKariFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KormoKariFCM"
        const val EXTRA_TARGET_URL = "extra_target_url"
        private val notificationIdGenerator = AtomicInteger(1001)

        /**
         * Resolves the target destination URL from notification payload.
         */
        fun resolveTargetUrl(data: Map<String, String>): String {
            val explicitUrl = data["url"] ?: data["target_url"] ?: data["link"]
            if (!explicitUrl.isNullOrBlank()) {
                return explicitUrl
            }
            val jobId = data["job_id"] ?: data["jobId"]
            if (!jobId.isNullOrBlank()) {
                return "${KormoKariConfig.PRODUCTION_URL}jobs/$jobId"
            }
            val applicationId = data["application_id"] ?: data["applicationId"]
            if (!applicationId.isNullOrBlank()) {
                return "${KormoKariConfig.PRODUCTION_URL}dashboard/applications/$applicationId"
            }
            return KormoKariConfig.PRODUCTION_URL
        }

        /**
         * Selects the appropriate notification channel based on message category.
         */
        fun resolveChannelId(data: Map<String, String>): String {
            val category = (data["channel"] ?: data["type"] ?: data["category"]).orEmpty().lowercase()
            return when {
                category.contains("app") || category.contains("status") || category.contains("interview") -> {
                    NotificationChannelHelper.CHANNEL_APP_STATUS
                }
                else -> {
                    NotificationChannelHelper.CHANNEL_JOB_ALERTS
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token generated.")
        FcmTokenManager(applicationContext).saveToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // 1. Ensure channels are registered
        NotificationChannelHelper.createNotificationChannels(applicationContext)

        val data = remoteMessage.data
        val notificationPayload = remoteMessage.notification

        // Extract title and body from either payload type
        val title = notificationPayload?.title
            ?: data["title"]
            ?: getString(R.string.app_name)

        val body = notificationPayload?.body
            ?: data["body"]
            ?: data["message"]
            ?: "You have a new update in KormoKari."

        val targetUrl = resolveTargetUrl(data)
        val channelId = resolveChannelId(data)

        showNotification(
            title = title,
            body = body,
            targetUrl = targetUrl,
            channelId = channelId
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        targetUrl: String,
        channelId: String
    ) {
        // Check Android 13+ runtime notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Suppressing push notification display.")
                return
            }
        }

        val notificationId = notificationIdGenerator.incrementAndGet()

        // Deep link intent to launch MainActivity with targetUrl
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_URL, targetUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(resources, R.drawable.kormokari_logo)
        } catch (_: Exception) {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.kormokari_splash_icon)
            .apply {
                if (largeIcon != null) {
                    setLargeIcon(largeIcon)
                }
            }
            .setColor(getColor(R.color.kormokari_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
                    .setSummaryText(if (channelId == NotificationChannelHelper.CHANNEL_JOB_ALERTS) "Job Alert" else "Application Status")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(
                if (channelId == NotificationChannelHelper.CHANNEL_JOB_ALERTS) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.i(TAG, "Delivered notification ID $notificationId to channel $channelId ($title)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while displaying notification: ${e.message}")
        }
    }
}
