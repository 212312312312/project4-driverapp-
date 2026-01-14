package com.taxiapp.driver.network

import retrofit2.Response
import retrofit2.http.*

interface DriverApiService {

    // --- ВХОД ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // --- ЗАКАЗЫ ---
    @GET("/api/v1/driver/me")
    suspend fun getDriverProfile(): Response<DriverProfileDto>

    @GET("api/v1/driver/orders/available")
    suspend fun getAvailableOrders(): Response<List<Order>>

    @GET("api/v1/driver/orders/heatmap")
    suspend fun getHeatmap(): Response<List<HeatmapZoneDto>>

    @POST("api/v1/driver/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") orderId: Long): Response<Order>

    @POST("api/v1/driver/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<Void>

    @POST("api/v1/driver/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: Long): Response<Void>

    // --- СТАТУС ---
    @PATCH("api/v1/driver/status")
    suspend fun updateStatus(@Body request: UpdateDriverStatusRequest): Response<Void>

    // --- ГЕОЛОКАЦИЯ ---
    @POST("api/v1/driver/location")
    suspend fun updateLocation(@Body request: UpdateLocationRequest): Response<Void>

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

    @GET("api/v1/driver/sectors")
    suspend fun getSectors(): Response<List<Sector>>

    // --- АКТИВНІСТЬ (НОВЕ) ---
    @GET("api/v1/driver/activity")
    suspend fun getDriverActivity(): Response<DriverActivityDto>
    // -------------------------

    @GET("api/v1/driver/filters")
    suspend fun getFilters(): Response<List<DriverFilter>>

    @POST("api/v1/driver/filters")
    suspend fun createFilter(@Body req: CreateFilterRequest): Response<DriverFilter>

    @PATCH("api/v1/driver/filters/{id}/toggle")
    suspend fun toggleFilter(@Path("id") id: Long): Response<Void>

    @PATCH("api/v1/driver/filters/disable-all")
    suspend fun disableAllFilters(): Response<Void>

    @DELETE("api/v1/driver/filters/{id}")
    suspend fun deleteFilter(@Path("id") id: Long): Response<Void>

    @GET("api/v1/driver/search-settings")
    suspend fun getSearchSettings(): Response<DriverSearchStateDto>

    @POST("api/v1/driver/search-settings")
    suspend fun updateSearchSettings(@Body settings: DriverSearchSettingsDto): Response<DriverSearchStateDto>

    @POST("api/v1/driver/orders/{id}/reject")
    suspend fun rejectOffer(@Path("id") orderId: Long): Response<Unit>

    @POST("api/v1/auth/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenDto): Response<Void>

    @GET("api/v1/orders/{id}")
    suspend fun getOrderById(@Path("id") id: Long): Response<Order>
}