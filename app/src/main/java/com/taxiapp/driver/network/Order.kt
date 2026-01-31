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

    // ИСПРАВЛЕНО: Сектора приходят строками, так и оставляем
    @SerializedName("fromSector") val fromSector: String? = null,
    @SerializedName("toSector") val toSector: String? = null,

    // --- ДОБАВЛЕНО: Объект клиента для отображения рейтинга ---
    @SerializedName("client") val client: OrderClient? = null,

    @SerializedName("arrivedAt") val arrivedAt: String? = null,
    @SerializedName("carModel") val carModel: String? = null,
    @SerializedName("carPlate") val carPlate: String? = null,
    @SerializedName("carColor") val carColor: String? = null,

    @SerializedName("isRatedByDriver") val isRatedByDriver: Boolean = false,
    @SerializedName("scheduledAt") val scheduledAt: String? = null
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

    fun isScheduled(): Boolean {
        return status == "SCHEDULED" || !scheduledAt.isNullOrEmpty()
    }
}

// Новый класс для данных клиента
data class OrderClient(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("rating") val rating: Double = 5.0,
    @SerializedName("completedRides") val completedRides: Int = 0 // Или другое поле, если сервер шлет tripsCount
) : Serializable