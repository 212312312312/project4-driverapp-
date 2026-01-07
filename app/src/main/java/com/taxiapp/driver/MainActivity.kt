package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDriverStatusRequest
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var sessionManager: SessionManager
    private lateinit var switchOnline: SwitchMaterial
    private lateinit var map: GoogleMap
    private lateinit var btnLockLocation: ImageButton

    // Елементи для відображення статусу замовлення
    private lateinit var btnNavOrders: LinearLayout
    private lateinit var orderBadgeDot: View

    private var manualLocationMarker: Marker? = null

    // Реєстрація дозволів
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            updateMapUI()
            startLocationService()
            // Якщо щойно отримали дозвіл — фокусуємо камеру
            if (::map.isInitialized) centerMapOnUser()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        // Перевірка авторизації
        if (sessionManager.fetchAuthToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        checkPermissionsAndStart()

        // Авто-перехід на замовлення тільки при старті додатка
        checkActiveOrderOnStart()
    }

    override fun onResume() {
        super.onResume()
        updateLockIconState()
        updateOrdersBadge()

        if (::map.isInitialized) {
            updateMapUI()
            // При поверненні на екран (наприклад, з налаштувань) теж фокусуємося
            centerMapOnUser()
        }
    }

    /**
     * Центрує карту на водієві (GPS або ручна точка)
     */
    @SuppressLint("MissingPermission")
    private fun centerMapOnUser() {
        if (!::map.isInitialized) return

        // 1. Пріоритет ручній локації
        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let {
                val latLng = LatLng(it.first, it.second)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            }
            return
        }

        // 2. Якщо авто-режим — беремо реальний GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateMapUI()

        // ГОЛОВНЕ: Фокусуємо камеру відразу після завантаження карти
        centerMapOnUser()

        findViewById<View>(R.id.btn_my_location).setOnClickListener {
            centerMapOnUser()
        }
    }

    private fun updateOrdersBadge() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActiveOrder()
                orderBadgeDot.visibility = if (response.isSuccessful && response.body() != null) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                orderBadgeDot.visibility = View.GONE
            }
        }
    }

    private fun checkActiveOrderOnStart() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActiveOrder()
                if (response.isSuccessful && response.body() != null) {
                    if (!sessionManager.isOrderMinimized()) {
                        val activeOrder = response.body()!!
                        val intent = Intent(this@MainActivity, OrderProgressActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", activeOrder)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun setupUI() {
        switchOnline = findViewById(R.id.switch_online)
        switchOnline.setOnClickListener { updateDriverStatus(switchOnline.isChecked) }

        findViewById<View>(R.id.btn_nav_ether).setOnClickListener {
            startActivity(Intent(this, EtherActivity::class.java))
        }

        btnNavOrders = findViewById(R.id.btn_nav_orders)
        orderBadgeDot = findViewById(R.id.order_badge_dot)
        btnNavOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }

        btnLockLocation = findViewById(R.id.btn_lock_location)
        btnLockLocation.setOnClickListener { handleLockLocationClick() }

        findViewById<View>(R.id.btn_menu).setOnClickListener {
            Toast.makeText(this, "Меню в розробці", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateMapUI() {
        if (!::map.isInitialized) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (e: Exception) {}
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                val latLng = LatLng(manualLoc.first, manualLoc.second)
                if (manualLocationMarker == null) {
                    manualLocationMarker = map.addMarker(MarkerOptions()
                        .position(latLng)
                        .title("Фіксована позиція")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
                } else {
                    manualLocationMarker?.position = latLng
                }
            }
        } else {
            manualLocationMarker?.remove()
            manualLocationMarker = null
            if (hasPermission) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
            }
        }
    }

    private fun updateLockIconState() {
        if (sessionManager.isManualLocationActive()) {
            btnLockLocation.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))
        } else {
            btnLockLocation.backgroundTintList = null
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_text_primary))
        }
    }

    private fun handleLockLocationClick() {
        if (sessionManager.isManualLocationActive()) showDisableManualLocationDialog()
        else startActivity(Intent(this, LocationPickerActivity::class.java))
    }

    private fun showDisableManualLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Геолокація")
            .setMessage("Вимкнути ручне закріплення?")
            .setPositiveButton("Змінити") { _, _ -> startActivity(Intent(this, LocationPickerActivity::class.java)) }
            .setNegativeButton("Вимкнути") { _, _ ->
                sessionManager.clearManualLocation()
                updateLockIconState(); updateMapUI(); centerMapOnUser()
            }
            .setNeutralButton("Скасувати", null)
            .show()
    }

    private fun updateDriverStatus(isOnline: Boolean) {
        switchOnline.isEnabled = false
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            sendStatusRequest(isOnline, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        }.addOnFailureListener { sendStatusRequest(isOnline, 0.0, 0.0) }
    }

    private fun sendStatusRequest(isOnline: Boolean, lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity)
                    .updateStatus(UpdateDriverStatusRequest(isOnline, lat, lng))
                if (response.isSuccessful) switchOnline.text = if (isOnline) "ОНЛАЙН" else "ОФЛАЙН"
                else switchOnline.isChecked = !isOnline
            } catch (e: Exception) { switchOnline.isChecked = !isOnline
            } finally { switchOnline.isEnabled = true }
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}