package com.taxiapp.driver

import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LockLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var sessionManager: SessionManager
    private lateinit var tvLockAddress: TextView
    private var currentCenterLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_location)

        sessionManager = SessionManager(this)
        tvLockAddress = findViewById(R.id.tv_lock_address)

        // Кнопка назад
        findViewById<View>(R.id.btn_back_lock).setOnClickListener {
            finish()
        }

        // Кнопка сохранения закрепленной геопозиции
        findViewById<View>(R.id.btn_save_lock_location).setOnClickListener {
            currentCenterLatLng?.let { loc ->
                sessionManager.setManualLocation(loc.latitude, loc.longitude)
                Toast.makeText(this, "Позицію успішно фіксовано!", Toast.LENGTH_SHORT).show()
                finish()
            } ?: run {
                Toast.makeText(this, "Помилка визначення координат", Toast.LENGTH_SHORT).show()
            }
        }

        // Инициализируем карту
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_lock) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Кастомный темный стиль для карт Google
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (e: Exception) { e.printStackTrace() }

        // Убираем лишние элементы управления
        with(map.uiSettings) {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
        }

        // Трекаем перемещение карты пользователем
        map.setOnCameraIdleListener {
            currentCenterLatLng = map.cameraPosition.target
            currentCenterLatLng?.let { updateAddressText(it) }
        }

        // По умолчанию фокусируем карту на последнюю локацию водителя
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val startLatLng = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else if (sessionManager.isManualLocationActive()) {
                    val manual = sessionManager.getManualLocation()
                    LatLng(manual?.first ?: 50.4501, manual?.second ?: 30.5234)
                } else {
                    LatLng(50.4501, 30.5234) // Киев по умолчанию
                }
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 17f))
            }
        } catch (e: SecurityException) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.4501, 30.5234), 17f))
        }

        val bottomPanel = findViewById<View>(R.id.bottom_panel_container)
        bottomPanel.post {
            // Как только панель просчитает свои размеры, передаем её высоту в качестве нижнего padding для карты
            val panelHeight = bottomPanel.height
            map.setPadding(0, 0, 0, panelHeight)
        }
    }

    private fun updateAddressText(latLng: LatLng) {
        tvLockAddress.text = "Визначення адреси..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@LockLocationActivity, Locale("uk"))
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        tvLockAddress.text = addresses[0].getAddressLine(0)
                    } else {
                        tvLockAddress.text = "Невідома адреса"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvLockAddress.text = String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
                }
            }
        }
    }
}