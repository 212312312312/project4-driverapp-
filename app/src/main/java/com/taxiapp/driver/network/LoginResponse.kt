package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("userId") // Сервер шлет "userId"
    val userId: Long,

    @SerializedName("phoneNumber") // Сервер шлет "phoneNumber"
    val userPhone: String, // В коде приложения оставляем имя userPhone, чтобы не ломать логику

    @SerializedName("fullName")
    val fullName: String,

    @SerializedName("role")
    val role: String?,

    @SerializedName("isNewUser") // Сервер шлет "isNewUser"
    val isNew: Boolean? = false
)