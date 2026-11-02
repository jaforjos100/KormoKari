package com.example.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import org.json.JSONObject

/**
 * Native Android Foreground Service for Worker Live GPS Dispatch telemetry.
 * Powers the "সক্রিয় (Active)" worker toggle and nearby job dispatching within 10 km.
 */
class WorkerLocationService : Service(), LocationListener {

    private var locationManager: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val authToken = intent?.getStringExtra(EXTRA_AUTH_TOKEN) ?: ""
        currentAuthToken = authToken
        isTracking = true

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val lm = locationManager ?: return
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 5f, this)
                val lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null) {
                    updateLocation(lastKnown)
                }
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 5f, this)
                val lastKnown = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastKnown != null && lastLocation == null) {
                    updateLocation(lastKnown)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        updateLocation(location)
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        isTracking = false
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KormoKari Worker Live Dispatch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous high-accuracy location tracking for nearby job dispatch"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KormoKari Live Dispatch Active")
            .setContentText("You are active for nearby job matching and dispatches (১০ কিমি রেডিয়াসে সক্রিয়)")
            .setSmallIcon(R.drawable.kormokari_splash_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "kormokari_worker_location_channel"
        const val NOTIFICATION_ID = 91010
        const val EXTRA_AUTH_TOKEN = "EXTRA_AUTH_TOKEN"

        @Volatile
        var isTracking = false
            private set

        @Volatile
        var lastLocation: Location? = null
            private set

        @Volatile
        var currentAuthToken: String = ""
            private set

        fun updateLocation(location: Location) {
            lastLocation = location
        }

        fun getTrackingStatusJson(): String {
            val json = JSONObject()
            json.put("status", if (isTracking) "ACTIVE" else "STOPPED")
            json.put("isTracking", isTracking)
            json.put("isNative", true)
            val loc = lastLocation
            if (loc != null) {
                json.put("latitude", loc.latitude)
                json.put("longitude", loc.longitude)
                json.put("accuracy", loc.accuracy.toDouble())
                json.put("timestamp", loc.time)
            } else {
                json.put("latitude", 23.7196) // Default fallback Dhaka coordinates
                json.put("longitude", 90.3751)
                json.put("accuracy", 10.0)
                json.put("timestamp", System.currentTimeMillis())
            }
            return json.toString()
        }

        fun start(context: Context, authToken: String): Boolean {
            return try {
                val intent = Intent(context, WorkerLocationService::class.java).apply {
                    putExtra(EXTRA_AUTH_TOKEN, authToken)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isTracking = true
                true
            } catch (e: Exception) {
                false
            }
        }

        fun stop(context: Context): Boolean {
            return try {
                val intent = Intent(context, WorkerLocationService::class.java)
                context.stopService(intent)
                isTracking = false
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
