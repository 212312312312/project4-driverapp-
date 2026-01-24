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

    fun submitList(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
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

        // --- ПОЛЯ ДЛЯ СЕКТОРОВ ---
        private val tvSectorFrom: TextView = itemView.findViewById(R.id.tv_sector_from)
        private val tvSectorTo: TextView = itemView.findViewById(R.id.tv_sector_to)
        // -------------------------

        private val tvTariff: TextView = itemView.findViewById(R.id.tv_tariff_badge)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
        private val stopsContainer: LinearLayout = itemView.findViewById(R.id.ll_stops_container)

        fun bind(order: Order) {
            tvPrice.text = order.getFormattedPrice()
            tvPricePerKm.text = order.getPricePerKm()
            tvFrom.text = order.fromAddress
            tvTo.text = order.toAddress
            tvTariff.text = order.tariffName
            tvDistance.text = order.getFormattedDistance()

            // --- ЛОГИКА СЕКТОРОВ ---
            // Сектор подачи (Откуда)
            if (!order.fromSector.isNullOrEmpty()) {
                tvSectorFrom.text = order.fromSector
                tvSectorFrom.visibility = View.VISIBLE
            } else {
                tvSectorFrom.visibility = View.GONE
            }

            // Сектор назначения (Куда)
            if (!order.toSector.isNullOrEmpty()) {
                tvSectorTo.text = order.toSector
                tvSectorTo.visibility = View.VISIBLE
            } else {
                tvSectorTo.visibility = View.GONE
            }
            // -----------------------

            // Оплата (Cash/Card)
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

            // Остановки
            stopsContainer.removeAllViews()
            if (!order.stops.isNullOrEmpty()) {
                val inflater = LayoutInflater.from(itemView.context)
                val sortedStops = order.stops.sortedBy { it.stopOrder }

                for (stop in sortedStops) {
                    val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)
                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val line = stopView.findViewById<View>(R.id.view_line)

                    tvAddress.text = stop.address
                    ivIcon.setImageResource(R.drawable.ic_circle_green)
                    ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.driver_neon_teal))
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