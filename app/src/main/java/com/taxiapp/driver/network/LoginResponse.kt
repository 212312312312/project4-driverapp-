package com.taxiapp.driver.network

data class LoginResponse(
    val token: String,
    val fullName: String,
    val userId: Long
)