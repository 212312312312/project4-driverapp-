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

        // Заменяем текстовое "грн" на символ "₴"
        binding.tvPrice.text = order.getFormattedPrice().replace("грн", "₴")
        binding.tvTotalDistanceHeader.text = order.getFormattedDistance().replace("грн", "₴")

        binding.tvBlockActivityBonus.text = if (order.activityBonus >= 0) "+${order.activityBonus}" else "${order.activityBonus}"

        // ЛОГИКА КАЛЬКУЛЯЦИИ ТАРИФА (Если путь меньше 1 км -> показываем — ₴/км)
        val distanceKm = (order.distanceMeters ?: 0) / 1000.0
        if (distanceKm < 1.0) {
            binding.tvBlockPricePerKm.text = "— ₴/км"
        } else {
            binding.tvBlockPricePerKm.text = order.getPricePerKm().replace("грн", "₴")
        }

        // Вывод секторов чистым текстом без скобок
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

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
    }

    private fun setupSwipeGesture() {
        var startX = 0f

        binding.btnAccept.setOnTouchListener { _, event ->
            if (swipeTriggered) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentX = event.x
                    val deltaX = currentX - startX
                    val threshold = binding.btnContainerLayout.width * 0.4f

                    if (deltaX > threshold && !swipeTriggered) {
                        swipeTriggered = true
                        binding.tvBtnTitle.text = "ОБРОБКА..."
                        binding.tvBtnSubtitle.text = "Будь ласка, зачекайте"
                        acceptOrder()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            binding.llCommentBubble.background = createNeonBackground() // Добавляем эту строку
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
            val isLast = (i == allPoints.size - 1)

            val view = inflater.inflate(R.layout.item_route_point, binding.llRouteContainer, false)

            val tvAddress = view.findViewById<TextView>(R.id.tv_point_address)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_point_icon)
            val line = view.findViewById<View>(R.id.view_line)

            tvAddress.text = point.address
            when (point.type) {
                PointType.START -> ivIcon.setImageResource(R.drawable.ic_circle_green)
                PointType.END -> ivIcon.setImageResource(R.drawable.ic_circle_red)
                PointType.WAYPOINT -> {
                    ivIcon.setImageResource(R.drawable.ic_circle_green)
                    ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.driver_neon_teal))
                }
            }
            line.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
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

    private fun resetSwipeButtonState() {
        binding.btnAccept.isEnabled = true
        binding.tvBtnTitle.text = "Прийняти"
        binding.tvBtnSubtitle.text = "Проведіть, щоб прийняти"
        swipeTriggered = false
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.apply {
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isMapToolbarEnabled = false
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
            setColor(Color.parseColor("#2633CCA1")) // 15% прозрачности неона
            setStroke(4, ContextCompat.getColor(this@OrderDetailsActivity, R.color.driver_neon_teal))

            val radius = 14f * resources.displayMetrics.density
            // Массив из 8 значений (по 2 радиуса X и Y на каждый угол):
            cornerRadii = floatArrayOf(
                0f, 0f,          // Top-Left (острый)
                radius, radius,  // Top-Right (скругленный)
                radius, radius,  // Bottom-Right (скругленный)
                radius, radius   // Bottom-Left (скругленный)
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