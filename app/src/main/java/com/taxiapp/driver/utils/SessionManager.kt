package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_TOKEN = "user_token"
        const val KEY_MANUAL_LAT = "manual_lat"
        const val KEY_MANUAL_LNG = "manual_lng"
        const val KEY_IS_MANUAL_LOC = "is_manual_loc"
        // Ключ для флага свернутого заказа
        const val KEY_WAS_ORDER_MINIMIZED = "was_order_minimized"
    }

    // --- ЛОГИКА СВЕРНУТОГО ЗАКАЗА ---

    // Установить флаг (водитель сам вышел из экрана заказа)
    fun setOrderMinimized(minimized: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_ORDER_MINIMIZED, minimized).apply()
    }

    // Проверить, был ли заказ свернут
    fun isOrderMinimized(): Boolean {
        return prefs.getBoolean(KEY_WAS_ORDER_MINIMIZED, false)
    }

    // Очистить флаг (нужно вызывать, когда заказ завершен или принят новый)
    fun resetOrderMinimized() {
        setOrderMinimized(false)
    }

    // --- ОСТАЛЬНЫЕ МЕТОДЫ (БЕЗ ИЗМЕНЕНИЙ) ---
    fun saveAuthToken(token: String) = prefs.edit().putString(USER_TOKEN, token).apply()
    fun fetchAuthToken(): String? = prefs.getString(USER_TOKEN, null)
    fun clearSession() = prefs.edit().clear().apply()

    fun setManualLocation(lat: Double, lng: Double) {
        val editor = prefs.edit()
        editor.putFloat(KEY_MANUAL_LAT, lat.toFloat())
        editor.putFloat(KEY_MANUAL_LNG, lng.toFloat())
        editor.putBoolean(KEY_IS_MANUAL_LOC, true)
        editor.apply()
    }

    fun clearManualLocation() {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_MANUAL_LOC, false).apply()
    }

    fun isManualLocationActive(): Boolean = prefs.getBoolean(KEY_IS_MANUAL_LOC, false)

    fun getManualLocation(): Pair<Double, Double>? {
        if (!isManualLocationActive()) return null
        return Pair(prefs.getFloat(KEY_MANUAL_LAT, 0f).toDouble(), prefs.getFloat(KEY_MANUAL_LNG, 0f).toDouble())
    }
}