package com.taxiapp.driver

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.Order

class OrderAdapter(
    private val onItemClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val orders = mutableListOf<Order>()
    private var isSectorFirst: Boolean = false
    private var isPricePerKmHidden: Boolean = false

    fun submitList(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    fun updateDisplaySettings(sectorFirst: Boolean, hidePricePerKm: Boolean) {
        this.isSectorFirst = sectorFirst
        this.isPricePerKmHidden = hidePricePerKm
        notifyDataSetChanged()
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
        private val tvFrom: TextView = itemView.findViewById(R.id.tv_address_from)
        private val tvTo: TextView = itemView.findViewById(R.id.tv_address_to)
        private val tvSectorFrom: TextView = itemView.findViewById(R.id.tv_sector_from)
        private val tvSectorTo: TextView = itemView.findViewById(R.id.tv_sector_to)
        private val tvTariff: TextView = itemView.findViewById(R.id.tv_tariff_badge)
        private val stopsContainer: LinearLayout = itemView.findViewById(R.id.ll_stops_container)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
        private val tvActivityBonus: TextView = itemView.findViewById(R.id.tv_activity_bonus)
        private val tvScheduledTime: TextView = itemView.findViewById(R.id.tv_scheduled_time)

        fun bind(order: Order) {
            val context = itemView.context

            // Общая стоимость поездки
            tvPrice.text = order.getFormattedPrice()

            // --- ДИНАМИЧЕСКИЙ РАСЧЕТ ЦЕНЫ ЗА КМ С ПРОВЕРКОЙ НА КОРОТКИЙ МАРШРУТ (< 1 КМ) ---
            if (isPricePerKmHidden) {
                tvPricePerKm.visibility = View.GONE
            } else {
                tvPricePerKm.visibility = View.VISIBLE
                val meters = order.distanceMeters ?: 0

                if (meters >= 1000) {
                    val km = meters / 1000.0
                    val calculatedPricePerKm = order.price / km
                    tvPricePerKm.text = String.format(java.util.Locale.US, "%.2f ₴/км", calculatedPricePerKm)
                } else {
                    // Если маршрут меньше 1 км, выводим аккуратный прочерк с символом валюты
                    tvPricePerKm.text = "- ₴/км"
                }
            }

            tvTariff.text = order.tariffName

            // --- ЛОГИКА ОТОБРАЖЕНИЯ АДРЕСОВ И СЕКТОРОВ ---
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
                tvFrom.text = order.fromAddress
                if (!order.fromSector.isNullOrEmpty()) {
                    tvSectorFrom.text = order.fromSector
                    tvSectorFrom.visibility = View.VISIBLE
                } else {
                    tvSectorFrom.visibility = View.GONE
                }
                tvTo.text = order.toAddress
                if (!order.toSector.isNullOrEmpty()) {
                    tvSectorTo.text = order.toSector
                    tvSectorTo.visibility = View.VISIBLE
                } else {
                    tvSectorTo.visibility = View.GONE
                }
            }

            // --- ИНТЕЛЛЕКТУАЛЬНАЯ ЛОГИКА ДЛЯ ЗАПЛАНИРОВАННЫХ ЗАКАЗОВ (НА ВРЕМЯ) ---
            tvDistance.text = order.getFormattedDistance()
            tvDistance.setTextColor(ContextCompat.getColor(context, R.color.driver_text_primary))

            if (order.isScheduled()) {
                val scheduledDate = order.getScheduledDate()
                if (scheduledDate != null) {
                    // Текущий день (обнуляем время)
                    val today = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }

                    // День заказа (обнуляем время)
                    val orderDay = java.util.Calendar.getInstance().apply {
                        time = scheduledDate
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }

                    // Считаем разницу в днях
                    val diffMillis = orderDay.timeInMillis - today.timeInMillis
                    val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()

                    // Форматируем чистое время заказа
                    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    val timeStr = timeFormat.format(scheduledDate)

                    val displayStr = when {
                        diffDays <= 0 -> timeStr // Сегодня
                        diffDays == 1 -> "Завтра, $timeStr" // Завтра
                        else -> {
                            // Позже (например, "14 черв., 14:30")
                            val ukrLocale = java.util.Locale("uk")
                            val dateFormat = java.text.SimpleDateFormat("d MMM, HH:mm", ukrLocale)
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
                tvActivityBonus.text = "+11"
            } else {
                tvScheduledTime.visibility = View.GONE
                tvActivityBonus.text = "+3"
            }

            // --- СТИЛИЗАЦИЯ ТИПА ОПЛАТЫ + ВСЕГДА ЧЕРНЫЙ ЦВЕТ UI ---
            val method = order.paymentMethod ?: "CASH"
            if (method == "CASH") {
                val neonTeal = ContextCompat.getColor(context, R.color.driver_neon_teal)
                llPriceBg.backgroundTintList = ColorStateList.valueOf(neonTeal)
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
            } else {
                llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#54b1f0"))
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
            }

            // Фиксируем черный цвет текста цены, знака ₴ и иконки оплаты принудительно
            tvPrice.setTextColor(Color.BLACK)
            ivPaymentIcon.setColorFilter(Color.BLACK)

            // --- ДИНАМИЧЕСКИЕ ПРОМЕЖУТОЧНЫЕ ОСТАНОВКИ ---
            stopsContainer.removeAllViews()
            if (!order.stops.isNullOrEmpty()) {
                val inflater = LayoutInflater.from(context)
                val sortedStops = order.stops.sortedBy { it.stopOrder }
                for (stop in sortedStops) {
                    val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)
                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val line = stopView.findViewById<View>(R.id.view_line)

                    tvAddress.text = stop.address
                    ivIcon.setImageResource(R.drawable.ic_circle_green)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.driver_neon_teal))
                    line.visibility = View.VISIBLE
                    stopsContainer.addView(stopView)
                }
            }

            itemView.setOnClickListener {
                onItemClick(order)
            }
        }
    }
}