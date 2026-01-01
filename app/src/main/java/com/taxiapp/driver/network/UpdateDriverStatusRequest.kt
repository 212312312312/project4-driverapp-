package com.taxiapp.driver.network
import com.google.gson.annotations.SerializedName

data class UpdateDriverStatusRequest(
    @SerializedName("status") val status: String // "ONLINE" або "OFFLINE"
)