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
import com.taxiapp.driver.databinding.ActivityOrderDetailsBinding // ВАЖНО: Импорт Binding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrderDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityOrderDetailsBinding // Объявляем Binding
    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализация ViewBinding
        binding = ActivityOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Безопасное получение объекта заказа
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

        // 3. Инициализация карты
        // Используем findFragmentById с R.id.map, так как это надежнее для фрагментов
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 4. Обработчики кнопок через Binding
        binding.btnBackCard.setOnClickListener { finish() }
        binding.btnAccept.setOnClickListener { acceptOrder() }
    }

    private fun setupUI() {
        // Заполняем данные через binding (больше никаких findViewById!)
        binding.tvOrderId.text = "Замовлення #${currentOrder?.id}"
        binding.tvTariff.text = currentOrder?.tariffName ?: "Стандарт"

        val distInfo = "${currentOrder?.getFormattedDistance()} • ${currentOrder?.getPricePerKm()}"
        binding.tvDistanceInfo.text = distInfo
        binding.tvPrice.text = currentOrder?.getFormattedPrice()

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
    }

    private fun setupPaymentMethod() {
        val method = currentOrder?.paymentMethod ?: "CASH"

        if (method == "CASH") {
            binding.llPriceBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD600"))
            binding.ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
        } else {
            binding.llPriceBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2979FF"))
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

            // Здесь мы инфлейтим отдельный элемент списка, он не часть ActivityBinding
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
        binding.btnAccept.text = "ОБРОБКА..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderDetailsActivity).acceptOrder(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val updatedOrder = response.body()!!

                    // --- ИСПРАВЛЕНИЕ ЛОГИКИ ПЕРЕХОДА ---
                    if (updatedOrder.status == "SCHEDULED") {
                        // Если заказ запланированный - просто уведомляем и закрываем экран
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення успішно заплановано!", Toast.LENGTH_LONG).show()
                        finish() // Возвращаемся в список/эфир
                    } else {
                        // Если заказ активный ("на сейчас") - переходим к выполнению
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення прийнято!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@OrderDetailsActivity, OrderProgressActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", updatedOrder)
                        intent.putExtra("EXTRA_ORDER_ID", orderId)
                        startActivity(intent)
                        finish()
                    }
                    // -----------------------------------

                } else {
                    binding.btnAccept.isEnabled = true
                    binding.btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"

                    // Пытаемся распарсить ошибку
                    val errorBody = response.errorBody()?.string()
                    if (errorBody?.contains("Вже має водія") == true || response.code() == 409) {
                        Toast.makeText(this@OrderDetailsActivity, "Замовлення вже забрали", Toast.LENGTH_SHORT).show()
                        finish() // Закрываем, так как заказ ушел
                    } else {
                        Toast.makeText(this@OrderDetailsActivity, "Помилка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                binding.btnAccept.isEnabled = true
                binding.btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"
                Toast.makeText(this@OrderDetailsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
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

                    // Безопасное перемещение камеры с проверкой размера экрана
                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                    } catch (e: Exception) {
                        // Фолбек, если карта еще не готова по размеру
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