package com.taxiapp.driver.network

import java.io.Serializable

data class DriverFilter(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val description: String,
    val fromType: String, // "DISTANCE" або "SECTORS"
    val fromDistance: Double?,
    val fromSectors: List<Long>,
    val toSectors: List<Long>,
    val tariffType: String, // "SIMPLE" або "COMPLEX"
    val minPrice: Double?,
    val minPricePerKm: Double?,
    val complexMinPrice: Double?,
    val complexPriceKmCity: Double?,
    val paymentType: String // "CASH", "CARD", "ANY"
) : Serializable

data class CreateFilterRequest(
    val name: String,
    val fromType: String,
    val fromDistance: Double?,
    val fromSectors: List<Long>,
    val toSectors: List<Long>,
    val tariffType: String,
    val minPrice: Double?,
    val minPricePerKm: Double?,
    val complexMinPrice: Double?,
    val complexPriceKmCity: Double?,
    val paymentType: String
)