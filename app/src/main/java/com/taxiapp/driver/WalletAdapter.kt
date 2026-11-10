package com.taxiapp.driver

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.WalletTransactionDto
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class WalletAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<WalletTransactionDto, WalletAdapter.WalletViewHolder>(DiffCallback()) {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDateHeader: TextView? = itemView.findViewById(R.id.tv_date_header)
        val tvType: TextView = itemView.findViewById(R.id.tv_tx_type)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_tx_desc)
        val tvAmount: TextView = itemView.findViewById(R.id.tv_tx_amount)
        val tvDate: TextView = itemView.findViewById(R.id.tv_tx_date)
        val tvBalanceAfter: TextView = itemView.findViewById(R.id.tv_tx_balance_after)
        val imgIcon: ImageView = itemView.findViewById(R.id.img_tx_icon)
        val ivChevron: ImageView? = itemView.findViewById(R.id.iv_tx_chevron)
        val rowContainer: View = itemView.findViewById(R.id.tx_row_container) ?: itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallet_transaction, parent, false)
        return WalletViewHolder(view)
    }

    /**
     * Повертає відформатовану дату ("31 серп. 2026") для елемента за індексом,
     * щоб закріплена шапка дати під повзунком шторки оновлювалася при скролі (як на відео).
     */
    fun getDateHeaderForPosition(position: Int): String? {
        if (currentList.isEmpty()) return null
        val safePos = position.coerceIn(0, currentList.lastIndex)
        val item = currentList[safePos]
        return formatUkrainianDateHeader(parseIsoDate(item.createdAt), item.createdAt)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        // 1. Парсинг дати та часу транзакції
        val parsedDate = parseIsoDate(item.createdAt)
        val currentDayHeader = formatUkrainianDateHeader(parsedDate, item.createdAt)
        val previousDayHeader = if (position > 0) {
            val prevItem = getItem(position - 1)
            formatUkrainianDateHeader(parseIsoDate(prevItem.createdAt), prevItem.createdAt)
        } else {
            null
        }

        // Для position == 0 дата виводиться у закріпленій шапці під повзунком шторки (tv_sticky_date),
        // а для наступних днів (напр. "28 серп. 2026" на відео 00:19) — показується між групами транзакцій
        if (holder.tvDateHeader != null) {
            if (position > 0 && currentDayHeader != previousDayHeader) {
                holder.tvDateHeader.text = currentDayHeader
                holder.tvDateHeader.visibility = View.VISIBLE
            } else {
                holder.tvDateHeader.visibility = View.GONE
            }
        }

        // Час операції (напр. "20:09")
        holder.tvDate.text = if (parsedDate != null) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsedDate)
        } else {
            extractTimeFallback(item.createdAt)
        }

        // 2. Визначаємо знак та тип операції
        val isPositive = item.amount > 0.0 ||
                ((item.operationType == "DEPOSIT" || item.operationType == "BONUS") && item.amount >= 0.0)

        val neonTealColor = ContextCompat.getColor(context, R.color.driver_neon_teal)
        val errorRedColor = ContextCompat.getColor(context, R.color.driver_error_red)

        holder.imgIcon.imageTintList = null
        holder.imgIcon.colorFilter = null

        if (isPositive) {
            holder.tvAmount.text = "+${formatSmartAmount(abs(item.amount))}"
            holder.tvAmount.setTextColor(neonTealColor)
            holder.imgIcon.setImageDrawable(
                TransactionBadgeDrawable(
                    isIncoming = true,
                    circleColor = Color.parseColor("#253D3B"),
                    arrowColor = neonTealColor
                )
            )
        } else {
            holder.tvAmount.text = "-${formatSmartAmount(abs(item.amount))}"
            holder.tvAmount.setTextColor(errorRedColor)
            holder.imgIcon.setImageDrawable(
                TransactionBadgeDrawable(
                    isIncoming = false,
                    circleColor = Color.parseColor("#3E2E35"),
                    arrowColor = errorRedColor
                )
            )
        }

        // 3. Головний заголовок операції
        val finalOrderId = item.orderId ?: item.description?.let { desc ->
            Regex("#(\\d+)").find(desc)?.groupValues?.get(1)?.toLongOrNull()
        }

        holder.tvType.text = buildCleanTitle(item, isPositive)

        // 4. Залишок після операції ("Залишок: 262.27 ₴" або "Залишок: 0 ₴")
        holder.tvBalanceAfter.text = "Залишок: ${formatBalanceAfterAmount(item.balanceAfter)} ₴"
        holder.tvBalanceAfter.visibility = View.VISIBLE

        // 5. ID замовлення та стрілка переходу справа
        if (finalOrderId != null && finalOrderId > 0L) {
            val formattedOrderHex = String.format(Locale.US, "%06x", finalOrderId)
            holder.tvDesc.text = "ID замовлення: **** $formattedOrderHex"
            holder.tvDesc.visibility = View.VISIBLE
            holder.ivChevron?.visibility = View.VISIBLE
        } else {
            holder.tvDesc.visibility = View.GONE
            holder.ivChevron?.visibility = View.GONE
        }

        // 6. Клік по рядку транзакції
        holder.rowContainer.setOnClickListener {
            if (finalOrderId != null && finalOrderId > 0L) {
                onItemClick(finalOrderId)
            } else {
                Toast.makeText(context, "Ця операція не пов'язана з конкретним замовленням", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildCleanTitle(item: WalletTransactionDto, isPositive: Boolean): String {
        val rawDesc = item.description?.trim().orEmpty()
        return when {
            rawDesc.contains("LiqPay", ignoreCase = true) ||
                    rawDesc.contains("Поповнення", ignoreCase = true) -> "Поповнення баланса картою"
            rawDesc.contains("Безготівкова оплата", ignoreCase = true) -> "Нараховано вартість замовлення на баланс"
            rawDesc.contains("Компенсація за скасування", ignoreCase = true) -> "Компенсація за скасування замовлення"
            rawDesc.contains("Доплата за знижку", ignoreCase = true) -> "Нарахування бонусу за замовлення"
            item.operationType == "COMMISSION" -> {
                if (rawDesc.isNotBlank()) {
                    rawDesc.replace(Regex("\\s*\\(?#\\d+\\)?"), "").trim().ifEmpty { "Комісія Unit" }
                } else {
                    "Комісія Unit"
                }
            }
            item.operationType == "WITHDRAWAL" -> {
                if (rawDesc.contains("комісія", ignoreCase = true)) {
                    "Комісія фінансових установ за перерахування коштів"
                } else {
                    "Перерахування на карту"
                }
            }
            item.operationType == "PENALTY" -> "Списання вартості замовлення"
            item.operationType == "BONUS" -> "Нарахування вартості замовлення"
            item.operationType == "DEPOSIT" && item.orderId != null -> "Нараховано вартість замовлення на баланс"
            item.operationType == "DEPOSIT" -> "Поповнення баланса картою"
            rawDesc.isNotBlank() -> rawDesc.replace(Regex("\\s*\\(?#\\d+\\)?"), "").trim()
            isPositive -> "Нарахування на баланс"
            else -> "Списання з балансу"
        }
    }

    private fun formatSmartAmount(absValue: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }
        val cents = ((absValue * 100.0) + 0.5).toLong() % 100L
        return if (cents == 0L) {
            DecimalFormat("#,##0", symbols).format(absValue)
        } else {
            DecimalFormat("#,##0.00", symbols).format(absValue)
        }
    }

    private fun formatBalanceAfterAmount(value: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }
        val cents = ((abs(value) * 100.0) + 0.5).toLong() % 100L
        return if (cents == 0L) {
            DecimalFormat("#,##0", symbols).format(value)
        } else {
            DecimalFormat("#,##0.00", symbols).format(value)
        }
    }

    private fun parseIsoDate(raw: String): Date? {
        val clean = raw.trim()
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(clean.take(26))
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractTimeFallback(raw: String): String {
        val match = Regex("(\\d{2}:\\d{2})").find(raw)
        return match?.groupValues?.get(1) ?: raw
    }

    private fun formatUkrainianDateHeader(date: Date?, rawFallback: String): String {
        if (date == null) return rawFallback.take(10)
        val cal = Calendar.getInstance().apply { time = date }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIndex = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val monthsShort = arrayOf(
            "січ.", "лют.", "бер.", "квіт.", "трав.", "черв.",
            "лип.", "серп.", "вер.", "жовт.", "лист.", "груд."
        )
        val monthName = monthsShort.getOrElse(monthIndex) { "" }
        return "$day $monthName $year"
    }

    private class TransactionBadgeDrawable(
        private val isIncoming: Boolean,
        private val circleColor: Int,
        private val arrowColor: Int
    ) : Drawable() {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = circleColor
            style = Paint.Style.FILL
        }

        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = arrowColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val radius = minOf(b.width(), b.height()) / 2f

            canvas.drawCircle(cx, cy, radius, bgPaint)

            val scale = (radius * 2f) / 24f
            arrowPaint.strokeWidth = 1.8f * scale

            val path = Path()
            if (isIncoming) {
                path.moveTo(cx - 3.8f * scale, cy - 3.6f * scale)
                path.lineTo(cx - 0.3f * scale, cy)
                path.lineTo(cx - 3.8f * scale, cy + 3.6f * scale)

                path.moveTo(cx + 0.5f * scale, cy - 3.6f * scale)
                path.lineTo(cx + 4.0f * scale, cy)
                path.lineTo(cx + 0.5f * scale, cy + 3.6f * scale)
            } else {
                path.moveTo(cx + 3.8f * scale, cy - 3.6f * scale)
                path.lineTo(cx + 0.3f * scale, cy)
                path.lineTo(cx + 3.8f * scale, cy + 3.6f * scale)

                path.moveTo(cx - 0.5f * scale, cy - 3.6f * scale)
                path.lineTo(cx - 4.0f * scale, cy)
                path.lineTo(cx - 0.5f * scale, cy + 3.6f * scale)
            }
            canvas.drawPath(path, arrowPaint)
        }

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
            arrowPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bgPaint.colorFilter = colorFilter
            arrowPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class DiffCallback : DiffUtil.ItemCallback<WalletTransactionDto>() {
        override fun areItemsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WalletTransactionDto, newItem: WalletTransactionDto) = oldItem == newItem
    }
}