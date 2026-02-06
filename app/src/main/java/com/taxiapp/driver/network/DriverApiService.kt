package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// --- DTO КЛАССЫ ---

data class RateClientRequest(
    val orderId: Long,
    val score: Int,
    val comment: String?
)

data class DriverStatsDto(
    val totalIncome: Double,
    val commission: Double,
    val incomeCard: Double,
    val incomeCash: Double,
    val incomeBalance: Double,
    val ordersCount: Int,
    val totalDistanceKm: Double,
    val avgPricePerKm: Double,
    val totalHours: Double
)

// Используем @SerializedName, чтобы на сервер уходило поле "phone",
// даже если в Android оно называется phoneNumber
data class SmsRequestDto(
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class SmsVerifyDto(
    @SerializedName("phoneNumber") val phoneNumber: String,
    val code: String
)

// Специальный DTO для подтверждения только кодом (без телефона)
data class CodeVerifyRequest(
    val code: String
)

data class ChangePhoneConfirmRequest(
    val newPhone: String,
    val code: String,
    val changeToken: String
)

data class UpdateDriverRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val rnokpp: String? = null
)

data class MessageResponse(
    val message: String
)

interface DriverApiService {

    // --- ВХОД (ПАРОЛЬ) ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // --- ВХОД (SMS) ---
    @POST("api/v1/auth/driver/login/sms/request")
    suspend fun requestDriverLoginSms(@Body request: SmsRequestDto): Response<MessageResponse>

    @POST("api/v1/auth/driver/login/sms/verify")
    suspend fun verifyDriverLoginSms(@Body request: SmsVerifyDto): Response<LoginResponse>

    // --- ПРОФИЛЬ И НАСТРОЙКИ (НОВОЕ) ---

    @GET("api/v1/driver/me")
    suspend fun getDriverProfile(): Response<DriverProfileDto>

    // 1. Смена телефона: Запрос кода на текущий номер
    @POST("api/v1/driver/profile/change-phone/request-current")
    suspend fun requestCodeForCurrentPhone(): Response<MessageResponse>

    // 2. Смена телефона: Проверка кода текущего номера
    @POST("api/v1/driver/profile/change-phone/verify-current")
    suspend fun verifyCurrentPhoneCode(@Body request: CodeVerifyRequest): Response<Map<String, String>>

    // 3. Смена телефона: Запрос кода на НОВЫЙ номер
    @POST("api/v1/driver/profile/change-phone/request-new")
    suspend fun requestCodeForNewPhone(@Body request: SmsRequestDto): Response<MessageResponse>

    // 4. Смена телефона: Финальное подтверждение
    @POST("api/v1/driver/profile/change-phone/confirm-new")
    suspend fun confirmNewPhone(@Body request: ChangePhoneConfirmRequest): Response<LoginResponse>

    // Смена РНОКПП
    @PUT("api/v1/driver/profile/rnokpp")
    suspend fun updateRnokpp(@Body request: UpdateDriverRequest): Response<MessageResponse>


    // --- ЗАКАЗЫ ---
    @GET("api/v1/driver/orders/available")
    suspend fun getAvailableOrders(): Response<List<Order>>

    @GET("api/v1/driver/orders/heatmap")
    suspend fun getHeatmap(): Response<List<HeatmapZoneDto>>

    @POST("api/v1/driver/sos")
    suspend fun sendSos(@Body location: UpdateLocationRequest): Response<Void>

    @POST("api/v1/driver/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") orderId: Long): Response<Order>

    @POST("api/v1/driver/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<Void>

    @POST("api/v1/driver/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: Long): Response<Void>

    @POST("api/v1/driver/orders/{id}/reject")
    suspend fun rejectOffer(@Path("id") orderId: Long): Response<Unit>

    // --- СТАТУС ---
    @PATCH("api/v1/driver/status")
    suspend fun updateStatus(@Body request: UpdateDriverStatusRequest): Response<Void>

    // --- ГЕОЛОКАЦИЯ ---
    @POST("api/v1/driver/location")
    suspend fun updateLocation(@Body request: UpdateLocationRequest): Response<Void>

    @DELETE("api/v1/driver/location")
    suspend fun deleteLocation(): Response<Void>

    // --- УВЕДОМЛЕНИЯ ---
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

    @GET("api/v1/driver/activity")
    suspend fun getDriverActivity(): Response<DriverActivityDto>

    // --- ФИЛЬТРЫ ---
    @GET("api/v1/driver/filters")
    suspend fun getFilters(): Response<List<DriverFilter>>

    @POST("api/v1/driver/filters")
    suspend fun createFilter(@Body req: CreateFilterRequest): Response<DriverFilter>

    @PATCH("api/v1/driver/filters/{id}/toggle")
    suspend fun toggleFilter(@Path("id") id: Long): Response<Void>

    @PATCH("api/v1/driver/filters/{id}/mode")
    suspend fun updateFilterMode(
        @Path("id") id: Long,
        @Body req: UpdateFilterModeRequest
    ): Response<DriverFilter>

    @PUT("api/v1/driver/filters/{id}")
    suspend fun updateFilter(
        @Path("id") id: Long,
        @Body req: CreateFilterRequest
    ): Response<DriverFilter>

    @PATCH("api/v1/driver/filters/disable-all")
    suspend fun disableAllFilters(): Response<Void>

    @DELETE("api/v1/driver/filters/{id}")
    suspend fun deleteFilter(@Path("id") id: Long): Response<Void>

    @GET("api/v1/driver/search-settings")
    suspend fun getSearchSettings(): Response<DriverSearchStateDto>

    @POST("api/v1/driver/search-settings")
    suspend fun updateSearchSettings(@Body settings: DriverSearchSettingsDto): Response<DriverSearchStateDto>

    @POST("api/v1/auth/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenDto): Response<Void>

    @GET("api/v1/orders/{id}")
    suspend fun getOrderById(@Path("id") id: Long): Response<Order>

    @POST("api/v1/driver/rate")
    suspend fun rateClient(@Body request: RateClientRequest): Response<Void>

    @GET("api/v1/driver/stats")
    suspend fun getStats(
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<DriverStatsDto>

    @GET("api/v1/driver/cars")
    suspend fun getMyCars(): Response<List<CarDto>>

    @POST("api/v1/driver/cars/{id}/select")
    suspend fun selectActiveCar(@Path("id") id: Long): Response<Void>
}