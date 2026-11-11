package com.taxiapp.driver

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.Order
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class OrderAdapter(
    private val onItemClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val orders = mutableListOf<Order>()
    private var isSectorFirst: Boolean = false
    private var isPricePerKmHidden: Boolean = false
    private var driverLat: Double? = null
    private var driverLng: Double? = null

    fun submitList(newOrders: List<Order>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = orders.size
            override fun getNewListSize(): Int = newOrders.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return orders[oldItemPosition].id == newOrders[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = orders[oldItemPosition]
                val newItem = newOrders[newItemPosition]
                return oldItem.price == newItem.price &&
                        oldItem.getTotalFullPrice() == newItem.getTotalFullPrice() &&
                        oldItem.status == newItem.status &&
                        oldItem.paymentMethod == newItem.paymentMethod &&
                        oldItem.fromAddress == newItem.fromAddress &&
                        oldItem.toAddress == newItem.toAddress &&
                        oldItem.fromSector == newItem.fromSector &&
                        oldItem.toSector == newItem.toSector &&
                        oldItem.activityBonus == newItem.activityBonus &&
                        oldItem.scheduledAt == newItem.scheduledAt &&
                        oldItem.stops?.size == newItem.stops?.size
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        orders.clear()
        orders.addAll(newOrders)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateDisplaySettings(sectorFirst: Boolean, hidePricePerKm: Boolean) {
        this.isSectorFirst = sectorFirst
        this.isPricePerKmHidden = hidePricePerKm
        notifyDataSetChanged()
    }

    fun updateDriverLocation(lat: Double?, lng: Double?) {
        if (this.driverLat != lat || this.driverLng != lng) {
            this.driverLat = lat
            this.driverLng = lng
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = orders.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        private val llPriceBg: LinearLayout = itemView.findViewById(R.id.ll_price_background)
        private val ivPaymentIcon: ImageView = itemView.findViewById(R.id.iv_payment_icon)
        private val tvPricePerKm: TextView = itemView.findViewById(R.id.tv_price_per_km)

        private val ivIconFrom: ImageView? = itemView.findViewById(R.id.iv_icon_from)
        private val ivIconTo: ImageView? = itemView.findViewById(R.id.iv_icon_to)

        private val tvFrom: TextView = itemView.findViewById(R.id.tv_address_from)
        private val tvTo: TextView = itemView.findViewById(R.id.tv_address_to)
        private val tvSectorFrom: TextView = itemView.findViewById(R.id.tv_sector_from)
        private val tvSectorTo: TextView = itemView.findViewById(R.id.tv_sector_to)
        private val stopsContainer: LinearLayout = itemView.findViewById(R.id.ll_stops_container)

        private val tvPickupDistance: TextView? = itemView.findViewById(R.id.tv_pickup_distance)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
        private val tvDistanceUnit: TextView? = itemView.findViewById(R.id.tv_distance_unit)

        private val llTariffSegment: View? = itemView.findViewById(R.id.ll_tariff_segment)
        private val ivTariffCar: ImageView? = itemView.findViewById(R.id.iv_tariff_car)
        private val tvTariff: TextView = itemView.findViewById(R.id.tv_tariff_badge)

        private val llActivitySegment: View? = itemView.findViewById(R.id.ll_activity_segment)
        private val ivActivityIcon: ImageView? = itemView.findViewById(R.id.iv_activity_icon)
        private val tvActivityBonus: TextView = itemView.findViewById(R.id.tv_activity_bonus)
        private val tvScheduledTime: TextView = itemView.findViewById(R.id.tv_scheduled_time)

        fun bind(order: Order) {
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val primaryColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val secondaryColor = ContextCompat.getColor(context, R.color.driver_text_secondary)
            val cardBgColor = ContextCompat.getColor(context, R.color.driver_black_bg)
            val neonTeal = ContextCompat.getColor(context, R.color.driver_neon_teal)
            val errorRed = ContextCompat.getColor(context, R.color.driver_error_red)
            val darkText = Color.parseColor("#1A1C1E")

            // 1. Єдині векторні іконки точок маршруту як у картці Архіву (RoutePinDrawable)
            ivIconFrom?.apply {
                imageTintList = null
                setImageDrawable(RoutePinDrawable(hasInnerDot = true, strokeColor = secondaryColor, bgColor = cardBgColor))
            }
            ivIconTo?.apply {
                imageTintList = null
                setImageDrawable(RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor, bgColor = cardBgColor))
            }

            // 2. Повна сумарна вартість поїздки та ціна за кілометр
            val fullPrice = order.getTotalFullPrice()
            tvPrice.text = "${fullPrice.toInt()} ₴"

            if (isPricePerKmHidden) {
                tvPricePerKm.visibility = View.GONE
            } else {
                tvPricePerKm.visibility = View.VISIBLE
                val meters = order.distanceMeters ?: 0
                if (meters >= 1000) {
                    val km = meters / 1000.0
                    val calculatedPricePerKm = fullPrice / km
                    tvPricePerKm.text = String.format(Locale.US, "%.2f ₴/км", calculatedPricePerKm)
                } else if (meters > 0) {
                    tvPricePerKm.text = String.format(Locale.US, "%.2f ₴/км", fullPrice)
                } else {
                    tvPricePerKm.text = "- ₴/км"
                }
            }

            // 3. Плашка ціни та єдина векторна іконка методу оплати (Гаманець для готівки, Картка для безготівки)
            val method = order.paymentMethod ?: "CASH"
            val priceBadgeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                if (method == "CARD" || method == "ELECTRONIC") {
                    setColor(Color.parseColor("#28C2E1"))
                } else {
                    setColor(neonTeal)
                }
            }
            llPriceBg.backgroundTintList = null
            llPriceBg.background = priceBadgeBg

            ivPaymentIcon.imageTintList = null
            ivPaymentIcon.clearColorFilter()
            ivPaymentIcon.setImageDrawable(
                UnifiedCardIconDrawable(
                    type = if (method == "CARD" || method == "ELECTRONIC") UnifiedCardIconDrawable.Type.CARD else UnifiedCardIconDrawable.Type.WALLET,
                    strokeColor = darkText
                )
            )
            tvPrice.setTextColor(darkText)

            // 4. Відображення адрес та секторів з урахуванням налаштування "Спочатку сектор"
            if (isSectorFirst) {
                if (!order.fromSector.isNullOrEmpty()) {
                    tvFrom.text = order.fromSector
                    tvSectorFrom.text = order.fromAddress
                    tvSectorFrom.visibility = View.VISIBLE
                } else {
                    tvFrom.text = order.fromAddress
                    tvSectorFrom.visibility = View.GONE
                }
                if (!order.toSector.isNullOrEmpty()) {
                    tvTo.text = order.toSector
                    tvSectorTo.text = order.toAddress
                    tvSectorTo.visibility = View.VISIBLE
                } else {
                    tvTo.text = order.toAddress
                    tvSectorTo.visibility = View.GONE
                }
            } else {
                tvFrom.text = order.fromAddress ?: "Адреса не вказана"
                if (!order.fromSector.isNullOrEmpty()) {
                    tvSectorFrom.text = order.fromSector
                    tvSectorFrom.visibility = View.VISIBLE
                } else {
                    tvSectorFrom.visibility = View.GONE
                }
                tvTo.text = order.toAddress ?: "Адреса не вказана"
                if (!order.toSector.isNullOrEmpty()) {
                    tvSectorTo.text = order.toSector
                    tvSectorTo.visibility = View.VISIBLE
                } else {
                    tvSectorTo.visibility = View.GONE
                }
            }

            // 5. Ліва колонка дистанцій: [подача 2.8 км / - км] зверху та [маршрут 2.6 км] знизу
            if (order.isScheduled()) {
                tvPickupDistance?.text = "-"
            } else {
                val dLat = driverLat
                val dLng = driverLng
                val oLat = order.originLat ?: 0.0
                val oLng = order.originLng ?: 0.0
                if (dLat != null && dLng != null && dLat != 0.0 && oLat != 0.0) {
                    val res = FloatArray(1)
                    Location.distanceBetween(dLat, dLng, oLat, oLng, res)
                    val roadKm = ((res[0] / 1000.0) * 1.28).coerceAtLeast(0.1)
                    tvPickupDistance?.text = String.format(Locale.US, "%.1f", roadKm)
                } else {
                    tvPickupDistance?.text = "-"
                }
            }

            val routeKm = (order.distanceMeters ?: 0) / 1000.0
            if (tvDistanceUnit != null) {
                tvDistance.text = if (routeKm > 0.0) String.format(Locale.US, "%.1f", routeKm) else "-"
                tvDistanceUnit.visibility = View.VISIBLE
            } else {
                tvDistance.text = String.format(Locale.US, "%.1f км", routeKm)
            }
            tvDistance.setTextColor(primaryColor)

            // 6. Проміжні зупинки маршруту строго по осі X = 50dp (marginStart = 42dp, size = 16dp) та текст з 68dp
            stopsContainer.removeAllViews()
            if (!order.stops.isNullOrEmpty()) {
                stopsContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(context)
                val sortedStops = order.stops.sortedBy { it.stopOrder }
                sortedStops.forEachIndexed { index, stop ->
                    val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)
                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val lineTop = stopView.findViewById<View>(R.id.view_line_top)
                    val lineBottom = stopView.findViewById<View>(R.id.view_line_bottom)

                    lineTop.visibility = View.GONE
                    lineBottom.visibility = View.GONE

                    (ivIcon.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                        width = (16 * density).toInt()
                        height = (16 * density).toInt()
                        marginStart = (42 * density).toInt()
                        topMargin = (4 * density).toInt()
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        topToTop = R.id.tv_point_address
                        ivIcon.layoutParams = this
                    }
                    ivIcon.imageTintList = null
                    ivIcon.clearColorFilter()
                    ivIcon.setImageDrawable(RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor, bgColor = cardBgColor))

                    (tvAddress.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                        marginStart = (68 * density).toInt()
                        tvAddress.layoutParams = this
                    }
                    tvAddress.setPadding(0, 0, 0, 0)
                    tvAddress.text = stop.address
                    tvAddress.textSize = 17f
                    tvAddress.setTextColor(primaryColor)
                    tvAddress.setTypeface(tvFrom.typeface, Typeface.NORMAL)

                    if (index > 0) {
                        val rowParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (8 * density).toInt()
                        }
                        stopView.layoutParams = rowParams
                    }

                    stopsContainer.addView(stopView)
                }
            } else {
                stopsContainer.visibility = View.GONE
            }

            // 7. Нижня стрічка сегментів: [ 🚗 Стандарт | 📈 +3 | Завтра, 05:40 ]
            tvTariff.text = order.tariffName ?: "Стандарт"
            ivTariffCar?.apply {
                imageTintList = null
                clearColorFilter()
                setImageDrawable(UnifiedCardIconDrawable(UnifiedCardIconDrawable.Type.CAR_FRONT, primaryColor))
            }

            val bonus = order.activityBonus
            tvActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"
            val bonusColor = if (bonus >= 0) neonTeal else errorRed
            tvActivityBonus.setTextColor(bonusColor)

            // Єдина іконка активності як в історії/архіві (стовпчики + стрілка тренду вгору)
            ivActivityIcon?.apply {
                imageTintList = null
                clearColorFilter()
                setImageDrawable(UnifiedCardIconDrawable(UnifiedCardIconDrawable.Type.ACTIVITY_TREND, primaryColor))
            }

            val isScheduled = order.isScheduled()
            if (isScheduled) {
                val scheduledDate = order.getScheduledDate()
                if (scheduledDate != null) {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val orderDay = Calendar.getInstance().apply {
                        time = scheduledDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val diffMillis = orderDay.timeInMillis - today.timeInMillis
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
                    val timeStr = timeFormat.format(scheduledDate)

                    val displayStr = when {
                        diffDays <= 0 -> timeStr
                        diffDays == 1 -> "Завтра, $timeStr"
                        else -> {
                            val ukrLocale = Locale("uk")
                            val dateFormat = SimpleDateFormat("d MMM, HH:mm", ukrLocale)
                            dateFormat.format(scheduledDate)
                        }
                    }
                    tvScheduledTime.text = displayStr
                } else {
                    val timeOnly = try {
                        order.scheduledAt?.substring(11, 16) ?: ""
                    } catch (e: Exception) { "" }
                    tvScheduledTime.text = timeOnly
                }
                tvScheduledTime.visibility = View.VISIBLE
            } else {
                tvScheduledTime.visibility = View.GONE
            }

            // Акуратне скруглення нижніх бейджів під форму картки
            val darkBadgeColor = Color.parseColor("#26272B")
            llTariffSegment?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(darkBadgeColor)
                val rBottomLeft = 14f * density
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, rBottomLeft, rBottomLeft)
            }
            llActivitySegment?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(darkBadgeColor)
                val rTopRight = if (isScheduled) 0f else 6f * density
                cornerRadii = floatArrayOf(0f, 0f, rTopRight, rTopRight, 0f, 0f, 0f, 0f)
            }
            if (isScheduled) {
                tvScheduledTime.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#F5D033"))
                    val rTopRight = 6f * density
                    cornerRadii = floatArrayOf(0f, 0f, rTopRight, rTopRight, 0f, 0f, 0f, 0f)
                }
            }

            itemView.setOnClickListener {
                onItemClick(order)
            }
        }
    }

    /**
     * Точні векторні піни маршруту (кільце з точкою для Початку та пусте кільце для Кінця/Зупинок),
     * на 100% ідентичні HistoryOrderAdapter та HistoryDetailsActivity.
     */
    class RoutePinDrawable(
        private val hasInnerDot: Boolean,
        private val strokeColor: Int,
        private val bgColor: Int
    ) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
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
            canvas.drawCircle(cx, cy, r * 0.80f, bgPaint)
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

    /**
     * Єдина система векторних іконок для карток Ефіру, Деталей поїздки та Архіву:
     * - WALLET: гаманець із застібкою (готівка)
     * - CARD: банківська картка зі смугою та чіпом (картка)
     * - ACTIVITY_TREND: графік стовпчиків зі стрілкою росту вгору (активність)
     * - CAR_FRONT: силует авто спереду (клас авто)
     */
    class UnifiedCardIconDrawable(
        private val type: Type,
        private val strokeColor: Int
    ) : Drawable() {

        enum class Type { WALLET, CARD, ACTIVITY_TREND, CAR_FRONT }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
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
                Type.WALLET -> {
                    val body = RectF(3.0f, 5.2f, 20.5f, 18.8f)
                    canvas.drawRoundRect(body, 2.6f, 2.6f, strokePaint)
                    val clasp = RectF(14.8f, 9.6f, 21.0f, 14.4f)
                    canvas.drawRoundRect(clasp, 1.8f, 1.8f, strokePaint)
                    canvas.drawCircle(17.4f, 12.0f, 1.1f, fillPaint)
                }

                Type.CARD -> {
                    val card = RectF(2.5f, 5.2f, 21.5f, 18.8f)
                    canvas.drawRoundRect(card, 2.5f, 2.5f, strokePaint)
                    canvas.drawLine(2.5f, 9.5f, 21.5f, 9.5f, strokePaint)
                    val chip = RectF(5.5f, 13.5f, 9.5f, 15.6f)
                    canvas.drawRoundRect(chip, 0.8f, 0.8f, fillPaint)
                }

                Type.ACTIVITY_TREND -> {
                    strokePaint.strokeWidth = 1.9f
                    canvas.drawLine(6f, 19f, 6f, 15f, strokePaint)
                    canvas.drawLine(10f, 19f, 10f, 13f, strokePaint)
                    canvas.drawLine(14f, 19f, 14f, 11f, strokePaint)
                    canvas.drawLine(18f, 19f, 18f, 9f, strokePaint)

                    val trendPath = Path().apply {
                        moveTo(5f, 12f)
                        lineTo(10f, 7.5f)
                        lineTo(13.5f, 10f)
                        lineTo(18.5f, 5f)
                    }
                    canvas.drawPath(trendPath, strokePaint)
                    canvas.drawLine(14.8f, 5f, 18.5f, 5f, strokePaint)
                    canvas.drawLine(18.5f, 5f, 18.5f, 8.7f, strokePaint)
                }

                Type.CAR_FRONT -> {
                    strokePaint.strokeWidth = 1.8f
                    val roof = Path().apply {
                        moveTo(6.5f, 6.0f)
                        lineTo(17.5f, 6.0f)
                        lineTo(20.0f, 11.0f)
                        lineTo(4.0f, 11.0f)
                        close()
                    }
                    canvas.drawPath(roof, strokePaint)
                    val bumper = RectF(3.2f, 11.0f, 20.8f, 16.5f)
                    canvas.drawRoundRect(bumper, 2.0f, 2.0f, strokePaint)
                    canvas.drawLine(5.2f, 16.5f, 5.2f, 19.2f, strokePaint)
                    canvas.drawLine(18.8f, 16.5f, 18.8f, 19.2f, strokePaint)
                    canvas.drawCircle(6.8f, 13.8f, 1.2f, fillPaint)
                    canvas.drawCircle(17.2f, 13.8f, 1.2f, fillPaint)
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