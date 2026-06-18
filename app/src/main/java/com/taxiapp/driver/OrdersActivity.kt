package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.util.SparseArray // <-- Добавили для кеширования страниц холдера
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2 // <-- Добавили поддержку ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator // <-- Добавили медиатор связи
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrdersActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2 // <-- Поля для пейджера
    private lateinit var pagerAdapter: OrdersPagerAdapter // <-- Наш умный адаптер
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
        viewPager = findViewById(R.id.orders_view_pager) // <-- Инициализируем пейджер
        progressBar = findViewById(R.id.progressBar)

        pagerAdapter = OrdersPagerAdapter() // <-- Привязываем адаптер свайпов
        viewPager.adapter = pagerAdapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupListeners() {
        // Связываем TabLayout и ViewPager2 свайпы в единую систему (как в Эфире)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.orders_tab_active) else getString(R.string.orders_tab_history)
        }.attach()

        // Слушатель смены страниц: автоматически подгружает данные при свайпе
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
                updateActiveOrderUI() // Обновляем элементы на странице
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                currentOrder = null
                updateActiveOrderUI()
            }
        }
    }

    // --- ФУНКЦИЯ ОБНОВЛЕНИЯ ДИНАМИЧЕСКИХ ДАННЫХ ДЛЯ ХОЛДЕРА СТРАНИЦЫ 0 ---
    private fun updateActiveOrderUI() {
        val holder = pagerAdapter.getHolder(0) ?: return // Если холдер страницы еще не создан, выйдем
        val order = currentOrder

        if (order != null) {
            holder.cardActiveOrder.visibility = View.VISIBLE
            holder.tvNoActive.visibility = View.GONE

            if (order.status == "OFFERING") {
                holder.tvActiveRoute.text = "⚡ Нова пропозиція! (${order.tariffName})"
                holder.tvActiveRoute.setTextColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
                holder.btnOpenActive.text = "Переглянути"
                holder.btnOpenActive.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            }
            else if (order.status == "SCHEDULED") {
                val time = order.scheduledAt?.replace("T", " ")?.take(16) ?: ""
                holder.tvActiveRoute.text = "⏳ Чекайте часу подачі: $time"
                holder.tvActiveRoute.setTextColor(ContextCompat.getColor(this, R.color.taxi_yellow))

                holder.btnOpenActive.text = "Деталі (Чекаємо)"
                holder.btnOpenActive.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            }
            else {
                holder.tvActiveRoute.text = "${order.fromAddress} -> ${order.toAddress}"
                holder.tvActiveRoute.setTextColor(ContextCompat.getColor(this, android.R.color.white))
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
                // Передаем точечный флаг: этот заказ открыт из списка НАШИХ принятых заказов
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

    // --- ВНУТРЕННИЙ АДАПТЕР СТРАНИЦ VIEW_PAGER_2 С АВТО-КЕШИРОВАНИЕМ (ПО СТРУКТУРЕ ЭФИРА) ---
    private inner class OrdersPagerAdapter : RecyclerView.Adapter<OrdersPagerAdapter.PageViewHolder>() {

        private val viewHolders = SparseArray<PageViewHolder>()

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val containerActive: FrameLayout = view.findViewById(R.id.container_active)
            val cardActiveOrder: CardView = view.findViewById(R.id.card_active_order)
            val tvActiveRoute: TextView = view.findViewById(R.id.tv_active_route)
            val btnOpenActive: Button = view.findViewById(R.id.btn_open_active)
            val tvNoActive: TextView = view.findViewById(R.id.tv_no_active)
            val rvHistory: RecyclerView = view.findViewById(R.id.rv_history)
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
                updateActiveOrderUI() // Инициализируем UI активного заказа при привязке
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