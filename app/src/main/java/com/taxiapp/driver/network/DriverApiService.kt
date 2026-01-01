package com.taxiapp.driver.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

interface DriverApiService {

    // --- ВХІД ---
    // (Перевірте AuthController, який там шлях? Зазвичай /api/auth/...)
    @POST("/api/auth/login/driver")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    // --- ЗАМОВЛЕННЯ (Виправляємо шляхи під /api/v1/...) ---

    // 1. Отримати список
    // Сервер: @RequestMapping("/api/v1/driver/orders") + @GetMapping("/available")
    @GET("/api/v1/driver/orders/available")
    fun getAvailableOrders(): Call<List<Order>>

    // 2. Прийняти
    @POST("/api/v1/driver/orders/{id}/accept")
    fun acceptOrder(@Path("id") orderId: Long): Call<Void>

    // 3. Завершити
    @POST("/api/v1/driver/orders/{id}/complete")
    fun completeOrder(@Path("id") orderId: Long): Call<Void>

    // --- СТАТУС ВОДІЯ ---
    // Сервер DriverAppController: @RequestMapping("/driver") + @PatchMapping("/status")
    // Якщо у вас в application.properties сервера є "server.servlet.context-path=/api", то шлях буде /api/driver/status
    // Якщо немає, то просто /driver/status.
    // Давайте спробуємо стандартний варіант (без /api/v1, бо контролер старий):
    @PATCH("/driver/status") // Використовуємо PATCH, бо на сервері @PatchMapping
    fun updateStatus(@Body request: UpdateDriverStatusRequest): Call<Void>

    // --- ГЕОЛОКАЦІЯ ---
    // На сервері DriverLocationController зараз тільки для АДМІНА (/admin/drivers).
    // Водію нікуди слати координати!
    // ПОТРІБНО ДОДАТИ метод updateLocation в DriverAppController або створити новий endpoint.
    // Поки що закоментуйте або залиште як заглушку:
    @POST("/api/driver/location")
    fun updateLocation(@Body request: UpdateLocationRequest): Call<Void>
}