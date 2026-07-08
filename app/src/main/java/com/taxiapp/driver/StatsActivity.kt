package com.taxiapp.driver

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class StatsActivity : AppCompatActivity() {

    private lateinit var tvCurrentPeriod: TextView
    private lateinit var btnLifetimeStats: ImageView

    // Табы периодов
    private lateinit var tabDay: FrameLayout
    private lateinit var tabWeek: FrameLayout
    private lateinit var tabMonth: FrameLayout
    private lateinit var tabAllTime: FrameLayout
    private lateinit var tvTabDay: TextView
    private lateinit var tvTabWeek: TextView
    private lateinit var tvTabMonth: TextView
    private lateinit var tvTabAllTime: TextView

    // Текстовые поля аналитики
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvIncomeCard: TextView
    private lateinit var tvIncomeCash: TextView
    private lateinit var tvIncomeBalance: TextView
    private lateinit var tvCommission: TextView
    private lateinit var tvOrdersCount: TextView
    private lateinit var tvTotalKm: TextView
    private lateinit var tvAvgPrice: TextView
    private lateinit var tvTotalHours: TextView

    // Блоки отображения состояний
    private lateinit var layoutNoData: LinearLayout
    private lateinit var cardDetailedAnalytics: View
    private lateinit var chartContainer: View
    private lateinit var chartBarsContainer: LinearLayout
    private lateinit var tvChartYMax: TextView
    private lateinit var tvChartYMid: TextView
    private lateinit var tvChartYMin: TextView
    private lateinit var tvChartDateStart: TextView
    private lateinit var tvChartDateEnd: TextView

    // Компоненты интерактивного Smart-Label
    private lateinit var chartTooltip: View
    private lateinit var tvTooltipAmount: TextView
    private lateinit var tvTooltipDate: TextView

    private var selectedPeriodType = PeriodType.TODAY
    private var fromDate: LocalDate = LocalDate.now()
    private var toDate: LocalDate = LocalDate.now()

    enum class PeriodType {
        TODAY, THIS_WEEK, THIS_MONTH, ALL_TIME
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Связываем элементы
        tvCurrentPeriod = findViewById(R.id.tv_current_period)
        btnLifetimeStats = findViewById(R.id.btn_lifetime_stats)

        tabDay = findViewById(R.id.tab_day)
        tabWeek = findViewById(R.id.tab_week)
        tabMonth = findViewById(R.id.tab_month)
        tabAllTime = findViewById(R.id.tab_all_time)
        tvTabDay = findViewById(R.id.tv_tab_day)
        tvTabWeek = findViewById(R.id.tv_tab_week)
        tvTabMonth = findViewById(R.id.tv_tab_month)
        tvTabAllTime = findViewById(R.id.tv_tab_all_time)

        tvTotalIncome = findViewById(R.id.tv_total_income)
        tvIncomeCard = findViewById(R.id.tv_income_card)
        tvIncomeCash = findViewById(R.id.tv_income_cash)
        tvIncomeBalance = findViewById(R.id.tv_income_balance)
        tvCommission = findViewById(R.id.tv_expense_commission)
        tvOrdersCount = findViewById(R.id.tv_orders_count)
        tvTotalKm = findViewById(R.id.tv_total_km)
        tvAvgPrice = findViewById(R.id.tv_avg_price)
        tvTotalHours = findViewById(R.id.tv_total_hours)

        // Состояния
        layoutNoData = findViewById(R.id.layout_no_data)
        cardDetailedAnalytics = findViewById(R.id.card_detailed_analytics)
        chartContainer = findViewById(R.id.chart_container)
        chartBarsContainer = findViewById(R.id.chart_bars_container)
        tvChartYMax = findViewById(R.id.tv_chart_y_max)
        tvChartYMid = findViewById(R.id.tv_chart_y_mid)
        tvChartYMin = findViewById(R.id.tv_chart_y_min)
        tvChartDateStart = findViewById(R.id.tv_chart_date_start)
        tvChartDateEnd = findViewById(R.id.tv_chart_date_end)

        chartTooltip = findViewById(R.id.chart_tooltip)
        tvTooltipAmount = findViewById(R.id.tv_tooltip_amount)
        tvTooltipDate = findViewById(R.id.tv_tooltip_date)

        // Слушатели кликов
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
        tabAllTime.setOnClickListener {
            selectedPeriodType = PeriodType.ALL_TIME
            updateTabsVisuals()
            calculateDatesAndLoad()
        }

        btnLifetimeStats.setOnClickListener {
            showLifetimeStatsDialog()
        }

        updateTabsVisuals()
        calculateDatesAndLoad()
    }

    private fun updateTabsVisuals() {
        val density = resources.displayMetrics.density

        val activeDrawable = GradientDrawable().apply {
            cornerRadius = 10 * density
            setColor(ContextCompat.getColor(this@StatsActivity, R.color.driver_text_primary))
        }

        tabDay.background = null
        tabWeek.background = null
        tabMonth.background = null
        tabAllTime.background = null

        val activeTextColor = ContextCompat.getColor(this, R.color.driver_black_bg)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.driver_text_secondary)

        tvTabDay.setTextColor(inactiveTextColor)
        tvTabWeek.setTextColor(inactiveTextColor)
        tvTabMonth.setTextColor(inactiveTextColor)
        tvTabAllTime.setTextColor(inactiveTextColor)

        when (selectedPeriodType) {
            PeriodType.TODAY -> {
                tabDay.background = activeDrawable
                tvTabDay.setTextColor(activeTextColor)
            }
            PeriodType.THIS_WEEK -> {
                tabWeek.background = activeDrawable
                tvTabWeek.setTextColor(activeTextColor)
            }
            PeriodType.THIS_MONTH -> {
                tabMonth.background = activeDrawable
                tvTabMonth.setTextColor(activeTextColor)
            }
            PeriodType.ALL_TIME -> {
                tabAllTime.background = activeDrawable
                tvTabAllTime.setTextColor(activeTextColor)
            }
        }
    }

    private fun calculateDatesAndLoad() {
        val today = LocalDate.now()
        val axisFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        when (selectedPeriodType) {
            PeriodType.TODAY -> {
                fromDate = today
                toDate = today
                tvCurrentPeriod.text = "Сьогодні"
                tvChartDateStart.text = fromDate.format(axisFormatter)
            }
            PeriodType.THIS_WEEK -> {
                fromDate = today.with(DayOfWeek.MONDAY)
                toDate = today.with(DayOfWeek.SUNDAY)
                tvCurrentPeriod.text = "Поточний тиждень"
                tvChartDateStart.text = fromDate.format(axisFormatter)
            }
            PeriodType.THIS_MONTH -> {
                fromDate = today.with(TemporalAdjusters.firstDayOfMonth())
                toDate = today.with(TemporalAdjusters.lastDayOfMonth())
                tvCurrentPeriod.text = "Поточний місяць"
                tvChartDateStart.text = fromDate.format(axisFormatter)
            }
            PeriodType.ALL_TIME -> {
                fromDate = LocalDate.of(2024, 1, 1)
                toDate = today
                tvCurrentPeriod.text = "За весь час"
                tvChartDateStart.text = "Початок"
            }
        }

        tvChartDateEnd.text = toDate.format(axisFormatter)
        loadStats()
    }

    private fun showLifetimeStatsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_lifetime_stats, null)
        dialog.setContentView(view)

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Оставлена только кнопка действия "Зрозуміло"
        val btnCloseAction = view.findViewById<View>(R.id.btn_close_lifetime)
        val tvOrdersCount = view.findViewById<TextView>(R.id.tv_lifetime_orders_count)

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
                }
            } catch (e: Exception) {
                tvOrdersCount?.text = "0"
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
                    Toast.makeText(this@StatsActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StatsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(stats: com.taxiapp.driver.network.DriverStatsDto) {
        val locale = Locale.US

        if (stats.totalIncome <= 0.0 || stats.ordersCount == 0) {
            chartContainer.visibility = View.GONE
            cardDetailedAnalytics.visibility = View.GONE
            layoutNoData.visibility = View.VISIBLE
        } else {
            chartContainer.visibility = View.VISIBLE
            cardDetailedAnalytics.visibility = View.VISIBLE
            layoutNoData.visibility = View.GONE
        }

        val rawIncomeStr = String.format(locale, "%.2f ₴", stats.totalIncome)
        val spannableIncome = SpannableString(rawIncomeStr)
        val dotIndex = rawIncomeStr.indexOfAny(charArrayOf('.', ','))
        if (dotIndex != -1) {
            spannableIncome.setSpan(RelativeSizeSpan(0.55f), dotIndex, rawIncomeStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvTotalIncome.text = spannableIncome

        tvIncomeCard.text = String.format(locale, "%.2f ₴", stats.incomeCard)
        tvIncomeCash.text = String.format(locale, "%.2f ₴", stats.incomeCash)
        tvIncomeBalance.text = String.format(locale, "%.2f ₴", stats.incomeBalance)
        tvCommission.text = String.format(locale, "- %.2f ₴", stats.commission)
        tvOrdersCount.text = stats.ordersCount.toString()
        tvTotalKm.text = String.format(locale, "%.1f км", stats.totalDistanceKm)
        tvAvgPrice.text = String.format(locale, "%.2f ₴", stats.avgPricePerKm)

        val hours = stats.totalHours.toInt()
        val minutes = ((stats.totalHours - hours) * 60).toInt()
        tvTotalHours.text = String.format(locale, "%dг %dхв", hours, minutes)

        if (chartContainer.visibility == View.VISIBLE) {
            renderDynamicChart(stats.chartPoints ?: emptyList())
        }
    }

    private fun renderDynamicChart(chartPoints: List<com.taxiapp.driver.network.ChartPointDto>) {
        val locale = Locale.US
        val density = resources.displayMetrics.density

        chartBarsContainer.removeAllViews()
        chartTooltip.visibility = View.GONE

        if (selectedPeriodType == PeriodType.ALL_TIME) {
            val monthlyIncomeMap = mutableMapOf<YearMonth, Double>()

            chartPoints.forEach { point ->
                try {
                    val date = LocalDate.parse(point.date, DateTimeFormatter.ISO_DATE)
                    val ym = YearMonth.from(date)
                    monthlyIncomeMap[ym] = monthlyIncomeMap.getOrDefault(ym, 0.0) + point.income
                } catch (e: Exception) {}
            }

            val firstActiveMonth = monthlyIncomeMap.keys.minOrNull() ?: YearMonth.from(fromDate)
            val currentMonth = YearMonth.from(LocalDate.now())
            val totalMonthsCount = (ChronoUnit.MONTHS.between(firstActiveMonth, currentMonth).toInt() + 1).coerceIn(1, 24)

            val monthAxisFormatter = DateTimeFormatter.ofPattern("MM.yyyy")
            tvChartDateStart.text = firstActiveMonth.format(monthAxisFormatter)
            tvChartDateEnd.text = currentMonth.format(monthAxisFormatter)

            val maxMonthValue = monthlyIncomeMap.values.maxOrNull() ?: 0.0
            updateYScaleLabels(maxMonthValue)

            // ДИНАМИЧЕСКИЙ ЗАМЕР: Измеряем точную физическую высоту контейнера на экране
            val containerHeight = if (chartBarsContainer.height > 0) chartBarsContainer.height else (160 * density).toInt()

            for (i in 0 until totalMonthsCount) {
                val targetMonth = firstActiveMonth.plusMonths(i.toLong())
                val incomeValue = monthlyIncomeMap[targetMonth] ?: 0.0

                val bar = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = 4f * density
                    cardElevation = 0f
                    strokeWidth = 0
                    setCardBackgroundColor(ContextCompat.getColor(this@StatsActivity, R.color.purple_500))
                }

                val layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                }

                if (incomeValue > 0.0) {
                    bar.visibility = View.VISIBLE
                    // Расчет высоты пиксель-в-пиксель, на 100% совпадающий со шкалой Y
                    val calculatedHeight = ((incomeValue / maxMonthValue) * containerHeight).toInt().coerceIn((10 * density).toInt(), containerHeight)
                    layoutParams.height = calculatedHeight

                    val monthLabelStr = targetMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("uk", "UA"))).replaceFirstChar { it.uppercase() }

                    bar.setOnClickListener {
                        tvTooltipAmount.text = String.format(locale, "%.2f ₴", incomeValue)
                        tvTooltipDate.text = monthLabelStr
                        showSmartTooltip(bar)
                    }
                } else {
                    bar.visibility = View.INVISIBLE
                    layoutParams.height = (10 * density).toInt()
                }

                bar.layoutParams = layoutParams
                chartBarsContainer.addView(bar)
            }

        } else {
            val serverDataMap = chartPoints.associate { it.date to it.income }

            val barsCount = when (selectedPeriodType) {
                PeriodType.TODAY -> 1
                PeriodType.THIS_WEEK -> 7
                PeriodType.THIS_MONTH -> fromDate.lengthOfMonth()
                else -> 7
            }

            val maxPointValue = chartPoints.maxOfOrNull { it.income } ?: 0.0
            updateYScaleLabels(maxPointValue)

            // ДИНАМИЧЕСКИЙ ЗАМЕР: Измеряем точную физическую высоту контейнера на экране
            val containerHeight = if (chartBarsContainer.height > 0) chartBarsContainer.height else (160 * density).toInt()

            for (index in 0 until barsCount) {
                val slotDate = fromDate.plusDays(index.toLong())
                val slotDateStr = slotDate.format(DateTimeFormatter.ISO_DATE)
                val value = serverDataMap[slotDateStr] ?: 0.0

                val bar = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = if (barsCount >= 30) 1.5f * density else 5f * density
                    cardElevation = 0f
                    strokeWidth = 0
                    setCardBackgroundColor(ContextCompat.getColor(this@StatsActivity, R.color.purple_500))
                }

                val marginPx = if (barsCount >= 30) (1 * density).toInt() else (4 * density).toInt()
                val layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }

                if (value > 0.0) {
                    bar.visibility = View.VISIBLE
                    // Расчет высоты пиксель-в-пиксель, на 100% совпадающий со шкалой Y
                    val calculatedHeight = ((value / maxPointValue) * containerHeight).toInt().coerceIn((10 * density).toInt(), containerHeight)
                    layoutParams.height = calculatedHeight

                    val dateLabelStr = slotDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    bar.setOnClickListener {
                        tvTooltipAmount.text = String.format(locale, "%.2f ₴", value)
                        tvTooltipDate.text = dateLabelStr
                        showSmartTooltip(bar)
                    }
                } else {
                    bar.visibility = View.INVISIBLE
                    layoutParams.height = (10 * density).toInt()
                }

                bar.layoutParams = layoutParams
                chartBarsContainer.addView(bar)
            }
        }
    }

    private fun updateYScaleLabels(maxValue: Double) {
        val locale = Locale.US
        if (maxValue > 0) {
            tvChartYMax.text = String.format(locale, "%.2f ₴", maxValue)
            tvChartYMid.text = String.format(locale, "%.2f ₴", maxValue / 2)
        } else {
            tvChartYMax.text = "0.00 ₴"
            tvChartYMid.text = "0.00 ₴"
        }
        tvChartYMin.text = "0 ₴"
    }

    private fun showSmartTooltip(bar: View) {
        val density = resources.displayMetrics.density
        chartTooltip.visibility = View.VISIBLE
        chartTooltip.post {
            val barCenterX = bar.x + (bar.width / 2f)
            var tooltipX = barCenterX - (chartTooltip.width / 2f)

            val maxAllowedX = (chartContainer.width - chartTooltip.width).toFloat()
            tooltipX = tooltipX.coerceIn(0f, maxAllowedX)

            chartTooltip.translationX = tooltipX

            val targetY = bar.y - chartTooltip.height - (4 * density)
            chartTooltip.translationY = maxOf(0f, targetY)
        }
    }
}