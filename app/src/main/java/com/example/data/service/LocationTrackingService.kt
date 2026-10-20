package com.example.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.DailyTrackApplication
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private var isLocationCallbackRegistered = false

    companion object {
        const val CHANNEL_ID = "daily_track_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_TRACKING"
        const val ACTION_PAUSE = "com.example.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.example.action.RESUME_TRACKING"
        const val ACTION_STOP = "com.example.action.STOP_TRACKING"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused = _isPaused.asStateFlow()

        private val _lastLocation = MutableStateFlow<Location?>(null)
        val lastLocation = _lastLocation.asStateFlow()

        private val _trackingStatus = MutableStateFlow("Initializing...")
        val trackingStatus = _trackingStatus.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _lastLocation.value = loc
            processIncomingLocation(loc)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                _trackingStatus.value = "GPS Signal Unavailable / Waiting"
                updateNotification("GPS signal weak or unavailable")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                _isServiceRunning.value = true
                _isPaused.value = false
                startForegroundWithNotification("Daily routine tracking active")
                startLocationUpdates()
            }
            ACTION_PAUSE -> {
                _isPaused.value = true
                _trackingStatus.value = "Tracking Paused"
                stopLocationUpdates()
                updateNotification("Tracking is temporarily paused")
                serviceScope.launch {
                    val app = application as DailyTrackApplication
                    app.preferencesRepository.setTrackingPaused(true)
                }
            }
            ACTION_RESUME -> {
                _isPaused.value = false
                _trackingStatus.value = "Resuming Tracking..."
                startLocationUpdates()
                updateNotification("Daily routine tracking active")
                serviceScope.launch {
                    val app = application as DailyTrackApplication
                    app.preferencesRepository.setTrackingPaused(false)
                }
            }
            ACTION_STOP -> {
                stopTrackingAndService()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = if (_isPaused.value) ACTION_RESUME else ACTION_PAUSE
        }
        val pendingPauseResume = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeActionTitle = if (_isPaused.value) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DailyTrack - Routine Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingOpenApp)
            .addAction(0, pauseResumeActionTitle, pendingPauseResume)
            .addAction(0, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationCallbackRegistered) return

        serviceScope.launch {
            val app = application as DailyTrackApplication
            val intervalSec = app.preferencesRepository.trackingIntervalSeconds.first()

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                intervalSec * 1000L
            ).apply {
                setMinUpdateIntervalMillis((intervalSec * 500L).coerceAtLeast(2000L))
                setMinUpdateDistanceMeters(3f)
                setWaitForAccurateLocation(false)
            }.build()

            try {
                fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
                isLocationCallbackRegistered = true
                _trackingStatus.value = "Recording Movements"
            } catch (e: SecurityException) {
                _trackingStatus.value = "Location Permission Revoked"
                updateNotification("Location permission revoked")
            } catch (e: Exception) {
                _trackingStatus.value = "Error: ${e.localizedMessage ?: "Unknown"}"
            }
        }
    }

    private fun stopLocationUpdates() {
        if (isLocationCallbackRegistered) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
            isLocationCallbackRegistered = false
        }
    }

    private fun processIncomingLocation(location: Location) {
        serviceScope.launch {
            val app = application as DailyTrackApplication
            val radius = app.preferencesRepository.stationaryRadiusMeters.first()
            val dwell = app.preferencesRepository.dwellTimeMinutes.first()

            try {
                val (point, visit) = app.locationRepository.recordLocation(
                    location = location,
                    stationaryRadiusMeters = radius,
                    dwellTimeMinutes = dwell
                )

                val statusText: String
                val placeDesc: String
                if (visit != null) {
                    val durationMin = visit.durationSeconds / 60
                    placeDesc = visit.placeName
                    statusText = "At ${visit.placeName} (${durationMin}m)"
                } else {
                    placeDesc = "In Transit / Moving"
                    statusText = String.format(Locale.US, "Moving (Speed: %.1f km/h)", point.speed * 3.6f)
                }

                _trackingStatus.value = statusText
                updateNotification(statusText)

                app.preferencesRepository.updateLastLocation(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracy = point.accuracy,
                    timestampUtc = point.timestampUtc,
                    placeName = placeDesc,
                    statusMessage = statusText
                )
            } catch (e: Exception) {
                _trackingStatus.value = "Storage warning: ${e.message}"
            }
        }
    }

    private fun stopTrackingAndService() {
        stopLocationUpdates()
        serviceScope.launch {
            val app = application as DailyTrackApplication
            app.locationRepository.closeOngoingVisit()
            app.preferencesRepository.setTrackingEnabled(false)
            app.preferencesRepository.setTrackingPaused(false)
            _isServiceRunning.value = false
            _isPaused.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of DailyTrack background routine recording."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        _isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
