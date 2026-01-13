package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class CarTariffDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String
)