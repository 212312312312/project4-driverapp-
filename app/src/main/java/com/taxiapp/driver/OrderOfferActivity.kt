package com.taxiapp.driver

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class OrderOfferActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private var timer: CountDownTimer? = null
    private lateinit var sessionManager: SessionManager

    // UI
    private lateinit var tvTimer: TextView
    private lateinit var btnAcceptContainer: CardView
    private lateinit var btnBackCard: CardView
    private lateinit var tvHeaderPrice: TextView
    private lateinit var tvHeaderDistance: TextView
    private lateinit var tvPickupDistance: TextView
    private lateinit var tvPricePerKm: TextView
    private lateinit var tvAddressFrom: TextView
    private lateinit var tvAddressTo: TextView
    private lateinit var tvSectorsFlow: TextView
    private lateinit var tvClientTrips: TextView
    private lateinit var tvClientRating: TextView
    private lateinit var tvTariffBadge: TextView
    private lateinit var tvActivityBonus: TextView // ИСПРАВЛЕНО: Объявили переменную под баллы активности

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndKeyguardOff()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_offer)
        sessionManager = SessionManager(this)

        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (currentOrder == null) {
            finish()
            return
        }

        initViews()
        setupUI()
        startTimer()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        }
        with(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestDismissKeyguard(this@OrderOfferActivity, null)
            }
        }
    }

    private fun initViews() {
        btnAcceptContainer = findViewById(R.id.btn_accept_container)
        btnBackCard = findViewById(R.id.btn_back_card)
        tvTimer = findViewById(R.id.tv_timer)
        tvHeaderPrice = findViewById(R.id.tv_header_price)
        tvHeaderDistance = findViewById(R.id.tv_header_distance)
        tvPickupDistance = findViewById(R.id.tv_pickup_distance)
        tvPricePerKm = findViewById(R.id.tv_price_per_km)
        tvAddressFrom = findViewById(R.id.tv_address_from)
        tvAddressTo = findViewById(R.id.tv_address_to)
        tvSectorsFlow = findViewById(R.id.tv_sectors_flow)
        tvClientTrips = findViewById(R.id.tv_client_trips)
        tvClientRating = findViewById(R.id.tv_client_rating)
        tvTariffBadge = findViewById(R.id.tv_tariff_badge)
        tvActivityBonus = findViewById(R.id.tv_activity_bonus) // ИСПРАВЛЕНО: Связали View по ID

        btnAcceptContainer.setOnClickListener { acceptOrder() }
        btnBackCard.setOnClickListener { rejectOrder() }
    }

    private fun setupUI() {
        val order = currentOrder ?: return
        tvHeaderPrice.text = order.getFormattedPrice()
        tvHeaderDistance.text = order.getFormattedDistance()
        tvPickupDistance.text = "Рахуємо..."
        tvPricePerKm.text = order.getPricePerKm()
        tvAddressFrom.text = order.fromAddress ?: "Точка А"
        tvAddressTo.text = order.toAddress ?: "Точка Б"
        tvSectorsFlow.text = "${order.fromSector ?: "Місто"} > ${order.toSector ?: "Місто"}"
        tvClientTrips.text = "Поїздки: ${order.client?.completedRides ?: 0}"
        tvClientRating.text = String.format("%.1f", order.client?.rating ?: 5.0)
        tvTariffBadge.text = order.tariffName ?: "Standard"

        // ИСПРАВЛЕНО: Выводим реальные баллы от бэкенда со знаком "+" (если они положительные)
        tvActivityBonus.text = if (order.activityBonus > 0) "+${order.activityBonus}" else order.activityBonus.toString()
    }

    private fun fetchRouteToPickup(driverLat: Double, driverLng: Double) {
        val pickupLat = currentOrder?.originLat ?: return
        val pickupLng = currentOrder?.originLng ?: return
        val apiKey = getString(R.string.google_maps_key)

        lifecycleScope.launch {
            try {
                val origin = "$driverLat,$driverLng"
                val dest = "$pickupLat,$pickupLng"

                val response = ApiClient.getInstance().getGoogleMapsApi().getDirections(
                    origin = origin,
                    destination = dest,
                    apiKey = apiKey
                )

                if (response.routes.isNotEmpty()) {
                    val route = response.routes[0]
                    val leg = route.legs[0]

                    tvPickupDistance.text = "${leg.duration.text} (${leg.distance.text})"

                    val points = PolyUtil.decode(route.overview_polyline.points)
                    val polylineOptions = PolylineOptions()
                        .addAll(points)
                        .width(10f)
                        .color(Color.GRAY)
                        .pattern(listOf(Dash(20f), Gap(10f)))

                    map.addPolyline(polylineOptions)

                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(driverLat, driverLng))
                        .include(LatLng(pickupLat, pickupLng))
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 150))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateDistanceUIFallback(driverLat, driverLng, pickupLat, pickupLng)
            }
        }
    }

    private fun updateDistanceUIFallback(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        val distMeters = results[0]
        tvPickupDistance.text = "~${(distMeters/1000).toInt()} км"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)) } catch (e: Exception) {}
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isZoomGesturesEnabled = false

        val order = currentOrder ?: return
        if (!order.polyline.isNullOrEmpty()) {
            val path = PolyUtil.decode(order.polyline)
            map.addPolyline(PolylineOptions().addAll(path).width(12f).color(ContextCompat.getColor(this, R.color.driver_neon_teal)))
        }

        getCurrentLocation()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        if (sessionManager.isManualLocationActive()) {
            val manual = sessionManager.getManualLocation()!!
            fetchRouteToPickup(manual.first, manual.second)
        } else {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fetchRouteToPickup(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = (millisUntilFinished / 1000).toString()
                if (millisUntilFinished < 5000) tvTimer.setTextColor(Color.RED)
            }
            override fun onFinish() { rejectOrder() }
        }.start()
    }

    private fun acceptOrder() {
        timer?.cancel()
        val orderId = currentOrder?.id ?: return
        btnAcceptContainer.isEnabled = false
        btnAcceptContainer.setCardBackgroundColor(Color.GRAY)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderOfferActivity).acceptOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@OrderOfferActivity, "Прийнято!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@OrderOfferActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", response.body()!!)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) { finish() }
        }
    }

    private fun rejectOrder() {
        timer?.cancel()
        val orderId = currentOrder?.id ?: return
        lifecycleScope.launch {
            try {
                ApiClient.getInstance().getApiService(this@OrderOfferActivity).rejectOffer(orderId)
            } catch (e: Exception) {}
            finally {
                val intent = Intent(this@OrderOfferActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); timer?.cancel() }
    override fun onBackPressed() { Toast.makeText(this, "Тисніть стрілку!", Toast.LENGTH_SHORT).show() }
}