package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class HeatmapZoneDto(
    @SerializedName("centerLat") val centerLat: Double,
    @SerializedName("centerLng") val centerLng: Double,
    @SerializedName("orderCount") val count: Int,
    @SerializedName("level") val level: Int // 1, 2 або 3
)