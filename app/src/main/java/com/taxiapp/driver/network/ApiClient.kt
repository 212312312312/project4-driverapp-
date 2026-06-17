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

            // --- АВТОРИЗАТОР ДЛЯ РЕФРЕША ---
            val tokenAuthenticator = object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.priorResponse?.code == 401) return null

                    val sm = sessionManager ?: return null
                    val refreshToken = sm.fetchRefreshToken() ?: return null

                    try {
                        val refreshCall = apiService?.refreshTokenSync(TokenRefreshRequestDto(refreshToken))
                        val refreshResponse = refreshCall?.execute()

                        if (refreshResponse != null && refreshResponse.isSuccessful && refreshResponse.body() != null) {
                            val loginResponse = refreshResponse.body()!!

                            sm.saveAuthToken(loginResponse.token)
                            if (!loginResponse.refreshToken.isNullOrEmpty()) {
                                sm.saveRefreshToken(loginResponse.refreshToken)
                            }

                            return response.request.newBuilder()
                                .header("Authorization", "Bearer ${loginResponse.token}")
                                .build()
                        } else {
                            sm.clearSession()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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

// --- ИНТЕРФЕЙС И МОДЕЛИ ДЛЯ КАРТ (КОТОРЫЕ БЫЛИ ПОТЕРЯНЫ) ---
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