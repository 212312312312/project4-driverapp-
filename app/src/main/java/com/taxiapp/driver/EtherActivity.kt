package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order // Використовуємо твій клас
import com.taxiapp.driver.utils.SessionManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient

class EtherActivity : AppCompatActivity() {

    private lateinit var stompClient: StompClient
    private val compositeDisposable = CompositeDisposable()
    private lateinit var sessionManager: SessionManager

    private lateinit var adapter: OrderAdapter
    private lateinit var rvOrders: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var emptyState: View

    // Тепер список працює з твоїм класом Order
    private val ordersList = mutableListOf<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        sessionManager = SessionManager(this)

        rvOrders = findViewById(R.id.rv_orders_list)
        pbLoading = findViewById(R.id.pb_loading)
        emptyState = findViewById(R.id.ll_empty_state)
        val btnBack = findViewById<View>(R.id.btn_back)

        rvOrders.layoutManager = LinearLayoutManager(this)

        // Налаштування адаптера
        adapter = OrderAdapter { selectedOrder ->
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }
        rvOrders.adapter = adapter

        btnBack.setOnClickListener { finish() }

        setupWebSocket()
    }

    override fun onResume() {
        super.onResume()
        stompClient.connect()
        fetchOrders()
    }

    override fun onPause() {
        super.onPause()
        stompClient.disconnect()
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    private fun fetchOrders() {
        pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Виклик API через Retrofit
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).getAvailableOrders()
                pbLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val orders = response.body() ?: emptyList()
                    ordersList.clear()
                    ordersList.addAll(orders)
                    updateUI()
                }
            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
                Log.e("EtherActivity", "Помилка завантаження: ${e.message}")
            }
        }
    }

    private fun setupWebSocket() {
        // Заміни на актуальний IP твого сервера
        val url = "ws://192.168.0.104:8080/ws-taxi/websocket"
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url)

        val driverId = sessionManager.getDriverId()

        val topicDisposable = stompClient.topic("/topic/drivers/$driverId/orders")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ topicMessage ->
                // Використовуємо Order::class.java для десеріалізації
                val newOrder = Gson().fromJson(topicMessage.payload, Order::class.java)
                addNewOrder(newOrder)
            }, { error ->
                Log.e("WS", "Помилка підписки: ${error.message}")
            })

        val lifecycleDisposable = stompClient.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lifecycleEvent ->
                when (lifecycleEvent.type) {
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.OPENED -> Log.d("WS", "Connected")
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.ERROR -> Log.e("WS", "Error", lifecycleEvent.exception)
                    else -> {}
                }
            }

        compositeDisposable.add(topicDisposable)
        compositeDisposable.add(lifecycleDisposable)
    }

    private fun addNewOrder(order: Order) {
        // Виправлено помилку "it": явно вказуємо об'єкт перевірки
        if (ordersList.none { existing -> existing.id == order.id }) {
            ordersList.add(0, order)
            updateUI()
            Toast.makeText(this, "Нове замовлення!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if (ordersList.isNotEmpty()) {
            rvOrders.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter.submitList(ordersList.toList())
        } else {
            rvOrders.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }
}