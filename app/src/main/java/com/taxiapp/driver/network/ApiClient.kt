package com.taxiapp.driver.network

import android.content.Context
import com.google.gson.GsonBuilder
import com.taxiapp.driver.BuildConfig
import com.taxiapp.driver.utils.SessionManager
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
// --- ДОБАВЛЕНЫ ИМПОРТЫ РАСШИРЕНИЙ ДЛЯ OKHTTP 4+ ---
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
// -------------------------------------------------
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class ApiClient private constructor() {

    private var apiService: DriverApiService? = null
    private var googleMapsApi: GoogleMapsApi? = null

    fun getApiService(context: Context): DriverApiService {
        if (apiService == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = AuthInterceptor(context.applicationContext)

            // --- ИСПРАВЛЕНИЕ: Гарантируем, что sessionManager проинициализирован ---
            if (sessionManager == null) {
                sessionManager = SessionManager(context.applicationContext)
            }

            // --- АВТОРИЗАТОР ДЛЯ РЕФРЕША (ОБНОВЛЕННЫЙ И ИСПРАВЛЕННЫЙ) ---
            val tokenAuthenticator = object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.priorResponse?.code == 401) return null

                    val sm = sessionManager ?: return null

                    // Извлекаем токен, с которым этот конкретный запрос ходил на сервер и получил 401
                    val requestToken = response.request.header("Authorization")
                        ?.replace("Bearer", "")?.trim() ?: ""

                    // Синхронизируем потоки, чтобы только один поток выполнял запрос к серверу
                    synchronized(this) {
                        val currentToken = sm.fetchAuthToken() ?: ""

                        // DIRTY CHECK: Если токен в SessionManager уже обновился (другой поток успел сделать рефреш),
                        // то мы просто берем новый токен и повторяем текущий запрос без повторного рефреша!
                        if (currentToken.isNotEmpty() && currentToken != requestToken) {
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                        }

                        // Если мы оказались первыми — делаем реальный сетевой запрос рефреша
                        val refreshToken = sm.fetchRefreshToken() ?: return null

                        try {
                            // Используем абсолютно чистый OkHttpClient без интерцепторов заголовков!
                            val cleanClient = OkHttpClient()
                            val jsonRequestBody = com.google.gson.Gson().toJson(TokenRefreshRequestDto(refreshToken))

                            // ✅ ИСПРАВЛЕНО: MediaType и RequestBody переведены на extension-функции Kotlin
                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            val body = jsonRequestBody.toRequestBody(mediaType)

                            // ✅ ИСПРАВЛЕНО: url() изменен на свойство url без скобок
                            val originalUrl = response.request.url.toString()
                            val baseUrl = originalUrl.substring(0, originalUrl.indexOf("api/v1/"))

                            val refreshRequest = Request.Builder()
                                .url(baseUrl + "api/v1/auth/refresh")
                                .post(body)
                                .build()

                            val refreshResponse = cleanClient.newCall(refreshRequest).execute()

                            // ✅ ИСПРАВЛЕНО: body() изменен на свойство body без скобок
                            val responseBody = refreshResponse.body
                            if (refreshResponse.isSuccessful && responseBody != null) {
                                val responseBodyString = responseBody.string()
                                val loginResponse = com.google.gson.Gson().fromJson(responseBodyString, LoginResponse::class.java)

                                // Обновляем сессию свежими данными
                                sm.saveAuthToken(loginResponse.token)
                                if (!loginResponse.refreshToken.isNullOrEmpty()) {
                                    sm.saveRefreshToken(loginResponse.refreshToken)
                                }

                                // Повторяем исходный запрос уже с абсолютно новым Access-токеном
                                return response.request.newBuilder()
                                    .header("Authorization", "Bearer ${loginResponse.token}")
                                    .build()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return null
                }
            }
            // ------------------------------------

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator) // <-- ПОДКЛЮЧЕНО К КЛИЕНТУ
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val gson = GsonBuilder()
                .setLenient()
                .create()

            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build()

            apiService = retrofit.create(DriverApiService::class.java)
        }
        return apiService!!
    }

    fun getGoogleMapsApi(): GoogleMapsApi {
        if (googleMapsApi == null) {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            googleMapsApi = retrofit.create(GoogleMapsApi::class.java)
        }
        return googleMapsApi!!
    }

    fun reset() {
        apiService = null
    }

    companion object {
        @Volatile
        private var instance: ApiClient? = null

        var sessionManager: SessionManager? = null // <-- ДОБАВЛЕНО ДЛЯ ДОСТУПА ИЗ APP

        fun getInstance(): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient().also { instance = it }
            }
        }
    }
}

// --- ИНТЕРФЕЙС И МОДЕЛИ ДЛЯ КАРТ ---
interface GoogleMapsApi {
    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving",
        @Query("language") language: String = "uk"
    ): GoogleDirectionsResponse
}

data class GoogleDirectionsResponse(val routes: List<GoogleRoute>)
data class GoogleRoute(val legs: List<GoogleLeg>, val overview_polyline: GooglePolyline)
data class GooglePolyline(val points: String)
data class GoogleLeg(val distance: GoogleTextValue, val duration: GoogleTextValue)
data class GoogleTextValue(val text: String, val value: Int)