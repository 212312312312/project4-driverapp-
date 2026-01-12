package com.taxiapp.driver.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HexagonUtils {
    // Радіус має точно співпадати з сервером! (0.0135)
    // Можна додати мікро-напуск (0.01355), щоб перекрити шви рендерингу
    private const val R = 0.01355

    fun getHexagonPoints(center: LatLng): List<LatLng> {
        val points = mutableListOf<LatLng>()

        // Фактор розтягування по довготі
        // Це робить соту "ширшою" в градусах, але "нормальною" на екрані
        val lngFactor = 1.0 / cos(Math.toRadians(center.latitude))

        // Ми малюємо "Pointy Topped" шестикутник
        // Кути: 30°, 90°, 150°, 210°, 270°, 330°
        // 90° і 270° - це бокові вершини (стикуються по горизонталі)

        for (i in 0 until 6) {
            val angleDeg = 30.0 + (60.0 * i)
            val angleRad = Math.toRadians(angleDeg)

            // Y (Latitude): R * cos(a) - стандартна формула
            // Увага: для мапи Y - це Latitude
            val yOffset = R * sin(angleRad)

            // X (Longitude): R * sin(a) * lngFactor
            // Увага: для мапи X - це Longitude
            val xOffset = R * cos(angleRad) * lngFactor

            val lat = center.latitude + yOffset
            val lng = center.longitude + xOffset

            points.add(LatLng(lat, lng))
        }

        return points
    }
}