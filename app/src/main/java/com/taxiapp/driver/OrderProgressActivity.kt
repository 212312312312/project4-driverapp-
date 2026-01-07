package com.taxiapp.driver

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class OrderProgressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var driverMarker: Marker? = null

    private lateinit var btnAction: MaterialButton
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDestinationLabel: TextView
    private lateinit var tvClientName: TextView
    private lateinit var tvOrderInfo: TextView

    private enum class RideState { TO_CLIENT, WAITING, TO_DESTINATION }
    private var currentState = RideState.TO_CLIENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_progress)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initViews()

        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (currentOrder != null) {
            setupOrderData()
        } else {
            loadActiveOrderFromServer()
        }

        setupLocationListener()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initViews() {
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvDestinationLabel = findViewById(R.id.tv_destination_label)
        tvClientName = findViewById(R.id.tv_client_name)
        tvOrderInfo = findViewById(R.id.tv_order_info)
        btnAction = findViewById(R.id.btn_action)

        findViewById<View>(R.id.btn_back_progress).setOnClickListener {
            val session = com.taxiapp.driver.utils.SessionManager(this)
            session.setOrderMinimized(true)
            finish()
        }

        btnAction.setOnClickListener { handleActionButton() }
    }

    // Оновлення синьої мітки (без переміщення камери, якщо ми вже сфокусувались при старті)
    private fun updateDriverMarker(location: Location) {
        if (!::map.isInitialized) return
        val currentLatLng = LatLng(location.latitude, location.longitude)

        if (driverMarker == null) {
            driverMarker = map.addMarker(MarkerOptions()
                .position(currentLatLng)
                .title("Ви")
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        } else {
            driverMarker?.position = currentLatLng
        }
    }

    private fun determineStateByStatus(status: String) {
        when (status) {
            "ACCEPTED" -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
            "DRIVER_ARRIVED" -> { currentState = RideState.WAITING; setupUiForWaiting() }
            "IN_PROGRESS" -> { currentState = RideState.TO_DESTINATION; setupUiForInTrip() }
            else -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
        }
    }

    private fun setupOrderData() {
        val order = currentOrder ?: return
        determineStateByStatus(order.status ?: "")
        tvClientName.text = "Клієнт"
        tvOrderInfo.text = "${if(order.paymentMethod == "CASH") "Готівка" else "Картка"} • ${order.price.toInt()} ₴"
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onResume() { super.onResume(); startLocationUpdates() }
    override fun onPause() { super.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback) }

    private fun setupLocationListener() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                updateDriverMarker(location)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateMapVisuals()
    }

    /**
     * ГОЛОВНА ЛОГІКА МАЛЮВАННЯ ТА НАВЕДЕННЯ КАМЕРИ
     */
    @SuppressLint("MissingPermission")
    private fun updateMapVisuals() {
        if (!::map.isInitialized || currentOrder == null) return
        map.clear()
        driverMarker = null

        val order = currentOrder!!

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val driverLoc = if (location != null) LatLng(location.latitude, location.longitude) else LatLng(50.45, 30.52)

            // Додаємо мітку водія
            updateDriverMarker(location ?: Location("").apply { latitude = 50.45; longitude = 30.52 })

            if (currentState == RideState.TO_CLIENT || currentState == RideState.WAITING) {
                // --- ЇДЕМО ДО КЛІЄНТА ---
                val originLoc = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
                map.addMarker(MarkerOptions()
                    .position(originLoc)
                    .title("Клієнт")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

                // Запускаємо побудову маршруту, яка сама наведе камеру по завершенню
                drawRoadRoute(driverLoc, originLoc, R.color.driver_neon_teal)

            } else if (currentState == RideState.TO_DESTINATION) {
                // --- В ДОРОЗІ ДО ФІНІШУ ---
                val builder = LatLngBounds.Builder()
                builder.include(driverLoc)

                val polylineString = order.polyline
                if (!polylineString.isNullOrEmpty()) {
                    val roadPoints = PolyUtil.decode(polylineString)
                    map.addPolyline(PolylineOptions()
                        .addAll(roadPoints)
                        .width(14f)
                        .color(ContextCompat.getColor(this, R.color.driver_neon_teal))
                        .jointType(JointType.ROUND)
                        .endCap(RoundCap()))

                    // Додаємо ВСІ точки дороги в огляд
                    roadPoints.forEach { builder.include(it) }
                }

                val destLoc = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)
                map.addMarker(MarkerOptions()
                    .position(destLoc)
                    .title("Фініш")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

                builder.include(destLoc)

                // Фокусуємо камеру на маршруті А->Б
                try {
                    val bounds = builder.build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
                } catch (e: Exception) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLoc, 15f))
                }
            }
        }
    }

    private fun drawRoadRoute(start: LatLng, end: LatLng, colorRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiKey = getApiKeyFromManifest()
                val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.latitude},${start.longitude}&destination=${end.latitude},${end.longitude}&mode=driving&key=$apiKey"
                val result = URL(url).readText()
                val routes = JSONObject(result).getJSONArray("routes")

                if (routes.length() > 0) {
                    val points = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                    val path = PolyUtil.decode(points)

                    withContext(Dispatchers.Main) {
                        map.addPolyline(PolylineOptions()
                            .addAll(path)
                            .width(14f)
                            .color(ContextCompat.getColor(this@OrderProgressActivity, colorRes))
                            .jointType(JointType.ROUND))

                        // НАВЕДЕННЯ КАМЕРИ НА ПОВНИЙ МАРШРУТ ДО КЛІЄНТА
                        val builder = LatLngBounds.Builder()
                        path.forEach { builder.include(it) }
                        builder.include(start)
                        builder.include(end)

                        val bounds = builder.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Якщо API не спрацював — просто наводимо на дві точки
                    val builder = LatLngBounds.Builder().include(start).include(end).build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder, 200))
                }
            }
        }
    }

    private fun handleActionButton() {
        val orderId = currentOrder?.id ?: return
        btnAction.isEnabled = false
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@OrderProgressActivity)
                when (currentState) {
                    RideState.TO_CLIENT -> if (api.notifyArrived(orderId).isSuccessful) { currentState = RideState.WAITING; setupUiForWaiting(); updateMapVisuals() }
                    RideState.WAITING -> if (api.startTrip(orderId).isSuccessful) { currentState = RideState.TO_DESTINATION; setupUiForInTrip(); updateMapVisuals() }
                    RideState.TO_DESTINATION -> if (api.completeOrder(orderId).isSuccessful) {
                        com.taxiapp.driver.utils.SessionManager(this@OrderProgressActivity).resetOrderMinimized()
                        finish()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() } finally { btnAction.isEnabled = true }
        }
    }

    private fun loadActiveOrderFromServer() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).getActiveOrder()
                if (response.isSuccessful && response.body() != null) {
                    currentOrder = response.body(); setupOrderData(); if (::map.isInitialized) updateMapVisuals()
                } else { finish() }
            } catch (e: Exception) { finish() }
        }
    }

    private fun getApiKeyFromManifest(): String {
        return try {
            val ai = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            ai.metaData.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) { "" }
    }

    private fun setupUiForWaiting() {
        tvStatusTitle.text = "Очікування"; tvDestinationLabel.text = "Клієнт виходить..."
        btnAction.text = "ПОЧАТИ ПОЇЗДКУ"; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.taxi_yellow)
    }

    private fun setupUiForInTrip() {
        tvStatusTitle.text = "В дорозі"; tvDestinationLabel.text = currentOrder?.toAddress ?: "Кінцева точка"
        btnAction.text = "ЗАВЕРШИТИ"; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_error)
    }

    private fun setupUiForToClient() {
        tvStatusTitle.text = "Їду до клієнта"; tvDestinationLabel.text = currentOrder?.fromAddress ?: "Адреса посадки"
        btnAction.text = "НА МІСЦІ"; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
    }
}