package com.taxiapp.driver

import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var tvAddress: TextView
    private lateinit var btnConfirm: Button
    private lateinit var sessionManager: SessionManager
    private var currentCenter: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        sessionManager = SessionManager(this)
        tvAddress = findViewById(R.id.tv_address)
        btnConfirm = findViewById(R.id.btn_confirm)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirm.setOnClickListener {
            currentCenter?.let { loc ->
                // Сохраняем "замороженную" локацию
                sessionManager.setManualLocation(loc.latitude, loc.longitude)
                finish()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Если уже есть ручная локация - показываем её, иначе пробуем показать текущую (если есть права) или Киев
        val manualLoc = sessionManager.getManualLocation()
        if (manualLoc != null) {
            val latLng = LatLng(manualLoc.first, manualLoc.second)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        } else {
            // Дефолт (Киев)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.4501, 30.5234), 12f))
        }

        // Слушатель движения камеры (когда перестали двигать карту)
        map.setOnCameraIdleListener {
            currentCenter = map.cameraPosition.target
            updateAddress(currentCenter!!)
        }
    }

    private fun updateAddress(latLng: LatLng) {
        tvAddress.text = "Визначення..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@LocationPickerActivity, Locale("uk"))
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        tvAddress.text = addresses[0].getAddressLine(0)
                    } else {
                        tvAddress.text = "Невідома адреса"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvAddress.text = "${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                }
            }
        }
    }
}