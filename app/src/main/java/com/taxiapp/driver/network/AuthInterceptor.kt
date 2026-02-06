package com.taxiapp.driver.network

import android.content.Context
import com.taxiapp.driver.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(context: Context) : Interceptor {
    // Используем applicationContext, чтобы избежать утечек памяти Activity
    private val sessionManager = SessionManager(context.applicationContext)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // 1. Пропускаем добавление токена для входа и регистрации,
        // чтобы не отправлять старый/протухший токен.
        if (url.contains("auth/login") ||
            url.contains("auth/register") ||
            url.contains("auth/driver/login")) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()

        // 2. Достаем токен
        val token = sessionManager.fetchAuthToken()

        if (!token.isNullOrBlank()) {
            // Защита: если в SessionManager уже сохранен "Bearer ...", не добавляем второй раз.
            // Если сохранен чистый токен, добавляем префикс.
            val finalHeader = if (token.startsWith("Bearer ")) {
                token
            } else {
                "Bearer $token"
            }

            requestBuilder.header("Authorization", finalHeader)
        }

        return chain.proceed(requestBuilder.build())
    }
}