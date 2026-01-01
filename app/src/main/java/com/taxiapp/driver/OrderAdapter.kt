package com.taxiapp.driver

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

    // Додаємо цей метод, щоб перевірити, чи список порожній (для EtherActivity)
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
        private val tvPricePerKm: TextView = itemView.findViewById(R.id.tv_price_per_km)
        private val tvFrom: TextView = itemView.findViewById(R.id.tv_address_from)
        private val tvTo: TextView = itemView.findViewById(R.id.tv_address_to)
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

            // --- ДИНАМІЧНІ ЗУПИНКИ В КАРТЦІ ---
            stopsContainer.removeAllViews()

            if (!order.stops.isNullOrEmpty()) {
                val inflater = LayoutInflater.from(itemView.context)

                // Сортуємо зупинки
                val sortedStops = order.stops.sortedBy { it.stopOrder }

                for (stop in sortedStops) {
                    // Використовуємо наш item_route_point.xml
                    val stopView = inflater.inflate(R.layout.item_route_point, stopsContainer, false)

                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val line = stopView.findViewById<View>(R.id.view_line)

                    tvAddress.text = stop.address

                    // Фарбуємо іконку в жовтий/бірюзовий для проміжних точок
                    ivIcon.setImageResource(R.drawable.ic_circle_green)
                    ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.driver_neon_teal))

                    // Лінія завжди видима для проміжних точок, бо знизу ще є Точка Б
                    line.visibility = View.VISIBLE

                    stopsContainer.addView(stopView)
                }
            }
            // ----------------------------------

            itemView.setOnClickListener {
                onItemClick(order)
            }
        }
    }
}