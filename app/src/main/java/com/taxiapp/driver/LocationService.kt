package com.taxiapp.driver.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
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
                    // Мы используем реальный GPS как "триггер" (таймер),
                    // но внутри метода решим, какие координаты слать
                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
        }

        // 1. Отправляем координаты немедленно при запуске
        sendInitialLocation()

        // 2. Запускаем регулярные обновления
        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun sendInitialLocation() {
        // Пытаемся получить последнюю известную локацию для мгновенного апдейта
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                Log.d("LocationService", "Мгновенная отправка при старте (Raw): ${it.latitude}")
                sendLocationToServer(it.latitude, it.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToServer(realLat: Double, realLng: Double) {
        // --- ЛОГИКА ПОДМЕНЫ ЛОКАЦИИ (АНТИ-ГЛУШИЛКА) ---
        var latToSend = realLat
        var lngToSend = realLng

        // Проверяем, включил ли водитель ручную фиксацию
        if (sessionManager.isManualLocationActive()) {
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                latToSend = manualLoc.first
                lngToSend = manualLoc.second
                Log.d("LocationService", "⚠️ ИСПОЛЬЗУЕТСЯ РУЧНАЯ ЛОКАЦИЯ: $latToSend, $lngToSend")
            }
        }
        // ------------------------------------------------

        if (latToSend == 0.0 && lngToSend == 0.0) return

        val token = sessionManager.fetchAuthToken()
        if (token == null) {
            stopSelf()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Шлем итоговые координаты (либо реальные, либо ручные)
                val request = UpdateLocationRequest(latToSend, lngToSend)
                val response = ApiClient.getInstance().getApiService(applicationContext)
                    .updateLocation(request)

                if (response.isSuccessful) {
                    Log.d("LocationService", "Координаты отправлены: $latToSend, $lngToSend")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Ошибка сети: ${e.message}")
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocationService", "Приложение закрыто свайпом. Удаляем с карты.")
        isServiceRunning = false

        val token = sessionManager.fetchAuthToken()
        if (token != null) {
            runBlocking(Dispatchers.IO) {
                try {
                    // Вызываем удаление с карты
                    ApiClient.getInstance().getApiService(applicationContext).deleteLocation()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        stopSelf()
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "Геолокація водія"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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

    override fun onBind(intent: Intent?): IBinder? = null
}