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
import org.json.JSONObject // <--- ДОДАНО ІМПОРТ
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

        // --- ІНІЦІАЛІЗАЦІЯ АДАПТЕРА ---
        adapter = OrderAdapter { selectedOrder ->
            // Клік по картці відкриває деталі
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }
        rvOrders.adapter = adapter

        btnBack.setOnClickListener { finish() }

        setupWebSocket()
    }

    // --- ЛОГІКА ПРИЙНЯТТЯ ---

    private fun acceptScheduledOrder(order: Order) {
        pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).acceptOrder(order.id)

                if (response.isSuccessful) {
                    Toast.makeText(this@EtherActivity, "Ви забронювали замовлення!", Toast.LENGTH_SHORT).show()

                    removeOrderFromList(order.id)

                    val intent = Intent(this@EtherActivity, OrdersActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: ""
                    if (errorMsg.contains("Conflict") || response.code() == 409) {
                        Toast.makeText(this@EtherActivity, "Замовлення вже зайняте", Toast.LENGTH_SHORT).show()
                        removeOrderFromList(order.id)
                    } else {
                        Toast.makeText(this@EtherActivity, "Помилка: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@EtherActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun acceptOrder(order: Order) {
        acceptScheduledOrder(order)
    }

    private fun removeOrderFromList(orderId: Long) {
        allOrdersList.removeAll { it.id == orderId }
        filterAndShowOrders()
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
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).getAvailableOrders()
                val list = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()

                allOrdersList.clear()
                allOrdersList.addAll(list)
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
                if (topicMessage.payload == null || topicMessage.payload == "null") return@subscribe

                try {
                    val msgObj = JSONObject(topicMessage.payload)
                    val action = msgObj.optString("action")
                    val orderId = msgObj.optLong("orderId")

                    if (action == "REMOVE") {
                        removeOrderFromList(orderId)
                    } else if (action == "ADD") {
                        val orderJson = msgObj.optJSONObject("order")?.toString()
                        if (orderJson != null) {
                            // ВИПРАВЛЕНО: Явне приведення типу для усунення неоднозначності Gson
                            val newOrder = Gson().fromJson(orderJson as String, Order::class.java)
                            handleSocketOrderUpdate(newOrder)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    private fun handleSocketOrderUpdate(order: Order) {
        // Якщо замовлення не в статусі пошуку або заплановане - прибираємо
        if (order.status != "REQUESTED" && order.status != "SCHEDULED") {
            removeOrderFromList(order.id)
            return
        }

        allOrdersList.removeAll { it.id == order.id }
        allOrdersList.add(0, order)
        allOrdersList.sortByDescending { it.id }

        filterAndShowOrders()
    }

    private fun updateUI() {
        filterAndShowOrders()
    }

    private fun filterAndShowOrders() {
        val filtered = if (currentTabIndex == 0) {
            // Вкладка "Зараз"
            allOrdersList.filter { !it.isScheduled() }
        } else {
            // Вкладка "Заплановані"
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