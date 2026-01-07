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
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.google.android.material.button.MaterialButton
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrderDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private lateinit var btnAccept: MaterialButton
    private lateinit var routeContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_details)

        // БЕЗОПАСНОЕ ИЗВЛЕЧЕНИЕ ОБЪЕКТА (Fix deprecation)
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

        routeContainer = findViewById(R.id.ll_route_container)
        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<View>(R.id.btn_back_card).setOnClickListener { finish() }
        btnAccept = findViewById(R.id.btn_accept)
        btnAccept.setOnClickListener { acceptOrder() }
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.tv_order_id).text = "Замовлення #${currentOrder?.id}"
        findViewById<TextView>(R.id.tv_tariff).text = currentOrder?.tariffName ?: "Стандарт"

        val distInfo = "${currentOrder?.getFormattedDistance()} • ${currentOrder?.getPricePerKm()}"
        findViewById<TextView>(R.id.tv_distance_info).text = distInfo
        findViewById<TextView>(R.id.tv_price).text = currentOrder?.getFormattedPrice()

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
    }

    private fun setupPaymentMethod() {
        val llPriceBg = findViewById<LinearLayout>(R.id.ll_price_background)
        val ivIcon = findViewById<ImageView>(R.id.iv_payment_icon)
        val method = currentOrder?.paymentMethod ?: "CASH"

        if (method == "CASH") {
            llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD600"))
            ivIcon.setImageResource(R.drawable.ic_payment_cash)
        } else {
            llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2979FF"))
            ivIcon.setImageResource(R.drawable.ic_payment_card)
        }
    }

    private fun setupServices() {
        val servicesBlock = findViewById<LinearLayout>(R.id.ll_services_block)
        val servicesList = findViewById<LinearLayout>(R.id.ll_services_list)
        val services = currentOrder?.services

        if (!services.isNullOrEmpty()) {
            servicesBlock.visibility = View.VISIBLE
            servicesList.removeAllViews()
            for (service in services) {
                val tv = TextView(this)
                tv.text = "• ${service.name}"
                tv.setTextColor(ContextCompat.getColor(this, R.color.driver_text_primary))
                tv.textSize = 14f
                tv.setPadding(0, 4, 0, 4)
                servicesList.addView(tv)
            }
        } else {
            servicesBlock.visibility = View.GONE
        }
    }

    private fun setupComment() {
        val commentBlock = findViewById<LinearLayout>(R.id.ll_comment_block)
        val tvComment = findViewById<TextView>(R.id.tv_comment_text)
        val comment = currentOrder?.comment

        if (!comment.isNullOrEmpty()) {
            commentBlock.visibility = View.VISIBLE
            tvComment.text = comment
        } else {
            commentBlock.visibility = View.GONE
        }
    }

    private fun buildRouteList() {
        routeContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val order = currentOrder ?: return
        val allPoints = mutableListOf<RoutePoint>()

        // ИСПРАВЛЕНО: Добавлен ?: "" для обработки String?
        allPoints.add(RoutePoint(order.fromAddress ?: "Адреса не вказана", PointType.START))

        order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
            allPoints.add(RoutePoint(stop.address ?: "Проміжна точка", PointType.WAYPOINT))
        }

        allPoints.add(RoutePoint(order.toAddress ?: "Кінцева точка", PointType.END))

        for (i in allPoints.indices) {
            val point = allPoints[i]
            val isLast = (i == allPoints.size - 1)
            val view = inflater.inflate(R.layout.item_route_point, routeContainer, false)
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
            routeContainer.addView(view)
        }
    }

    data class RoutePoint(val address: String, val type: PointType)
    enum class PointType { START, WAYPOINT, END }

    private fun acceptOrder() {
        val orderId = currentOrder?.id ?: return
        btnAccept.isEnabled = false
        btnAccept.text = "ОБРОБКА..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderDetailsActivity).acceptOrder(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val updatedOrder = response.body()!!

                    Toast.makeText(this@OrderDetailsActivity, "Замовлення прийнято!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@OrderDetailsActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", updatedOrder)
                    intent.putExtra("EXTRA_ORDER_ID", orderId)
                    startActivity(intent)
                    finish()
                } else {
                    btnAccept.isEnabled = true
                    btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"
                    Toast.makeText(this@OrderDetailsActivity, "Помилка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnAccept.isEnabled = true
                btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"
                Toast.makeText(this@OrderDetailsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.apply {
            isScrollGesturesEnabled = true // Разрешим скролл, чтобы водителю было удобнее
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
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Если полилайна нет, пробуем поставить маркеры по координатам
            val origin = LatLng(order.originLat ?: 50.45, order.originLng ?: 30.52)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 14f))
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