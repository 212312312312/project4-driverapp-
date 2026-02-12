package com.taxiapp.driver

import android.app.Application
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.taxiapp.driver.service.FloatingWidgetService
import com.taxiapp.driver.utils.SessionManager

class App : Application(), DefaultLifecycleObserver {

    private lateinit var sessionManager: SessionManager

    override fun onCreate() {
        // ВИПРАВЛЕННЯ: Явно вказуємо, що викликаємо onCreate з Application
        super<Application>.onCreate()

        sessionManager = SessionManager(this)

        // Підписуємось на події життєвого циклу ВСЬОГО додатка
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // Прибираємо super.onStart(owner), бо він порожній і викликає помилку
    override fun onStart(owner: LifecycleOwner) {
        // Ховаємо віджет, бо ми всередині додатка
        stopService(Intent(this, FloatingWidgetService::class.java))
    }

    // Прибираємо super.onStop(owner), бо він порожній і викликає помилку
    override fun onStop(owner: LifecycleOwner) {
        // Якщо функція увімкнена і є права -> показуємо віджет
        if (sessionManager.isQuickAccessEnabled()) {
            startService(Intent(this, FloatingWidgetService::class.java))
        }
    }
}