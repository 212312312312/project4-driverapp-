package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CarDto(
    @SerializedName("id") val id: Long,
    @SerializedName("make") val make: String,
    @SerializedName("model") val model: String,
    @SerializedName("color") val color: String,
    @SerializedName("plateNumber") val plateNumber: String,
    @SerializedName("vin") val vin: String,
    @SerializedName("year") val year: Int,
    @SerializedName("carType") val carType: String?,
    @SerializedName("photoUrl") val photoUrl: String?,

    // --- НОВЫЕ ПОЛЯ: ДОКУМЕНТЫ ---
    @SerializedName("techPassportFront") val techPassportFront: String?,
    @SerializedName("techPassportBack") val techPassportBack: String?,
    @SerializedName("insurancePhoto") val insurancePhoto: String?,

    // --- НОВЫЕ ПОЛЯ: СТОРОНЫ АВТО ---
    @SerializedName("photoFront") val photoFront: String?,
    @SerializedName("photoBack") val photoBack: String?,
    @SerializedName("photoLeft") val photoLeft: String?,
    @SerializedName("photoRight") val photoRight: String?,
    @SerializedName("photoSeatsFront") val photoSeatsFront: String?,
    @SerializedName("photoSeatsBack") val photoSeatsBack: String?,

    // --- ВАЖНО: ДОБАВЛЯЕМ СТАТУС ---
    @SerializedName("status") val status: String? = "PENDING",
    @SerializedName("rejectionReason") val rejectionReason: String? = null
) : Serializable