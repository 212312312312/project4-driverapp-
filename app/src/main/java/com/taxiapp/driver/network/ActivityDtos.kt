package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class DriverActivityDto(
    @SerializedName("score")
    val score: Int,

    @SerializedName("level")
    val level: String, // "GREEN", "YELLOW", "RED", "BLOCKED"

    @SerializedName("history")
    val history: List<ActivityHistoryItemDto>


)

data class ActivityHistoryItemDto(
    @SerializedName("change")
    val change: Int,

    @SerializedName("reason")
    val reason: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("orderUuid")
    val orderUuid: String? = null
)