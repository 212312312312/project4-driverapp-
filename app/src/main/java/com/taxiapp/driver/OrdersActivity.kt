package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrdersActivity : AppCompatActivity() {

    private lateinit var tabActive: TextView
    private lateinit var tabHistory: TextView
    private lateinit var containerActive: FrameLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Элементы активного заказа
    private lateinit var cardActive: CardView
    private lateinit var tvActiveRoute: TextView
    private lateinit var btnOpenActive: Button
    private lateinit var tvNoActive: TextView

    private val historyAdapter = HistoryOrderAdapter { order ->
        // Открытие деталей истории
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

        // По умолчанию открываем Активные
        switchTab(true)
    }

    private fun initViews() {
        tabActive = findViewById(R.id.tab_active)
        tabHistory = findViewById(R.id.tab_history)
        containerActive = findViewById(R.id.container_active)
        rvHistory = findViewById(R.id.rv_history)
        progressBar = findViewById(R.id.progressBar)

        cardActive = findViewById(R.id.card_active_order)
        tvActiveRoute = findViewById(R.id.tv_active_route)
        btnOpenActive = findViewById(R.id.btn_open_active)
        tvNoActive = findViewById(R.id.tv_no_active)

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupListeners() {
        tabActive.setOnClickListener { switchTab(true) }
        tabHistory.setOnClickListener { switchTab(false) }

        btnOpenActive.setOnClickListener {
            currentOrder?.let { order ->
                val intent = Intent(this, OrderProgressActivity::class.java)
                intent.putExtra("EXTRA_ORDER", order) // Передаем объект
                startActivity(intent)
            }
        }
    }

    private fun switchTab(isActive: Boolean) {
        if (isActive) {
            // Стиль кнопок
            tabActive.background = ContextCompat.getDrawable(this, R.drawable.bg_status_pill)
            tabActive.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            tabActive.setTextColor(ContextCompat.getColor(this, R.color.driver_black_bg))

            tabHistory.background = null
            tabHistory.setTextColor(ContextCompat.getColor(this, R.color.driver_text_secondary))

            // Видимость контейнеров
            containerActive.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE

            loadActiveOrder()
        } else {
            tabHistory.background = ContextCompat.getDrawable(this, R.drawable.bg_status_pill)
            tabHistory.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            tabHistory.setTextColor(ContextCompat.getColor(this, R.color.driver_black_bg))

            tabActive.background = null
            tabActive.setTextColor(ContextCompat.getColor(this, R.color.driver_text_secondary))

            containerActive.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE

            loadHistory()
        }
    }

    private fun loadActiveOrder() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrdersActivity).getActiveOrder()
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    currentOrder = response.body()
                    cardActive.visibility = View.VISIBLE
                    tvNoActive.visibility = View.GONE
                    tvActiveRoute.text = "${currentOrder?.fromAddress} -> ${currentOrder?.toAddress}"
                } else {
                    cardActive.visibility = View.GONE
                    tvNoActive.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                cardActive.visibility = View.GONE
                tvNoActive.visibility = View.VISIBLE
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
}