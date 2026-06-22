package com.taxiapp.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ActivityHistoryItemDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryActivityAdapter(
    private var items: List<ActivityHistoryItemDto>,
    private val onItemClick: (String) -> Unit // 👈 Добавлено для обработки кликов
) : RecyclerView.Adapter<HistoryActivityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvPoints: TextView = view.findViewById(R.id.tvPoints)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_history, parent, false)
        return ViewHolder(view)
    }

    // НАЙДИ И ОБНОВИ ВНУТРЕННОСТЬ МЕТОДА onBindViewHolder
    // НАЙДИ И ЗАМЕНИ ТОЛЬКО ЭТOТ МЕТОД ВНУТРИ КЛАССА HistoryActivityAdapter
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // Если есть UUID заказа, красиво форматируем строку причины
        if (!item.orderUuid.isNullOrBlank()) {
            val shortUuid = if (item.orderUuid.length >= 6) item.orderUuid.takeLast(6) else item.orderUuid
            holder.tvReason.text = "${item.reason} (****${shortUuid.uppercase()})"
        } else {
            holder.tvReason.text = item.reason
        }

        // Форматирование даты
        try {
            val parsed = LocalDateTime.parse(item.date)
            holder.tvDate.text = parsed.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } catch (e: Exception) {
            holder.tvDate.text = item.date
        }

        // Подгрузка системных цветов в зависимости от знака баллов
        if (item.change > 0) {
            holder.tvPoints.text = "+${item.change}"
            holder.tvPoints.setTextColor(ContextCompat.getColor(context, R.color.activity_green))
        } else {
            holder.tvPoints.text = "${item.change}"
            holder.tvPoints.setTextColor(ContextCompat.getColor(context, R.color.activity_red))
        }

        // Логика клика: передаем UUID вверх в Активити только при положительном балансе (> 0)
        holder.itemView.setOnClickListener {
            if (item.change > 0 && !item.orderUuid.isNullOrBlank()) {
                onItemClick(item.orderUuid)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ActivityHistoryItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }
}