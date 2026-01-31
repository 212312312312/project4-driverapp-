package com.taxiapp.driver.network

import android.content.Context
import com.taxiapp.driver.BuildConfig // Импорт настроек из Gradle
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class ApiClient private constructor() {

    private var apiService: DriverApiService? = null
    private var googleMapsApi: GoogleMapsApi? = null // Кешируем клиент для Google

    // Клиент для твоего сервера
    fun getApiService(context: Context): DriverApiService {
        if (apiService == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context)) // Твой интерсептор с Context
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL) // Бэйс URL из build.gradle
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            apiService = retrofit.create(DriverApiService::class.java)
        }
        return apiService!!
    }

    // Клиент для Google Maps (не требует авторизации через AuthInterceptor)
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