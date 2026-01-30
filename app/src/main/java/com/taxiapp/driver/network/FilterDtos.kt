package com.taxiapp.driver.network

import java.io.Serializable

data class DriverFilter(
    val id: Long,
    val name: String,
    val isActive: Boolean,

    // --- ДОДАНО НОВЕ ПОЛЕ ---
    val isEther: Boolean, // <--- Цього не вистачало
    val isAuto: Boolean,
    val isCycle: Boolean,

    val description: String,
    val fromType: String, // "DISTANCE" або "SECTORS"
    val fromDistance: Double?,
    val fromSectors: List<Long>,
    val toSectors: List<Long>,
    val tariffType: String, // "SIMPLE" або "COMPLEX"
    val minPrice: Double?,
    val minPricePerKm: Double?,
    val complexMinPrice: Double?,
    val complexKmInMin: Double?,
    val complexPriceKmCity: Double?,
    val complexPriceKmSuburbs: Double?,
    val paymentType: String // "CASH", "CARD", "ANY"
) : Serializable

data class CreateFilterRequest(
    val name: String,

    // --- ДОДАНО НОВЕ ПОЛЕ ---
    val isEther: Boolean = false, // <--- Додали з дефолтним значенням
    val isAuto: Boolean = false,
    val isCycle: Boolean = false,

    val fromType: String,
    val fromDistance: Double?,
    val fromSectors: List<Long>,
    val toSectors: List<Long>,
    val tariffType: String,
    val minPrice: Double?,
    val minPricePerKm: Double?,
    val complexMinPrice: Double?,
    val complexKmInMin: Double?,
    val complexPriceKmCity: Double?,
    val complexPriceKmSuburbs: Double?,
    val paymentType: String
)

// DTO для зміни режимів "на льоту"
data class UpdateFilterModeRequest(
    val isActive: Boolean,

    // --- ДОДАНО НОВЕ ПОЛЕ ---
    val isEther: Boolean, // <--- Додали сюди
    val isAuto: Boolean,
    val isCycle: Boolean
)