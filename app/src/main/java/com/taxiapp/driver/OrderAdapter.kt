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
import com.taxiapp.driver.utils.SessionManager

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

        // Поля расстояния (Выровненные внутри левой оси 56dp)
        private val tvDistanceToClient: TextView = itemView.findViewById(R.id.id_tv_distance_to_client)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)

        fun bind(order: Order) {
            val context = itemView.context
            val sessionManager = SessionManager(context)

            tvPrice.text = order.getFormattedPrice()

            if (isPricePerKmHidden) {
                tvPricePerKm.visibility = View.GONE
            } else {
                tvPricePerKm.visibility = View.VISIBLE
                tvPricePerKm.text = order.getPricePerKm()
            }

            tvTariff.text = order.tariffName

            // --- ЛОГИКА ОТОБРАЖЕНИЯ АДРЕСОВ И СЕКТОРОВ ---
            if (isSectorFirst) {
                if (!order.fromSector.isNullOrEmpty()) {
                    tvFrom.text = order.fromSector
                    tvSectorFrom.text = order.fromAddress
                    tvSectorFrom.visibility = View.VISIBLE // Исправлено: убран ошибочный вызов .copy()
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

            // --- РАСЧЕТ И ОТОБРАЖЕНИЕ КИЛОМЕТРАЖА ПО ЛЕВОЙ ОСИ ---
            val orderLat = order.originLat
            val orderLng = order.originLng
            val driverLoc = sessionManager.getManualLocation()

            if (driverLoc != null && orderLat != null && orderLng != null && orderLat != 0.0 && orderLng != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    driverLoc.first, driverLoc.second,
                    orderLat, orderLng,
                    results
                )
                val distanceInKm = results[0] / 1000.0
                tvDistanceToClient.text = String.format(java.util.Locale.getDefault(), "%.1f км", distanceInKm)
                tvDistanceToClient.visibility = View.VISIBLE
            } else {
                tvDistanceToClient.visibility = View.GONE
            }

            // 2. Расстояние поездки или время (Точка Б)
            if (order.isScheduled()) {
                val timeOnly = try {
                    order.scheduledAt?.substring(11, 16) ?: ""
                } catch (e: Exception) { "" }

                tvDistance.text = "🕒 $timeOnly"
                tvDistance.setTextColor(Color.parseColor("#FF9800"))
            } else {
                tvDistance.text = order.getFormattedDistance()
                tvDistance.setTextColor(ContextCompat.getColor(context, R.color.driver_text_secondary))
            }

            // --- СТИЛИЗАЦИЯ ТИПА ОПЛАТЫ ---
            val method = order.paymentMethod ?: "CASH"
            if (method == "CASH") {
                llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD600"))
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
                tvPrice.setTextColor(Color.BLACK)
                ivPaymentIcon.setColorFilter(Color.BLACK)
            } else {
                llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2979FF"))
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
                tvPrice.setTextColor(Color.WHITE)
                ivPaymentIcon.setColorFilter(Color.WHITE)
            }

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