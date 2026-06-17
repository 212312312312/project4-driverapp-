package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Order(
    @SerializedName("id") val id: String,
    @SerializedName("idLong") val idLong: Long?,
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

    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("waitingPrice") val waitingPrice: Double = 0.0,
    @SerializedName("freeWaitingMinutes") val freeWaitingMinutes: Int = 3,
    @SerializedName("pricePerWaitingMinute") val pricePerWaitingMinute: Double = 0.0,

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
    @SerializedName("isDriverConfirmed") val isDriverConfirmed: Boolean = false,

    // --- СИНХРОНИЗАЦИЯ: ПРИНИМАЕМ РЕАЛЬНЫЕ БАЛЛЫ АКТИВНОСТИ ОТ СЕРВЕРА ---
    @SerializedName("activityBonus") val activityBonus: Int = 0,
    @SerializedName("serviceCommission") val serviceCommission: Double? = null,
    @SerializedName("amountToBalance") val amountToBalance: Double? = null,
    @SerializedName("bankCommission") val bankCommission: Double? = null,
    @SerializedName("transferToCard") val transferToCard: Double? = null, // Было

    // 👈 ДОБАВИТЬ ЭТИ ДВА ПОЛЯ ДЛЯ СИНХРОНИЗАЦИИ СКИДОК:
    @SerializedName("clientPayAmount") val clientPayAmount: Double = 0.0,
    @SerializedName("companyDiscountCompensation") val companyDiscountCompensation: Double = 0.0
) : Serializable {

    fun getFormattedPrice(): String = "${price.toInt()} ₴"

    fun getPricePerKm(): String {
        if (distanceMeters == null || distanceMeters == 0) return "—"
        val km = distanceMeters / 1000.0
        return String.format("%.0f грн/км", price / km)
    }

    // ИСПРАВЛЕНО: Изменено на %.2f для вывода "2,30 км" строго по новой структуре
    fun getFormattedDistance(): String {
        val km = (distanceMeters ?: 0) / 1000.0
        return String.format("%.2f км", km)
    }

    fun isScheduled(): Boolean {
        return status == "SCHEDULED" || !scheduledAt.isNullOrEmpty()
    }

    // Парсинг часу подачі
    fun getScheduledDate(): Date? {
        if (scheduledAt.isNullOrEmpty()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
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

    // 🎁 Возвращает полную стоимость поездки для водителя с учетом доплаты за скидку
    fun getTotalFullPrice(): Double {
        return if (companyDiscountCompensation > 0.0) {
            clientPayAmount + companyDiscountCompensation
        } else {
            price
        }
    }
}



data class OrderClient(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("rating") val rating: Double = 5.0,
    @SerializedName("tripsCount") val completedRides: Int = 0,
    @SerializedName("phoneNumber") val phoneNumber: String? = null
) : Serializable