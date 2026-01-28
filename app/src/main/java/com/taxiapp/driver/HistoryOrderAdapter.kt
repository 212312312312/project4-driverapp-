package com.taxiapp.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.Order
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

        fun bind(order: Order) {
            tvFrom.text = order.fromAddress ?: "Адреса не вказана"
            tvTo.text = order.toAddress ?: "Адреса не вказана"
            tvPrice.text = order.getFormattedPrice()
            tvDistance.text = order.getFormattedDistance()

            // Форматирование даты: 12 січ. 14:30
            if (order.arrivedAt != null) {
                try {
                    val parsedDate = LocalDateTime.parse(order.arrivedAt)
                    // Используем Locale("uk") для украинских названий месяцев
                    val formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale("uk"))
                    tvDate.text = parsedDate.format(formatter)
                } catch (e: Exception) {
                    tvDate.text = "---"
                }
            } else {
                tvDate.text = "---"
            }

            itemView.setOnClickListener { onClick(order) }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem == newItem
    }
}