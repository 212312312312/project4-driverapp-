package com.taxiapp.driver.network

import android.content.Context
import com.google.gson.GsonBuilder
import com.taxiapp.driver.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class ApiClient private constructor() {

    private var apiService: DriverApiService? = null
    private var googleMapsApi: GoogleMapsApi? = null

    // Клиент для сервера (Backend)
    fun getApiService(context: Context): DriverApiService {
        if (apiService == null) {
            // Логирование запросов (полезно для отладки)
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Интерцептор для добавления Токена
            val authInterceptor = AuthInterceptor(context.applicationContext)

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // Настройка Gson с setLenient (Важно для обработки текстовых ответов от LiqPay/Сервера)
            val gson = GsonBuilder()
                .setLenient()
                .create()

            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL) // Берет URL из build.gradle (твои 192.168...)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build()

            apiService = retrofit.create(DriverApiService::class.java)
        }
        return apiService!!
    }

    // Клиент для Google Maps (Маршруты)
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

    // Сброс клиента (при выходе из аккаунта)
    fun reset() {
        apiService = null
    }

    companion object {
        @Volatile
        private var instance: ApiClient? = null

        fun getInstance(): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient().also { instance = it }
            }
        }
    }
}

// --- Интерфейсы и DTO для Google Maps ---

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