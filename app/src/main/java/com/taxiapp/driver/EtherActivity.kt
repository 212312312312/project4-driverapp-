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
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
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
    private lateinit var tabLayout: TabLayout

    private val allOrdersList = mutableListOf<Order>()
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        sessionManager = SessionManager(this)

        rvOrders = findViewById(R.id.rv_orders_list)
        pbLoading = findViewById(R.id.pb_loading)
        emptyState = findViewById(R.id.ll_empty_state)
        tabLayout = findViewById(R.id.ether_tabs)
        val btnBack = findViewById<View>(R.id.btn_back)

        // Настройка табов
        tabLayout.addTab(tabLayout.newTab().setText("Зараз"))
        tabLayout.addTab(tabLayout.newTab().setText("Заплановані"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabIndex = tab?.position ?: 0
                filterAndShowOrders()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        rvOrders.layoutManager = LinearLayoutManager(this)

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
        adapter.updateDisplaySettings(
            sectorFirst = sessionManager.isEtherSectorFirst(),
            hidePricePerKm = sessionManager.isEtherPricePerKmHidden()
        )
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
                // 1. Обычные заказы
                val responseActive = ApiClient.getInstance().getApiService(this@EtherActivity).getAvailableOrders()
                val activeList = if (responseActive.isSuccessful) responseActive.body() ?: emptyList() else emptyList()

                // 2. Запланированные (Предположим, тот же метод возвращает и их, или сервер шлет все)
                // Если у тебя на сервере getAvailableOrders фильтрует только REQUESTED,
                // то запланированные придут только через сокет или если сервер их включает.
                // В OrderService.getFilteredOrdersForDriver мы берем REQUESTED.
                // Для SCHEDULED нужна отдельная ручка или логика на сервере.
                // ПОКА работаем с тем, что есть: считаем, что сервер может прислать SCHEDULED

                allOrdersList.clear()
                allOrdersList.addAll(activeList)

                // Сортировка: Срочные сверху
                allOrdersList.sortByDescending { it.id }

                updateUI()
            } catch (e: Exception) {
                Log.e("EtherActivity", "Помилка: ${e.message}")
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun setupWebSocket() {
        val url = "ws://192.168.0.104:8080/ws-taxi/websocket"
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url)

        val driverId = sessionManager.getDriverId()

        val topicDisposable = stompClient.topic("/topic/drivers/$driverId/orders")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ topicMessage ->
                val newOrder = Gson().fromJson(topicMessage.payload, Order::class.java)
                addNewOrder(newOrder)
            }, { error ->
                Log.e("WS", "Error: ${error.message}")
            })

        val lifecycleDisposable = stompClient.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lifecycleEvent ->
                when (lifecycleEvent.type) {
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.OPENED -> Log.d("WS", "Connected")
                    else -> {}
                }
            }

        compositeDisposable.add(topicDisposable)
        compositeDisposable.add(lifecycleDisposable)
    }

    private fun addNewOrder(order: Order) {
        // Удаляем старую версию, если есть
        allOrdersList.removeAll { it.id == order.id }

        // Добавляем новую, если она подходит (например, не CANCELLED)
        if (order.status != "CANCELLED" && order.status != "COMPLETED") {
            allOrdersList.add(0, order)
            Toast.makeText(this, "Нове замовлення!", Toast.LENGTH_SHORT).show()
        }

        updateUI()
    }

    private fun updateUI() {
        filterAndShowOrders()
    }

    private fun filterAndShowOrders() {
        val filtered = if (currentTabIndex == 0) {
            // Вкладка "Зараз": REQUESTED (и другие активные, если вдруг попадут)
            allOrdersList.filter { !it.isScheduled() }
        } else {
            // Вкладка "Заплановані": SCHEDULED
            allOrdersList.filter { it.isScheduled() }
        }

        if (filtered.isNotEmpty()) {
            rvOrders.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter.submitList(filtered)
        } else {
            rvOrders.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }
}