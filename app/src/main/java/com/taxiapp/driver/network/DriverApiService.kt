package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*
import retrofit2.Call

// --- DTO КЛАССЫ ---

data class ChartPointDto(
    @SerializedName("date") val date: String,
    @SerializedName("income") val income: Double
)

data class TokenRefreshRequestDto(val refreshToken: String)

data class ChatMessageDto(
    val id: Long?,
    val orderId: String,
    val senderRole: String, // "CLIENT" или "DRIVER"
    val senderId: Long,
    val content: String,
    val createdAt: String
)

data class SendMessageRequest(
    val content: String
)

enum class NewsTarget { CLIENT, DRIVER, ALL }

data class NewsDto(
    val id: Long,
    val title: String,
    val content: String,
    val date: String,
    val target: NewsTarget,
    val imageUrl: String?
)

data class CommissionInfoDto(
    val percent: Double,
    val description: String?
)

data class RateClientRequest(
    val orderId: String, // 👈 ФИКС: Переведено на String для поддержки UUID заказов сервера
    val score: Int,
    val comment: String?
)

data class CancellationReason(
    val id: Long,
    val reasonText: String,
    val penaltyScore: Int
)

data class DriverNotificationDto(
    val id: Long,
    val title: String,
    val body: String,
    val type: String,
    val date: String,
    val isRead: Boolean
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
    val totalHours: Double,
    @SerializedName("chartPoints") val chartPoints: List<ChartPointDto>? = null
)

