package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.network.ApiClient
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class StatsActivity : AppCompatActivity() {

    private lateinit var tvCurrentPeriod: TextView
    private lateinit var btnLifetimeStats: ImageView

    // Табы периодов
    private lateinit var tabDay: LinearLayout
    private lateinit var tabWeek: LinearLayout
    private lateinit var tabMonth: LinearLayout
    private lateinit var tvTabDay: TextView
    private lateinit var tvTabWeek: TextView
    private lateinit var tvTabMonth: TextView

    private lateinit var tvTotalIncome: TextView
    private lateinit var tvIncomeCard: TextView
    private lateinit var tvIncomeCash: TextView
    private lateinit var tvIncomeBalance: TextView
    private lateinit var tvCommission: TextView

    private lateinit var tvOrdersCount: TextView
    private lateinit var tvTotalKm: TextView
    private lateinit var tvAvgPrice: TextView
    private lateinit var tvTotalHours: TextView

    private var selectedPeriodType = PeriodType.TODAY
    private var fromDate: LocalDate = LocalDate.now()
    private var toDate: LocalDate = LocalDate.now()

    enum class PeriodType {
        TODAY, THIS_WEEK, THIS_MONTH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Связываем элементы
        tvCurrentPeriod = findViewById(R.id.tv_current_period)
        btnLifetimeStats = findViewById(R.id.btn_lifetime_stats)

        tabDay = findViewById(R.id.tab_day)
        tabWeek = findViewById(R.id.tab_week)
        tabMonth = findViewById(R.id.tab_month)
        tvTabDay = findViewById(R.id.tv_tab_day)
        tvTabWeek = findViewById(R.id.tv_tab_week)
        tvTabMonth = findViewById(R.id.tv_tab_month)

        tvTotalIncome = findViewById(R.id.tv_total_income)
        tvIncomeCard = findViewById(R.id.tv_income_card)
        tvIncomeCash = findViewById(R.id.tv_income_cash)
        tvIncomeBalance = findViewById(R.id.tv_income_balance)
        tvCommission = findViewById(R.id.tv_expense_commission)

        tvOrdersCount = findViewById(R.id.tv_orders_count)
        tvTotalKm = findViewById(R.id.tv_total_km)
        tvAvgPrice = findViewById(R.id.tv_avg_price)
        tvTotalHours = findViewById(R.id.tv_total_hours)

        // Обработка кликов по табам
        tabDay.setOnClickListener {
            selectedPeriodType = PeriodType.TODAY
            updateTabsVisuals()
            calculateDatesAndLoad()
        }
        tabWeek.setOnClickListener {
            selectedPeriodType = PeriodType.THIS_WEEK
            updateTabsVisuals()
            calculateDatesAndLoad()
        }
        tabMonth.setOnClickListener {
            selectedPeriodType = PeriodType.THIS_MONTH
            updateTabsVisuals()
            calculateDatesAndLoad()
        }

        // Кнопка вызова кастомного диалога поездок за все время
        btnLifetimeStats.setOnClickListener {
            showLifetimeStatsDialog()
        }

        updateTabsVisuals()
        calculateDatesAndLoad()
    }

    private fun updateTabsVisuals() {
        val activeColor = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val inactiveColor = ContextCompat.getColor(this, R.color.driver_text_secondary)

        tvTabDay.setTextColor(if (selectedPeriodType == PeriodType.TODAY) activeColor else inactiveColor)
        tvTabWeek.setTextColor(if (selectedPeriodType == PeriodType.THIS_WEEK) activeColor else inactiveColor)
        tvTabMonth.setTextColor(if (selectedPeriodType == PeriodType.THIS_MONTH) activeColor else inactiveColor)
    }

    private fun calculateDatesAndLoad() {
        val today = LocalDate.now()

        when (selectedPeriodType) {
            PeriodType.TODAY -> {
                fromDate = today
                toDate = today
                tvCurrentPeriod.text = "Сьогодні"
            }
            PeriodType.THIS_WEEK -> {
                fromDate = today.with(DayOfWeek.MONDAY)
                toDate = today
                tvCurrentPeriod.text = "Поточний тиждень"
            }
            PeriodType.THIS_MONTH -> {
                fromDate = today.with(TemporalAdjusters.firstDayOfMonth())
                toDate = today
                tvCurrentPeriod.text = "Поточний місяць"
            }
        }

        loadStats()
    }

    private fun showLifetimeStatsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_lifetime_stats, null)
        dialog.setContentView(view)

        val btnCloseIcon = view.findViewById<ImageView>(R.id.btn_close_dialog)
        val btnCloseAction = view.findViewById<View>(R.id.btn_close_lifetime)
        val tvOrdersCount = view.findViewById<TextView>(R.id.tv_lifetime_orders_count)

        btnCloseIcon?.setOnClickListener { dialog.dismiss() }
        btnCloseAction?.setOnClickListener { dialog.dismiss() }

        dialog.show()

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@StatsActivity)
                    .getStats("2000-01-01", LocalDate.now().format(DateTimeFormatter.ISO_DATE))

                if (response.isSuccessful && response.body() != null) {
                    tvOrdersCount?.text = response.body()!!.ordersCount.toString()
                } else {
                    tvOrdersCount?.text = "0"
                    Toast.makeText(this@StatsActivity, "Не вдалося отримати дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                tvOrdersCount?.text = "0"
                Toast.makeText(this@StatsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStats() {
        val apiFormatter = DateTimeFormatter.ISO_DATE
        val fromString = fromDate.format(apiFormatter)
        val toString = toDate.format(apiFormatter)

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@StatsActivity)
                    .getStats(fromString, toString)

                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                } else {
                    Toast.makeText(this@StatsActivity, "Не вдалося завантажити статистику", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StatsActivity, "Помилка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(stats: com.taxiapp.driver.network.DriverStatsDto) {
        val locale = Locale("uk", "UA")

        tvTotalIncome.text = String.format(locale, "%.2f ₴", stats.totalIncome)
        tvIncomeCard.text = String.format(locale, "%.2f", stats.incomeCard)
        tvIncomeCash.text = String.format(locale, "%.2f", stats.incomeCash)
        tvIncomeBalance.text = String.format(locale, "%.2f", stats.incomeBalance)
        tvCommission.text = String.format(locale, "- %.2f", stats.commission)

        tvOrdersCount.text = stats.ordersCount.toString()
        tvTotalKm.text = String.format(locale, "%.1f км", stats.totalDistanceKm)
        tvAvgPrice.text = String.format(locale, "%.2f ₴", stats.avgPricePerKm)

        val hours = stats.totalHours.toInt()
        val minutes = ((stats.totalHours - hours) * 60).toInt()
        tvTotalHours.text = String.format(locale, "%dг %dхв", hours, minutes)
    }
}