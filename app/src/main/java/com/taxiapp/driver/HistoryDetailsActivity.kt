package com.taxiapp.driver

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.databinding.ActivityHistoryDetailsBinding
import com.taxiapp.driver.network.Order

class HistoryDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHistoryDetailsBinding
    private lateinit var map: GoogleMap
    private lateinit var order: Order

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Binding
        binding = ActivityHistoryDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем объект Order (безопасно)
        val receivedOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (receivedOrder == null) {
            Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        order = receivedOrder

        binding.btnBack.setOnClickListener { finish() }

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_detail) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupUI() {
        binding.tvPriceDetail.text = order.getFormattedPrice()
        binding.tvDistanceDetail.text = order.getFormattedDistance()
        binding.tvFromDetail.text = order.fromAddress ?: "---"
        binding.tvToDetail.text = order.toAddress ?: "---"

        binding.tvTariffName.text = order.tariffName ?: "Standard"
        binding.tvCarModel.text = order.carModel ?: "Авто"
        binding.tvCarPlate.text = order.carPlate ?: "---"
        binding.tvCarClass.text = order.tariffName ?: "Class"

        // --- ДИНАМИЧЕСКОЕ И ЧЕСТНОЕ ОТОБРАЖЕНИЕ БАЛЛОВ АКТИВНОСТИ ---
        val activityBonus = order.activityBonus
        when {
            activityBonus > 0 -> {
                binding.tvActivityScore.text = "+$activityBonus"
                binding.tvActivityScore.setTextColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
            }
            activityBonus < 0 -> {
                // Минус уже автоматически содержится в значении отрицательного Int
                binding.tvActivityScore.text = activityBonus.toString()
                binding.tvActivityScore.setTextColor(ContextCompat.getColor(this, R.color.driver_error))
            }
            else -> {
                binding.tvActivityScore.text = "0"
                binding.tvActivityScore.setTextColor(ContextCompat.getColor(this, R.color.driver_text_secondary))
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // 1. Блокируем карту (нельзя двигать)
        map.uiSettings.setAllGesturesEnabled(false)
        map.uiSettings.isMapToolbarEnabled = false

        // 2. Устанавливаем темный стиль
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
            )
            if (!success) {
                // Лог ошибки, если стиль не применился
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val polylineString = order.polyline

        if (!polylineString.isNullOrEmpty()) {
            try {
                // Декодируем строку маршрута
                val path: List<LatLng> = PolyUtil.decode(polylineString)

                // Рисуем линию
                map.addPolyline(PolylineOptions()
                    .addAll(path)
                    .width(12f)
                    .color(ContextCompat.getColor(this, R.color.driver_neon_teal))
                    .geodesic(true))

                if (path.isNotEmpty()) {
                    var currentNumber = 1

                    // Точка А (Start)
                    val startBitmap = createCustomMarkerBitmap(currentNumber++, R.color.driver_neon_teal)
                    map.addMarker(MarkerOptions()
                        .position(path.first())
                        .icon(BitmapDescriptorFactory.fromBitmap(startBitmap))
                        .anchor(0.5f, 0.5f)
                        .zIndex(2f))

                    // Промежуточные остановки
                    order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                        val stopBitmap = createCustomMarkerBitmap(currentNumber++, R.color.taxi_yellow)
                        map.addMarker(MarkerOptions()
                            .position(LatLng(stop.lat, stop.lng))
                            .icon(BitmapDescriptorFactory.fromBitmap(stopBitmap))
                            .anchor(0.5f, 0.5f)
                            .zIndex(2f))
                    }

                    // Точка Б (End)
                    val endBitmap = createCustomMarkerBitmap(currentNumber, R.color.driver_error)
                    map.addMarker(MarkerOptions()
                        .position(path.last())
                        .icon(BitmapDescriptorFactory.fromBitmap(endBitmap))
                        .anchor(0.5f, 0.5f)
                        .zIndex(2f))

                    // Фокусируем камеру на всем маршруте
                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }

                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
                    } catch (e: Exception) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(path.first(), 13f))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                drawFallbackMarkers()
            }
        } else {
            drawFallbackMarkers()
        }
    }

    private fun drawFallbackMarkers() {
        val origin = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
        val dest = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)

        if (origin.latitude == 0.0 && dest.latitude == 0.0) return

        map.addMarker(MarkerOptions().position(origin).title("A"))
        map.addMarker(MarkerOptions().position(dest).title("B"))

        val builder = LatLngBounds.Builder()
        builder.include(origin)
        builder.include(dest)
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
        } catch (e: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13f))
        }
    }

    // Функция создания красивого маркера с цифрой
    private fun createCustomMarkerBitmap(number: Int, colorResId: Int): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_custom_marker, null)
        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)

        tvNumber.text = number.toString()
        ivBg.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN)

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}