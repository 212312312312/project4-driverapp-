package com.taxiapp.driver.network

import retrofit2.Call
import retrofit2.http.*

// ТУТ МИ ВИДАЛИЛИ data class LoginRequest і LoginResponse
// Вони тепер беруться з окремих файлів

interface DriverApiService {

    // Вхід (Публичный)
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    // Прийняти замовлення
    @POST("driver/orders/{id}/accept")
    fun acceptOrder(
        @Path("id") orderId: Long
    ): Call<Void>

    // Завершити замовлення
    @POST("driver/orders/{id}/complete")
    fun completeOrder(
        @Path("id") orderId: Long
    ): Call<Void>
}