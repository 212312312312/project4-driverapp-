package com.taxiapp.driver.network

import android.content.Context
import com.taxiapp.driver.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(context: Context) : Interceptor {
    private val sessionManager = SessionManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // Проверка: Если мы пытаемся войти или зарегистрироваться,
        // то старый токен нам не нужен (он только мешает серверу).
        if (url.contains("auth/login") || url.contains("auth/register")) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()

        // В остальных случаях добавляем токен
        sessionManager.fetchAuthToken()?.let { token ->
            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}