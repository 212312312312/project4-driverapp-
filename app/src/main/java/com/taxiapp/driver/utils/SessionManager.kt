package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.taxiapp.driver.network.DriverSearchMode

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_TOKEN = "user_token"
        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_PENDING_DELETION = "pending_deletion"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_DRIVER_PHONE = "driver_phone"
        const val KEY_FCM_TOKEN = "fcm_token"

        const val KEY_SEARCH_MODE = "search_mode"

        const val KEY_MANUAL_LAT = "manual_lat"
        const val KEY_MANUAL_LNG = "manual_lng"
        const val KEY_IS_MANUAL_LOC = "is_manual_loc"
        const val KEY_WAS_ORDER_MINIMIZED = "was_order_minimized"
        const val KEY_STATUS_REMINDER = "status_reminder_enabled"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_THEME = "app_theme"

        // --- НОВІ НАЛАШТУВАННЯ ЕФІРУ ---
        const val KEY_ETHER_SECTOR_FIRST = "ether_sector_first"
        const val KEY_ETHER_HIDE_PRICE_KM = "ether_hide_price_km"

        const val KEY_NAVIGATOR = "preferred_navigator" // "google" or "waze"

        const val KEY_QUICK_ACCESS_ENABLED = "quick_access_enabled"
    }

    fun setQuickAccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_ACCESS_ENABLED, enabled).apply()
    }

    fun isQuickAccessEnabled(): Boolean {
        return prefs.getBoolean(KEY_QUICK_ACCESS_ENABLED, false)
    }

    fun saveNavigator(nav: String) {
        prefs.edit().putString(KEY_NAVIGATOR, nav).apply()
    }

    fun setStatusReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATUS_REMINDER, enabled).apply()
    }

    fun isStatusReminderEnabled(): Boolean {
        // За замовчуванням увімкнено (true)
        return prefs.getBoolean(KEY_STATUS_REMINDER, true)
    }

    fun setPendingDeletion(isPending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING_DELETION, isPending).apply()
    }

    fun isPendingDeletion(): Boolean {
        return prefs.getBoolean(KEY_PENDING_DELETION, false)
    }

    fun getNavigator(): String {
        // За замовчуванням Google Maps
        return prefs.getString(KEY_NAVIGATOR, "google") ?: "google"
    }

    // --- Ether Settings Methods ---
    fun setEtherSectorFirst(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_ETHER_SECTOR_FIRST, isFirst).apply()
    }

    fun isEtherSectorFirst(): Boolean {
        // По замовчуванню false (Спочатку адреса)
        return prefs.getBoolean(KEY_ETHER_SECTOR_FIRST, false)
    }

    fun setEtherHidePricePerKm(isHidden: Boolean) {
        prefs.edit().putBoolean(KEY_ETHER_HIDE_PRICE_KM, isHidden).apply()
    }

    fun isEtherPricePerKmHidden(): Boolean {
        // По замовчуванню false (Показувати ціну)
        return prefs.getBoolean(KEY_ETHER_HIDE_PRICE_KM, false)
    }
    // -----------------------------

    // --- Theme Methods ---
    fun saveTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, "DARK") ?: "DARK"
    }

    fun getThemeMode(): Int {
        return when (getTheme()) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    // --- Language Methods ---
    fun saveLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, "uk") ?: "uk"
    }

    fun isLoggedIn(): Boolean {
        // Перевіряємо не тільки на null, але й на порожній рядок!
        return !fetchAuthToken().isNullOrEmpty()
    }

    fun saveAuthToken(token: String) {
        val cleanToken = token.replace("Bearer", "").trim()
        prefs.edit().putString(USER_TOKEN, cleanToken).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

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

    fun saveDriverPhone(phone: String) {
        prefs.edit().putString(KEY_DRIVER_PHONE, phone).apply()
    }

    fun getDriverPhone(): String? {
        return prefs.getString(KEY_DRIVER_PHONE, null)
    }

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun fetchFcmToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    // --- Search Mode Management ---
    fun saveSearchMode(mode: DriverSearchMode) {
        prefs.edit().putString(KEY_SEARCH_MODE, mode.name).apply()
    }

    fun getSearchMode(): DriverSearchMode {
        val modeStr = prefs.getString(KEY_SEARCH_MODE, DriverSearchMode.CHAIN.name)
        return try {
            DriverSearchMode.valueOf(modeStr!!)
        } catch (e: Exception) {
            DriverSearchMode.CHAIN
        }
    }

    fun setOrderMinimized(minimized: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_ORDER_MINIMIZED, minimized).apply()
    }

    fun isOrderMinimized(): Boolean {
        return prefs.getBoolean(KEY_WAS_ORDER_MINIMIZED, false)
    }

    fun resetOrderMinimized() {
        setOrderMinimized(false)
    }

    fun clearSession() {
        val fcmToken = fetchFcmToken()
        val language = getLanguage()
        val theme = getTheme()
        val sectorFirst = isEtherSectorFirst() // Зберігаємо налаштування ефіру
        val hidePrice = isEtherPricePerKmHidden()

        prefs.edit().clear().apply()

        if (fcmToken != null) saveFcmToken(fcmToken)
        saveLanguage(language)
        saveTheme(theme)
        setEtherSectorFirst(sectorFirst) // Відновлюємо
        setEtherHidePricePerKm(hidePrice)
    }

    // Manual location methods...
    fun setManualLocation(lat: Double, lng: Double) {
        val editor = prefs.edit()
        editor.putFloat(KEY_MANUAL_LAT, lat.toFloat())
        editor.putFloat(KEY_MANUAL_LNG, lng.toFloat())
        editor.putBoolean(KEY_IS_MANUAL_LOC, true)
        editor.apply()
    }

    fun clearManualLocation() {
        prefs.edit().putBoolean(KEY_IS_MANUAL_LOC, false).apply()
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