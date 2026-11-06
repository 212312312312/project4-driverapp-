package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

// ✅ ИСПРАВЛЕНО: Добавлены OFFLINE и BUSY для 100% совпадения с серверным enum DriverSearchMode
enum class DriverSearchMode { OFFLINE, MANUAL, CHAIN, HOME, BUSY }

data class DriverSearchStateDto(
    @SerializedName("mode") val mode: DriverSearchMode?,
    @SerializedName("radius") val radius: Double,
    @SerializedName("homeSectorIds") val homeSectorIds: List<Long>?,
    @SerializedName("homeSectorNames") val homeSectorNames: String?,
    @SerializedName("homeRidesLeft") val homeRidesLeft: Int
)

data class DriverSearchSettingsDto(
    @SerializedName("mode") val mode: DriverSearchMode,
    @SerializedName("radius") val radius: Double,
    @SerializedName("homeSectorIds") val homeSectorIds: List<Long>?
)