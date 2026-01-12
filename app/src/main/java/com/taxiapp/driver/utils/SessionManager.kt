package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_TOKEN = "user_token"
        const val KEY_DRIVER_ID = "driver_id" // Новий ключ
        const val KEY_MANUAL_LAT = "manual_lat"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_MANUAL_LNG = "manual_lng"
        const val KEY_IS_MANUAL_LOC = "is_manual_loc"
        const val KEY_WAS_ORDER_MINIMIZED = "was_order_minimized"
    }

    // --- ЛОГІКА ID ВОДІЯ (Для WebSockets) ---

    fun saveDriverId(id: Long) {
        prefs.edit().putLong(KEY_DRIVER_ID, id).apply()
    }

    fun getDriverId(): Long {
        return prefs.getLong(KEY_DRIVER_ID, -1L)
    }

    // --- ЛОГІКА ЗГОРНУТОГО ЗАМОВЛЕННЯ ---

    fun setOrderMinimized(minimized: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_ORDER_MINIMIZED, minimized).apply()
    }

    fun isOrderMinimized(): Boolean {
        return prefs.getBoolean(KEY_WAS_ORDER_MINIMIZED, false)
    }

    fun resetOrderMinimized() {
        setOrderMinimized(false)
    }

    // --- АВТОРИЗАЦІЯ ---

    fun saveAuthToken(token: String) = prefs.edit().putString(USER_TOKEN, token).apply()

    fun fetchAuthToken(): String? = prefs.getString(USER_TOKEN, null)

    fun clearSession() {
        prefs.edit().clear().apply()
        val editor = prefs.edit()
        editor.remove(USER_TOKEN) // Видаляємо токен
        // editor.clear() // Можна використати це, якщо треба видалити ВСЕ (налаштування і т.д.)
        editor.apply()
    }

    // --- ЛОГІКА ЛОКАЦІЇ ---
    fun saveDriverName(name: String) {
        prefs.edit().putString(KEY_DRIVER_NAME, name).apply()
    }

    // Получение имени
    fun getDriverName(): String? {
        return prefs.getString(KEY_DRIVER_NAME, null)
    }
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
        return Pair(
            prefs.getFloat(KEY_MANUAL_LAT, 0f).toDouble(),
            prefs.getFloat(KEY_MANUAL_LNG, 0f).toDouble()
        )
    }
}