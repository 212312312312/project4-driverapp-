package com.taxiapp.driver.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.taxiapp.driver.BuildConfig
import com.taxiapp.driver.utils.SessionManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    // ✅ ИСПРАВЛЕНО: Единый легковесный клиент и Gson для рефреша токенов (без утечки пулов потоков на каждый 401)
    private val refreshHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    fun getApiService(context: Context): DriverApiService {
        if (apiService == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val authInterceptor = AuthInterceptor(context.applicationContext)

            if (sessionManager == null) {
                sessionManager = SessionManager(context.applicationContext)
            }

            val tokenAuthenticator = object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.priorResponse?.code == 401) return null

                    val sm = sessionManager ?: return null

                    val requestToken = response.request.header("Authorization")
                        ?.replace("Bearer", "")?.trim() ?: ""

                    synchronized(this) {
                        val currentToken = sm.fetchAuthToken() ?: ""

                        if (currentToken.isNotEmpty() && currentToken != requestToken) {
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                        }

                        val refreshToken = sm.fetchRefreshToken() ?: return null

                        try {
                            val jsonRequestBody = gson.toJson(TokenRefreshRequestDto(refreshToken))
                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            val body = jsonRequestBody.toRequestBody(mediaType)

                            val originalUrl = response.request.url.toString()
                            if (!originalUrl.contains("api/v1/")) {
                                return null
                            }

                            val baseUrl = originalUrl.substring(0, originalUrl.indexOf("api/v1/"))

                            val refreshRequest = Request.Builder()
                                .url(baseUrl + "api/v1/auth/refresh")
                                .post(body)
                                .build()

                            refreshHttpClient.newCall(refreshRequest).execute().use { refreshResponse ->
                                val responseBodyString = refreshResponse.body?.string()
                                if (refreshResponse.isSuccessful && !responseBodyString.isNullOrEmpty()) {
                                    val loginResponse = gson.fromJson(responseBodyString, LoginResponse::class.java)

                                    sm.saveAuthToken(loginResponse.token)
                                    if (!loginResponse.refreshToken.isNullOrEmpty()) {
                                        sm.saveRefreshToken(loginResponse.refreshToken)
                                    }

                                    return response.request.newBuilder()
                                        .header("Authorization", "Bearer ${loginResponse.token}")
                                        .build()
                                } else if (refreshResponse.code == 401 || refreshResponse.code == 403) {
                                    // Если рефреш-токен тоже протух или отозван — очищаем мертвую сессию
                                    sm.clearTokens()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return null
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

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

        var sessionManager: SessionManager? = null

        fun getInstance(): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient().also { instance = it }
            }
        }
    }
}

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