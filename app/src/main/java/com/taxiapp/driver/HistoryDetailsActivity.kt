package com.taxiapp.driver

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.databinding.ActivityHistoryDetailsBinding
import com.taxiapp.driver.network.Order
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHistoryDetailsBinding
    private lateinit var map: GoogleMap
    private lateinit var order: Order

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoryDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val headerInner = findViewById<View>(R.id.header_history_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (14 * density).toInt(),
                    (16 * density).toInt(),
                    (14 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        val receivedOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (receivedOrder == null) {
            Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        order = receivedOrder

        binding.btnBack.setOnClickListener { finish() }

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_detail) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupUI() {
        val currentOrder = order
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val errorRed = ContextCompat.getColor(this, R.color.driver_error_red)
        val blackText = ContextCompat.getColor(this, R.color.driver_text_black)

        // 1. Векторні іконки точок маршруту та іконки секцій ("Транзакції в замовленні" і "Бали за замовлення")
        findViewById<ImageView>(R.id.iv_detail_from_pin)?.apply {
            imageTintList = null
            setImageDrawable(DetailRoutePinDrawable(hasInnerDot = true, strokeColor = secondaryColor))
        }
        findViewById<ImageView>(R.id.iv_detail_to_pin)?.apply {
            imageTintList = null
            setImageDrawable(DetailRoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor))
        }
        findViewById<ImageView>(R.id.iv_finance_wallet_icon)?.apply {
            imageTintList = null
            setImageDrawable(DetailSectionIconDrawable(isWallet = true, strokeColor = secondaryColor))
        }
        findViewById<ImageView>(R.id.iv_activity_trend_icon)?.apply {
            imageTintList = null
            setImageDrawable(DetailSectionIconDrawable(isWallet = false, strokeColor = secondaryColor))
        }

        // 2. Адреси, ціна, дистанція та дата
        val fullPrice = currentOrder.getTotalFullPrice()
        binding.tvPriceDetail.text = "${fullPrice.toInt()} ₴"
        binding.tvDistanceDetail.text = currentOrder.getFormattedDistance().replace('.', ',')
        binding.tvFromDetail.text = currentOrder.fromAddress ?: "---"
        binding.tvToDetail.text = currentOrder.toAddress ?: "---"

        binding.tvTariffName.text = currentOrder.tariffName ?: "Standard"
        binding.tvCarModel.text = currentOrder.carModel ?: "Авто"
        binding.tvCarPlate.text = currentOrder.carPlate ?: "---"
        binding.tvCarClass.text = currentOrder.tariffName ?: "Standard"

        val rawDate = currentOrder.arrivedAt ?: currentOrder.startedAt ?: currentOrder.scheduledAt
        findViewById<TextView>(R.id.tv_date_detail)?.text = formatOrderDetailDate(rawDate)

        // Проміжні зупинки маршруту (виносимо в локальну val stopsList для Smart Cast)
        val stopsList = currentOrder.stops
        val stopsContainer = findViewById<android.widget.LinearLayout>(R.id.ll_detail_stops_container)
        stopsContainer?.removeAllViews()
        if (!stopsList.isNullOrEmpty() && stopsContainer != null) {
            val inflater = LayoutInflater.from(this)
            val sortedStops = stopsList.sortedBy { it.stopOrder }
            for (stop in sortedStops) {
                val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)
                val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                val lineTop = stopView.findViewById<View>(R.id.view_line_top)
                val lineBottom = stopView.findViewById<View>(R.id.view_line_bottom)

                tvAddress.text = stop.address
                tvAddress.textSize = 15f
                ivIcon.imageTintList = null
                ivIcon.setImageDrawable(DetailRoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor))
                lineTop.visibility = View.GONE
                lineBottom.visibility = View.GONE

                stopsContainer.addView(stopView)
            }
        }

        // 3. Колір плашки ціни та іконка оплати
        val llPriceBadge = findViewById<View>(R.id.ll_price_detail_badge)
        val ivPayIcon = findViewById<ImageView>(R.id.iv_payment_detail_icon)
        if (currentOrder.paymentMethod == "CARD" || currentOrder.paymentMethod == "ELECTRONIC") {
            ivPayIcon?.setImageResource(R.drawable.ic_payment_card)
            llPriceBadge?.backgroundTintList = ColorStateList.valueOf(neonTeal)
            ivPayIcon?.imageTintList = ColorStateList.valueOf(blackText)
            binding.tvPriceDetail.setTextColor(blackText)
        } else {
            ivPayIcon?.setImageResource(R.drawable.ic_payment_cash)
            llPriceBadge?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_surface_elevated))
            ivPayIcon?.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.tvPriceDetail.setTextColor(primaryColor)
        }

        // 4. Короткий ID замовлення ("****0933e4", виносимо idLong в локальну val для Smart Cast)
        val fullUuid = currentOrder.id
        val idLongVal = currentOrder.idLong
        val shortUuid = when {
            idLongVal != null && idLongVal > 0L -> String.format(Locale.US, "%06x", idLongVal)
            fullUuid.length >= 6 -> fullUuid.takeLast(6)
            else -> fullUuid
        }
        binding.tvOrderUuidShort.text = "****$shortUuid"

        // 5. Блок "Зміни Активності" ("Бали за замовлення")
        val activityBonus = currentOrder.activityBonus
        val formattedScore = when {
            activityBonus > 0 -> "+$activityBonus"
            activityBonus < 0 -> activityBonus.toString()
            else -> "0"
        }
        binding.tvActivityScore.text = formattedScore
        findViewById<TextView>(R.id.tv_activity_score_breakdown)?.apply {
            text = formattedScore
            setTextColor(if (activityBonus >= 0) neonTeal else errorRed)
        }
        findViewById<TextView>(R.id.tv_activity_subtitle)?.text = when {
            activityBonus > 0 -> "Нараховано за виконання замовлення"
            activityBonus < 0 -> "Списано за скасування замовлення"
            else -> "Без змін активності"
        }

        val layoutActivityHeader = findViewById<View>(R.id.layout_activity_header)
        val layoutActivityDetails = findViewById<View>(R.id.layout_activity_details)
        val ivActivityChevron = findViewById<ImageView>(R.id.iv_activity_chevron)
        layoutActivityHeader?.setOnClickListener {
            if (layoutActivityDetails?.visibility == View.VISIBLE) {
                layoutActivityDetails.visibility = View.GONE
                ivActivityChevron?.animate()?.rotation(90f)?.setDuration(200)?.start()
            } else {
                layoutActivityDetails?.visibility = View.VISIBLE
                ivActivityChevron?.animate()?.rotation(270f)?.setDuration(200)?.start()
            }
        }

        // 6. Блок "Рух коштів на балансі" ("Транзакції в замовленні")
        val serviceCommission = currentOrder.serviceCommission
        val amountToBalance = currentOrder.amountToBalance
        val bankCommission = currentOrder.bankCommission
        val transferToCard = currentOrder.transferToCard

        binding.tvLabelFinance.visibility = View.VISIBLE
        binding.layoutFinanceContainer.visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_finance_base_price)?.text = "${fullPrice.toInt()} ₴"

        if (serviceCommission != null) {
            binding.rowServiceCommission.visibility = View.VISIBLE
            binding.tvServiceCommission.text = "-${serviceCommission.toInt()} ₴"
        } else {
            binding.rowServiceCommission.visibility = View.GONE
        }

        if (amountToBalance != null) {
            binding.rowAmountToBalance.visibility = View.VISIBLE
            binding.tvAmountToBalance.text = "+${amountToBalance.toInt()} ₴"
        } else {
            binding.rowAmountToBalance.visibility = View.GONE
        }

        if (bankCommission != null) {
            binding.rowBankCommission.visibility = View.VISIBLE
            binding.tvBankCommission.text = "-${String.format(Locale.US, "%.2f", bankCommission)} ₴"
        } else {
            binding.rowBankCommission.visibility = View.GONE
        }

        if (transferToCard != null) {
            binding.rowTransferToCard.visibility = View.VISIBLE
            binding.tvTransferToCard.text = "${String.format(Locale.US, "%.2f", transferToCard)} ₴"
        } else {
            binding.rowTransferToCard.visibility = View.GONE
        }

        binding.layoutFinanceHeader.setOnClickListener {
            if (binding.layoutFinanceDetails.visibility == View.VISIBLE) {
                binding.layoutFinanceDetails.visibility = View.GONE
                binding.ivFinanceChevron.animate().rotation(90f).setDuration(200).start()
            } else {
                binding.layoutFinanceDetails.visibility = View.VISIBLE
                binding.ivFinanceChevron.animate().rotation(270f).setDuration(200).start()
            }
        }
    }

    private fun formatOrderDetailDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "---"
        val clean = raw.trim()
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        var parsed: Date? = null
        for (p in patterns) {
            try {
                parsed = SimpleDateFormat(p, Locale.US).parse(clean.take(26))
                if (parsed != null) break
            } catch (_: Exception) {}
        }
        if (parsed == null) return clean.take(16).replace('T', ' ')

        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(parsed)
        val monthsShort = arrayOf(
            "січ.", "лют.", "бер.", "квіт.", "трав.", "черв.",
            "лип.", "серп.", "вер.", "жовт.", "лист.", "груд."
        )
        val monthName = monthsShort.getOrElse(monthIdx) { "" }
        return "$day $monthName $timeStr"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val currentOrder = order

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
            val isNightMode = when (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

            val styleRes = if (isNightMode) R.raw.map_style_dark else R.raw.map_style_standard
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
        } catch (e: Exception) {
            android.util.Log.e("UNIT_MAP", "Map style load error", e)
        }

        val polylineString = currentOrder.polyline

        if (!polylineString.isNullOrEmpty()) {
            try {
                val path: List<LatLng> = PolyUtil.decode(polylineString)

                map.addPolyline(
                    PolylineOptions()
                        .addAll(path)
                        .width(12f)
                        .color(Color.parseColor("#F5D033"))
                        .geodesic(true)
                )

                if (path.isNotEmpty()) {
                    var currentNumber = 1

                    val startBitmap = createCustomMarkerBitmap(currentNumber++, R.color.driver_neon_teal)
                    map.addMarker(
                        MarkerOptions()
                            .position(path.first())
                            .icon(BitmapDescriptorFactory.fromBitmap(startBitmap))
                            .anchor(0.5f, 0.5f)
                            .zIndex(2f)
                    )

                    val stopsList = currentOrder.stops
                    stopsList?.sortedBy { it.stopOrder }?.forEach { stop ->
                        val stopBitmap = createCustomMarkerBitmap(currentNumber++, R.color.taxi_yellow)
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(stop.lat, stop.lng))
                                .icon(BitmapDescriptorFactory.fromBitmap(stopBitmap))
                                .anchor(0.5f, 0.5f)
                                .zIndex(2f)
                        )
                    }

                    val endBitmap = createCustomMarkerBitmap(currentNumber, R.color.activity_green)
                    map.addMarker(
                        MarkerOptions()
                            .position(path.last())
                            .icon(BitmapDescriptorFactory.fromBitmap(endBitmap))
                            .anchor(0.5f, 0.5f)
                            .zIndex(2f)
                    )

                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }

                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 110))
                    } catch (e: Exception) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(path.first(), 13f))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                drawFallbackMarkers()
            }
        } else {
            drawFallbackMarkers()
        }
    }

    private fun drawFallbackMarkers() {
        val currentOrder = order
        val origin = LatLng(currentOrder.originLat ?: 0.0, currentOrder.originLng ?: 0.0)
        val dest = LatLng(currentOrder.destLat ?: 0.0, currentOrder.destLng ?: 0.0)

        if (origin.latitude == 0.0 && dest.latitude == 0.0) return

        val startBitmap = createCustomMarkerBitmap(1, R.color.driver_neon_teal)
        val endBitmap = createCustomMarkerBitmap(2, R.color.activity_green)

        map.addMarker(MarkerOptions().position(origin).icon(BitmapDescriptorFactory.fromBitmap(startBitmap)).anchor(0.5f, 0.5f))
        map.addMarker(MarkerOptions().position(dest).icon(BitmapDescriptorFactory.fromBitmap(endBitmap)).anchor(0.5f, 0.5f))

        val builder = LatLngBounds.Builder()
        builder.include(origin)
        builder.include(dest)
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 110))
        } catch (e: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13f))
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

    private class DetailRoutePinDrawable(
        private val hasInnerDot: Boolean,
        private val strokeColor: Int
    ) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = minOf(b.width(), b.height()) / 2f

            strokePaint.strokeWidth = (r * 0.26f).coerceAtLeast(1.8f)
            canvas.drawCircle(cx, cy, r * 0.80f, strokePaint)

            if (hasInnerDot) {
                canvas.drawCircle(cx, cy, r * 0.28f, fillPaint)
            }
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

    private class DetailSectionIconDrawable(
        private val isWallet: Boolean,
        private val strokeColor: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
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
            paint.strokeWidth = 1.9f

            if (isWallet) {
                val rect = RectF(3.5f, 5.5f, 20.5f, 18.5f)
                canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                val clasp = RectF(15.2f, 10.0f, 20.5f, 14.0f)
                canvas.drawRoundRect(clasp, 1.6f, 1.6f, paint)
            } else {
                canvas.drawLine(6f, 19f, 6f, 15f, paint)
                canvas.drawLine(10f, 19f, 10f, 13f, paint)
                canvas.drawLine(14f, 19f, 14f, 11f, paint)
                canvas.drawLine(18f, 19f, 18f, 9f, paint)

                val trendPath = Path().apply {
                    moveTo(5f, 12f)
                    lineTo(10f, 7.5f)
                    lineTo(13.5f, 10f)
                    lineTo(18.5f, 5f)
                }
                canvas.drawPath(trendPath, paint)
                canvas.drawLine(14.8f, 5f, 18.5f, 5f, paint)
                canvas.drawLine(18.5f, 5f, 18.5f, 8.7f, paint)
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}