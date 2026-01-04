package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class UpdateDriverStatusRequest(
    @SerializedName("isOnline") val isOnline: Boolean,

    // ИСПРАВЛЕНО: Было "lat", стало "latitude" (как на сервере)
    @SerializedName("latitude") val lat: Double?,

    // ИСПРАВЛЕНО: Было "lng", стало "longitude" (как на сервере)
    @SerializedName("longitude") val lng: Double?
)