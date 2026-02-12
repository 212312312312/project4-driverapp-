package com.taxiapp.driver

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
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
import com.taxiapp.driver.network.RateClientRequest
import com.taxiapp.driver.service.LocationService
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

    private enum class RideState { TO_CLIENT, WAITING, TO_DESTINATION, COMPLETED }
    private var currentState = RideState.TO_CLIENT

    // --- ЗВ'ЯЗОК З СЕРВІСОМ (ДЛЯ НАГАДУВАНЬ) ---
    private var locationService: LocationService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            // Передаємо замовлення для відстеження
            locationService?.setTargetOrder(currentOrder)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
        }
    }
    // ------------------------------------------

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

    override fun onStart() {
        super.onStart()
        // Біндимо сервіс, щоб передавати йому дані про замовлення
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (locationService != null) {
            unbindService(serviceConnection)
            locationService = null
        }
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
            "COMPLETED" -> {
                currentState = RideState.COMPLETED
                if (currentOrder?.isRatedByDriver == false) {
                    showRatingDialog()
                } else {
                    finishAndReturnToMap()
                }
            }
            else -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
        }
    }

    private fun setupOrderData() {
        val order = currentOrder ?: return
        determineStateByStatus(order.status ?: "")
        tvClientName.text = "Клієнт"
        tvOrderInfo.text = "${if(order.paymentMethod == "CASH") "Готівка" else "Картка"} • ${order.price.toInt()} ₴"

        // Оновлюємо дані в сервісі локації
        locationService?.setTargetOrder(order)
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

    @SuppressLint("MissingPermission")
    private fun updateMapVisuals() {
        if (!::map.isInitialized || currentOrder == null) return
        map.clear()
        driverMarker = null

        val order = currentOrder!!

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val driverLoc = if (location != null) LatLng(location.latitude, location.longitude) else LatLng(50.45, 30.52)
            updateDriverMarker(location ?: Location("").apply { latitude = 50.45; longitude = 30.52 })

            if (currentState == RideState.TO_CLIENT || currentState == RideState.WAITING) {
                val originLoc = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
                map.addMarker(MarkerOptions()
                    .position(originLoc)
                    .title("Клієнт")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
                drawRoadRoute(driverLoc, originLoc, R.color.driver_neon_teal)

            } else if (currentState == RideState.TO_DESTINATION) {
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
                    roadPoints.forEach { builder.include(it) }
                }

                val destLoc = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)
                map.addMarker(MarkerOptions()
                    .position(destLoc)
                    .title("Фініш")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                builder.include(destLoc)

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
                    RideState.TO_CLIENT -> {
                        if (api.notifyArrived(orderId).isSuccessful) {
                            // ВИПРАВЛЕННЯ: Створюємо копію об'єкта з новим статусом
                            currentOrder = currentOrder?.copy(status = "DRIVER_ARRIVED")

                            // Оновлюємо сервіс вже НОВИМ об'єктом
                            locationService?.setTargetOrder(currentOrder)

                            currentState = RideState.WAITING
                            setupUiForWaiting()
                            updateMapVisuals()
                        }
                    }
                    RideState.WAITING -> {
                        if (api.startTrip(orderId).isSuccessful) {
                            // ВИПРАВЛЕННЯ: copy()
                            currentOrder = currentOrder?.copy(status = "IN_PROGRESS")

                            locationService?.setTargetOrder(currentOrder)

                            currentState = RideState.TO_DESTINATION
                            setupUiForInTrip()
                            updateMapVisuals()
                        }
                    }
                    RideState.TO_DESTINATION -> {
                        if (api.completeOrder(orderId).isSuccessful) {
                            // ВИПРАВЛЕННЯ: copy()
                            currentOrder = currentOrder?.copy(status = "COMPLETED")

                            locationService?.setTargetOrder(null) // Зупиняємо відстеження

                            currentState = RideState.COMPLETED
                            showRatingDialog()
                        }
                    }
                    RideState.COMPLETED -> { }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@OrderProgressActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnAction.isEnabled = true
            }
        }
    }

    private fun showRatingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_rate_client)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)

        val ratingBar = dialog.findViewById<RatingBar>(R.id.rating_bar)
        val etComment = dialog.findViewById<EditText>(R.id.et_comment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btn_submit_rating)

        btnSubmit.setOnClickListener {
            val score = ratingBar.rating.toInt()
            if (score == 0) {
                Toast.makeText(this, "Поставте оцінку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendRating(score, etComment.text.toString(), dialog)
        }

        dialog.show()
    }

    private fun sendRating(score: Int, comment: String, dialog: Dialog) {
        val orderId = currentOrder?.id ?: return
        lifecycleScope.launch {
            try {
                val req = RateClientRequest(orderId, score, comment)
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).rateClient(req)
                if (response.isSuccessful) {
                    Toast.makeText(this@OrderProgressActivity, "Оцінка збережена", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    finishAndReturnToMap()
                } else {
                    Toast.makeText(this@OrderProgressActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderProgressActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishAndReturnToMap() {
        com.taxiapp.driver.utils.SessionManager(this).resetOrderMinimized()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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