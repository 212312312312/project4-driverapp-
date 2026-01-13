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

    // Ссылка на объекты в других файлах
    @SerializedName("car") val car: CarDto?,
    @SerializedName("allowedTariffs") val allowedTariffs: List<CarTariffDto>?
)