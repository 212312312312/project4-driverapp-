package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    // Им'я файлу налаштувань залишене "DriverPrefs", щоб не втратити дані користувачів
    private var prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_TOKEN = "user_token"
        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_DRIVER_NAME = "driver_name"

        // Ключі для FCM (Push-повідомлення)
        const val KEY_FCM_TOKEN = "fcm_token"

        // Логіка ручної локації
        const val KEY_MANUAL_LAT = "manual_lat"
        const val KEY_MANUAL_LNG = "manual_lng"
        const val KEY_IS_MANUAL_LOC = "is_manual_loc"

        // Стан додатку
        const val KEY_WAS_ORDER_MINIMIZED = "was_order_minimized"
    }

    // --- FCM TOKEN (ДОДАНО ДЛЯ ВИПРАВЛЕННЯ ПОМИЛКИ) ---
    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun fetchFcmToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    // --- ЛОГІКА ID ВОДІЯ (Для WebSockets) ---
    fun saveDriverId(id: Long) {
        prefs.edit().putLong(KEY_DRIVER_ID, id).apply()
    }

    fun getDriverId(): Long {
        return prefs.getLong(KEY_DRIVER_ID, -1L)
    }

    fun saveDriverName(name: String) {
        prefs.edit().putString(KEY_DRIVER_NAME, name).apply()
    }

    fun getDriverName(): String? {
        return prefs.getString(KEY_DRIVER_NAME, null)
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
    fun saveAuthToken(token: String) {
        prefs.edit().putString(USER_TOKEN, token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    fun clearSession() {
        // Зберігаємо FCM токен перед очищенням, щоб при повторному вході він був доступний
        val fcmToken = fetchFcmToken()

        prefs.edit().clear().apply()

        // Відновлюємо токен
        if (fcmToken != null) {
            saveFcmToken(fcmToken)
        }
    }

    // --- ЛОГІКА ЛОКАЦІЇ (ANTIGPS) ---
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