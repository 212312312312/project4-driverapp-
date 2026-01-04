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
import com.taxiapp.driver.network.UpdateDriverStatusRequest
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

    // Флаг, работает ли сервис
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        isServiceRunning = true

        // 1. Запуск Foreground (чтобы работать в фоне, когда свернул)
        startForegroundService()

        // 2. Настройка координат (как в старом коде)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isServiceRunning) return
                for (location in locationResult.locations) {
                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
        }

        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 секунд
            // Мы убрали ограничение по дистанции.
            // Теперь телефон будет пытаться слать координаты каждые 10 секунд,
            // даже если сдвиг 0 метров.
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToServer(lat: Double, lng: Double) {
        // 1. ФИЛЬТР: Не отправляем мусорные координаты
        if (lat == 0.0 && lng == 0.0) return

        val token = sessionManager.fetchAuthToken()
        if (token == null) {
            stopSelf()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateLocationRequest(lat, lng)

                val response = ApiClient.getInstance().getApiService(applicationContext)
                    .updateLocation(request)

                if (response.isSuccessful) {
                    Log.d("LocationService", "Координаты OK: $lat, $lng")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Ошибка сети: ${e.message}")
            }
        }
    }

    // --- ВОТ ЧТО МЫ ДОБАВЛЯЕМ К СТАРОМУ КОДУ ---
    // Срабатывает ТОЛЬКО когда пользователь смахивает приложение из списка задач
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocationService", "Приложение убито свайпом. Отправляем OFFLINE.")

        isServiceRunning = false

        // Блокирующий запрос, чтобы успеть отправить перед смертью процесса
        try {
            val token = sessionManager.fetchAuthToken()
            if (token != null) {
                runBlocking(Dispatchers.IO) {
                    try {
                        // Принудительно ставим ОФЛАЙН
                        val request = UpdateDriverStatusRequest(false, 0.0, 0.0)
                        ApiClient.getInstance().getApiService(applicationContext).updateStatus(request)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            .setContentText("Ви на лінії")
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}