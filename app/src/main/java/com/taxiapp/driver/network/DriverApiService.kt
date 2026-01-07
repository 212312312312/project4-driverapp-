package com.taxiapp.driver.network

import retrofit2.Response
import retrofit2.http.*

interface DriverApiService {

    // --- ВХОД ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // --- ЗАКАЗЫ ---
    @GET("api/v1/driver/orders/available")
    suspend fun getAvailableOrders(): Response<List<Order>>

    @POST("api/v1/driver/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") orderId: Long): Response<Order>

    @POST("api/v1/driver/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<Void>

    // --- СТАТУС ---
    @PATCH("api/v1/driver/status")
    suspend fun updateStatus(@Body request: UpdateDriverStatusRequest): Response<Void>

    // --- ГЕОЛОКАЦИЯ ---
    @POST("api/v1/driver/location")
    suspend fun updateLocation(@Body request: UpdateLocationRequest): Response<Void>

    // --- НОВЫЙ МЕТОД ДЛЯ СВАЙПА ---
    @DELETE("api/v1/driver/location")
    suspend fun deleteLocation(): Response<Void>

    // --- УВЕДОМЛЕНИЯ О СТАТУСЕ ЗАКАЗА ---
    @POST("api/v1/driver/orders/{id}/arrive")
    suspend fun notifyArrived(@Path("id") orderId: Long): Response<Void>

    @POST("api/v1/driver/orders/{id}/start")
    suspend fun startTrip(@Path("id") orderId: Long): Response<Void>

    @GET("api/v1/driver/orders/active")
    suspend fun getActiveOrder(): Response<Order>

    @GET("api/v1/driver/orders/history")
    suspend fun getOrderHistory(): Response<List<Order>>
}