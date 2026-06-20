package com.taxiapp.driver.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
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

    var onLocationUpdated: ((Double, Double) -> Unit)? = null
    private var trackingOrder: Order? = null
    private var isReminderTriggered = false

    inner class LocalBinder : android.os.Binder() {
        fun getService(): LocationService = this@LocationService
    }
    private val binder = LocalBinder()

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
                    // 1. Отправка координат и угла поворота на сервер
                    sendLocationToServer(location.latitude, location.longitude, location.bearing)

                    // 2. Проверка дистанции до цели
                    checkDistanceToTarget(location)

                    // Передаем актуальные координаты в карту MainActivity
                    var latToSend = location.latitude
                    var lngToSend = location.longitude

                    if (sessionManager.isManualLocationActive()) {
                        sessionManager.getManualLocation()?.let {
                            latToSend = it.first
                            lngToSend = it.second
                        }
                    }

                    onLocationUpdated?.invoke(latToSend, lngToSend)
                }
            }
        }

        sendInitialLocation()
        requestLocationUpdates()
    }

    fun setTargetOrder(order: Order?) {
        if (this.trackingOrder?.id != order?.id || this.trackingOrder?.status != order?.status) {
            this.isReminderTriggered = false
        }
        this.trackingOrder = order
        Log.d("LocationService", "Tracking order: ID=${order?.id}, Status=${order?.status}")
    }

    private fun checkDistanceToTarget(currentLoc: Location) {
        val order = trackingOrder ?: return
        if (isReminderTriggered) return

        val targetLoc = Location("target")
        var shouldTrack = false

        when (order.status) {
            "ACCEPTED" -> {
                targetLoc.latitude = order.originLat ?: 0.0
                targetLoc.longitude = order.originLng ?: 0.0
                shouldTrack = true
            }
            "IN_PROGRESS" -> {
                targetLoc.latitude = order.destLat ?: 0.0
                targetLoc.longitude = order.destLng ?: 0.0
                shouldTrack = true
            }
        }

        if (!shouldTrack) return
        if (targetLoc.latitude == 0.0 && targetLoc.longitude == 0.0) return

        val distance = currentLoc.distanceTo(targetLoc)

        if (distance < 300) {
            isReminderTriggered = true
            sendProximityNotification()
        }
    }

    private fun sendProximityNotification() {
        val title = "Статус замовлення"
        val message = "Майже на місці"

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "driver_alert_channel"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_place_small)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(200, notification)
    }

    @SuppressLint("MissingPermission")
    private fun sendInitialLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { sendLocationToServer(it.latitude, it.longitude, it.bearing) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        // ЛИМИТ ВОЗВРАЩЕН: Родные 10 метров и 4 секунды снова в строю!
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToServer(realLat: Double, realLng: Double, bearing: Float) {
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

        if (sessionManager.fetchAuthToken() == null) { stopSelf(); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateLocationRequest(latToSend, lngToSend, bearing)
                val response = ApiClient.getInstance().getApiService(applicationContext).updateLocation(request)

                if (!response.isSuccessful) {
                    Log.e("LocationService", "Сервер відхилив локацію: Код=${response.code()}, Текст=${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Помилка мережі при відправці локації: ${e.message}")
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

    override fun onBind(intent: Intent?): IBinder = binder
}