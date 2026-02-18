package com.taxiapp.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.WalletTransactionDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WalletAdapter : ListAdapter<WalletTransactionDto, WalletAdapter.WalletViewHolder>(DiffCallback()) {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvType: TextView = itemView.findViewById(R.id.tv_tx_type)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_tx_desc)
        val tvAmount: TextView = itemView.findViewById(R.id.tv_tx_amount)
        val tvDate: TextView = itemView.findViewById(R.id.tv_tx_date)
        val imgIcon: ImageView = itemView.findViewById(R.id.img_tx_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallet_transaction, parent, false)
        return WalletViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        // 1. Описание
        holder.tvDesc.text = item.description ?: "Без опису"

        // 2. Дата (парсинг ISO 8601)
        try {
            // Сервер отдает время, скорее всего, без часового пояса или в UTC.
            // Подстраиваем формат под то, что шлет Java LocalDateTime.toString()
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

            val date = inputFormat.parse(item.createdAt)
            holder.tvDate.text = if (date != null) outputFormat.format(date) else item.createdAt
        } catch (e: Exception) {
            holder.tvDate.text = item.createdAt // Если ошибка, показываем как есть
        }

        // 3. Цвета и Иконки
        // DEPOSIT (Пополнение) -> Зеленый
        if (item.operationType == "DEPOSIT" || item.operationType == "BONUS") {
            holder.tvAmount.text = "+%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50")) // Green

            holder.imgIcon.setImageResource(R.drawable.ic_wallet) // Или иконка "стрелка вниз"
            holder.imgIcon.setColorFilter(Color.parseColor("#4CAF50"))

            holder.tvType.text = if (item.operationType == "DEPOSIT") "Поповнення" else "Бонус"
        }
        // COMMISSION / PENALTY / WITHDRAWAL -> Красный
        else {
            // amount может приходить отрицательным с сервера (например, -15.0), а может положительным
            // Если оно уже отрицательное, знак минус будет автоматически.
            // Если положительное, но это списание - добавим минус.

            // В нашем коде сервера мы сохраняли: amount = -commissionAmount. Значит число уже с минусом.
            holder.tvAmount.text = "%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#F44336")) // Red

            holder.imgIcon.setImageResource(R.drawable.ic_payment_card) // Или иконка "стрелка вверх"
            holder.imgIcon.setColorFilter(Color.parseColor("#F44336"))

            holder.tvType.text = when (item.operationType) {
                "COMMISSION" -> "Комісія"
                "PENALTY" -> "Штраф"
                "WITHDRAWAL" -> "Виведення"
                else -> item.operationType
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WalletTransactionDto>() {
        override fun areItemsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem == newItem
    }
}