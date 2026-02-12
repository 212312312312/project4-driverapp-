package com.taxiapp.driver.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.taxiapp.driver.MainActivity
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.network.UpdateLocationRequest
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private var isServiceRunning = false

    // --- ЛОГІКА НАГАДУВАННЯ (НОВА) ---
    private var trackingOrder: Order? = null
    private var isReminderTriggered = false

    // Биндери для зв'язку з Activity
    inner class LocalBinder : android.os.Binder() {
        fun getService(): LocationService = this@LocationService
    }
    private val binder = LocalBinder()
    // ---------------------------------

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        isServiceRunning = true

        startForegroundService()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isServiceRunning) return
                for (location in locationResult.locations) {
                    // 1. Основна логіка: відправка на сервер (твоя стара логіка)
                    sendLocationToServer(location.latitude, location.longitude)

                    // 2. Нова логіка: перевірка дистанції для віджета
                    checkDistanceToTarget(location)
                }
            }
        }

        sendInitialLocation()
        requestLocationUpdates()
    }

    // --- МЕТОДИ ДЛЯ Activity (НОВІ) ---
    fun setTargetOrder(order: Order?) {
        this.trackingOrder = order
        this.isReminderTriggered = false // Скидаємо тригер при новому замовленні/статусі
        Log.d("LocationService", "Tracking order updates: ID=${order?.id}, Status=${order?.status}")
    }
    // ----------------------------------

    private fun checkDistanceToTarget(currentLoc: Location) {
        val order = trackingOrder ?: return

        if (!sessionManager.isStatusReminderEnabled() || isReminderTriggered) return

        val targetLoc = Location("target")
        var actionType = 0

        when (order.status) {
            "ACCEPTED" -> {
                // Використовуємо ?: 0.0, щоб перетворити Double? в Double
                targetLoc.latitude = order.originLat ?: 0.0
                targetLoc.longitude = order.originLng ?: 0.0
                actionType = StatusWidgetService.ACTION_ARRIVED
            }
            "IN_PROGRESS" -> {
                // Використовуємо ?: 0.0, щоб перетворити Double? в Double
                targetLoc.latitude = order.destLat ?: 0.0
                targetLoc.longitude = order.destLng ?: 0.0
                actionType = StatusWidgetService.ACTION_COMPLETE
            }
        }

        if (actionType == 0) return

        // Перевірка на "нульові" координати, щоб не спрацьовувало в океані
        if (targetLoc.latitude == 0.0 && targetLoc.longitude == 0.0) return

        val distance = currentLoc.distanceTo(targetLoc)

        if (distance < 150) {
            isReminderTriggered = true
            triggerReminder(actionType)
        }
    }

    private fun triggerReminder(actionType: Int) {
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else { true }

        if (canDrawOverlay) {
            val intent = Intent(this, StatusWidgetService::class.java)
            intent.putExtra(StatusWidgetService.EXTRA_ACTION_TYPE, actionType)
            startService(intent)
        } else {
            sendReminderNotification(actionType)
        }
    }

    private fun sendReminderNotification(actionType: Int) {
        val title = getString(R.string.notification_zone_title)
        val message = if (actionType == StatusWidgetService.ACTION_ARRIVED)
            getString(R.string.status_widget_arrived)
        else
            getString(R.string.status_widget_complete)

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "status_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Status Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_place_small)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(200, notification)
    }

    @SuppressLint("MissingPermission")
    private fun sendInitialLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                sendLocationToServer(it.latitude, it.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Зменшив до 5с для точності тригера
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToServer(realLat: Double, realLng: Double) {
        var latToSend = realLat
        var lngToSend = realLng

        if (sessionManager.isManualLocationActive()) {
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                latToSend = manualLoc.first
                lngToSend = manualLoc.second
            }
        }

        if (latToSend == 0.0 && lngToSend == 0.0) return

        val token = sessionManager.fetchAuthToken() ?: run { stopSelf(); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateLocationRequest(latToSend, lngToSend)
                ApiClient.getInstance().getApiService(applicationContext).updateLocation(request)
            } catch (e: Exception) {
                Log.e("LocationService", "Error sending location: ${e.message}")
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isServiceRunning = false
        val token = sessionManager.fetchAuthToken()
        if (token != null) {
            runBlocking(Dispatchers.IO) {
                try {
                    ApiClient.getInstance().getApiService(applicationContext).deleteLocation()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        stopSelf()
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "Геолокація водія"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Taxi Driver")
            .setContentText("Ви в системі")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ТЕПЕР МИ ПОВЕРТАЄМО BINDER
    override fun onBind(intent: Intent?): IBinder = binder
}