package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable // 1. Додаємо імпорт

data class Order(
    @SerializedName("id") val id: Long,
    @SerializedName("price") val price: Double,
    @SerializedName("tariffName") val tariffName: String,
    @SerializedName("fromAddress") val fromAddress: String,
    @SerializedName("stops") val stops: List<OrderStop>? = null,
    @SerializedName("googleRoutePolyline") val polyline: String? = null,
    @SerializedName("toAddress") val toAddress: String,
    @SerializedName("distanceMeters") val distanceMeters: Int? = 0,
    @SerializedName("status") val status: String? = null
) : Serializable // 2. Додаємо маркер
{
    // ... ваші методи getPricePerKm і т.д. залишаються ...
    fun getPricePerKm(): String {
        if (distanceMeters == null || distanceMeters == 0) return "—"
        val km = distanceMeters / 1000.0
        val perKm = price / km
        return String.format("%.0f грн/км", perKm)
    }

    fun getFormattedDistance(): String {
        if (distanceMeters == null) return ""
        val km = distanceMeters / 1000.0
        return String.format("%.1f км", km)
    }

    fun getFormattedPrice(): String {
        return "${price.toInt()} ₴"
    }
}