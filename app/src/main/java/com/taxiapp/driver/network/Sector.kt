package com.taxiapp.driver.network

data class Sector(
    val id: Long,
    val name: String,
    val points: List<SectorPointDto>
)

data class SectorPointDto(
    val lat: Double,
    val lng: Double
)