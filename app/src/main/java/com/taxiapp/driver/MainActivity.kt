package com.taxiapp.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.taxiapp.driver.utils.SessionManager

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var sessionManager: SessionManager

    // UI
    private lateinit var btnEther: MaterialButton
    private lateinit var cardStatusPill: CardView
    private lateinit var viewStatusIndicator: View
    private lateinit var tvStatus: TextView
    private lateinit var btnMenu: ImageView
    private lateinit var btnMyLocation: CardView

    private var isOnline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        // 1. Init UI
        btnEther = findViewById(R.id.btn_ether)
        cardStatusPill = findViewById(R.id.card_status_pill)
        viewStatusIndicator = findViewById(R.id.view_status_indicator)
        tvStatus = findViewById(R.id.tv_driver_status)
        btnMenu = findViewById(R.id.btn_menu) // Кнопка всередині CardView, але ID ми дали самій ImageView
        btnMyLocation = findViewById(R.id.btn_my_location)

        // 2. Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 3. Clicks

        // Кнопка ЕФІР
        btnEther.setOnClickListener {
            if (!isOnline) {
                Toast.makeText(this, "Спочатку вийдіть на лінію (статус зверху)", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, EtherActivity::class.java))
            }
        }

        // Перемикач Онлайн/Офлайн
        cardStatusPill.setOnClickListener {
            toggleOnlineStatus()
        }

        // Геолокація
        btnMyLocation.setOnClickListener {
            centerMapOnMyLocation()
        }

        // Меню (поки що вихід)
        findViewById<CardView>(R.id.btn_menu_card).setOnClickListener {
            logout()
        }

        // 4. Permission
        checkLocationPermission()

        // Встановлюємо початковий вигляд статусу
        updateStatusUI()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Тут можна задати стиль карти
        enableUserLocation()
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            centerMapOnMyLocation()
        }
    }

    private fun centerMapOnMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun toggleOnlineStatus() {
        isOnline = !isOnline
        updateStatusUI()

        if (isOnline) {
            Toast.makeText(this, "Ви вийшли на лінію", Toast.LENGTH_SHORT).show()
            // Тут буде API запит: setDriverOnline()
        } else {
            Toast.makeText(this, "Ви пішли з лінії", Toast.LENGTH_SHORT).show()
            // Тут буде API запит: setDriverOffline()
        }
    }

    private fun updateStatusUI() {
        if (isOnline) {
            tvStatus.text = "ОНЛАЙН"
            viewStatusIndicator.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            btnEther.isEnabled = true
            btnEther.alpha = 1.0f
        } else {
            tvStatus.text = "ОФЛАЙН"
            viewStatusIndicator.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_error))
            // Можна робити кнопку Ефір напівпрозорою, якщо водій офлайн
            btnEther.isEnabled = true // Або false, якщо хочете заборонити вхід в ефір без онлайну
            btnEther.alpha = 0.7f
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            if (::map.isInitialized) enableUserLocation()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації", Toast.LENGTH_LONG).show()
        }
    }
}