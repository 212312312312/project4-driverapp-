package com.taxiapp.driver.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("DriverPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_TOKEN = "user_token"
    }

    // Сохранить токен
    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    // Получить токен
    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    // Выйти (очистить)
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}