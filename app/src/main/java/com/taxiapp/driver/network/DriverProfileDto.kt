package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class DriverProfileDto(
    @SerializedName("id") val id: Long,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("phoneNumber") val phoneNumber: String?,
    @SerializedName("photoUrl") val photoUrl: String?,

    // Личные данные
    @SerializedName("email") val email: String?,
    @SerializedName("rnokpp") val rnokpp: String?,
    @SerializedName("driverLicense") val driverLicense: String?,

    // Медицинские данные (Добавлено)
    @SerializedName("hasMovementIssue") val hasMovementIssue: Boolean = false,
    @SerializedName("hasHearingIssue") val hasHearingIssue: Boolean = false,
    @SerializedName("isDeaf") val isDeaf: Boolean = false,
    @SerializedName("hasSpeechIssue") val hasSpeechIssue: Boolean = false,

    // Ссылка на объекты
    @SerializedName("car") val car: CarDto?,
    @SerializedName("allowedTariffs") val allowedTariffs: List<CarTariffDto>?
)