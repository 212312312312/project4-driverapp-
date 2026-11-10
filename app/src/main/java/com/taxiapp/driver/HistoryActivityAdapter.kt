package com.taxiapp.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ActivityHistoryItemDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivityAdapter(
    private var items: List<ActivityHistoryItemDto>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryActivityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvPoints: TextView = view.findViewById(R.id.tvPoints)
        val ivChevron: ImageView? = view.findViewById(R.id.ivHistoryChevron)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // Чистий текст причини без технічного UUID у дужках (як на референсному скріншоті)
        val cleanReason = item.reason.replace(Regex("\\s*\\(\\*{2,}[A-Za-z0-9-]+\\)"), "").trim()
        holder.tvReason.text = cleanReason.ifEmpty { item.reason }

        // Форматування дати з повною українською назвою місяця ("31 серпня 20:09")
        holder.tvDate.text = formatUkrainianFullMonthDate(item.date)

        // Відображення балів та стрілки переходу
        val orderUuidVal = item.orderUuid
        val hasOrderLink = !orderUuidVal.isNullOrBlank()

        if (item.change > 0) {
            holder.tvPoints.text = "+${item.change}"
            holder.tvPoints.setTextColor(ContextCompat.getColor(context, R.color.driver_text_secondary))
        } else if (item.change < 0) {
            holder.tvPoints.text = "${item.change}"
            holder.tvPoints.setTextColor(ContextCompat.getColor(context, R.color.driver_error_red))
        } else {
            holder.tvPoints.text = "0"
            holder.tvPoints.setTextColor(ContextCompat.getColor(context, R.color.driver_text_secondary))
        }

        holder.ivChevron?.visibility = if (hasOrderLink) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (!orderUuidVal.isNullOrBlank()) {
                onItemClick(orderUuidVal)
            }
        }
    }

    private fun formatUkrainianFullMonthDate(raw: String): String {
        val clean = raw.trim()
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "dd.MM.yyyy HH:mm"
        )
        var parsed: Date? = null
        for (p in patterns) {
            try {
                parsed = SimpleDateFormat(p, Locale.US).parse(clean.take(26))
                if (parsed != null) break
            } catch (_: Exception) {}
        }
        if (parsed == null) return clean

        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(parsed)

        val monthsFullGenitive = arrayOf(
            "січня", "лютого", "березня", "квітня", "травня", "червня",
            "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
        )
        val monthName = monthsFullGenitive.getOrElse(monthIdx) { "" }
        return "$day $monthName $timeStr"
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ActivityHistoryItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }
}