package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Order(
    @SerializedName("id") val id: Long,
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("tariffName") val tariffName: String? = null,
    @SerializedName("fromAddress") val fromAddress: String? = null,
    @SerializedName("toAddress") val toAddress: String? = null,
    @SerializedName("googleRoutePolyline") val polyline: String? = null,
    @SerializedName("stops") val stops: List<OrderStop>? = null,
    @SerializedName("distanceMeters") val distanceMeters: Int? = 0,
    @SerializedName("status") val status: String? = null,

    @SerializedName("originLat") val originLat: Double? = 0.0,
    @SerializedName("originLng") val originLng: Double? = 0.0,
    @SerializedName("destLat") val destLat: Double? = 0.0,
    @SerializedName("destLng") val destLng: Double? = 0.0,

    @SerializedName("paymentMethod") val paymentMethod: String? = "CASH",
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("services") val services: List<TaxiService>? = null,

    @SerializedName("fromSector") val fromSector: String? = null,
    @SerializedName("toSector") val toSector: String? = null,

    @SerializedName("arrivedAt") val arrivedAt: String? = null, // Приходит как ISO строка
    @SerializedName("carModel") val carModel: String? = null,
    @SerializedName("carPlate") val carPlate: String? = null,
    @SerializedName("carColor") val carColor: String? = null,

    // --- НОВОЕ ПОЛЕ ---
    @SerializedName("isRatedByDriver") val isRatedByDriver: Boolean = false
) : Serializable {

    fun getFormattedPrice(): String = "${price.toInt()} ₴"

    fun getPricePerKm(): String {
        if (distanceMeters == null || distanceMeters == 0) return "—"
        val km = distanceMeters / 1000.0
        return String.format("%.0f грн/км", price / km)
    }

    fun getFormattedDistance(): String {
        val km = (distanceMeters ?: 0) / 1000.0
        return String.format("%.1f км", km)
    }
}