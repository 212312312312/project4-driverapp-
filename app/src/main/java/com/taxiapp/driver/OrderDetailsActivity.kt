package com.taxiapp.driver

import android.content.Intent
import android.graphics.Bitmap // Імпорт
import android.graphics.Canvas // Імпорт
import android.graphics.PorterDuff // Імпорт
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.google.maps.android.PolyUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OrderDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private lateinit var btnAccept: MaterialButton
    private lateinit var routeContainer: LinearLayout

    // --- НОВА ДОПОМІЖНА ФУНКЦІЯ ---
    // Створює картинку маркера з цифрою та кольором
    private fun createCustomMarkerBitmap(number: Int, colorResId: Int): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_custom_marker, null)

        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)

        tvNumber.text = number.toString()
        // Фарбуємо білий круг у потрібний колір
        ivBg.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN)

        // Магія перетворення View на Bitmap
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }
    // ------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_details)

        currentOrder = intent.getSerializableExtra("EXTRA_ORDER") as? Order
        if (currentOrder == null) {
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
        // ... (код setupUI залишається без змін, він заповнює текстові поля)
        findViewById<TextView>(R.id.tv_order_id).text = "Замовлення #${currentOrder?.id}"
        findViewById<TextView>(R.id.tv_price).text = currentOrder?.getFormattedPrice()
        findViewById<TextView>(R.id.tv_tariff).text = currentOrder?.tariffName
        val distInfo = "${currentOrder?.getFormattedDistance()} • ${currentOrder?.getPricePerKm()}"
        findViewById<TextView>(R.id.tv_distance_info).text = distInfo
        buildRouteList()
    }

    // ... (код buildRouteList та acceptOrder залишається без змін) ...
    private fun buildRouteList() {
        routeContainer.removeAllViews() // Очистити на всякий випадок

        val inflater = LayoutInflater.from(this)
        val order = currentOrder ?: return

        // 1. Складаємо повний список точок: [Початок] + [Зупинки] + [Кінець]
        val allPoints = mutableListOf<RoutePoint>()

        // Початок
        allPoints.add(RoutePoint(order.fromAddress, PointType.START))

        // Проміжні зупинки (сортуємо по stopOrder, якщо треба)
        order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
            allPoints.add(RoutePoint(stop.address, PointType.WAYPOINT))
        }

        // Кінець
        allPoints.add(RoutePoint(order.toAddress, PointType.END))

        // 2. Створюємо View для кожної точки
        for (i in allPoints.indices) {
            val point = allPoints[i]
            val isLast = (i == allPoints.size - 1)

            val view = inflater.inflate(R.layout.item_route_point, routeContainer, false)

            val tvAddress = view.findViewById<TextView>(R.id.tv_point_address)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_point_icon)
            val line = view.findViewById<View>(R.id.view_line)

            tvAddress.text = point.address

            // Налаштування іконки
            when (point.type) {
                PointType.START -> ivIcon.setImageResource(R.drawable.ic_circle_green)
                PointType.END -> ivIcon.setImageResource(R.drawable.ic_circle_red)
                PointType.WAYPOINT -> {
                    // Для waypoint використовуємо жовтий або білий колір
                    // Якщо немає окремої іконки, беремо зелену і фарбуємо
                    ivIcon.setImageResource(R.drawable.ic_circle_green)
                    ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.driver_neon_teal))
                }
            }

            // Налаштування лінії (ховаймо, якщо це остання точка)
            if (isLast) {
                line.visibility = View.INVISIBLE
            } else {
                line.visibility = View.VISIBLE
            }

            routeContainer.addView(view)
        }
    }

    // Внутрішні класи для зручності
    data class RoutePoint(val address: String, val type: PointType)
    enum class PointType { START, WAYPOINT, END }

    private fun acceptOrder() {
        val orderId = currentOrder?.id ?: return
        btnAccept.isEnabled = false
        btnAccept.text = "ОБРОБКА..."

        ApiClient.getInstance().getApiService(this).acceptOrder(orderId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@OrderDetailsActivity, "Замовлення прийнято!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@OrderDetailsActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", currentOrder) // Передаем объект заказа дальше
                    startActivity(intent)
                    finish()
                } else {
                    btnAccept.isEnabled = true
                    btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"
                    Toast.makeText(this@OrderDetailsActivity, "Помилка: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                btnAccept.isEnabled = true
                btnAccept.text = "ПРИЙНЯТИ ЗАМОВЛЕННЯ"
                Toast.makeText(this@OrderDetailsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- ОНОВЛЕНИЙ onMapReady ---
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.uiSettings.apply {
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
            isTiltGesturesEnabled = false
            isRotateGesturesEnabled = false
            isMapToolbarEnabled = false // Прибираємо кнопки переходу в Google Maps
        }

        val order = currentOrder ?: return
        val polylineString = order.polyline

        if (!polylineString.isNullOrEmpty()) {
            try {
                val path: List<LatLng> = PolyUtil.decode(polylineString)

                // 1. Малюємо лінію
                map.addPolyline(PolylineOptions()
                    .addAll(path)
                    .width(12f)
                    .color(ContextCompat.getColor(this, R.color.driver_neon_teal))
                    .geodesic(true))

                if (path.isNotEmpty()) {
                    // 2. Розставляємо НУМЕРОВАНІ маркери
                    var currentNumber = 1

                    // Точка А (Номер 1, Зелений)
                    val startBitmap = createCustomMarkerBitmap(currentNumber++, R.color.driver_neon_teal)
                    map.addMarker(MarkerOptions()
                        .position(path.first())
                        .icon(BitmapDescriptorFactory.fromBitmap(startBitmap))
                        .anchor(0.5f, 0.5f) // Центруємо маркер точно по координаті
                        .title("Точка А: ${order.fromAddress}"))

                    // Проміжні точки (Номер 2, 3..., Жовтий)
                    order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                        val stopBitmap = createCustomMarkerBitmap(currentNumber++, R.color.taxi_yellow) // Використовуємо жовтий
                        map.addMarker(MarkerOptions()
                            .position(LatLng(stop.lat, stop.lng))
                            .icon(BitmapDescriptorFactory.fromBitmap(stopBitmap))
                            .anchor(0.5f, 0.5f)
                            .title("Зупинка ${stop.stopOrder}: ${stop.address}"))
                    }

                    // Точка Б (Останній номер, Червоний)
                    val endBitmap = createCustomMarkerBitmap(currentNumber, R.color.driver_error)
                    map.addMarker(MarkerOptions()
                        .position(path.last())
                        .icon(BitmapDescriptorFactory.fromBitmap(endBitmap))
                        .anchor(0.5f, 0.5f)
                        .title("Точка Б: ${order.toAddress}"))


                    // 3. Зумуємо карту
                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }
                    val bounds = builder.build()
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Не вдалося побудувати маршрут", Toast.LENGTH_SHORT).show()
            }
        } else {
            val kiev = LatLng(50.45, 30.52)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(kiev, 12f))
        }
    }
}