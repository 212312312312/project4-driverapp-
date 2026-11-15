package com.taxiapp.driver.ui.ride

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.taxiapp.driver.R
import com.taxiapp.driver.network.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Контролер таймера очікування пасажира (до подачі, безкоштовне та платне очікування)
 * і періодичного тікера запланованих замовлень для OrderProgressActivity.
 * Підтримує світлу та темну тему через семантичні ресурси R.color.driver_waiting_*.
 */
class RideWaitingTimerController(
    private val context: Context,
    private val layoutWaitingInfo: LinearLayout,
    private val tvWaitingTimer: TextView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var waitingRunnable: Runnable? = null
    private var scheduledTickerRunnable: Runnable? = null

    fun startWaitingTimer(order: Order) {
        stopWaitingTimer(hideView = false)
        val timeToParse = order.waitingStartTime ?: order.arrivedAt ?: return
        layoutWaitingInfo.visibility = View.VISIBLE

        val cleanTime = timeToParse.substringBefore(".").substringBefore("Z")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val arrivedTime = try {
            format.parse(cleanTime)?.time ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val preWaitBg = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_waiting_free_bg))
        val preWaitText = ContextCompat.getColor(context, R.color.driver_waiting_free_text)
        val freeWaitBg = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_card_bg))
        val freeWaitText = ContextCompat.getColor(context, R.color.driver_text_primary)
        val paidWaitBg = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_waiting_alert_bg))
        val paidWaitText = ContextCompat.getColor(context, R.color.driver_waiting_alert_text)

        val runnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val diffMs = now - arrivedTime

                if (diffMs < 0) {
                    val absDiffMs = abs(diffMs)
                    val remMin = (absDiffMs / (1000 * 60)).toInt()
                    val remSec = ((absDiffMs / 1000) % 60).toInt()

                    layoutWaitingInfo.backgroundTintList = preWaitBg
                    tvWaitingTimer.setTextColor(preWaitText)
                    tvWaitingTimer.text = String.format(Locale.US, "⏱ До початку очікування: %02d:%02d", remMin, remSec)

                    handler.postDelayed(this, 1000)
                    return
                }

                val diffMinutesFull = diffMs / (1000 * 60).toDouble()
                val freeMins = order.freeWaitingMinutes

                if (diffMinutesFull <= freeMins) {
                    val remainingMs = (freeMins * 60 * 1000) - diffMs
                    val remMin = (remainingMs / (1000 * 60)).toInt()
                    val remSec = ((remainingMs / 1000) % 60).toInt()

                    layoutWaitingInfo.backgroundTintList = freeWaitBg
                    tvWaitingTimer.setTextColor(freeWaitText)
                    tvWaitingTimer.text = String.format(Locale.US, "Безкоштовне очікування: %02d:%02d", remMin, remSec)
                } else {
                    val paidMins = floor(diffMinutesFull - freeMins).toInt()
                    val extraCost = paidMins * order.pricePerWaitingMinute

                    layoutWaitingInfo.backgroundTintList = paidWaitBg
                    tvWaitingTimer.setTextColor(paidWaitText)
                    tvWaitingTimer.text = String.format(Locale.US, "   Платне очікування: %d хв (+%.2f ₴)", paidMins, extraCost)
                }
                handler.postDelayed(this, 1000)
            }
        }
        waitingRunnable = runnable
        handler.post(runnable)
    }

    fun stopWaitingTimer(hideView: Boolean = false) {
        waitingRunnable?.let { handler.removeCallbacks(it) }
        waitingRunnable = null
        if (hideView) {
            layoutWaitingInfo.visibility = View.GONE
        }
    }

    fun showInTripWaitingSummary(order: Order?) {
        stopWaitingTimer(hideView = false)
        if (order != null && order.waitingPrice > 0) {
            layoutWaitingInfo.visibility = View.VISIBLE
            layoutWaitingInfo.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_waiting_paid_bg))
            tvWaitingTimer.setTextColor(ContextCompat.getColor(context, R.color.driver_waiting_paid_text))
            tvWaitingTimer.text = String.format(Locale.US, "💰 Додано за очікування: %.2f ₴", order.waitingPrice)
        } else {
            layoutWaitingInfo.visibility = View.GONE
        }
    }

    fun startScheduledOrderTicker(onTick: () -> Unit) {
        stopScheduledOrderTicker()
        val runnable = object : Runnable {
            override fun run() {
                onTick()
                handler.postDelayed(this, 30_000L)
            }
        }
        scheduledTickerRunnable = runnable
        handler.post(runnable)
    }

    fun stopScheduledOrderTicker() {
        scheduledTickerRunnable?.let { handler.removeCallbacks(it) }
        scheduledTickerRunnable = null
    }

    fun bindScheduledStateUi(
        order: Order,
        tvStatusTitle: TextView,
        tvDestinationLabel: TextView,
        btnSaveAction: TextView,
        btnContainerLayout: View
    ) {
        val scheduledDate = order.getScheduledDate() ?: return
        val diffMillis = scheduledDate.time - Date().time
        val diffMinutes = diffMillis / (60 * 1000)

        tvStatusTitle.text = "Заплановано на ${order.getFormattedScheduledTime()}"
        tvDestinationLabel.text = "Час подачі: ${order.getFormattedScheduledTime()}"

        if (diffMinutes > 35) {
            btnSaveAction.text = "ЧЕКАЙТЕ ЧАСУ"
            btnSaveAction.isEnabled = false
            btnSaveAction.setTextColor(Color.GRAY)
            btnContainerLayout.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_black_bg))
        } else if (order.isDriverConfirmed) {
            btnSaveAction.text = "ОЧІКУВАННЯ ПОЧАТКУ..."
            btnSaveAction.isEnabled = false
            btnSaveAction.setTextColor(Color.GRAY)
        } else {
            btnSaveAction.text = "ПІДТВЕРДИТИ ЗАМОВЛЕННЯ"
            btnSaveAction.isEnabled = true
            btnSaveAction.setTextColor(ContextCompat.getColor(context, R.color.driver_text_black))
            btnContainerLayout.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_neon_teal))
        }
    }

    fun release() {
        stopWaitingTimer(hideView = false)
        stopScheduledOrderTicker()
    }
}
