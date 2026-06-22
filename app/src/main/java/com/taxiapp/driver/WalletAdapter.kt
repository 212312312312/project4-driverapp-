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

class WalletAdapter(
    private val onItemClick: (Long) -> Unit // 👈 Добавлено для проброса клика в Активити
) : ListAdapter<WalletTransactionDto, WalletAdapter.WalletViewHolder>(DiffCallback()) {

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

    // ПОЛНОСТЬЮ ЗАМЕНИ ЭТОТ МЕТОД В WalletAdapter.kt
    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        // 1. Описание транзакции
        holder.tvDesc.text = item.description ?: "Без опису"

        // Выводим остаток (залишок) после транзакции
        holder.tvBalanceAfter.text = "Залишок: %.2f ₴".format(item.balanceAfter)
        holder.tvBalanceAfter.visibility = View.VISIBLE

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
        if (item.operationType == "DEPOSIT" || item.operationType == "BONUS") {
            val neonTealColor = ContextCompat.getColor(context, R.color.driver_neon_teal)

            holder.tvAmount.text = "+%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(neonTealColor)

            holder.imgIcon.setImageResource(R.drawable.ic_wallet)
            holder.imgIcon.setColorFilter(neonTealColor)

            holder.tvType.text = if (item.operationType == "DEPOSIT") "Поповнення" else "Бонус"
        } else {
            val errorRedColor = ContextCompat.getColor(context, R.color.driver_error_red)

            holder.tvAmount.text = "%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(errorRedColor)

            holder.imgIcon.setImageResource(R.drawable.ic_payment_card)
            holder.imgIcon.setColorFilter(errorRedColor)

            holder.tvType.text = when (item.operationType) {
                "COMMISSION" -> "Комісія"
                "PENALTY" -> "Штраф"
                "WITHDRAWAL" -> "Виведення"
                else -> item.operationType
            }
        }

        // 4. ПУЛЕНЕПРОБИВАЕМАЯ ОБРАБОТКА КЛИКА (Перенесена вниз для стабильности)
        holder.itemView.setOnClickListener {
            // Резервный парсинг: если orderId null, вытаскиваем число после знака '#' из описания строки
            val finalOrderId = item.orderId ?: item.description?.let { desc ->
                Regex("#(\\d+)").find(desc)?.groupValues?.get(1)?.toLongOrNull()
            }

            if (finalOrderId != null && finalOrderId > 0) {
                onItemClick(finalOrderId) // 👈 Железно отправляем запрос на сервер Спринга
            } else {
                android.widget.Toast.makeText(context, "Ця операція не пов'язана з конкретним замовленням", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WalletTransactionDto>() {
        override fun areItemsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem == newItem
    }
}