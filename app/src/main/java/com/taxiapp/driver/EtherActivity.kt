package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient

class EtherActivity : AppCompatActivity() {

    private lateinit var stompClient: StompClient
    private val compositeDisposable = CompositeDisposable()
    private lateinit var sessionManager: SessionManager

    // Два отдельных адаптера для параллельного независимого отображения списков
    private lateinit var activeAdapter: OrderAdapter
    private lateinit var scheduledAdapter: OrderAdapter

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: EtherPagerAdapter
    private lateinit var pbLoading: ProgressBar
    private lateinit var tabLayout: TabLayout

    private val allOrdersList = mutableListOf<Order>()
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ для сохранения Edge-to-Edge фона на Android 15
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        viewPager = findViewById(R.id.ether_view_pager)
        pbLoading = findViewById(R.id.pb_loading)
        tabLayout = findViewById(R.id.ether_tabs)
        val btnBack = findViewById<View>(R.id.btn_back)

        activeAdapter = OrderAdapter { selectedOrder ->
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }

        scheduledAdapter = OrderAdapter { selectedOrder ->
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("EXTRA_ORDER", selectedOrder)
            startActivity(intent)
        }

        pagerAdapter = EtherPagerAdapter()
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "Зараз" else "Заплановані"
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentTabIndex = position
            }
        })

        btnBack.setOnClickListener { finish() }
    }

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

    // ИСПРАВЛЕНО: Тип параметра изменен на String под UUID
    private fun removeOrderFromList(orderId: String) {
        allOrdersList.removeAll { it.id == orderId }
        filterAndShowOrders()
    }

    override fun onResume() {
        super.onResume()

        // TOЧЕЧНОЕ ОБНОВЛЕНИЕ: Пересоздаем подписки и включаем сокет при каждом возврате на экран
        setupWebSocket()
        stompClient.connect()

        val sectorFirst = sessionManager.isEtherSectorFirst()
        val hidePrice = sessionManager.isEtherPricePerKmHidden()
        activeAdapter.updateDisplaySettings(sectorFirst, hidePrice)
        scheduledAdapter.updateDisplaySettings(sectorFirst, hidePrice)

        fetchOrders()
    }

    override fun onPause() {
        super.onPause()
        // TOЧЕЧНОЕ ОБНОВЛЕНИЕ: Жестко отключаем сокет и ОЧИЩАЕМ подписки для предотвращения дублирования
        stompClient.disconnect()
        compositeDisposable.clear()
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
                // ИСПРАВЛЕНО: Сортируем по числовому idLong, чтобы сохранить правильный хронологический порядок
                allOrdersList.sortByDescending { it.idLong ?: 0L }

                updateUI()
            } catch (e: Exception) {
                Log.e("EtherActivity", "Помилка: ${e.message}")
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun setupWebSocket() {
        // --- УМНОЕ ОБНОВЛЕНИЕ: Динамически переключаем сокет на основе BuildConfig.BASE_URL ---
        val baseUrl = BuildConfig.BASE_URL // Берем адрес (например, "https://decorous-tempie-nonsubjugable.ngrok-free.dev/" или "http://192.168.0.104:8080/")
        val wsProtocol = if (baseUrl.startsWith("https")) "wss" else "ws"

        // Очищаем адрес от префиксов и завершающих слешей, чтобы строка склеилась идеально
        val cleanHost = baseUrl.replace("https://", "").replace("http://", "").removeSuffix("/")
        val url = "$wsProtocol://$cleanHost/ws-taxi/websocket"

        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url)
        Log.d("WS", "Попытка подключения к сокету Эфира: $url")
        // -------------------------------------------------------------------------------------

        // TOЧЕЧНОЕ ОБНОВЛЕНИЕ: Слушаем общий широковещательный канал Эфира
        val topicDisposable = stompClient.topic("/topic/ether")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ topicMessage ->
                if (topicMessage.payload == null || topicMessage.payload == "null") return@subscribe

                try {
                    val msgObj = JSONObject(topicMessage.payload)
                    val action = msgObj.optString("action")
                    val orderId = msgObj.optString("orderId")

                    if (action == "REMOVE") {
                        removeOrderFromList(orderId)
                    } else if (action == "ADD") {
                        val orderJson = msgObj.optJSONObject("order")?.toString()
                        if (orderJson != null) {
                            val newOrder = Gson().fromJson(orderJson, Order::class.java)
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
        if (order.status != "REQUESTED" && order.status != "SCHEDULED") {
            removeOrderFromList(order.id)
            return
        }

        allOrdersList.removeAll { it.id == order.id }
        allOrdersList.add(0, order)
        // ИСПРАВЛЕНО: Сортируем по числовому idLong для сохранения структуры списков
        allOrdersList.sortByDescending { it.idLong ?: 0L }

        filterAndShowOrders()
    }

    private fun updateUI() {
        filterAndShowOrders()
    }

    private fun filterAndShowOrders() {
        // TOЧЕЧНОЕ ОБНОВЛЕНИЕ: Загружаем отфильтрованные данные в адаптеры напрямую
        val activeList = allOrdersList.filter { !it.isScheduled() }
        val scheduledList = allOrdersList.filter { it.isScheduled() }

        activeAdapter.submitList(activeList)
        scheduledAdapter.submitList(scheduledList)

        // Просим внутренний Pager-адаптер обновить только состояние видимости (Empty State)
        pagerAdapter.updatePage(0)
        pagerAdapter.updatePage(1)
    }

    // --- ВНУТРЕННИЙ УМНЫЙ АДАПТЕР СТРАНИЦ VIEW_PAGER_2 ---
    private inner class EtherPagerAdapter : RecyclerView.Adapter<EtherPagerAdapter.PageViewHolder>() {

        private val viewHolders = SparseArray<PageViewHolder>()

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rvOrdersList: RecyclerView = view.findViewById(R.id.rv_orders_list)
            val llEmptyState: View = view.findViewById(R.id.ll_empty_state)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ether_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            viewHolders.put(position, holder)
            holder.rvOrdersList.layoutManager = LinearLayoutManager(this@EtherActivity)
            holder.rvOrdersList.adapter = if (position == 0) activeAdapter else scheduledAdapter

            updatePageVisibility(position, holder)
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val index = viewHolders.indexOfValue(holder)
            if (index >= 0) {
                viewHolders.removeAt(index)
            }
            super.onViewRecycled(holder)
        }

        fun updatePage(position: Int) {
            val holder = viewHolders.get(position)
            if (holder != null) {
                updatePageVisibility(position, holder)
            }
        }

        private fun updatePageVisibility(position: Int, holder: PageViewHolder) {
            // TOЧЕЧНОЕ ОБНОВЛЕНИЕ: Управляем только видимостью списков и Empty State карточек
            val filteredList = if (position == 0) {
                allOrdersList.filter { !it.isScheduled() }
            } else {
                allOrdersList.filter { it.isScheduled() }
            }

            if (filteredList.isNotEmpty()) {
                holder.rvOrdersList.visibility = View.VISIBLE
                holder.llEmptyState.visibility = View.GONE
            } else {
                holder.rvOrdersList.visibility = View.GONE
                holder.llEmptyState.visibility = View.VISIBLE
            }
        }

        override fun getItemCount(): Int = 2
    }
}