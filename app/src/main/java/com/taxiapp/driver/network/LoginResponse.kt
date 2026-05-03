package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("refreshToken") // <-- ДОБАВЛЕНО
    val refreshToken: String?,

    @SerializedName("userId")
    val userId: Long,

    @SerializedName("phoneNumber")
    val phoneNumber: String? = null,

    @SerializedName("fullName")
    val fullName: String,

    @SerializedName("role")
    val role: String?,

    @SerializedName("isNewUser")
    val isNew: Boolean? = false,

    @SerializedName("pendingDeletion", alternate = ["isPendingDeletion"])
    val isPendingDeletion: Boolean? = false
)