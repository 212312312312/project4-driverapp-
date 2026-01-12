package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class DriverProfileDto(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("phoneNumber") val phoneNumber: String?,
    @SerializedName("photoUrl") val photoUrl: String? // Ссылка на фото
)