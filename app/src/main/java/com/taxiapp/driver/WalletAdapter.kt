package com.taxiapp.driver

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

class WalletAdapter : ListAdapter<WalletTransactionDto, WalletAdapter.WalletViewHolder>(DiffCallback()) {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvType: TextView = itemView.findViewById(R.id.tv_tx_type)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_tx_desc)
        val tvAmount: TextView = itemView.findViewById(R.id.tv_tx_amount)
        val tvDate: TextView = itemView.findViewById(R.id.tv_tx_date)
        val tvBalanceAfter: TextView = itemView.findViewById(R.id.tv_tx_balance_after)
        val imgIcon: ImageView = itemView.findViewById(R.id.img_tx_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallet_transaction, parent, false)
        return WalletViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        // 1. Описание транзакции
        holder.tvDesc.text = item.description ?: "Без опису"

        // Выводим остаток (залишок) после транзакции
        holder.tvBalanceAfter.text = "Залишок: %.2f ₴".format(item.balanceAfter)
        holder.tvBalanceAfter.visibility = View.VISIBLE

        // Обработка клика: открываем детали, если транзакция привязана к заказу
        holder.itemView.setOnClickListener {
            if (item.orderId != null && item.orderId > 0) {
                val intent = android.content.Intent(context, HistoryDetailsActivity::class.java).apply {
                    putExtra("ORDER_ID", item.orderId)
                }
                context.startActivity(intent)
            }
        }

        // 2. Дата (парсинг ISO 8601)
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

            val date = inputFormat.parse(item.createdAt)
            holder.tvDate.text = if (date != null) outputFormat.format(date) else item.createdAt
        } catch (e: Exception) {
            holder.tvDate.text = item.createdAt
        }

        // 3. Динамические цвета из colors.xml и Иконки
        // DEPOSIT (Пополнение) или BONUS -> Зеленый
        if (item.operationType == "DEPOSIT" || item.operationType == "BONUS") {
            val greenColor = ContextCompat.getColor(context, R.color.activity_green)

            holder.tvAmount.text = "+%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(greenColor)

            holder.imgIcon.setImageResource(R.drawable.ic_wallet)
            holder.imgIcon.setColorFilter(greenColor)

            holder.tvType.text = if (item.operationType == "DEPOSIT") "Поповнення" else "Бонус"
        }
        // COMMISSION / PENALTY / WITHDRAWAL -> Красный
        else {
            val redColor = ContextCompat.getColor(context, R.color.activity_red)

            holder.tvAmount.text = "%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(redColor)

            holder.imgIcon.setImageResource(R.drawable.ic_payment_card)
            holder.imgIcon.setColorFilter(redColor)

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