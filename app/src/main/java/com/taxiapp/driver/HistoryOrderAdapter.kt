package com.taxiapp.driver

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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
            val density = context.resources.displayMetrics.density
            val secondaryColor = ContextCompat.getColor(context, R.color.driver_text_secondary)
            val primaryColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val cardBgColor = ContextCompat.getColor(context, R.color.driver_black_bg)
            val neonTeal = ContextCompat.getColor(context, R.color.driver_neon_teal)
            val errorRed = ContextCompat.getColor(context, R.color.driver_error_red)
            val blackText = ContextCompat.getColor(context, R.color.driver_text_black)

            // 1. Векторні іконки точок маршруту (із заливкою фону всередині кільця, щоб лінія не просвічувала)
            ivIconFrom?.apply {
                imageTintList = null
                setImageDrawable(OrderAdapter.RoutePinDrawable(hasInnerDot = true, strokeColor = secondaryColor, bgColor = cardBgColor))
            }
            ivIconTo?.apply {
                imageTintList = null
                setImageDrawable(OrderAdapter.RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor, bgColor = cardBgColor))
            }

            // 2. Адреси, ціна та дистанція
            tvFrom.text = order.fromAddress ?: "Адреса не вказана"
            tvTo.text = order.toAddress ?: "Адреса не вказана"
            tvPrice.text = "${order.getTotalFullPrice().toInt()} ₴"
            tvDistance.text = order.getFormattedDistance().replace('.', ',')

            // 3. Бали активності з єдиною іконкою графіка тренду (як в Ефірі та Деталях поїздки)
            val bonus = order.activityBonus
            tvActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"
            val bonusColor = if (bonus >= 0) neonTeal else errorRed
            tvActivityBonus.setTextColor(bonusColor)
            ivActivityIcon?.apply {
                imageTintList = null
                clearColorFilter()
                setImageDrawable(
                    OrderAdapter.UnifiedCardIconDrawable(
                        type = OrderAdapter.UnifiedCardIconDrawable.Type.ACTIVITY_TREND,
                        strokeColor = primaryColor
                    )
                )
            }

            // 4. Плашка ціни та єдина векторна іконка методу оплати (Картка / Гаманець)
            val isCard = (order.paymentMethod == "CARD" || order.paymentMethod == "ELECTRONIC")
            ivPaymentIcon.imageTintList = null
            ivPaymentIcon.clearColorFilter()
            if (isCard) {
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_neon_teal)
                ivPaymentIcon.setImageDrawable(
                    OrderAdapter.UnifiedCardIconDrawable(
                        type = OrderAdapter.UnifiedCardIconDrawable.Type.CARD,
                        strokeColor = blackText
                    )
                )
                tvPrice.setTextColor(blackText)
            } else {
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_surface_elevated)
                ivPaymentIcon.setImageDrawable(
                    OrderAdapter.UnifiedCardIconDrawable(
                        type = OrderAdapter.UnifiedCardIconDrawable.Type.WALLET,
                        strokeColor = primaryColor
                    )
                )
                tvPrice.setTextColor(primaryColor)
            }

            // 5. Форматування дати у форматі "31 серп. 19:47"
            val rawDateStr = order.arrivedAt ?: order.startedAt ?: order.scheduledAt
            tvDate.text = formatArchiveOrderDate(rawDateStr)

            // 6. Проміжні зупинки маршруту — строго по осі X = 11dp (marginStart = 4dp, size = 14dp) та текст з 30dp
            val stopsList = order.stops
            stopsContainer.removeAllViews()
            if (!stopsList.isNullOrEmpty()) {
                stopsContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(context)
                val sortedStops = stopsList.sortedBy { it.stopOrder }
                sortedStops.forEachIndexed { index, stop ->
                    val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)
                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val lineTop = stopView.findViewById<View>(R.id.view_line_top)
                    val lineBottom = stopView.findViewById<View>(R.id.view_line_bottom)

                    lineTop.visibility = View.GONE
                    lineBottom.visibility = View.GONE

                    (ivIcon.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                        width = (14 * density).toInt()
                        height = (14 * density).toInt()
                        marginStart = (4 * density).toInt()
                        topMargin = (4 * density).toInt()
                        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                        topToTop = R.id.tv_point_address
                        ivIcon.layoutParams = this
                    }
                    ivIcon.imageTintList = null
                    ivIcon.setImageDrawable(
                        OrderAdapter.RoutePinDrawable(hasInnerDot = false, strokeColor = secondaryColor, bgColor = cardBgColor)
                    )

                    (tvAddress.layoutParams as? ConstraintLayout.LayoutParams)?.apply {
                        marginStart = (30 * density).toInt()
                        tvAddress.layoutParams = this
                    }
                    tvAddress.setPadding(0, 0, 0, 0)
                    tvAddress.text = stop.address
                    tvAddress.textSize = 16f
                    tvAddress.setTextColor(primaryColor)
                    tvAddress.alpha = 0.9f
                    tvAddress.setTypeface(tvFrom.typeface, Typeface.NORMAL)

                    if (index > 0) {
                        val rowParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (12 * density).toInt()
                        }
                        stopView.layoutParams = rowParams
                    }

                    stopsContainer.addView(stopView)
                }
            } else {
                stopsContainer.visibility = View.GONE
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

    class HistoryDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem == newItem
    }
}