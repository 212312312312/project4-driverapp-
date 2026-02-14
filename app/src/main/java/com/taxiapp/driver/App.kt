package com.taxiapp.driver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.taxiapp.driver.service.FloatingWidgetService
import com.taxiapp.driver.utils.SessionManager

class App : Application(), DefaultLifecycleObserver {

    private lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super<Application>.onCreate()

        sessionManager = SessionManager(this)

        // Створюємо канали сповіщень
        createNotificationChannels()

        // Підписуємось на події життєвого циклу
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Канал для Foreground Service (Геолокація) - Низький пріоритет (без звуку)
            val serviceChannel = NotificationChannel(
                "location_channel",
                "Геолокація водія",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            // 2. Канал для Важливих сповіщень (Прибуття) - Високий пріоритет (зі звуком і спливаючим вікном)
            val alertChannel = NotificationChannel(
                "driver_alert_channel",
                "Сповіщення про замовлення",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сповіщення про наближення до клієнта або точки висадки"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Ховаємо плаваючу кнопку (швидкого доступу), коли ми в додатку
        stopService(Intent(this, FloatingWidgetService::class.java))
    }

    override fun onStop(owner: LifecycleOwner) {
        // Якщо функція швидкого доступу увімкнена -> показуємо кнопку
        if (sessionManager.isQuickAccessEnabled()) {
            startService(Intent(this, FloatingWidgetService::class.java))
        }
    }
}