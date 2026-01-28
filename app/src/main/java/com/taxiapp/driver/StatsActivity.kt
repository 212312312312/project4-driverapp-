package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var btnPeriodSelector: LinearLayout

    private lateinit var tvTotalIncome: TextView
    private lateinit var tvIncomeCard: TextView
    private lateinit var tvIncomeCash: TextView
    private lateinit var tvIncomeBalance: TextView
    private lateinit var tvCommission: TextView

    private lateinit var tvOrdersCount: TextView
    private lateinit var tvTotalKm: TextView
    private lateinit var tvAvgPrice: TextView
    private lateinit var tvTotalHours: TextView

    // Текущее состояние
    private var selectedPeriodType = PeriodType.TODAY
    private var fromDate: LocalDate = LocalDate.now()
    private var toDate: LocalDate = LocalDate.now()

    enum class PeriodType {
        TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        // Инициализация Views
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        tvCurrentPeriod = findViewById(R.id.tv_current_period)
        btnPeriodSelector = findViewById(R.id.btn_period_selector)

        tvTotalIncome = findViewById(R.id.tv_total_income)
        tvIncomeCard = findViewById(R.id.tv_income_card)
        tvIncomeCash = findViewById(R.id.tv_income_cash)
        tvIncomeBalance = findViewById(R.id.tv_income_balance)
        tvCommission = findViewById(R.id.tv_expense_commission)

        tvOrdersCount = findViewById(R.id.tv_orders_count)
        tvTotalKm = findViewById(R.id.tv_total_km)
        tvAvgPrice = findViewById(R.id.tv_avg_price)
        tvTotalHours = findViewById(R.id.tv_total_hours)

        btnPeriodSelector.setOnClickListener {
            showPeriodDialog()
        }

        // Загружаем данные за сегодня по умолчанию
        calculateDatesAndLoad()
    }

    private fun calculateDatesAndLoad() {
        val today = LocalDate.now()

        when (selectedPeriodType) {
            PeriodType.TODAY -> {
                fromDate = today
                toDate = today
                tvCurrentPeriod.text = "Сьогодні"
            }
            PeriodType.YESTERDAY -> {
                fromDate = today.minusDays(1)
                toDate = today.minusDays(1)
                tvCurrentPeriod.text = "Вчора"
            }
            PeriodType.THIS_WEEK -> {
                // Понедельник текущей недели
                fromDate = today.with(DayOfWeek.MONDAY)
                toDate = today
                tvCurrentPeriod.text = "Поточний тиждень"
            }
            PeriodType.LAST_WEEK -> {
                val lastWeek = today.minusWeeks(1)
                fromDate = lastWeek.with(DayOfWeek.MONDAY)
                toDate = lastWeek.with(DayOfWeek.SUNDAY)
                tvCurrentPeriod.text = "Минулий тиждень"
            }
            PeriodType.THIS_MONTH -> {
                fromDate = today.with(TemporalAdjusters.firstDayOfMonth())
                toDate = today
                tvCurrentPeriod.text = "Поточний місяць"
            }
            PeriodType.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                fromDate = lastMonth.with(TemporalAdjusters.firstDayOfMonth())
                toDate = lastMonth.with(TemporalAdjusters.lastDayOfMonth())
                tvCurrentPeriod.text = "Минулий місяць"
            }
        }

        loadStats()
    }

    private fun showPeriodDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_period_bottom_sheet, null)
        dialog.setContentView(view)

        // Настраиваем видимость галочек
        val checkToday = view.findViewById<ImageView>(R.id.check_today)
        val checkYesterday = view.findViewById<ImageView>(R.id.check_yesterday)
        val checkThisWeek = view.findViewById<ImageView>(R.id.check_this_week)
        val checkLastWeek = view.findViewById<ImageView>(R.id.check_last_week)
        val checkThisMonth = view.findViewById<ImageView>(R.id.check_this_month)
        val checkLastMonth = view.findViewById<ImageView>(R.id.check_last_month)

        // Сброс всех
        listOf(checkToday, checkYesterday, checkThisWeek, checkLastWeek, checkThisMonth, checkLastMonth)
            .forEach { it.visibility = View.INVISIBLE }

        // Показываем нужную
        when (selectedPeriodType) {
            PeriodType.TODAY -> checkToday.visibility = View.VISIBLE
            PeriodType.YESTERDAY -> checkYesterday.visibility = View.VISIBLE
            PeriodType.THIS_WEEK -> checkThisWeek.visibility = View.VISIBLE
            PeriodType.LAST_WEEK -> checkLastWeek.visibility = View.VISIBLE
            PeriodType.THIS_MONTH -> checkThisMonth.visibility = View.VISIBLE
            PeriodType.LAST_MONTH -> checkLastMonth.visibility = View.VISIBLE
        }

        // Клики
        view.findViewById<View>(R.id.btn_today).setOnClickListener {
            selectedPeriodType = PeriodType.TODAY
            calculateDatesAndLoad()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_yesterday).setOnClickListener {
            selectedPeriodType = PeriodType.YESTERDAY
            calculateDatesAndLoad()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_this_week).setOnClickListener {
            selectedPeriodType = PeriodType.THIS_WEEK
            calculateDatesAndLoad()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_last_week).setOnClickListener {
            selectedPeriodType = PeriodType.LAST_WEEK
            calculateDatesAndLoad()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_this_month).setOnClickListener {
            selectedPeriodType = PeriodType.THIS_MONTH
            calculateDatesAndLoad()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_last_month).setOnClickListener {
            selectedPeriodType = PeriodType.LAST_MONTH
            calculateDatesAndLoad()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadStats() {
        val apiFormatter = DateTimeFormatter.ISO_DATE // YYYY-MM-DD
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