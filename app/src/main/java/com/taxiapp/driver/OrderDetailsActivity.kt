package com.taxiapp.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.databinding.ActivityOrderDetailsBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

class OrderDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityOrderDetailsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private var swipeTriggered = false
    private var acceptVisualTimer: CountDownTimer? = null
    private var timerRingDrawable: CircularTimerDrawable? = null

    companion object {
        private const val ACCEPT_COUNTDOWN_DURATION_MS = 10_000L
        private const val ACCEPT_COUNTDOWN_TICK_MS = 40L
    }

    private val orderClosedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val closedId = intent?.getStringExtra("EXTRA_ORDER_ID")
            if (closedId == null || closedId == currentOrder?.id) {
                Toast.makeText(this@OrderDetailsActivity, "Замовлення вже неактуальне або скасовано", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (currentOrder == null) {
            Toast.makeText(this, "Помилка завантаження замовлення", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnBack.setOnClickListener { finish() }
        setupSwipeGesture()

        val shouldHideAccept = intent.getBooleanExtra("EXTRA_HIDE_ACCEPT_BUTTON", false)
        if (!shouldHideAccept) {
            startVisualAcceptCountdown()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction("com.taxiapp.driver.ACTION_CLOSE_ORDER_OFFER")
            addAction("com.taxiapp.driver.ORDER_CANCELLED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(orderClosedReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(orderClosedReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(orderClosedReceiver)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        acceptVisualTimer?.cancel()
        super.onDestroy()
    }

    private fun setupUI() {
        val order = currentOrder ?: return
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val errorRed = ContextCompat.getColor(this, R.color.driver_error_red)
        val darkButtonText = Color.parseColor("#1A1C1E")

        // 1. Іконки в єдиному стилі з Ефіром та Архівом
        binding.ivPickupPinIcon.setImageDrawable(DetailsIconDrawable(DetailsIconDrawable.Type.LOCATION_PIN, primaryColor))
        binding.ivSurgeIcon.setImageDrawable(DetailsIconDrawable(DetailsIconDrawable.Type.LIGHTNING, Color.parseColor("#29B6F6")))
        binding.ivBlockActivityIcon.setImageDrawable(
            OrderAdapter.UnifiedCardIconDrawable(OrderAdapter.UnifiedCardIconDrawable.Type.ACTIVITY_TREND, primaryColor)
        )
        binding.ivClientAvatar.setImageDrawable(DetailsIconDrawable(DetailsIconDrawable.Type.PERSON_OUTLINE, primaryColor))
        binding.ivTariffCarIcon.setImageDrawable(
            OrderAdapter.UnifiedCardIconDrawable(OrderAdapter.UnifiedCardIconDrawable.Type.CAR_FRONT, primaryColor)
        )
        binding.ivPickupEtaClock.setImageDrawable(DetailsIconDrawable(DetailsIconDrawable.Type.CLOCK, primaryColor))

        // Круговий прогрес-індикатор навколо цифри таймера на кнопці "Прийняти"
        timerRingDrawable = CircularTimerDrawable(
            ringColor = darkButtonText,
            trackColor = Color.argb(48, 26, 28, 30),
            strokeWidthPx = 2.6f * density
        )
        binding.ivAcceptTimerRing.setImageDrawable(timerRingDrawable)

        // 2. Шапка: плашка ціни та плашка дистанції зі скругленими кутами
        val fullPrice = order.getTotalFullPrice()
        binding.tvPrice.text = "${fullPrice.toInt()} ₴"
        binding.tvTotalDistanceHeader.text = order.getFormattedDistance().replace(',', '.')

        binding.llDistanceHeaderBackground.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(Color.parseColor("#232428"))
        }

        // 3. Панель статистики: дистанція подачі, коефіцієнт, бали активності та ціна за км
        val bonus = order.activityBonus
        binding.tvBlockActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"
        binding.tvBlockActivityBonus.setTextColor(if (bonus >= 0) neonTeal else errorRed)

        val distanceKm = (order.distanceMeters ?: 0) / 1000.0
        if (distanceKm > 0.0) {
            val pricePerKm = fullPrice / distanceKm
            binding.tvBlockPricePerKm.text = String.format(Locale.US, "%.2f ₴/км", pricePerKm)
        } else {
            binding.tvBlockPricePerKm.text = "— ₴/км"
        }

        // Розрахунок коефіцієнта (⚡ 1.12)
        val surgeMultiplier = when {
            order.addedValue > 0.0 && order.price > 0.0 -> 1.0 + (order.addedValue / order.price)
            order.companyDiscountCompensation > 0.0 && fullPrice > 0.0 -> 1.0 + (order.companyDiscountCompensation / (fullPrice * 1.62))
            else -> 1.0
        }
        if (surgeMultiplier > 1.0) {
            binding.llSurgeContainer.visibility = View.VISIBLE
            binding.tvSurgeMultiplier.text = String.format(Locale.US, "%.2f", surgeMultiplier)
        } else {
            binding.llSurgeContainer.visibility = View.VISIBLE
            binding.tvSurgeMultiplier.text = "1.00"
        }

        // 4. Сектори маршруту (Борщагівка Кольцова → Борщагівка Микіл)
        binding.tvStartSector.text = if (!order.fromSector.isNullOrEmpty()) order.fromSector else "Не визначено"
        binding.tvEndSector.text = if (!order.toSector.isNullOrEmpty()) order.toSector else "Не визначено"

        // 5. Інформація про пасажира ("Поїздок: <50" та рейтинг "5.0")
        val clientData = order.client
        if (clientData != null) {
            val rides = clientData.completedRides
            binding.tvClientRides.text = if (rides < 50) "Поїздок: <50" else "Поїздок: $rides"
            binding.tvClientRating.text = String.format(Locale.US, "%.1f", clientData.rating)
        } else {
            binding.tvClientRides.text = "Поїздок: <50"
            binding.tvClientRating.text = "5.0"
        }

        // 6. Тариф
        binding.tvTariff.text = order.tariffName ?: "Стандарт"

        // 7. Кнопка прийняття замовлення (бірюзова @color/driver_neon_teal)
        val shouldHideAccept = intent.getBooleanExtra("EXTRA_HIDE_ACCEPT_BUTTON", false)
        if (shouldHideAccept) {
            binding.bottomAcceptPanel.visibility = View.GONE
        } else {
            binding.bottomAcceptPanel.visibility = View.VISIBLE
            binding.btnContainerLayout.backgroundTintList = null
            binding.btnContainerLayout.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(neonTeal)
            }
        }

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
        setupMarketingPaymentSplit(secondaryColor)
        calculatePickupDistanceAndEta()
    }

    /**
     * Запускає плавний зворотний відлік на кнопці "Прийняти":
     * - кільце навколо цифри плавно зменшується пропорційно часу, що залишився;
     * - цифра показує секунди до закриття;
     * - коли час виходить — екран автоматично закривається і повертає водія назад (в Ефір).
     */
    private fun startVisualAcceptCountdown() {
        acceptVisualTimer?.cancel()
        timerRingDrawable?.progress = 1f
        binding.tvAcceptTimer.text = (ACCEPT_COUNTDOWN_DURATION_MS / 1000L).toString()

        acceptVisualTimer = object : CountDownTimer(ACCEPT_COUNTDOWN_DURATION_MS, ACCEPT_COUNTDOWN_TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val fraction = (millisUntilFinished.toFloat() / ACCEPT_COUNTDOWN_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                timerRingDrawable?.progress = fraction

                val secondsLeft = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                val secStr = secondsLeft.toString()
                if (binding.tvAcceptTimer.text.toString() != secStr) {
                    binding.tvAcceptTimer.text = secStr
                }
            }

            override fun onFinish() {
                timerRingDrawable?.progress = 0f
                binding.tvAcceptTimer.text = "0"
                if (!swipeTriggered && !isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }.start()
    }

    private fun calculatePickupDistanceAndEta() {
        val order = currentOrder ?: return

        // Якщо сервер вже передав готову полілінію подачі водія до клієнта — рахуємо точну довжину по ній
        if (!order.driverToPickupPolyline.isNullOrEmpty()) {
            try {
                val path = PolyUtil.decode(order.driverToPickupPolyline)
                if (path.size >= 2) {
                    var totalMeters = 0.0
                    val seg = FloatArray(1)
                    for (i in 0 until path.size - 1) {
                        Location.distanceBetween(
                            path[i].latitude, path[i].longitude,
                            path[i + 1].latitude, path[i + 1].longitude,
                            seg
                        )
                        totalMeters += seg[0]
                    }
                    applyPickupMetrics(totalMeters / 1000.0)
                    return
                }
            } catch (_: Exception) {}
        }

        val originLat = order.originLat ?: 0.0
        val originLng = order.originLng ?: 0.0
        if (originLat == 0.0 && originLng == 0.0) {
            binding.tvPickupDistance.text = "— км"
            binding.tvPickupEta.text = "Подача авто"
            return
        }

        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let { (lat, lng) ->
                val res = FloatArray(1)
                Location.distanceBetween(lat, lng, originLat, originLng, res)
                applyPickupMetrics((res[0] / 1000.0) * 1.28)
                return
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val res = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, originLat, originLng, res)
                    applyPickupMetrics((res[0] / 1000.0) * 1.28)
                } else {
                    applyPickupMetrics(3.11)
                }
            }.addOnFailureListener {
                applyPickupMetrics(3.11)
            }
        } else {
            applyPickupMetrics(3.11)
        }
    }

    private fun applyPickupMetrics(roadKm: Double) {
        val safeKm = roadKm.coerceAtLeast(0.2)
        binding.tvPickupDistance.text = String.format(Locale.US, "%.2f км", safeKm)
        val estMinutes = ((safeKm / 30.0) * 60.0).toInt().coerceAtLeast(2)
        binding.tvPickupEta.text = "$estMinutes хв на подачу авто"
    }

    private fun setupSwipeGesture() {
        var startX = 0f

        binding.btnAccept.setOnTouchListener { _, event ->
            if (swipeTriggered) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentX = event.rawX
                    val deltaX = currentX - startX
                    val threshold = binding.btnContainerLayout.width * 0.45f

                    if (deltaX > 0 && !swipeTriggered) {
                        binding.llChevronsLayer.translationX = deltaX

                        val currentChevronRight = binding.llChevronsLayer.left + deltaX + binding.llChevronsLayer.width
                        val textStartPos = binding.llStaticTextLayer.left.toFloat()

                        if (currentChevronRight > textStartPos) {
                            val textWidth = binding.llStaticTextLayer.width.toFloat()
                            val eraseProgress = (currentChevronRight - textStartPos) / (textWidth * 0.6f)
                            val dynamicAlpha = 1f - eraseProgress

                            val finalAlpha = if (dynamicAlpha < 0f) 0f else dynamicAlpha
                            binding.tvBtnTitle.alpha = finalAlpha
                            binding.tvBtnSubtitle.alpha = finalAlpha * 0.75f
                        } else {
                            binding.tvBtnTitle.alpha = 1f
                            binding.tvBtnSubtitle.alpha = 0.75f
                        }
                    }

                    if (deltaX > threshold && !swipeTriggered) {
                        swipeTriggered = true
                        acceptVisualTimer?.cancel()
                        binding.btnAccept.isEnabled = false

                        binding.tvBtnTitle.alpha = 0f
                        binding.tvBtnSubtitle.alpha = 0f

                        val flyOutDistance = binding.btnContainerLayout.width.toFloat()
                        binding.llChevronsLayer.animate()
                            .translationX(flyOutDistance)
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                acceptOrder()
                            }
                            .start()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!swipeTriggered) {
                        binding.llChevronsLayer.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(250)
                            .start()

                        binding.tvBtnTitle.animate().alpha(1f).setDuration(250).start()
                        binding.tvBtnSubtitle.animate().alpha(0.75f).setDuration(250).start()
                    }
                    startX = 0f
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPaymentMethod() {
        val density = resources.displayMetrics.density
        val method = currentOrder?.paymentMethod ?: "CASH"
        val darkText = Color.parseColor("#1A1C1E")
        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)

        val isCard = (method == "CARD" || method == "ELECTRONIC")
        binding.llPriceBackground.backgroundTintList = null
        binding.llPriceBackground.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(if (isCard) Color.parseColor("#28C2E1") else neonTeal)
        }

        binding.ivPaymentIcon.imageTintList = null
        binding.ivPaymentIcon.clearColorFilter()
        binding.ivPaymentIcon.setImageDrawable(
            OrderAdapter.UnifiedCardIconDrawable(
                type = if (isCard) OrderAdapter.UnifiedCardIconDrawable.Type.CARD else OrderAdapter.UnifiedCardIconDrawable.Type.WALLET,
                strokeColor = darkText
            )
        )
        binding.tvPrice.setTextColor(darkText)
    }

    private fun setupServices() {
        val services = currentOrder?.services

        if (!services.isNullOrEmpty()) {
            binding.llServicesBlock.visibility = View.VISIBLE
            binding.llServicesList.removeAllViews()
            for (service in services) {
                val tv = TextView(this)
                tv.text = "• ${service.name}"
                tv.setTextColor(ContextCompat.getColor(this, R.color.driver_text_primary))
                tv.textSize = 14f
                tv.setPadding(0, 4, 0, 4)
                binding.llServicesList.addView(tv)
            }
        } else {
            binding.llServicesBlock.visibility = View.GONE
        }
    }

    private fun setupComment() {
        val comment = currentOrder?.comment

        if (!comment.isNullOrEmpty()) {
            binding.llCommentBlock.visibility = View.VISIBLE
            binding.llCommentBubble.background = createNeonBackground()
            binding.tvCommentText.text = comment
        } else {
            binding.llCommentBlock.visibility = View.GONE
        }
    }

    private fun buildRouteList() {
        binding.llRouteContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val order = currentOrder ?: return
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val cardBgColor = ContextCompat.getColor(this, R.color.driver_black_bg)

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

            val view = inflater.inflate(R.layout.item_route_point, binding.llRouteContainer, false)

            val tvAddress = view.findViewById<TextView>(R.id.tv_point_address)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_point_icon)
            val lineTop = view.findViewById<View>(R.id.view_line_top)
            val lineBottom = view.findViewById<View>(R.id.view_line_bottom)

            tvAddress.text = point.address
            tvAddress.textSize = 16.5f
            tvAddress.setTextColor(primaryColor)
            tvAddress.setTypeface(tvAddress.typeface, Typeface.NORMAL)
            tvAddress.setPadding(0, (11 * density).toInt(), (16 * density).toInt(), (11 * density).toInt())

            (tvAddress.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                marginStart = (46 * density).toInt()
                tvAddress.layoutParams = this
            }

            (ivIcon.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                width = (15 * density).toInt()
                height = (15 * density).toInt()
                marginStart = (16 * density).toInt()
                ivIcon.layoutParams = this
            }

            ivIcon.imageTintList = null
            ivIcon.clearColorFilter()
            ivIcon.setImageDrawable(
                OrderAdapter.RoutePinDrawable(
                    hasInnerDot = (point.type == PointType.START),
                    strokeColor = secondaryColor,
                    bgColor = cardBgColor
                )
            )

            lineTop.setBackgroundColor(secondaryColor)
            lineTop.alpha = 0.65f
            lineBottom.setBackgroundColor(secondaryColor)
            lineBottom.alpha = 0.65f

            lineTop.visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
            lineBottom.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            binding.llRouteContainer.addView(view)

            // Тонкий горизонтальний розділювач між рядками адрес як на скріншоті 3
            if (!isLast) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt().coerceAtLeast(1)
                    ).apply {
                        marginStart = (46 * density).toInt()
                    }
                    setBackgroundColor(Color.parseColor("#545762"))
                }
                binding.llRouteContainer.addView(divider)
            }
        }
    }

    data class RoutePoint(val address: String, val type: PointType)
    enum class PointType { START, WAYPOINT, END }

    private fun acceptOrder() {
        val orderId = currentOrder?.id ?: return
        binding.btnAccept.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderDetailsActivity).acceptOrder(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val updatedOrder = response.body()!!

                    if (updatedOrder.status == "SCHEDULED") {
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення успішно заплановано!", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@OrderDetailsActivity, OrderProgressActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", updatedOrder)
                        intent.putExtra("EXTRA_ORDER_ID", orderId)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення прийнято!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@OrderDetailsActivity, OrderProgressActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", updatedOrder)
                        intent.putExtra("EXTRA_ORDER_ID", orderId)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody?.contains("Вже має водія") == true || response.code() == 409) {
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення вже забрали", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        resetSwipeButtonState()
                        Toast.makeText(this@OrderDetailsActivity, "Помилка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                resetSwipeButtonState()
                Toast.makeText(this@OrderDetailsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Форматує блок "Оплата замовлення" 1-в-1 за скріншотом:
     * [ 👛  145 ₴ готівкою, 35 ₴ на баланс ]
     * де сума клієнта виділена жовтим (#F5D033), сума на баланс — бірюзовим (#33CCA1), а слова — білим.
     */
    private fun setupMarketingPaymentSplit(secondaryColor: Int) {
        val order = currentOrder ?: return
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val yellowColor = Color.parseColor("#F5D033")
        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)

        val isCard = (order.paymentMethod == "CARD" || order.paymentMethod == "ELECTRONIC")
        binding.ivPaymentSplitIcon.setImageDrawable(
            OrderAdapter.UnifiedCardIconDrawable(
                type = if (isCard) OrderAdapter.UnifiedCardIconDrawable.Type.CARD else OrderAdapter.UnifiedCardIconDrawable.Type.WALLET,
                strokeColor = secondaryColor
            )
        )

        binding.llPaymentSplitBlock.visibility = View.VISIBLE
        val paymentTypeWord = if (isCard) "на картку" else "готівкою"

        if (order.companyDiscountCompensation > 0.0) {
            val clientAmountStr = "${order.clientPayAmount.toInt()} ₴"
            val clientFullStr = "$clientAmountStr $paymentTypeWord,"
            val clientSpan = SpannableStringBuilder(clientFullStr).apply {
                setSpan(ForegroundColorSpan(yellowColor), 0, clientAmountStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(primaryColor), clientAmountStr.length, clientFullStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.tvClientPayDetail.text = clientSpan

            val compAmountStr = "${order.companyDiscountCompensation.toInt()} ₴"
            val compFullStr = "$compAmountStr на баланс"
            val compSpan = SpannableStringBuilder(compFullStr).apply {
                setSpan(ForegroundColorSpan(neonTeal), 0, compAmountStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(primaryColor), compAmountStr.length, compFullStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.tvCompanyCompensationDetail.text = compSpan
            binding.tvCompanyCompensationDetail.visibility = View.VISIBLE
        } else {
            val totalStr = "${order.getTotalFullPrice().toInt()} ₴"
            val fullStr = "$totalStr $paymentTypeWord"
            val span = SpannableStringBuilder(fullStr).apply {
                val amountColor = if (isCard) neonTeal else yellowColor
                setSpan(ForegroundColorSpan(amountColor), 0, totalStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(primaryColor), totalStr.length, fullStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.tvClientPayDetail.text = span
            binding.tvCompanyCompensationDetail.visibility = View.GONE
        }
    }

    private fun resetSwipeButtonState() {
        binding.btnAccept.isEnabled = true
        binding.llChevronsLayer.translationX = 0f
        binding.llChevronsLayer.alpha = 1f
        binding.tvBtnTitle.alpha = 1f
        binding.tvBtnSubtitle.alpha = 0.75f
        swipeTriggered = false
        val shouldHideAccept = intent.getBooleanExtra("EXTRA_HIDE_ACCEPT_BUTTON", false)
        if (!shouldHideAccept && !isFinishing && !isDestroyed) {
            startVisualAcceptCountdown()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.apply {
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isMapToolbarEnabled = false
            isCompassEnabled = false
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isIndoorLevelPickerEnabled = false
        }

        try {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val styleRes = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                R.raw.map_style_dark
            } else {
                R.raw.map_style_standard
            }
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
        } catch (_: Exception) {}

        val order = currentOrder ?: return
        val polylineString = order.polyline
        val pickupPolylineString = order.driverToPickupPolyline
        val neonTealColor = ContextCompat.getColor(this, R.color.driver_neon_teal)

        val boundsBuilder = LatLngBounds.Builder()
        var hasBoundsPoints = false

        // 1. Лінія подачі авто до Точки 1 та жовта точка позиції водія (як на скріншоті 3)
        if (!pickupPolylineString.isNullOrEmpty()) {
            try {
                val pickupPath = PolyUtil.decode(pickupPolylineString)
                if (pickupPath.isNotEmpty()) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(pickupPath)
                            .width(12f)
                            .color(neonTealColor)
                            .jointType(JointType.ROUND)
                            .geodesic(true)
                    )
                    val driverDot = createDriverDotBitmap()
                    map.addMarker(
                        MarkerOptions()
                            .position(pickupPath.first())
                            .icon(BitmapDescriptorFactory.fromBitmap(driverDot))
                            .anchor(0.5f, 0.5f)
                            .zIndex(3f)
                    )
                    pickupPath.forEach {
                        boundsBuilder.include(it)
                        hasBoundsPoints = true
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Основний маршрут поїздки та круглі маркери "1" (зелений) і "2" (салатовий)
        if (!polylineString.isNullOrEmpty()) {
            try {
                val path: List<LatLng> = PolyUtil.decode(polylineString)
                map.addPolyline(
                    PolylineOptions()
                        .addAll(path)
                        .width(12f)
                        .color(neonTealColor)
                        .jointType(JointType.ROUND)
                        .geodesic(true)
                )

                if (path.isNotEmpty()) {
                    var currentNumber = 1
                    val startBitmap = createCustomMarkerBitmap(currentNumber++, Color.parseColor("#33CCA1"))
                    map.addMarker(
                        MarkerOptions()
                            .position(path.first())
                            .icon(BitmapDescriptorFactory.fromBitmap(startBitmap))
                            .anchor(0.5f, 0.5f)
                            .zIndex(2f)
                    )

                    order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                        val stopBitmap = createCustomMarkerBitmap(currentNumber++, Color.parseColor("#F5D033"))
                        val stopLatLng = LatLng(stop.lat, stop.lng)
                        map.addMarker(
                            MarkerOptions()
                                .position(stopLatLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(stopBitmap))
                                .anchor(0.5f, 0.5f)
                                .zIndex(2f)
                        )
                        boundsBuilder.include(stopLatLng)
                    }

                    val endBitmap = createCustomMarkerBitmap(currentNumber, Color.parseColor("#6AE056"))
                    map.addMarker(
                        MarkerOptions()
                            .position(path.last())
                            .icon(BitmapDescriptorFactory.fromBitmap(endBitmap))
                            .anchor(0.5f, 0.5f)
                            .zIndex(2f)
                    )

                    path.forEach {
                        boundsBuilder.include(it)
                        hasBoundsPoints = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (hasBoundsPoints) {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
            } catch (e: Exception) {
                val origin = LatLng(order.originLat ?: 50.45, order.originLng ?: 30.52)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13.5f))
            }
        } else {
            val origin = LatLng(order.originLat ?: 50.45, order.originLng ?: 30.52)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13.5f))
        }
    }

    private fun createNeonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#2633CCA1"))
            setStroke(3, ContextCompat.getColor(this@OrderDetailsActivity, R.color.driver_neon_teal))
            val radius = 12f * resources.displayMetrics.density
            cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, radius, radius)
        }
    }

    private fun createCustomMarkerBitmap(number: Int, colorInt: Int): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_custom_marker, null)
        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)

        tvNumber.text = number.toString()
        ivBg.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun createDriverDotBitmap(): Bitmap {
        val size = (14 * resources.displayMetrics.density).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5D033")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
        return bitmap
    }

    /**
     * Кастомний Drawable для кругового таймера зворотного відліку навколо цифри на кнопці "Прийняти".
     * Малює фонову підкладку-кільце та дугу прогресу, яка зменшується від 360° до 0°.
     */
    private class CircularTimerDrawable(
        ringColor: Int,
        trackColor: Int,
        strokeWidthPx: Float
    ) : Drawable() {

        var progress: Float = 1f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidateSelf()
            }

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }

        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }

        private val arcRect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return

            val inset = arcPaint.strokeWidth / 2f + 2f
            arcRect.set(
                b.left + inset,
                b.top + inset,
                b.right - inset,
                b.bottom - inset
            )

            canvas.drawOval(arcRect, trackPaint)

            val sweepAngle = 360f * progress
            if (sweepAngle > 0.5f) {
                canvas.drawArc(arcRect, -90f, sweepAngle, false, arcPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            trackPaint.alpha = alpha
            arcPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            trackPaint.colorFilter = colorFilter
            arcPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * Допоміжні векторні іконки для екрану "Деталі поїздки":
     * - LOCATION_PIN: пін локації подачі ("3.11 км")
     * - LIGHTNING: блискавка коефіцієнта ("1.12")
     * - PERSON_OUTLINE: контур пасажира ("Поїздок: <50")
     * - CLOCK: годинник часу подачі ("6 хв на подачу авто")
     */
    private class DetailsIconDrawable(
        private val type: Type,
        private val color: Int
    ) : Drawable() {

        enum class Type { LOCATION_PIN, LIGHTNING, PERSON_OUTLINE, CLOCK }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@DetailsIconDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@DetailsIconDrawable.color
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = minOf(w, h) / 24f

            canvas.save()
            canvas.translate(b.left + (w - 24f * s) / 2f, b.top + (h - 24f * s) / 2f)
            canvas.scale(s, s)
            strokePaint.strokeWidth = 2.0f

            when (type) {
                Type.LOCATION_PIN -> {
                    val pinPath = Path().apply {
                        moveTo(12f, 21f)
                        cubicTo(12f, 21f, 5.5f, 15.2f, 5.5f, 9.8f)
                        arcTo(RectF(5.5f, 3.3f, 18.5f, 16.3f), 180f, 180f, false)
                        cubicTo(18.5f, 15.2f, 12f, 21f, 12f, 21f)
                        close()
                    }
                    canvas.drawPath(pinPath, strokePaint)
                    canvas.drawCircle(12f, 9.8f, 2.3f, strokePaint)
                }

                Type.LIGHTNING -> {
                    val bolt = Path().apply {
                        moveTo(13.5f, 2.5f)
                        lineTo(5.5f, 13.2f)
                        lineTo(11.5f, 13.2f)
                        lineTo(10.2f, 21.5f)
                        lineTo(18.5f, 10.8f)
                        lineTo(12.5f, 10.8f)
                        close()
                    }
                    canvas.drawPath(bolt, fillPaint)
                }

                Type.PERSON_OUTLINE -> {
                    canvas.drawCircle(12f, 8.2f, 3.8f, strokePaint)
                    val shoulders = Path().apply {
                        moveTo(4.8f, 19.5f)
                        cubicTo(4.8f, 15.2f, 19.2f, 15.2f, 19.2f, 19.5f)
                    }
                    canvas.drawPath(shoulders, strokePaint)
                    canvas.drawLine(3.8f, 19.5f, 20.2f, 19.5f, strokePaint)
                }

                Type.CLOCK -> {
                    canvas.drawCircle(12f, 12f, 8.5f, strokePaint)
                    val hands = Path().apply {
                        moveTo(12f, 7.2f)
                        lineTo(12f, 12.2f)
                        lineTo(15.2f, 14.2f)
                    }
                    canvas.drawPath(hands, strokePaint)
                }
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}