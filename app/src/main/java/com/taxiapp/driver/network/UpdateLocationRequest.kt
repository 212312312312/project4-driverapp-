package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class UpdateLocationRequest(
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lng: Double,
    @SerializedName("bearing") val bearing: Float? = 0f
)