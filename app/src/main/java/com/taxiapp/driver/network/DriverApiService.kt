package com.taxiapp.driver.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface DriverApiService {

    // --- ВХОД (ИСПРАВЛЕНО: добавлено /v1) ---
    // Было: @POST("/api/auth/login/driver") -> Ошибка 403
    @POST("/api/v1/auth/login") // Приводим в соответствие с сервером
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    // --- ЗАКАЗЫ (ИСПРАВЛЕНО: добавлено /v1) ---
    @GET("/api/v1/driver/orders/available")
    fun getAvailableOrders(): Call<List<Order>>

    @POST("/api/v1/driver/orders/{id}/accept")
    fun acceptOrder(@Path("id") orderId: Long): Call<Void>

    @POST("/api/v1/driver/orders/{id}/complete")
    fun completeOrder(@Path("id") orderId: Long): Call<Void>

    // --- СТАТУС (ИСПРАВЛЕНО: добавлено /v1) ---
    // Сервер ждет /api/v1/driver/status
    @PATCH("/api/v1/driver/status")
    fun updateStatus(@Body request: UpdateDriverStatusRequest): Call<Void>

    // --- ГЕОЛОКАЦИЯ (ИСПРАВЛЕНО: добавлено /v1) ---
    @POST("/api/v1/driver/location")
    fun updateLocation(@Body request: UpdateLocationRequest): Call<Void>


    @POST("/api/v1/driver/orders/{id}/arrive")
    fun notifyArrived(@Path("id") orderId: Long): Call<Void> // Можно Call<Order>, если сервер возвращает заказ

    @POST("/api/v1/driver/orders/{id}/start")
    fun startTrip(@Path("id") orderId: Long): Call<Void>
}