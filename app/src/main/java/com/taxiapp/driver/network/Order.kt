package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    @SerializedName("client") val client: OrderClient? = null,

    @SerializedName("arrivedAt") val arrivedAt: String? = null,
    @SerializedName("carModel") val carModel: String? = null,
    @SerializedName("carPlate") val carPlate: String? = null,
    @SerializedName("carColor") val carColor: String? = null,

    @SerializedName("isRatedByDriver") val isRatedByDriver: Boolean = false,
    @SerializedName("scheduledAt") val scheduledAt: String? = null,

    // --- НОВЕ ПОЛЕ ---
    @SerializedName("isDriverConfirmed") val isDriverConfirmed: Boolean = false
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

    // Парсинг часу подачі
    fun getScheduledDate(): Date? {
        if (scheduledAt.isNullOrEmpty()) return null
        return try {
            // Формат ISO 8601, який зазвичай шле Spring (наприклад "2023-10-25T14:30:00")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            // Важливо: Сервер шле час без Z, якщо це LocalDateTime, тому вважаємо локальним або UTC в залежності від налаштувань.
            // Для надійності припустимо, що сервер і клієнт в одній зоні або сервер шле UTC.
            // Якщо сервер шле без таймзони, Android сприйме це як локальний час.
            format.parse(scheduledAt)
        } catch (e: Exception) {
            null
        }
    }

    fun getFormattedScheduledTime(): String {
        val date = getScheduledDate() ?: return ""
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }
}

data class OrderClient(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("rating") val rating: Double = 5.0,
    @SerializedName("completedRides") val completedRides: Int = 0
) : Serializable