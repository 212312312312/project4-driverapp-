package com.taxiapp.driver.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient {

    // !!! ВАЖНО: Убедитесь, что IP правильный !!!
    private val BASE_URL = "http://192.168.0.104:8080/api/v1/"

    private lateinit var apiService: DriverApiService

    fun getApiService(context: Context): DriverApiService {
        if (!::apiService.isInitialized) {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okhttpClient(context)) // Подключаем клиент с перехватчиком
                .build()

            apiService = retrofit.create(DriverApiService::class.java)
        }
        return apiService
    }

    private fun okhttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context)) // Вставляем наш перехватчик
            .build()
    }

    // Singleton для удобства
    companion object {
        private var instance: ApiClient? = null
        fun getInstance(): ApiClient {
            if (instance == null) {
                instance = ApiClient()
            }
            return instance!!
        }
    }
}