package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EtherActivity : AppCompatActivity() {

    private lateinit var adapter: OrderAdapter
    private lateinit var rvOrders: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var emptyState: View

    // Для авто-обновления (Polling)
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchOrders()
            // Повторять каждые 5 секунд (5000 мс)
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        // Инициализация UI
        rvOrders = findViewById(R.id.rv_orders_list)
        pbLoading = findViewById(R.id.pb_loading)
        emptyState = findViewById(R.id.ll_empty_state)
        val btnBack = findViewById<View>(R.id.btn_back)

        rvOrders.layoutManager = LinearLayoutManager(this)

        adapter = OrderAdapter { selectedOrder ->
            // При клике открываем детали
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }
        rvOrders.adapter = adapter

        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Начинаем загрузку при открытии экрана
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        // Останавливаем загрузку, когда экран свернут (чтобы не садить батарею)
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        // Сразу запускаем первую загрузку
        fetchOrders()
        // И планируем следующие
        handler.postDelayed(refreshRunnable, 5000)
    }

    private fun stopAutoRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun fetchOrders() {
        // Если список пуст, показываем крутилку (только в первый раз)
        if (adapter.itemCount == 0) {
            // pbLoading.visibility = View.VISIBLE // Можно включить, если хотите видеть лоадер
        }

        ApiClient.getInstance().getApiService(this).getAvailableOrders().enqueue(object : Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                pbLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val orders = response.body() ?: emptyList()
                    updateList(orders)
                } else {
                    // Ошибка сервера (например 401 или 500)
                    // Можно показать Toast, но не слишком часто
                }
            }

            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                pbLoading.visibility = View.GONE
                // Ошибка сети - просто игнорируем и попробуем через 5 сек
            }
        })
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