package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class UpdateLocationRequest(
    // ИСПРАВЛЕНО: Используем полные имена для JSON
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lng: Double
)