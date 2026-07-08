package com.taxiapp.driver

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
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

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: android.os.Vibrator? = null
    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var btnAcceptContainer: CardView
    private lateinit var btnRejectContainer: LinearLayout // ИСПРАВЛЕНО: Новый контейнер вместо btnBackCard

    private lateinit var llPriceBackground: LinearLayout
    private lateinit var ivPaymentIcon: ImageView
    private lateinit var tvPrice: TextView
    private lateinit var tvTotalDistanceHeader: TextView

    private lateinit var tvBlockActivityBonus: TextView
    private lateinit var tvBlockPricePerKm: TextView
    private lateinit var tvPickupDistance: TextView

    private lateinit var llRouteContainer: LinearLayout
    private lateinit var tvStartSector: TextView
    private lateinit var tvEndSector: TextView

    private lateinit var tvClientRides: TextView
    private lateinit var tvClientRating: TextView
    private lateinit var tvTariff: TextView

    private lateinit var llCommentBlock: LinearLayout
    private lateinit var tvCommentText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndKeyguardOff()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_offer)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ для сохранения Edge-to-Edge фона на Android 15
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        startOfferSound()
    }
    private fun turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        with(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestDismissKeyguard(this@OrderOfferActivity, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }
        }
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tv_timer)
        btnAcceptContainer = findViewById(R.id.btn_accept_container)
        btnRejectContainer = findViewById(R.id.btn_reject_container) // ИСПРАВЛЕНО: Находим новый элемент макета

        llPriceBackground = findViewById(R.id.ll_price_background)
        ivPaymentIcon = findViewById(R.id.iv_payment_icon)
        tvPrice = findViewById(R.id.tv_price)
        tvTotalDistanceHeader = findViewById(R.id.tv_total_distance_header)

        tvBlockActivityBonus = findViewById(R.id.tv_block_activity_bonus)
        tvBlockPricePerKm = findViewById(R.id.tv_block_price_per_km)
        tvPickupDistance = findViewById(R.id.tv_pickup_distance)

        llRouteContainer = findViewById(R.id.ll_route_container)
        tvStartSector = findViewById(R.id.tv_start_sector)
        tvEndSector = findViewById(R.id.tv_end_sector)

        tvClientRides = findViewById(R.id.tv_client_rides)
        tvClientRating = findViewById(R.id.tv_client_rating)
        tvTariff = findViewById(R.id.tv_tariff)

        llCommentBlock = findViewById(R.id.ll_comment_block)
        tvCommentText = findViewById(R.id.tv_comment_text)

        btnAcceptContainer.setOnClickListener { acceptOrder() }
        btnRejectContainer.setOnClickListener { rejectOrder() } // Нажатие на крестик/штраф отклоняет заказ
    }

    private fun setupUI() {
        val order = currentOrder ?: return

        tvTariff.text = order.tariffName ?: "Стандарт"
        val fullPrice = order.getTotalFullPrice()
        tvPrice.text = "${fullPrice.toInt()} ₴"
        tvTotalDistanceHeader.text = order.getFormattedDistance().replace("грн", "₴")

        tvBlockActivityBonus.text = if (order.activityBonus >= 0) "+${order.activityBonus}" else "${order.activityBonus}"
        val distanceKm = (order.distanceMeters ?: 0) / 1000.0
        if (distanceKm < 1.0) {
            tvBlockPricePerKm.text = "— ₴/км"
        } else {
            val pricePerKm = fullPrice / distanceKm
            tvBlockPricePerKm.text = String.format(java.util.Locale.US, "%.0f ₴/км", pricePerKm)
        }

        tvStartSector.text = if (!order.fromSector.isNullOrEmpty()) order.fromSector else "Не визначено"
        tvEndSector.text = if (!order.toSector.isNullOrEmpty()) order.toSector else "Не визначено"

        val clientData = order.client
        if (clientData != null) {
            tvClientRides.text = "Поїздок: ${clientData.completedRides}"
            tvClientRating.text = clientData.rating.toString()
        } else {
            tvClientRides.text = "Поїздок: 0"
            tvClientRating.text = "5.0"
        }

        if ((order.paymentMethod ?: "CASH") == "CASH") {
            llPriceBackground.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
        } else {
            llPriceBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#54b1f0"))
            ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
        }

        if (!order.comment.isNullOrEmpty()) {
            llCommentBlock.visibility = View.VISIBLE
            tvCommentText.text = order.comment
        } else {
            llCommentBlock.visibility = View.GONE
        }

        buildRouteList()
    }

    private fun buildRouteList() {
        llRouteContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val order = currentOrder ?: return
        val allPoints = mutableListOf<RoutePoint>()

        allPoints.add(RoutePoint(order.fromAddress ?: "Адреса не вказана", PointType.START))
        order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
            allPoints.add(RoutePoint(stop.address ?: "Проміжна точка", PointType.WAYPOINT))
        }
        allPoints.add(RoutePoint(order.toAddress ?: "Кінцева точка", PointType.END))

        for (i in allPoints.indices) {
            val point = allPoints[i]
            val isFirst = (i == 0)
            val isLast = (i == allPoints.size - 1)

            val view = inflater.inflate(R.layout.item_route_point, llRouteContainer, false)
            val tvAddress = view.findViewById<TextView>(R.id.tv_point_address)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_point_icon)
            val lineTop = view.findViewById<View>(R.id.view_line_top)
            val lineBottom = view.findViewById<View>(R.id.view_line_bottom)

            tvAddress.text = point.address

            val params = ivIcon.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (point.type == PointType.START || point.type == PointType.END) {
                params.width = (18 * resources.displayMetrics.density).toInt()
                params.height = (18 * resources.displayMetrics.density).toInt()
                params.marginStart = (17 * resources.displayMetrics.density).toInt()
            } else {
                params.width = (14 * resources.displayMetrics.density).toInt()
                params.height = (14 * resources.displayMetrics.density).toInt()
                params.marginStart = (19 * resources.displayMetrics.density).toInt()
            }
            ivIcon.layoutParams = params

            when (point.type) {
                PointType.START -> ivIcon.setImageResource(R.drawable.ic_marker_from)
                PointType.END -> ivIcon.setImageResource(R.drawable.ic_marker_to)
                PointType.WAYPOINT -> ivIcon.setImageResource(R.drawable.ic_marker_waypoint)
            }

            lineTop.visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
            lineBottom.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            llRouteContainer.addView(view)
        }
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
                    origin = origin, destination = dest, apiKey = apiKey
                )

                if (response.routes.isNotEmpty()) {
                    val route = response.routes[0]
                    val leg = route.legs[0]

                    tvPickupDistance.text = "Подача: ${leg.duration.text} (${leg.distance.text})"

                    val points = PolyUtil.decode(route.overview_polyline.points)
                    val polylineOptions = PolylineOptions()
                        .addAll(points)
                        .width(8f)
                        .color(Color.GRAY)
                        .pattern(listOf(Dash(20f), Gap(10f)))

                    map.addPolyline(polylineOptions)

                    val builder = LatLngBounds.Builder()
                        .include(LatLng(driverLat, driverLng))
                        .include(LatLng(pickupLat, pickupLng))

                    currentOrder?.polyline?.let {
                        if (it.isNotEmpty()) {
                            PolyUtil.decode(it).forEach { pt -> builder.include(pt) }
                        }
                    }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
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
        tvPickupDistance.text = "Подача: ~${(distMeters / 1000).toInt()} км"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.apply {
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
            isCompassEnabled = false
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
        }

        try {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val styleRes = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) R.raw.map_style_dark else R.raw.map_style_standard
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
        } catch (e: Exception) {}

        val order = currentOrder ?: return
        val polylineString = order.polyline

        if (!polylineString.isNullOrEmpty()) {
            try {
                val path = PolyUtil.decode(polylineString)
                map.addPolyline(PolylineOptions()
                    .addAll(path)
                    .width(12f)
                    .color(ContextCompat.getColor(this, R.color.driver_neon_teal)))

                if (path.isNotEmpty()) {
                    var currentNumber = 1
                    map.addMarker(MarkerOptions().position(path.first()).icon(BitmapDescriptorFactory.fromBitmap(createCustomMarkerBitmap(currentNumber++, R.color.driver_neon_teal))).anchor(0.5f, 0.5f))
                    order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                        map.addMarker(MarkerOptions().position(LatLng(stop.lat, stop.lng)).icon(BitmapDescriptorFactory.fromBitmap(createCustomMarkerBitmap(currentNumber++, R.color.taxi_yellow))).anchor(0.5f, 0.5f))
                    }
                    map.addMarker(MarkerOptions().position(path.last()).icon(BitmapDescriptorFactory.fromBitmap(createCustomMarkerBitmap(currentNumber, R.color.driver_error))).anchor(0.5f, 0.5f))
                }
            } catch (e: Exception) {}
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

    // --- ИСПРАВЛЕНО: Перехватчик повторных интентов для защиты от дублирования экранов ---
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Обновляем интент активности новым прилетевшим payload-ом

        val newOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (newOrder != null) {
            // На ладони: сначала проверяем на дубликат. Если это он — звук НЕ трогаем, он продолжает играть!
            if (newOrder.id == currentOrder?.id) {
                android.util.Log.d("FCM_UNIT", "Защита: Сработал дубликат пуша для заказа ${newOrder.id}. Игнорируем.")
                return
            }

            // ФОЛБЕК СЦЕНАРИЙ: Если пришел действительно НОВЫЙ (перебивающий) заказ:
            stopOfferSound() // Глушим звук старого заказа
            timer?.cancel()  // Сбрасываем текущий таймер (используем 'timer' из твоего кода)

            currentOrder = newOrder
            setupUI()
            startTimer()
            startOfferSound() // Запускаем звук для нового заказа заново

            if (::map.isInitialized) {
                onMapReady(map) // Перезапускаем логику карты под новый маршрут
            }
        }
    }
    private fun startTimer() {
        timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = (millisUntilFinished / 1000).toString() // Используем tvTimer
                if (millisUntilFinished < 5000) tvTimer.setTextColor(Color.RED)
            }
            override fun onFinish() {
                stopOfferSound() // 👈 Выключаем звук, когда время вышло
                rejectOrder()
            }
        }.start()
    }

    private fun acceptOrder() {
        stopOfferSound()
        timer?.cancel()
        val orderId = currentOrder?.id ?: return
        btnAcceptContainer.isEnabled = false
        btnAcceptContainer.setCardBackgroundColor(Color.GRAY)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderOfferActivity).acceptOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@OrderOfferActivity, "Прийнято!", Toast.LENGTH_SHORT).show()

                    // Слой 1: Жестко создаем абсолютно чистый таск ОС, закладывая в фундамент главный экран
                    val mainIntent = Intent(this@OrderOfferActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(mainIntent)

                    // Слой 2: Накладываем поверх главного экрана список заказов
                    val ordersIntent = Intent(this@OrderOfferActivity, OrdersActivity::class.java)
                    startActivity(ordersIntent)

                    // Слой 3: На самый верх кладем экран выполнения, который сейчас увидит водитель
                    val progressIntent = Intent(this@OrderOfferActivity, OrderProgressActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", response.body()!!)
                    }
                    startActivity(progressIntent)

                    finish()
                } else {
                    finish()
                }
            } catch (e: Exception) { finish() }
        }
    }

    private fun rejectOrder() {
        stopOfferSound()
        timer?.cancel()
        val orderId = currentOrder?.id ?: return
        lifecycleScope.launch {
            try { ApiClient.getInstance().getApiService(this@OrderOfferActivity).rejectOffer(orderId) } catch (e: Exception) {}
            finally {
                val intent = Intent(this@OrderOfferActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }
    }

    private fun createCustomMarkerBitmap(number: Int, colorResId: Int): Bitmap {
        val view = LayoutInflater.from(this).inflate(R.layout.layout_custom_marker, null)
        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)
        tvNumber.text = number.toString()
        ivBg.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN)
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun startOfferSound() {
        if (mediaPlayer == null) {
            // Создаем плеер напрямую через конструктор, чтобы иметь возможность накрутить AudioAttributes до вызова prepare/start
            mediaPlayer = android.media.MediaPlayer().apply {
                val assetFileDescriptor = resources.openRawResourceFd(R.raw.incoming_offer)
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                assetFileDescriptor.close()

                isLooping = true

                // На ладони: принудительно пускаем звук через поток Будильника (USAGE_ALARM).
                // Это позволяет пробивать стандартный беззвучный режим на большинстве устройств!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }

                prepare()
                start()
            }
        }
        startHeavyVibration() // 👈 Запускаем агрессивную тряску
    }

    private fun startHeavyVibration() {
        if (vibrator == null) {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        // Паттерн: 0мс ждем, 600мс трясем, 200мс отдыхаем, 600мс трясем...
        val pattern = longArrayOf(0, 600, 200, 600, 200)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Индекс '1' означает циклически повторять паттерн, начиная с первого элемента (с вибрации)
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 1)
        }
    }
    private fun stopHeavyVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            vibrator = null
        }
    }

    private fun stopOfferSound() {
        stopHeavyVibration() // 👈 Мгновенно тушим вибрацию при любом исходе
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    data class RoutePoint(val address: String, val type: PointType)
    enum class PointType { START, WAYPOINT, END }

    override fun onDestroy() {
        stopOfferSound() // 👈 Гарантированно глушим плеер
        timer?.cancel()  // Используем корректное имя 'timer'
        super.onDestroy()
    }
    override fun onBackPressed() { Toast.makeText(this, "Тисніть хрестик!", Toast.LENGTH_SHORT).show() }
}