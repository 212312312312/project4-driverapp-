package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class EtherActivity : AppCompatActivity() {

    private lateinit var adapter: OrderAdapter
    private lateinit var rvOrders: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var emptyState: View

    // Авто-обновление (Polling)
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchOrders()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        // Инициализация
        rvOrders = findViewById(R.id.rv_orders_list)
        pbLoading = findViewById(R.id.pb_loading)
        emptyState = findViewById(R.id.ll_empty_state)
        val btnBack = findViewById<View>(R.id.btn_back)

        rvOrders.layoutManager = LinearLayoutManager(this)

        adapter = OrderAdapter { selectedOrder ->
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }
        rvOrders.adapter = adapter

        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        fetchOrders()
        handler.postDelayed(refreshRunnable, 5000)
    }

    private fun stopAutoRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun fetchOrders() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).getAvailableOrders()
                pbLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val orders = response.body() ?: emptyList()
                    updateList(orders)
                }
            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun updateList(orders: List<Order>) {
        if (orders.isNotEmpty()) {
            rvOrders.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter.submitList(orders)
        } else {
            rvOrders.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }
}