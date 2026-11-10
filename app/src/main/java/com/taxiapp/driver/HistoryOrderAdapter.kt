package com.taxiapp.driver

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.Order
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryOrderAdapter(private val onClick: (Order) -> Unit) :
    ListAdapter<Order, HistoryOrderAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_order, parent, false)
        return HistoryViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(itemView: View, val onClick: (Order) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvFrom: TextView = itemView.findViewById(R.id.tv_from)
        private val tvTo: TextView = itemView.findViewById(R.id.tv_to)
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)

        private val ivIconFrom: ImageView? = itemView.findViewById(R.id.iv_icon_from)
        private val ivIconTo: ImageView? = itemView.findViewById(R.id.iv_icon_to)

        private val ivPaymentIcon: ImageView = itemView.findViewById(R.id.iv_payment_icon)
        private val tvActivityBonus: TextView = itemView.findViewById(R.id.tv_activity_bonus)
        private val ivActivityIcon: ImageView? = itemView.findViewById(R.id.iv_activity_icon)
        private val llPriceBackground: View = itemView.findViewById(R.id.ll_price_background)
        private val stopsContainer: LinearLayout = itemView.findViewById(R.id.ll_stops_container)

        fun bind(order: Order) {
            val context = itemView.context
            val secondaryColor = ContextCompat.getColor(context, R.color.driver_text_secondary)
            val primaryColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val neonTeal = ContextCompat.getColor(context, R.color.driver_neon_teal)
            val errorRed = ContextCompat.getColor(context, R.color.driver_error_red)
            val blackText = ContextCompat.getColor(context, R.color.driver_text_black)

            // 1. Векторні іконки точок маршруту (верхнє кільце з крапкою всередині та нижнє порожнисте кільце)
            ivIconFrom?.apply {
                imageTintList = null
                setImageDrawable(RoutePinDrawable(hasInnerDot = true, strokeColor = secondaryColor))
            }
            ivIconTo?.apply {
                imageTintList = null
                setImageDrawable(RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor))
            }

            // 2. Адреси, ціна та дистанція (з комою як на скріншоті: "13,73 км")
            tvFrom.text = order.fromAddress ?: "Адреса не вказана"
            tvTo.text = order.toAddress ?: "Адреса не вказана"
            tvPrice.text = "${order.getTotalFullPrice().toInt()} ₴"
            tvDistance.text = order.getFormattedDistance().replace('.', ',')

            // 3. Бали активності
            val bonus = order.activityBonus
            tvActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"
            val bonusColor = if (bonus >= 0) neonTeal else errorRed
            tvActivityBonus.setTextColor(bonusColor)
            ivActivityIcon?.setColorFilter(bonusColor)

            // 4. Плашка ціни та іконка методу оплати
            if (order.paymentMethod == "CARD" || order.paymentMethod == "ELECTRONIC") {
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_neon_teal)
                ivPaymentIcon.imageTintList = android.content.res.ColorStateList.valueOf(blackText)
                tvPrice.setTextColor(blackText)
            } else {
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_surface_elevated)
                ivPaymentIcon.imageTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                tvPrice.setTextColor(primaryColor)
            }

            // 5. Форматування дати у форматі "31 серп. 19:47"
            val rawDateStr = order.arrivedAt ?: order.startedAt ?: order.scheduledAt
            tvDate.text = formatArchiveOrderDate(rawDateStr)

            // 6. Проміжні зупинки маршруту (виносимо stopsList у локальну val)
            val stopsList = order.stops
            stopsContainer.removeAllViews()
            if (!stopsList.isNullOrEmpty()) {
                val inflater = LayoutInflater.from(context)
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
                    ivIcon.setImageDrawable(RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor))
                    lineTop.visibility = View.GONE
                    lineBottom.visibility = View.GONE

                    stopsContainer.addView(stopView)
                }
            }

            itemView.setOnClickListener { onClick(order) }
        }

        private fun formatArchiveOrderDate(raw: String?): String {
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
    }

    private class RoutePinDrawable(
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

    class HistoryDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem == newItem
    }
}