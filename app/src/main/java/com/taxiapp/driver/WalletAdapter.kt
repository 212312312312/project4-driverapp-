package com.taxiapp.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
        val imgIcon: ImageView = itemView.findViewById(R.id.img_tx_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallet_transaction, parent, false)
        return WalletViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context // Отримуємо контекст для доступу до strings.xml

        holder.tvDesc.text = item.description ?: ""

        // Форматування дати
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            val date = inputFormat.parse(item.createdAt)
            holder.tvDate.text = if (date != null) outputFormat.format(date) else item.createdAt
        } catch (e: Exception) {
            holder.tvDate.text = item.createdAt
        }

        // Логіка відображення (Колір + Переклад тексту)
        if (item.amount >= 0) {
            // ПРИБУТОК (Зелений)
            holder.tvAmount.text = "+%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50"))
            holder.imgIcon.setColorFilter(Color.parseColor("#4CAF50"))

            if (item.operationType == "DEPOSIT") {
                holder.tvType.text = context.getString(R.string.tx_deposit)
            } else {
                holder.tvType.text = context.getString(R.string.tx_bonus)
            }
        } else {
            // ВИТРАТА (Червоний)
            holder.tvAmount.text = "%.2f ₴".format(item.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#FF5252"))
            holder.imgIcon.setColorFilter(Color.parseColor("#FF5252"))

            when (item.operationType) {
                "COMMISSION" -> holder.tvType.text = context.getString(R.string.tx_commission)
                "PENALTY" -> holder.tvType.text = context.getString(R.string.tx_penalty)
                else -> holder.tvType.text = context.getString(R.string.tx_withdrawal)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WalletTransactionDto>() {
        override fun areItemsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem == newItem
    }
}