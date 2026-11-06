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
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import java.util.concurrent.CopyOnWriteArrayList

class EtherActivity : AppCompatActivity() {

    private var stompClient: StompClient? = null
    private val compositeDisposable = CompositeDisposable()
    private lateinit var sessionManager: SessionManager

    // Два отдельных адаптера для параллельного независимого отображения списков
    private lateinit var activeAdapter: OrderAdapter
    private lateinit var scheduledAdapter: OrderAdapter

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: EtherPagerAdapter
    private lateinit var pbLoading: ProgressBar
    private lateinit var tabLayout: TabLayout

    // Потокобезопасный список во избежание ConcurrentModificationException при быстрых пушах сокета
    private val allOrdersList = CopyOnWriteArrayList<Order>()
    private var currentTabIndex = 0
    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
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

    private fun removeOrderFromList(orderId: String) {
        val removed = allOrdersList.removeAll { it.id == orderId }
        if (removed) {
            filterAndShowOrders()
        }
    }

    override fun onResume() {
        super.onResume()

        setupWebSocket()

        val sectorFirst = sessionManager.isEtherSectorFirst()
        val hidePrice = sessionManager.isEtherPricePerKmHidden()
        activeAdapter.updateDisplaySettings(sectorFirst, hidePrice)
        scheduledAdapter.updateDisplaySettings(sectorFirst, hidePrice)

        fetchOrders(showLoader = allOrdersList.isEmpty())
    }

    override fun onPause() {
        super.onPause()
        syncJob?.cancel()
        disconnectWebSocket()
    }

    override fun onDestroy() {
        syncJob?.cancel()
        disconnectWebSocket()
        super.onDestroy()
    }

    private fun disconnectWebSocket() {
        compositeDisposable.clear()
        try {
            stompClient?.disconnect()
        } catch (e: Exception) {
            Log.w("EtherActivity", "Помилка закриття сокета: ${e.message}")
        }
        stompClient = null
    }

    private fun fetchOrders(showLoader: Boolean = true) {
        if (showLoader) pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).getAvailableOrders()
                if (response.isSuccessful) {
                    val list = (response.body() ?: emptyList()).sortedByDescending { it.idLong ?: 0L }
                    allOrdersList.clear()
                    allOrdersList.addAll(list)
                    filterAndShowOrders()
                }
            } catch (e: Exception) {
                Log.e("EtherActivity", "Помилка завантаження ефіру: ${e.message}")
            } finally {
                if (showLoader) pbLoading.visibility = View.GONE
            }
        }
    }

    /**
     * Дебаунс-синхронизация с сервером при событиях ADD/UPDATE в сокете Эфира:
     * гарантирует 100% соблюдение активных фильтров водителя (сектора, радиус, тарифы, цена/км),
     * которые рассчитываются на сервере в getFilteredOrdersForDriver().
     */
    private fun scheduleFilteredSync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            delay(250)
            fetchOrders(showLoader = false)
        }
    }

    private fun setupWebSocket() {
        disconnectWebSocket()

        val baseUrl = BuildConfig.BASE_URL
        val wsProtocol = if (baseUrl.startsWith("https")) "wss" else "ws"
        val cleanHost = baseUrl.replace("https://", "").replace("http://", "").removeSuffix("/")

        val token = sessionManager.fetchAuthToken() ?: ""
        val url = if (token.isNotEmpty()) {
            "$wsProtocol://$cleanHost/ws-taxi/websocket?token=$token"
        } else {
            "$wsProtocol://$cleanHost/ws-taxi/websocket"
        }

        val client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url).apply {
            withClientHeartbeat(15000).withServerHeartbeat(15000)
        }
        stompClient = client

        val topicDisposable = client.topic("/topic/drivers/ether")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ topicMessage ->
                val payload = topicMessage.payload
                if (payload.isNullOrBlank() || payload == "null") return@subscribe

                try {
                    val msgObj = JSONObject(payload)
                    val action = msgObj.optString("action")
                    val orderId = msgObj.optString("orderId")

                    when (action) {
                        "REMOVE" -> {
                            removeOrderFromList(orderId)
                        }
                        "ADD", "UPDATE" -> {
                            // Синхронизируем список через серверный фильтр водителя,
                            // чтобы в Эфир попадали только заказы, проходящие по фильтрам и тарифам водителя
                            scheduleFilteredSync()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, { error ->
                Log.e("WS", "WS Subscription Error: ${error.message}")
            })

        val lifecycleDisposable = client.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lifecycleEvent ->
                when (lifecycleEvent.type) {
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.OPENED -> Log.d("WS", "Connected to Ether WebSocket!")
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.ERROR -> Log.e("WS", "WebSocket error: ", lifecycleEvent.exception)
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.CLOSED -> Log.d("WS", "WebSocket disconnected")
                    else -> {}
                }
            }

        compositeDisposable.add(topicDisposable)
        compositeDisposable.add(lifecycleDisposable)

        val headers = if (token.isNotEmpty()) {
            listOf(ua.naiksoftware.stomp.dto.StompHeader("Authorization", "Bearer $token"))
        } else {
            emptyList()
        }
        client.connect(headers)
    }

    private fun filterAndShowOrders() {
        val snapshot = allOrdersList.toList()
        val activeList = snapshot.filter { !it.isScheduled() }
        val scheduledList = snapshot.filter { it.isScheduled() }

        activeAdapter.submitList(activeList)
        scheduledAdapter.submitList(scheduledList)

        pagerAdapter.updatePage(0)
        pagerAdapter.updatePage(1)
    }

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
            holder.rvOrdersList.itemAnimator = null
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