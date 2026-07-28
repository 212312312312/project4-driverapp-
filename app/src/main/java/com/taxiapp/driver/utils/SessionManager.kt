package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.taxiapp.driver.network.DriverSearchMode

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    // 🛠️ ЗАЩИТА ОТ КРАША: Инициализация зашифрованного хранилища с перехватом ошибок KeyStore
    private val securePrefs: SharedPreferences = createSecurePrefs(context)

    companion object {
        private const val TAG = "SessionManager"
        private const val SECURE_PREFS_FILE = "SecureTokens"

        const val USER_TOKEN = "user_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val KEY_DRIVER_ID = "driver_id"

        const val KEY_SEARCH_RADIUS = "search_radius"
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

        const val KEY_ETHER_SECTOR_FIRST = "ether_sector_first"
        const val KEY_ETHER_HIDE_PRICE_KM = "ether_hide_price_km"

        const val KEY_NAVIGATOR = "preferred_navigator"
        const val KEY_QUICK_ACCESS_ENABLED = "quick_access_enabled"
    }

    /**
     * Создает EncryptedSharedPreferences.
     * При рассинхронизации ключей Android KeyStore удаляет битый файл и создает новый.
     */
    private fun createSecurePrefs(context: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(context)
        } catch (e: Exception) {
            Log.e(TAG, "KeyStore / EncryptedSharedPreferences error. Resetting secure storage.", e)
            try {
                context.deleteSharedPreferences(SECURE_PREFS_FILE)
                buildEncryptedPrefs(context)
            } catch (ex: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences completely failed. Falling back to unencrypted fallback.", ex)
                context.getSharedPreferences("${SECURE_PREFS_FILE}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- JWT & AUTH TOKENS ---

    fun saveAuthToken(token: String) {
        val cleanToken = token.replace("Bearer", "").trim()
        securePrefs.edit().putString(USER_TOKEN, cleanToken).apply()
    }

    fun fetchAuthToken(): String? {
        return try {
            securePrefs.getString(USER_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching auth token", e)
            null
        }
    }

    fun saveRefreshToken(token: String) {
        securePrefs.edit().putString(REFRESH_TOKEN, token).apply()
    }

    fun fetchRefreshToken(): String? {
        return try {
            securePrefs.getString(REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching refresh token", e)
            null
        }
    }

    fun isLoggedIn(): Boolean = !fetchAuthToken().isNullOrEmpty()

    // --- DRIVER PROFILE & STATUS ---

    fun saveDriverId(id: Long) {
        prefs.edit().putLong(KEY_DRIVER_ID, id).apply()
    }

    fun getDriverId(): Long = prefs.getLong(KEY_DRIVER_ID, -1L)

    fun saveDriverName(name: String) {
        prefs.edit().putString(KEY_DRIVER_NAME, name).apply()
    }

    fun getDriverName(): String? = prefs.getString(KEY_DRIVER_NAME, null)

    fun saveDriverPhone(phone: String) {
        prefs.edit().putString(KEY_DRIVER_PHONE, phone).apply()
    }

    fun getDriverPhone(): String? = prefs.getString(KEY_DRIVER_PHONE, null)

    fun saveDriverOnlineStatus(isOnline: Boolean) {
        prefs.edit().putBoolean("KEY_DRIVER_ONLINE_STATUS", isOnline).apply()
    }

    fun isDriverOnline(): Boolean = prefs.getBoolean("KEY_DRIVER_ONLINE_STATUS", false)

    fun setPendingDeletion(isPending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING_DELETION, isPending).apply()
    }

    fun isPendingDeletion(): Boolean = prefs.getBoolean(KEY_PENDING_DELETION, false)

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun fetchFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    // --- SEARCH & MAP SETTINGS ---

    fun saveSearchRadius(radius: Double) {
        prefs.edit().putFloat(KEY_SEARCH_RADIUS, radius.toFloat()).apply()
    }

    fun getSearchRadius(): Double {
        return prefs.getFloat(KEY_SEARCH_RADIUS, 0.5f).toDouble()
    }

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

    fun saveHomeSectorIds(ids: List<Long>) {
        prefs.edit().putString("key_home_sector_ids", ids.joinToString(",")).apply()
    }

    fun getHomeSectorIds(): List<Long> {
        val str = prefs.getString("key_home_sector_ids", null) ?: return emptyList()
        if (str.isBlank()) return emptyList()
        return str.split(",").mapNotNull { it.toLongOrNull() }
    }

    // --- UI / APP SETTINGS ---

    fun setQuickAccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_ACCESS_ENABLED, enabled).apply()
    }

    fun isQuickAccessEnabled(): Boolean = prefs.getBoolean(KEY_QUICK_ACCESS_ENABLED, false)

    fun saveNavigator(nav: String) {
        prefs.edit().putString(KEY_NAVIGATOR, nav).apply()
    }

    fun getNavigator(): String = prefs.getString(KEY_NAVIGATOR, "google") ?: "google"

    fun setStatusReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATUS_REMINDER, enabled).apply()
    }

    fun isStatusReminderEnabled(): Boolean = prefs.getBoolean(KEY_STATUS_REMINDER, true)

    fun setEtherSectorFirst(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_ETHER_SECTOR_FIRST, isFirst).apply()
    }

    fun isEtherSectorFirst(): Boolean = prefs.getBoolean(KEY_ETHER_SECTOR_FIRST, false)

    fun setEtherHidePricePerKm(isHidden: Boolean) {
        prefs.edit().putBoolean(KEY_ETHER_HIDE_PRICE_KM, isHidden).apply()
    }

    fun isEtherPricePerKmHidden(): Boolean = prefs.getBoolean(KEY_ETHER_HIDE_PRICE_KM, false)

    fun saveTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(): String = prefs.getString(KEY_THEME, "DARK") ?: "DARK"

    fun getThemeMode(): Int {
        return when (getTheme()) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun saveLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "uk") ?: "uk"

    // --- ORDER & LOCATION STATE ---

    fun setOrderMinimized(minimized: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_ORDER_MINIMIZED, minimized).apply()
    }

    fun isOrderMinimized(): Boolean = prefs.getBoolean(KEY_WAS_ORDER_MINIMIZED, false)

    fun resetOrderMinimized() {
        setOrderMinimized(false)
    }

    fun setManualLocation(lat: Double, lng: Double) {
        prefs.edit().apply {
            putFloat(KEY_MANUAL_LAT, lat.toFloat())
            putFloat(KEY_MANUAL_LNG, lng.toFloat())
            putBoolean(KEY_IS_MANUAL_LOC, true)
        }.apply()
    }

    fun clearManualLocation() {
        prefs.edit().apply {
            remove(KEY_MANUAL_LAT)
            remove(KEY_MANUAL_LNG)
            putBoolean(KEY_IS_MANUAL_LOC, false)
        }.apply()
    }

    fun isManualLocationActive(): Boolean = prefs.getBoolean(KEY_IS_MANUAL_LOC, false)

    fun getManualLocation(): Pair<Double, Double>? {
        if (!isManualLocationActive()) return null
        val lat = prefs.getFloat(KEY_MANUAL_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_MANUAL_LNG, 0f).toDouble()
        return Pair(lat, lng)
    }

    // --- CLEAR SESSION ---

    fun clearSession() {
        val fcmToken = fetchFcmToken()
        val language = getLanguage()
        val theme = getTheme()
        val sectorFirst = isEtherSectorFirst()
        val hidePrice = isEtherPricePerKmHidden()

        prefs.edit().clear().apply()
        securePrefs.edit().clear().apply()

        if (fcmToken != null) saveFcmToken(fcmToken)
        saveLanguage(language)
        saveTheme(theme)
        setEtherSectorFirst(sectorFirst)
        setEtherHidePricePerKm(hidePrice)
    }
}