package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDriverStatusRequest
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

// Добавляем OnMapReadyCallback
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var sessionManager: SessionManager
    private lateinit var switchOnline: SwitchMaterial
    private lateinit var map: GoogleMap // Объект карты

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            enableUserLocation() // Если дали права - включаем точку
            startLocationService()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (sessionManager.fetchAuthToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Инициализация карты
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        checkPermissionsAndStart()
    }


    // --- НАСТРОЙКА КАРТЫ ---
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Включаем слой "Мое местоположение" (синяя точка), если есть права
        enableUserLocation()

        // Кнопка "Где я" (FAB)
        findViewById<View>(R.id.btn_my_location).setOnClickListener {
            centerMapOnUser()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableUserLocation() {
        if (::map.isInitialized && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true // Включает синюю точку
            map.uiSettings.isMyLocationButtonEnabled = false // Мы используем свою кнопку, родную выключаем
            centerMapOnUser() // Сразу центруем при старте
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(this)
            fusedLocation.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }
    // -----------------------

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }

    private fun setupUI() {
        findViewById<View>(R.id.btn_menu).setOnClickListener {
            Toast.makeText(this, "Меню", Toast.LENGTH_SHORT).show()
        }

        switchOnline = findViewById(R.id.switch_online)

        switchOnline.setOnClickListener {
            updateDriverStatus(switchOnline.isChecked)
        }

        findViewById<View>(R.id.btn_nav_ether).setOnClickListener {
            startActivity(Intent(this, EtherActivity::class.java))
        }

        findViewById<View>(R.id.btn_nav_orders).setOnClickListener {
            Toast.makeText(this, "Мої замовлення", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateDriverStatus(isOnline: Boolean) {
        switchOnline.isEnabled = false

        val fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        fusedLocation.lastLocation.addOnSuccessListener { location ->
            val lat = location?.latitude ?: 0.0
            val lng = location?.longitude ?: 0.0
            sendStatusRequest(isOnline, lat, lng)
        }.addOnFailureListener {
            sendStatusRequest(isOnline, 0.0, 0.0)
        }
    }

    private fun sendStatusRequest(isOnline: Boolean, lat: Double?, lng: Double?) {
        lifecycleScope.launch {
            try {
                val request = UpdateDriverStatusRequest(isOnline, lat, lng)
                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateStatus(request)

                if (response.isSuccessful) {
                    val statusText = if (isOnline) "Ви ОНЛАЙН" else "Ви ОФЛАЙН"
                    Toast.makeText(this@MainActivity, statusText, Toast.LENGTH_SHORT).show()
                    switchOnline.text = if (isOnline) "ОНЛАЙН" else "ОФЛАЙН"
                } else {
                    revertSwitchState(!isOnline)
                    Toast.makeText(this@MainActivity, "Помилка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                revertSwitchState(!isOnline)
                Toast.makeText(this@MainActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                switchOnline.isEnabled = true
            }
        }
    }

    private fun revertSwitchState(correctState: Boolean) {
        switchOnline.isChecked = correctState
        switchOnline.text = if (correctState) "ОНЛАЙН" else "ОФЛАЙН"
    }
}