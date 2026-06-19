package com.taxiapp.driver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class OrdersActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: OrdersPagerAdapter
    private lateinit var progressBar: ProgressBar

    private val historyAdapter = HistoryOrderAdapter { order ->
        val intent = Intent(this, HistoryDetailsActivity::class.java)
        intent.putExtra("EXTRA_ORDER", order)
        startActivity(intent)
    }

    private var currentOrder: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.orders_tabs)
        viewPager = findViewById(R.id.orders_view_pager)
        progressBar = findViewById(R.id.progressBar)

        pagerAdapter = OrdersPagerAdapter()
        viewPager.adapter = pagerAdapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupListeners() {
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.orders_tab_active) else getString(R.string.orders_tab_history)
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    loadActiveOrder()
                } else {
                    loadHistory()
                }
            }
        })
    }

    private fun loadActiveOrder() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrdersActivity).getActiveOrder()
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    currentOrder = response.body()
                } else {
                    currentOrder = null
                }
                updateActiveOrderUI()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                currentOrder = null
                updateActiveOrderUI()
            }
        }
    }

    private fun updateActiveOrderUI() {
        val holder = pagerAdapter.getHolder(0) ?: return
        val order = currentOrder

        if (order != null) {
            holder.cardActiveOrder.visibility = View.VISIBLE
            holder.tvNoActive.visibility = View.GONE

            // 1. Установка стоимости и тарифа поездки
            val fullPrice = order.getTotalFullPrice()
            holder.tvPrice.text = "${fullPrice.toInt()} ₴"
            holder.tvTariffBadge.text = order.tariffName

            // 2. Расчет цены за км по логике короткого маршрута
            val meters = order.distanceMeters ?: 0
            if (meters >= 1000) {
                val km = meters / 1000.0
                val calculatedPricePerKm = fullPrice / km
                holder.tvPricePerKm.text = String.format(Locale.US, "%.2f ₴/км", calculatedPricePerKm)
                holder.tvPricePerKm.visibility = View.VISIBLE
            } else {
                holder.tvPricePerKm.text = "- ₴/км"
                holder.tvPricePerKm.visibility = View.VISIBLE
            }

            // 3. Расстояние поездки
            holder.tvDistance.text = order.getFormattedDistance()
            holder.tvDistance.setTextColor(ContextCompat.getColor(this, R.color.driver_text_primary))

            // 4. Логика адресов и секторов (Стандартное отображение: адрес крупно, сектор ниже)
            holder.tvAddressFrom.text = order.fromAddress
            if (!order.fromSector.isNullOrEmpty()) {
                holder.tvSectorFrom.text = order.fromSector
                holder.tvSectorFrom.visibility = View.VISIBLE
            } else {
                holder.tvSectorFrom.visibility = View.GONE
            }

            holder.tvAddressTo.text = order.toAddress
            if (!order.toSector.isNullOrEmpty()) {
                holder.tvSectorTo.text = order.toSector
                holder.tvSectorTo.visibility = View.VISIBLE
            } else {
                holder.tvSectorTo.visibility = View.GONE
            }

            // 5. Бонусные баллы активности за выполнение
            val bonus = order.activityBonus
            holder.tvActivityBonus.text = if (bonus >= 0) "+$bonus" else "$bonus"

            // 6. Динамический рендеринг промежуточных остановок
            holder.stopsContainer.removeAllViews()
            if (!order.stops.isNullOrEmpty()) {
                val inflater = LayoutInflater.from(this)
                val sortedStops = order.stops.sortedBy { it.stopOrder }
                for (stop in sortedStops) {
                    val stopView = inflater.inflate(R.layout.item_route_point, holder.stopsContainer, false)
                    val tvAddress = stopView.findViewById<TextView>(R.id.tv_point_address)
                    val ivIcon = stopView.findViewById<ImageView>(R.id.iv_point_icon)
                    val lineTop = stopView.findViewById<View>(R.id.view_line_top)
                    val lineBottom = stopView.findViewById<View>(R.id.view_line_bottom)

                    tvAddress.text = stop.address
                    ivIcon.setImageResource(R.drawable.ic_marker_waypoint)
                    ivIcon.clearColorFilter()
                    lineTop.visibility = View.GONE
                    lineBottom.visibility = View.GONE

                    holder.stopsContainer.addView(stopView)
                }
            }

            // 7. Динамическое оформление типа оплаты и цвета плашки цены
            val method = order.paymentMethod ?: "CASH"
            if (method == "CASH") {
                val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
                holder.llPriceBg.backgroundTintList = ColorStateList.valueOf(neonTeal)
                holder.ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
            } else {
                holder.llPriceBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#54b1f0"))
                holder.ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
            }
            holder.tvPrice.setTextColor(Color.BLACK)
            holder.ivPaymentIcon.setColorFilter(Color.BLACK)

            // 8. Логика таймера/даты подачи для запланированных предварительных заказов
            if (order.isScheduled()) {
                val scheduledDate = order.getScheduledDate()
                if (scheduledDate != null) {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val orderDay = Calendar.getInstance().apply {
                        time = scheduledDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val diffMillis = orderDay.timeInMillis - today.timeInMillis
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()

                    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
                    val timeStr = timeFormat.format(scheduledDate)

                    val displayStr = when {
                        diffDays <= 0 -> timeStr
                        diffDays == 1 -> "Завтра, $timeStr"
                        else -> {
                            val ukrLocale = Locale("uk")
                            val dateFormat = SimpleDateFormat("d MMM, HH:mm", ukrLocale)
                            dateFormat.format(scheduledDate)
                        }
                    }
                    holder.tvScheduledTime.text = displayStr
                } else {
                    val timeOnly = try {
                        order.scheduledAt?.substring(11, 16) ?: ""
                    } catch (e: Exception) { "" }
                    holder.tvScheduledTime.text = timeOnly
                }
                holder.tvScheduledTime.visibility = View.VISIBLE
            } else {
                holder.tvScheduledTime.visibility = View.GONE
            }

            // 9. Стилизация кнопки действия и сохранение состояний бизнес-статусов
            if (order.status == "OFFERING") {
                holder.btnOpenActive.text = "Переглянути"
                holder.btnOpenActive.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            } else if (order.status == "SCHEDULED") {
                holder.btnOpenActive.text = "Деталі (Чекаємо)"
                holder.btnOpenActive.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            } else {
                holder.btnOpenActive.text = "Відкрити"
                holder.btnOpenActive.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            }

            holder.btnOpenActive.setOnClickListener { handleActiveOrderClick() }
        } else {
            holder.cardActiveOrder.visibility = View.GONE
            holder.tvNoActive.visibility = View.VISIBLE
        }
    }

    private fun handleActiveOrderClick() {
        currentOrder?.let { order ->
            if (order.status == "OFFERING") {
                val intent = Intent(this, OrderOfferActivity::class.java)
                intent.putExtra("EXTRA_ORDER", order)
                startActivity(intent)
                finish()
            } else if (order.status == "SCHEDULED") {
                val intent = Intent(this, OrderDetailsActivity::class.java)
                intent.putExtra("EXTRA_ORDER", order)
                intent.putExtra("EXTRA_HIDE_ACCEPT_BUTTON", true)
                startActivity(intent)
            } else {
                val intent = Intent(this, OrderProgressActivity::class.java)
                intent.putExtra("EXTRA_ORDER", order)
                startActivity(intent)
            }
        }
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrdersActivity).getOrderHistory()
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    historyAdapter.submitList(response.body())
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
            }
        }
    }

    private inner class OrdersPagerAdapter : RecyclerView.Adapter<OrdersPagerAdapter.PageViewHolder>() {

        private val viewHolders = SparseArray<PageViewHolder>()

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val containerActive: FrameLayout = view.findViewById(R.id.container_active)
            val cardActiveOrder: CardView = view.findViewById(R.id.card_active_order)
            val rvHistory: RecyclerView = view.findViewById(R.id.rv_history)
            val tvNoActive: TextView = view.findViewById(R.id.tv_no_active)
            val btnOpenActive: Button = view.findViewById(R.id.btn_open_active)

            // Новые поля из item_order разметки карточки
            val llPriceBg: LinearLayout = view.findViewById(R.id.ll_price_background)
            val ivPaymentIcon: ImageView = view.findViewById(R.id.iv_payment_icon)
            val tvPrice: TextView = view.findViewById(R.id.tv_price)
            val tvPricePerKm: TextView = view.findViewById(R.id.tv_price_per_km)
            val tvAddressFrom: TextView = view.findViewById(R.id.tv_address_from)
            val tvSectorFrom: TextView = view.findViewById(R.id.tv_sector_from)
            val stopsContainer: LinearLayout = view.findViewById(R.id.ll_stops_container)
            val tvAddressTo: TextView = view.findViewById(R.id.tv_address_to)
            val tvSectorTo: TextView = view.findViewById(R.id.tv_sector_to)
            val tvTariffBadge: TextView = view.findViewById(R.id.tv_tariff_badge)
            val tvActivityBonus: TextView = view.findViewById(R.id.tv_activity_bonus)
            val tvScheduledTime: TextView = view.findViewById(R.id.tv_scheduled_time)
            val tvDistance: TextView = view.findViewById(R.id.tv_distance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_orders_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            viewHolders.put(position, holder)

            if (position == 0) {
                holder.containerActive.visibility = View.VISIBLE
                holder.rvHistory.visibility = View.GONE
                updateActiveOrderUI()
            } else {
                holder.containerActive.visibility = View.GONE
                holder.rvHistory.visibility = View.VISIBLE
                holder.rvHistory.layoutManager = LinearLayoutManager(this@OrdersActivity)
                holder.rvHistory.adapter = historyAdapter
            }
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val index = viewHolders.indexOfValue(holder)
            if (index >= 0) {
                viewHolders.removeAt(index)
            }
            super.onViewRecycled(holder)
        }

        fun getHolder(position: Int): PageViewHolder? = viewHolders.get(position)

        override fun getItemCount(): Int = 2
    }
}