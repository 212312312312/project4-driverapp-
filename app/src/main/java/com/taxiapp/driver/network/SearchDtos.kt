package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

enum class DriverSearchMode { MANUAL, CHAIN, HOME }

data class DriverSearchStateDto(
    @SerializedName("mode") val mode: DriverSearchMode,
    @SerializedName("radius") val radius: Double,
    @SerializedName("homeSectorIds") val homeSectorIds: List<Long>?, // Список ID
    @SerializedName("homeSectorNames") val homeSectorNames: String?, // Рядок "Центр, Поділ..."
    @SerializedName("homeRidesLeft") val homeRidesLeft: Int
)

data class DriverSearchSettingsDto(
    @SerializedName("mode") val mode: DriverSearchMode,
    @SerializedName("radius") val radius: Double,
    @SerializedName("homeSectorIds") val homeSectorIds: List<Long>? // Список ID
)