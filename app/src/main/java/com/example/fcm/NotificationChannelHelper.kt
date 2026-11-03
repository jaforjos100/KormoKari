package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.R

object NotificationChannelHelper {

    const val CHANNEL_JOB_ALERTS = "kormokari_job_alerts"
    const val CHANNEL_APP_STATUS = "kormokari_app_status"
    const val CHANNEL_GENERAL = "kormokari_general"
    const val CHANNEL_NEARBY_LIVE_JOBS = "nearby_live_jobs"
    const val CHANNEL_SYSTEM_UPDATES = "system_updates"

    /**
     * Initializes all notification channels on Android O (API 26) and above.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            // Channel 1: High-importance channel for urgent job updates & matches
            val jobAlertsChannel = NotificationChannel(
                CHANNEL_JOB_ALERTS,
                context.getString(R.string.fcm_channel_jobs_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.fcm_channel_jobs_description)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // Channel 2: Medium/Default-importance channel for application and account status updates
            val appStatusChannel = NotificationChannel(
                CHANNEL_APP_STATUS,
                context.getString(R.string.fcm_channel_status_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.fcm_channel_status_description)
                enableVibration(true)
                setShowBadge(true)
            }

            // Channel 3: High-importance channel for urgent nearby live jobs & dispatch opportunities
            val nearbyLiveJobsChannel = NotificationChannel(
                CHANNEL_NEARBY_LIVE_JOBS,
                context.getString(R.string.fcm_channel_nearby_live_jobs_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.fcm_channel_nearby_live_jobs_description)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // Channel 4: Default-importance channel for platform announcements and system updates
            val systemUpdatesChannel = NotificationChannel(
                CHANNEL_SYSTEM_UPDATES,
                context.getString(R.string.fcm_channel_system_updates_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.fcm_channel_system_updates_description)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(jobAlertsChannel, appStatusChannel, nearbyLiveJobsChannel, systemUpdatesChannel)
            )
        }
    }
}