data class SmsRequestDto(
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class SmsVerifyDto(
    @SerializedName("phoneNumber") val phoneNumber: String,
    val code: String
)

data class CodeVerifyRequest(
    val code: String
)

data class InitPaymentRequest(
    val amount: Double
)

data class InitPaymentResponse(
    val paymentUrl: String,
    val paymentId: Long
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

data class UpdateDisabilityRequest(
    val hasMovementIssue: Boolean,
    val hasHearingIssue: Boolean,
    val isDeaf: Boolean,
    val hasSpeechIssue: Boolean
)

data class MessageResponse(
    val message: String
)

interface DriverApiService {

    // --- ВХОД И РЕФРЕШ ---
    @POST("api/v1/auth/refresh")
    fun refreshTokenSync(@Body request: TokenRefreshRequestDto): Call<LoginResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/driver/login/sms/request")
    suspend fun requestDriverLoginSms(@Body request: SmsRequestDto): Response<MessageResponse>

    @POST("api/v1/auth/driver/login/sms/verify")
    suspend fun verifyDriverLoginSms(@Body request: SmsVerifyDto): Response<LoginResponse>

    // --- ПРОФИЛЬ И НАСТРОЙКИ ---
    @POST("api/v1/driver/profile/delete-request")
    suspend fun requestAccountDeletion(@Body emptyBody: Map<String, String> = emptyMap()): Response<MessageResponse>

    @POST("api/v1/driver/profile/restore")
    suspend fun restoreAccount(@Body emptyBody: Map<String, String> = emptyMap()): Response<Unit>

    @GET("api/v1/driver/me")
    suspend fun getDriverProfile(): Response<DriverProfileDto>

    // --- CHAT ---
    @GET("api/v1/chat/{orderId}")
    suspend fun getChatMessages(@Path("orderId") orderId: String): Response<List<ChatMessageDto>> // 👈 ФИКС: String (UUID)

    @POST("api/v1/chat/driver/{orderId}")
    suspend fun sendChatMessage(
        @Path("orderId") orderId: String, // 👈 ФИКС: String (UUID)
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>

    @POST("api/v1/driver/profile/change-phone/request-current")
    suspend fun requestCodeForCurrentPhone(): Response<MessageResponse>

    @POST("api/v1/driver/profile/change-phone/verify-current")
    suspend fun verifyCurrentPhoneCode(@Body request: CodeVerifyRequest): Response<Map<String, String>>

    @POST("api/v1/driver/profile/change-phone/request-new")
    suspend fun requestCodeForNewPhone(@Body request: SmsRequestDto): Response<MessageResponse>

    @POST("api/v1/driver/profile/change-phone/confirm-new")
    suspend fun confirmNewPhone(@Body request: ChangePhoneConfirmRequest): Response<LoginResponse>

    @PUT("api/v1/driver/profile/rnokpp")
    suspend fun updateRnokpp(@Body request: UpdateDriverRequest): Response<MessageResponse>

    @PATCH("api/v1/driver/profile/medical")
    suspend fun updateDisabilityStatus(@Body request: UpdateDisabilityRequest): Response<MessageResponse>

    // --- ЗАКАЗЫ ---
    @GET("api/v1/driver/orders/available")
    suspend fun getAvailableOrders(): Response<List<Order>>

    @GET("api/v1/driver/commission")
    suspend fun getCommission(): Response<CommissionInfoDto>

    @POST("api/v1/payments/init")
    suspend fun initPayment(@Body request: InitPaymentRequest): Response<InitPaymentResponse>

    @GET("api/v1/driver/orders/heatmap")
    suspend fun getHeatmap(): Response<List<HeatmapZoneDto>>

    @POST("api/v1/payments/check/{id}")
    suspend fun checkPaymentStatus(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/driver/sos")
    suspend fun sendSos(@Body location: UpdateLocationRequest): Response<Void>

    @POST("api/v1/driver/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") id: String): Response<Order>

    @POST("api/v1/driver/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") id: String): Response<Order>

    @POST("api/v1/driver/orders/{id}/cancel")
    suspend fun cancelOrder(
        @Path("id") id: String,
        @Query("reasonId") reasonId: Long?
    ): Response<Order>

    @POST("api/v1/driver/orders/{id}/confirm")
    suspend fun confirmOrder(@Path("id") id: String): Response<Order>

    @GET("api/v1/cancellation-reasons")
    suspend fun getCancellationReasons(): Response<List<CancellationReason>>

    @POST("api/v1/driver/orders/{id}/reject")
    suspend fun rejectOffer(@Path("id") id: String): Response<Void>

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
    suspend fun driverArrived(@Path("id") id: String): Response<Order>

    @POST("api/v1/driver/orders/{id}/start")
    suspend fun startTrip(@Path("id") id: String): Response<Order>

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

    @GET("/api/v1/driver/notifications")
    fun getNotifications(): Call<List<DriverNotificationDto>>

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

    @POST("api/v1/driver/orders/{id}/waypoint/arrive")
    suspend fun arriveAtWaypoint(@Path("id") id: String): Response<Order>

    @POST("api/v1/driver/orders/{id}/waypoint/resume")
    suspend fun resumeTrip(@Path("id") id: String): Response<Order>

    @PATCH("api/v1/driver/filters/disable-all")
    suspend fun disableAllFilters(): Response<Void>

    @DELETE("api/v1/driver/filters/{id}")
    suspend fun deleteFilter(@Path("id") id: Long): Response<Void>

    @GET("api/v1/driver/search-settings")
    suspend fun getSearchSettings(): Response<DriverSearchStateDto>

    @POST("api/v1/driver/search-settings")
    suspend fun updateSearchSettings(@Body settings: DriverSearchSettingsDto): Response<DriverSearchStateDto>

    @POST("api/v1/driver/profile/fcm-token")
    suspend fun updateFcmToken(@Body request: Map<String, String>): Response<MessageResponse>

    @GET("api/v1/driver/orders/{id}")
    suspend fun getOrderById(@Path("id") id: String): Response<Order>

    @POST("api/v1/driver/rate")
    suspend fun rateClient(@Body request: RateClientRequest): Response<Void>

    @GET("api/v1/driver/transactions")
    suspend fun getWalletTransactions(): Response<List<WalletTransactionDto>>

    @GET("api/v1/driver/transactions/pending")
    suspend fun getPendingWalletTransactions(): Response<List<WalletTransactionDto>>

    @POST("api/v1/driver/cards/init")
    suspend fun initBindCard(): Response<Map<String, String>>

    @GET("api/v1/driver/cards")
    suspend fun getCards(): Response<List<DriverCardDto>>

    @POST("api/v1/driver/cards/{cardId}/select")
    suspend fun selectMainCard(@Path("cardId") cardId: Long): Response<MessageResponse>

    @DELETE("api/v1/driver/cards/{cardId}")
    suspend fun deleteCard(@Path("cardId") cardId: Long): Response<MessageResponse>

    @GET("api/v1/driver/stats")
    suspend fun getStats(
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<DriverStatsDto>

    @GET("api/v1/driver/cars")
    suspend fun getMyCars(): Response<List<CarDto>>

    @POST("api/v1/driver/cars/{id}/select")
    suspend fun selectActiveCar(@Path("id") id: Long): Response<Void>

    @GET("api/v1/driver/news")
    suspend fun getNews(): Response<List<NewsDto>>
}