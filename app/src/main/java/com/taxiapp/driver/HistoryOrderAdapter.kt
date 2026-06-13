package com.taxiapp.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // Добавлен импорт
import android.widget.TextView
import androidx.core.content.ContextCompat // Добавлен импорт для работы с цветами
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

        // --- НОВЫЕ КОМПОНЕНТЫ ДЛЯ СВЯЗЫВАНИЯ СТРУКТУРЫ ТРЕХСЕКЦИОННОГО ФУТЕРА ---
        private val ivPaymentIcon: ImageView = itemView.findViewById(R.id.iv_payment_icon)
        private val tvActivityBonus: TextView = itemView.findViewById(R.id.tv_activity_bonus)
        private val llPriceBackground: View = itemView.findViewById(R.id.ll_price_background)

        fun bind(order: Order) {
            val context = itemView.context

            tvFrom.text = order.fromAddress ?: "Адреса не вказана"
            tvTo.text = order.toAddress ?: "Адреса не вказана"
            tvPrice.text = order.getFormattedPrice()
            tvDistance.text = order.getFormattedDistance()

            // 1. ДИНАМИЧЕСКАЯ АКТИВНОСТЬ: Выводим реальные баллы из пакетного запроса сервера
            val bonus = order.activityBonus
            tvActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"

            // 2. СВЯЗЫВАНИЕ ТИПА ОПЛАТЫ И СМЕНЫ ТЕМ ПРИЛОЖЕНИЯ
            if (order.paymentMethod == "CARD" || order.paymentMethod == "ELECTRONIC") {
                // Безналичный расчет: Карточка, Яркий бирюзовый фон плашки, Черный текст и иконка
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_neon_teal)
                ivPaymentIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.black)
                tvPrice.setTextColor(ContextCompat.getColor(context, R.color.black))
            } else {
                // Наличные (CASH): Кошелек/Деньги, Фоновый цвет карточки (driver_card_bg), Адаптивный белый текст
                ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
                llPriceBackground.backgroundTintList = ContextCompat.getColorStateList(context, R.color.driver_card_bg)
                ivPaymentIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.driver_text_primary)
                tvPrice.setTextColor(ContextCompat.getColor(context, R.color.driver_text_primary))
            }

            // Форматирование даты: 12 січ. 14:30
            if (order.arrivedAt != null) {
                try {
                    val parsedDate = LocalDateTime.parse(order.arrivedAt)
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