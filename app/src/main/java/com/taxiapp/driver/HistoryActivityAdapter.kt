package com.taxiapp.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ActivityHistoryItemDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryActivityAdapter(private var items: List<ActivityHistoryItemDto>) :
    RecyclerView.Adapter<HistoryActivityAdapter.ViewHolder>() {

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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvReason.text = item.reason

        // Форматування дати (якщо сервер надсилає ISO)
        try {
            val parsed = LocalDateTime.parse(item.date)
            holder.tvDate.text = parsed.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } catch (e: Exception) {
            holder.tvDate.text = item.date
        }

        if (item.change > 0) {
            holder.tvPoints.text = "+${item.change}"
            holder.tvPoints.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            holder.tvPoints.text = "${item.change}"
            holder.tvPoints.setTextColor(Color.parseColor("#F44336")) // Red
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ActivityHistoryItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }
}