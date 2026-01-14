package com.taxiapp.driver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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

class OrderOfferActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private var timer: CountDownTimer? = null
    private lateinit var tvTimer: TextView
    private lateinit var btnAccept: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var routeContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_offer)

        // Отримання об'єкта замовлення
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

    private fun initViews() {
        tvTimer = findViewById(R.id.tv_timer)
        btnAccept = findViewById(R.id.btn_accept)
        btnSkip = findViewById(R.id.btn_skip)
        routeContainer = findViewById(R.id.ll_route_container)

        findViewById<View>(R.id.btn_back_card).visibility = View.GONE // Ховаємо кнопку назад, тут тільки Skip

        btnAccept.setOnClickListener { acceptOrder() }
        btnSkip.setOnClickListener { rejectOrder() }
    }

    private fun startTimer() {
        // 20 секунд
        timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = seconds.toString()

                // Якщо залишилось мало часу - червоний колір
                if (seconds <= 5) {
                    tvTimer.setTextColor(Color.RED)
                    tvTimer.backgroundTintList = ColorStateList.valueOf(Color.RED)
                }
            }

            override fun onFinish() {
                tvTimer.text = "0"
                rejectOrder() // Час вийшов = відмова
            }
        }.start()
    }

    private fun acceptOrder() {
        timer?.cancel()
        val orderId = currentOrder?.id ?: return

        btnAccept.isEnabled = false
        btnAccept.text = "ОБРОБКА..."

        lifecycleScope.launch {
            try {
                // Використовуємо той самий API метод acceptOrder
                val response = ApiClient.getInstance().getApiService(this@OrderOfferActivity).acceptOrder(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val updatedOrder = response.body()!!
                    Toast.makeText(this@OrderOfferActivity, "Замовлення прийнято!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@OrderOfferActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", updatedOrder)
                    startActivity(intent)
                    finish()
                } else {
                    // Якщо хтось перехопив або час вийшов на сервері
                    Toast.makeText(this@OrderOfferActivity, "Не вдалося прийняти: ${response.code()}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderOfferActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun rejectOrder() {
        timer?.cancel()
        val orderId = currentOrder?.id ?: return

        lifecycleScope.launch {
            try {
                // Викликаємо новий метод rejectOffer (потрібно додати в ApiService)
                ApiClient.getInstance().getApiService(this@OrderOfferActivity).rejectOffer(orderId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                finish() // Закриваємо екран
            }
        }
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.tv_order_id).text = "Нове замовлення #${currentOrder?.id}"
        findViewById<TextView>(R.id.tv_tariff).text = currentOrder?.tariffName ?: "Стандарт"

        val distInfo = "${currentOrder?.getFormattedDistance()} • ${currentOrder?.getPricePerKm()}"
        findViewById<TextView>(R.id.tv_distance_info).text = distInfo
        findViewById<TextView>(R.id.tv_price).text = currentOrder?.getFormattedPrice()

        setupPaymentMethod()
        buildRouteList()
        setupServices()
        setupComment()
    }

    // --- (Методи setupPaymentMethod, setupServices, setupComment, buildRouteList ідентичні OrderDetailsActivity) ---
    // Я їх скоротив тут для зручності, скопіюй їх з OrderDetailsActivity або використовуй спільний Helper

    private fun setupPaymentMethod() {
        val llPriceBg = findViewById<LinearLayout>(R.id.ll_price_background)
        val ivIcon = findViewById<ImageView>(R.id.iv_payment_icon)
        if (currentOrder?.paymentMethod == "CASH") {
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
        if (!currentOrder?.services.isNullOrEmpty()) {
            servicesBlock.visibility = View.VISIBLE
            servicesList.removeAllViews()
            currentOrder?.services?.forEach {
                val tv = TextView(this)
                tv.text = "• ${it.name}"
                tv.setTextColor(Color.WHITE)
                servicesList.addView(tv)
            }
        } else {
            servicesBlock.visibility = View.GONE
        }
    }

    private fun setupComment() {
        val commentBlock = findViewById<LinearLayout>(R.id.ll_comment_block)
        val tvComment = findViewById<TextView>(R.id.tv_comment_text)
        if (!currentOrder?.comment.isNullOrEmpty()) {
            commentBlock.visibility = View.VISIBLE
            tvComment.text = currentOrder?.comment
        } else {
            commentBlock.visibility = View.GONE
        }
    }

    private fun buildRouteList() {
        routeContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val order = currentOrder ?: return

        // Start
        addPointView(inflater, order.fromAddress ?: "Точка А", R.drawable.ic_circle_green, false)
        // Stops
        order.stops?.sortedBy { it.stopOrder }?.forEach {
            addPointView(inflater, it.address ?: "Зупинка", R.drawable.ic_circle_green, false)
        }
        // End
        addPointView(inflater, order.toAddress ?: "Точка Б", R.drawable.ic_circle_red, true)
    }

    private fun addPointView(inflater: LayoutInflater, address: String, iconRes: Int, isLast: Boolean) {
        val view = inflater.inflate(R.layout.item_route_point, routeContainer, false)
        view.findViewById<TextView>(R.id.tv_point_address).text = address
        view.findViewById<ImageView>(R.id.iv_point_icon).setImageResource(iconRes)
        view.findViewById<View>(R.id.view_line).visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        routeContainer.addView(view)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (e: Exception) {}

        val order = currentOrder ?: return
        val polylineString = order.polyline

        if (!polylineString.isNullOrEmpty()) {
            val path = PolyUtil.decode(polylineString)
            map.addPolyline(PolylineOptions().addAll(path).width(12f).color(ContextCompat.getColor(this, R.color.driver_neon_teal)))

            if (path.isNotEmpty()) {
                val bounds = LatLngBounds.Builder()
                path.forEach { bounds.include(it) }
                // Трохи більше відступів, щоб влізло під таймер і нижню панель
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    override fun onBackPressed() {
        // Забороняємо вихід кнопкою назад, тільки Skip
        // super.onBackPressed()
        Toast.makeText(this, "Натисніть 'Пропустити', щоб відхилити", Toast.LENGTH_SHORT).show()
    }
}