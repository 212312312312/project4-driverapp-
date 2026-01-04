package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    // В коде Kotlin мы используем 'phone' (так понятнее)
    // Но на сервер отправляем как "login", чтобы он понял
    @SerializedName("login")
    val phone: String,

    val password: String
)