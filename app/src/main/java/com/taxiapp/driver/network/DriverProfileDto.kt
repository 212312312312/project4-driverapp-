package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DriverProfileDto(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("phoneNumber") val phoneNumber: String?,
    @SerializedName("photoUrl") val photoUrl: String?,

    @SerializedName("email") val email: String?,
    @SerializedName("rnokpp") val rnokpp: String?,
    @SerializedName("driverLicense") val driverLicense: String?,

    @SerializedName("hasMovementIssue") val hasMovementIssue: Boolean = false,
    @SerializedName("hasHearingIssue") val hasHearingIssue: Boolean = false,
    @SerializedName("isDeaf") val isDeaf: Boolean = false,
    @SerializedName("hasSpeechIssue") val hasSpeechIssue: Boolean = false,

    @SerializedName("car") val car: CarDto?,
    @SerializedName("allowedTariffs") val allowedTariffs: List<CarTariffDto>?,
    @SerializedName("selectedTariffIds") val selectedTariffIds: List<Long>?,

    @SerializedName("rating") val rating: Double = 5.0,

    // --- НОВОЕ ПОЛЕ ---
    @SerializedName("balance") val balance: Double = 0.0,
    @SerializedName("payoutBalance") val payoutBalance: Double = 0.0 // ДОБАВЛЕНО: Баланс выплат за безнал и чаевые
) : Serializable