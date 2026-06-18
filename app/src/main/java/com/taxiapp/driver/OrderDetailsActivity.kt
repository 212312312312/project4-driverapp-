package com.taxiapp.driver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.databinding.ActivityOrderDetailsBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrderDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityOrderDetailsBinding
    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private var swipeTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
    }

    private fun setupUI() {
        val order = currentOrder ?: return

        binding.tvTariff.text = order.tariffName ?: "Стандарт"

        val fullPrice = order.getTotalFullPrice()
        binding.tvPrice.text = "${fullPrice.toInt()} ₴"

        binding.tvTotalDistanceHeader.text = order.getFormattedDistance().replace("грн", "₴")

        binding.tvBlockActivityBonus.text = if (order.activityBonus >= 0) "+${order.activityBonus}" else "${order.activityBonus}"

        val distanceKm = (order.distanceMeters ?: 0) / 1000.0
        if (distanceKm < 1.0) {
            binding.tvBlockPricePerKm.text = "— ₴/км"
        } else {
            val pricePerKm = fullPrice / distanceKm
            binding.tvBlockPricePerKm.text = String.format(java.util.Locale.US, "%.0f ₴/км", pricePerKm)
        }

        binding.tvStartSector.text = if (!order.fromSector.isNullOrEmpty()) order.fromSector else "Не визначено"
        binding.tvEndSector.text = if (!order.toSector.isNullOrEmpty()) order.toSector else "Не визначено"

        val clientData = order.client
        if (clientData != null) {
            binding.tvClientRides.text = "Поїздок: ${clientData.completedRides}"
            binding.tvClientRating.text = clientData.rating.toString()
        } else {
            binding.tvClientRides.text = "Поїздок: 0"
            binding.tvClientRating.text = "5.0"
        }

        val shouldHideAccept = intent.getBooleanExtra("EXTRA_HIDE_ACCEPT_BUTTON", false)

        if (shouldHideAccept) {
            binding.btnContainerLayout.visibility = View.GONE
        } else {
            binding.btnContainerLayout.visibility = View.VISIBLE
        }

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
        setupMarketingPaymentSplit()
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
                            binding.tvBtnSubtitle.alpha = finalAlpha * 0.7f
                        } else {
                            binding.tvBtnTitle.alpha = 1f
                            binding.tvBtnSubtitle.alpha = 0.7f
                        }
                    }

                    if (deltaX > threshold && !swipeTriggered) {
                        swipeTriggered = true
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
                        binding.tvBtnSubtitle.animate().alpha(0.7f).setDuration(250).start()
                    }
                    startX = 0f
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPaymentMethod() {
        val method = currentOrder?.paymentMethod ?: "CASH"

        if (method == "CASH") {
            binding.llPriceBackground.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            binding.ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
        } else {
            binding.llPriceBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#54b1f0"))
            binding.ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
        }
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

            // Идеальная соосность: центрируем иконки разного размера по оси 26dp от края экрана
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
                PointType.START -> {
                    ivIcon.setImageResource(R.drawable.ic_marker_from)
                    ivIcon.clearColorFilter()
                }
                PointType.END -> {
                    ivIcon.setImageResource(R.drawable.ic_marker_to)
                    ivIcon.clearColorFilter()
                }
                PointType.WAYPOINT -> {
                    ivIcon.setImageResource(R.drawable.ic_marker_waypoint)
                    ivIcon.clearColorFilter()
                }
            }

            lineTop.visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
            lineBottom.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            binding.llRouteContainer.addView(view)
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
                        // Сразу переводим водителя на экран ведения заказа (OrderProgressActivity)
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
                    resetSwipeButtonState()

                    val errorBody = response.errorBody()?.string()
                    if (errorBody?.contains("Вже має водія") == true || response.code() == 409) {
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення вже забрали", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@OrderDetailsActivity, "Помилка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                resetSwipeButtonState()
                Toast.makeText(this@OrderDetailsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMarketingPaymentSplit() {
        val order = currentOrder ?: return

        if (order.companyDiscountCompensation > 0.0) {
            binding.llPaymentSplitBlock.visibility = View.VISIBLE

            val paymentTypeWord = if (order.paymentMethod == "CARD") "на картку" else "готівкою"

            binding.tvClientPayDetail.text = "${order.clientPayAmount.toInt()} ₴ $paymentTypeWord,"
            binding.tvCompanyCompensationDetail.text = "+${order.companyDiscountCompensation.toInt()} ₴ на баланс"
        } else {
            binding.llPaymentSplitBlock.visibility = View.GONE
        }
    }

    private fun resetSwipeButtonState() {
        binding.btnAccept.isEnabled = true
        binding.llChevronsLayer.translationX = 0f
        binding.llChevronsLayer.alpha = 1f
        binding.tvBtnTitle.alpha = 1f
        binding.tvBtnSubtitle.alpha = 0.7f
        swipeTriggered = false
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.apply {
    isScrollGesturesEnabled = true
    isZoomGesturesEnabled = true
    isMapToolbarEnabled = false // Уже отключено (убирает переход в стороннее приложение Гугл карт)
    
    // 🛠️ ДОБАВЛЯЕМ СЮДА ЭТИ СТРОКИ:
    isCompassEnabled = false          // Отключает компас, который появляется при вращении карты
    isZoomControlsEnabled = false     // Отключает наэкранные кнопки "+" и "-"
    isMyLocationButtonEnabled = false // Отключает стандартную кнопку привязки к геопозиции
    isIndoorLevelPickerEnabled = false // Отключает переключатель этажей (для зданий внутри)
}

        // 🌟 ИСПРАВЛЕНО: Установка динамического стиля карты на основе текущей темы Android
        try {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val styleRes = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                R.raw.map_style_dark
            } else {
                R.raw.map_style_standard
            }
            
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, styleRes)
            )
            if (!success) {
                android.util.Log.e("UNIT_MAP", "Не удалось распарсить JSON стиля карты.")
            }
        } catch (e: android.content.res.Resources.NotFoundException) {
            android.util.Log.e("UNIT_MAP", "Файл стиля карты не найден в папке res/raw", e)
        }

        val order = currentOrder ?: return
        val polylineString = order.polyline

        if (!polylineString.isNullOrEmpty()) {
            try {
                val path: List<LatLng> = PolyUtil.decode(polylineString)
                map.addPolyline(PolylineOptions()
                    .addAll(path)
                    .width(12f)
                    .color(ContextCompat.getColor(this, R.color.driver_neon_teal))
                    .geodesic(true))

                if (path.isNotEmpty()) {
                    var currentNumber = 1
                    val startBitmap = createCustomMarkerBitmap(currentNumber++, R.color.driver_neon_teal)
                    map.addMarker(MarkerOptions().position(path.first()).icon(BitmapDescriptorFactory.fromBitmap(startBitmap)).anchor(0.5f, 0.5f))

                    order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                        val stopBitmap = createCustomMarkerBitmap(currentNumber++, R.color.taxi_yellow)
                        map.addMarker(MarkerOptions().position(LatLng(stop.lat, stop.lng)).icon(BitmapDescriptorFactory.fromBitmap(stopBitmap)).anchor(0.5f, 0.5f))
                    }

                    val endBitmap = createCustomMarkerBitmap(currentNumber, R.color.driver_error)
                    map.addMarker(MarkerOptions().position(path.last()).icon(BitmapDescriptorFactory.fromBitmap(endBitmap)).anchor(0.5f, 0.5f))

                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }

                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                    } catch (e: Exception) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(path.first(), 14f))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val origin = LatLng(order.originLat ?: 50.45, order.originLng ?: 30.52)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 14f))
        }
    }

    private fun createNeonBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#2633CCA1"))
            setStroke(4, ContextCompat.getColor(this@OrderDetailsActivity, R.color.driver_neon_teal))

            val radius = 14f * resources.displayMetrics.density
            cornerRadii = floatArrayOf(
                0f, 0f,          
                radius, radius,  
                radius, radius,  
                radius, radius   
            )
        }
    }

    private fun createCustomMarkerBitmap(number: Int, colorResId: Int): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_custom_marker, null)
        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)

        tvNumber.text = number.toString()
        ivBg.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN)

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}