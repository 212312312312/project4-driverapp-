package com.taxiapp.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverSearchMode
import com.taxiapp.driver.network.DriverSearchSettingsDto
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EtherActivity : AppCompatActivity() {

    enum class ScheduledDateFilter { ALL, TODAY, TOMORROW, LATER }
    enum class ActiveSortMode { NEWEST, DISTANCE }

    private var stompClient: StompClient? = null
    private val compositeDisposable = CompositeDisposable()
    private lateinit var sessionManager: SessionManager
    private val gson = Gson()

    private lateinit var activeAdapter: OrderAdapter
    private lateinit var scheduledAdapter: OrderAdapter

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: EtherPagerAdapter
    private lateinit var pbLoading: ProgressBar
    private lateinit var tabLayout: TabLayout

    // Елементи нової шапки та сегментних вкладок
    private lateinit var tabBtnNow: TextView
    private lateinit var tabBtnScheduled: TextView

    private lateinit var llHeaderFilterAuto: View
    private lateinit var llHeaderFilterCycle: View
    private lateinit var llHeaderFilterEther: View
    private lateinit var ivHeaderFilterAuto: ImageView
    private lateinit var tvHeaderFilterAuto: TextView
    private lateinit var ivHeaderFilterCycle: ImageView
    private lateinit var tvHeaderFilterCycle: TextView
    private lateinit var ivHeaderFilterEther: ImageView
    private lateinit var tvHeaderFilterEther: TextView

    // Нижня панель: "Увімкнути ланцюг" + бірюзова кнопка сортування ("нові" / "час")
    private lateinit var cardToggleChain: MaterialCardView
    private lateinit var cardSortButton: MaterialCardView
    private lateinit var btnToggleChain: LinearLayout
    private lateinit var ivChainIcon: ImageView
    private lateinit var tvChainButtonText: TextView
    private lateinit var btnSortOrders: LinearLayout
    private lateinit var ivSortIcon: ImageView
    private lateinit var tvSortLabel: TextView

    private val allOrdersList = CopyOnWriteArrayList<Order>()
    private var currentTabIndex = 0
    private var currentScheduledFilter = ScheduledDateFilter.ALL
    private var currentActiveSortMode = ActiveSortMode.NEWEST
    private var scheduledTotalCount = 0

    private var isChainActive = false
    private var currentSearchRadiusKm = 3.0
    private var currentHomeSectorIds: List<Long> = emptyList()
    private var driverLat: Double? = null
    private var driverLng: Double? = null

    private var autoFiltersCount = 0
    private var cycleFiltersCount = 0
    private var etherFiltersCount = 0

    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether)

        val rootView = findViewById<View>(R.id.ether_root)
            ?: findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val headerInner = findViewById<View>(R.id.header_ether_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (12 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        sessionManager = SessionManager(this)
        currentSearchRadiusKm = sessionManager.getSearchRadius()
        currentHomeSectorIds = sessionManager.getHomeSectorIds()

        initViews()
        setupAdaptersAndPager()
        setupClickListeners()
        updateTabAndBottomBarVisuals()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.ether_view_pager)
        pbLoading = findViewById(R.id.pb_loading)
        tabLayout = findViewById(R.id.ether_tabs)

        tabBtnNow = findViewById(R.id.tab_btn_now)
        tabBtnScheduled = findViewById(R.id.tab_btn_scheduled)

        llHeaderFilterAuto = findViewById(R.id.ll_header_filter_auto)
        llHeaderFilterCycle = findViewById(R.id.ll_header_filter_cycle)
        llHeaderFilterEther = findViewById(R.id.ll_header_filter_ether)
        ivHeaderFilterAuto = findViewById(R.id.iv_header_filter_auto)
        tvHeaderFilterAuto = findViewById(R.id.tv_header_filter_auto)
        ivHeaderFilterCycle = findViewById(R.id.iv_header_filter_cycle)
        tvHeaderFilterCycle = findViewById(R.id.tv_header_filter_cycle)
        ivHeaderFilterEther = findViewById(R.id.iv_header_filter_ether)
        tvHeaderFilterEther = findViewById(R.id.tv_header_filter_ether)

        cardToggleChain = findViewById(R.id.card_toggle_chain)
        cardSortButton = findViewById(R.id.card_sort_button)
        cardSortButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
        btnToggleChain = findViewById(R.id.btn_toggle_chain)
        ivChainIcon = findViewById(R.id.iv_chain_icon)
        tvChainButtonText = findViewById(R.id.tv_chain_button_text)
        btnSortOrders = findViewById(R.id.btn_sort_orders)
        ivSortIcon = findViewById(R.id.iv_sort_icon)
        tvSortLabel = findViewById(R.id.tv_sort_label)

        renderHeaderFilterIcons()
        updateChainButtonVisuals()
    }

    private fun setupAdaptersAndPager() {
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
        // Обидві сторінки ("На зараз" та "На час" разом із чіпами фільтрів) тримаються готовими в пам'яті,
        // щоб під час свайпу чіпи "Всі / Сьогодні / Завтра / Пізніше" виїжджали синхронно зі сторінкою "На час"
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "На зараз" else "На час"
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentTabIndex = position
                updateTabAndBottomBarVisuals()
                renderHeaderFilterIcons()
            }
        })
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_open_filters).setOnClickListener {
            startActivity(Intent(this, FiltersActivity::class.java))
        }

        tabBtnNow.setOnClickListener {
            if (currentTabIndex != 0) {
                viewPager.currentItem = 0
            }
        }

        tabBtnScheduled.setOnClickListener {
            if (currentTabIndex != 1) {
                viewPager.currentItem = 1
            }
        }

        btnToggleChain.setOnClickListener {
            toggleChainMode()
        }

        btnSortOrders.setOnClickListener {
            if (currentTabIndex == 0) {
                currentActiveSortMode = if (currentActiveSortMode == ActiveSortMode.NEWEST) {
                    ActiveSortMode.DISTANCE
                } else {
                    ActiveSortMode.NEWEST
                }
                updateTabAndBottomBarVisuals()
                filterAndShowOrders()
            } else {
                filterAndShowOrders()
                Toast.makeText(this, "Відсортовано за часом подачі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        setupWebSocket()
        refreshDriverLocation()

        val sectorFirst = sessionManager.isEtherSectorFirst()
        val hidePrice = sessionManager.isEtherPricePerKmHidden()
        activeAdapter.updateDisplaySettings(sectorFirst, hidePrice)
        scheduledAdapter.updateDisplaySettings(sectorFirst, hidePrice)

        syncFiltersAndChainState()
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

    private fun refreshDriverLocation() {
        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let { (lat, lng) ->
                driverLat = lat
                driverLng = lng
                activeAdapter.updateDriverLocation(lat, lng)
                scheduledAdapter.updateDriverLocation(lat, lng)
            }
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    driverLat = loc.latitude
                    driverLng = loc.longitude
                    activeAdapter.updateDriverLocation(loc.latitude, loc.longitude)
                    scheduledAdapter.updateDriverLocation(loc.latitude, loc.longitude)
                }
            }
        }
    }

    private fun syncFiltersAndChainState() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@EtherActivity)
                coroutineScope {
                    val filtersDeferred = async {
                        try { api.getFilters() } catch (_: Exception) { null }
                    }
                    val searchDeferred = async {
                        try { api.getSearchSettings() } catch (_: Exception) { null }
                    }

                    val filtersRes = filtersDeferred.await()
                    val searchRes = searchDeferred.await()

                    if (filtersRes?.isSuccessful == true) {
                        val filters = filtersRes.body().orEmpty()
                        autoFiltersCount = filters.count { it.isActive && it.isAuto }
                        cycleFiltersCount = filters.count { it.isActive && it.isCycle }
                        etherFiltersCount = filters.count { it.isActive && it.isEther }
                        renderHeaderFilterIcons()
                    }

                    if (searchRes?.isSuccessful == true && searchRes.body() != null) {
                        val state = searchRes.body()!!
                        currentSearchRadiusKm = state.radius.takeIf { it >= 0.5 } ?: sessionManager.getSearchRadius()
                        currentHomeSectorIds = state.homeSectorIds ?: sessionManager.getHomeSectorIds()
                        val mode = state.mode ?: DriverSearchMode.MANUAL
                        isChainActive = (mode == DriverSearchMode.CHAIN || mode == DriverSearchMode.HOME)
                        updateChainButtonVisuals()
                    }
                }
            } catch (e: Exception) {
                Log.w("EtherActivity", "Помилка синхронізації стану фільтрів/ланцюга: ${e.message}")
            }
        }
    }

    private fun toggleChainMode() {
        val nextActive = !isChainActive
        isChainActive = nextActive
        updateChainButtonVisuals()

        lifecycleScope.launch {
            try {
                val targetMode = if (nextActive) {
                    val savedMode = sessionManager.getSearchMode()
                    if (savedMode == DriverSearchMode.HOME) DriverSearchMode.HOME else DriverSearchMode.CHAIN
                } else {
                    DriverSearchMode.MANUAL
                }

                val req = DriverSearchSettingsDto(
                    mode = targetMode,
                    radius = currentSearchRadiusKm,
                    homeSectorIds = currentHomeSectorIds
                )
                val response = ApiClient.getInstance().getApiService(this@EtherActivity).updateSearchSettings(req)
                if (response.isSuccessful && response.body() != null) {
                    val newState = response.body()!!
                    val activeNow = newState.mode == DriverSearchMode.CHAIN || newState.mode == DriverSearchMode.HOME
                    isChainActive = activeNow
                    updateChainButtonVisuals()
                    Toast.makeText(
                        this@EtherActivity,
                        if (activeNow) "Ланцюг замовлень увімкнено" else "Ланцюг замовлень вимкнено",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isChainActive = !nextActive
                    updateChainButtonVisuals()
                    Toast.makeText(this@EtherActivity, "Не вдалося змінити режим ланцюга", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isChainActive = !nextActive
                updateChainButtonVisuals()
                Toast.makeText(this@EtherActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderHeaderFilterIcons() {
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val yellowAccent = Color.parseColor("#F5D033")
        val sizePx = (18 * density).toInt()

        tvHeaderFilterAuto.text = autoFiltersCount.toString()
        tvHeaderFilterCycle.text = cycleFiltersCount.toString()
        tvHeaderFilterEther.text = etherFiltersCount.toString()

        if (currentTabIndex == 0) {
            // Вкладка "На зараз": показуємо всі 3 лічильники [ 🎯 0  ⇄ 0  ▽ 0 ] як на скріншоті 1
            llHeaderFilterAuto.visibility = View.VISIBLE
            llHeaderFilterCycle.visibility = View.VISIBLE
            llHeaderFilterEther.visibility = View.VISIBLE

            val autoColor = if (autoFiltersCount > 0) yellowAccent else primaryColor
            val cycleColor = if (cycleFiltersCount > 0) yellowAccent else primaryColor
            val etherColor = if (etherFiltersCount > 0) yellowAccent else primaryColor

            ivHeaderFilterAuto.setImageDrawable(EtherUiIconDrawable(EtherUiIconDrawable.Type.FILTER_AUTO, autoColor, sizePx))
            tvHeaderFilterAuto.setTextColor(autoColor)

            ivHeaderFilterCycle.setImageDrawable(EtherUiIconDrawable(EtherUiIconDrawable.Type.FILTER_CYCLE, cycleColor, sizePx))
            tvHeaderFilterCycle.setTextColor(cycleColor)

            ivHeaderFilterEther.setImageDrawable(EtherUiIconDrawable(EtherUiIconDrawable.Type.FILTER_ETHER, etherColor, sizePx))
            tvHeaderFilterEther.setTextColor(etherColor)
        } else {
            // Вкладка "На час": показуємо лише іконку фільтра ефіру [ ▽ 3 ] як на скріншоті 2
            llHeaderFilterAuto.visibility = View.GONE
            llHeaderFilterCycle.visibility = View.GONE
            llHeaderFilterEther.visibility = View.VISIBLE

            val etherColor = if (etherFiltersCount > 0) yellowAccent else primaryColor
            ivHeaderFilterEther.setImageDrawable(EtherUiIconDrawable(EtherUiIconDrawable.Type.FILTER_ETHER, etherColor, sizePx))
            tvHeaderFilterEther.setTextColor(etherColor)
        }
    }

    private fun updateTabAndBottomBarVisuals() {
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val darkIconColor = Color.parseColor("#1A1C1E")

        val selectedTabBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(Color.parseColor("#545762"))
            setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#A6A9B2"))
        }

        if (currentTabIndex == 0) {
            tabBtnNow.background = selectedTabBg
            tabBtnNow.setTextColor(primaryColor)
            tabBtnNow.setTypeface(tabBtnNow.typeface, Typeface.BOLD)

            tabBtnScheduled.background = null
            tabBtnScheduled.setTextColor(secondaryColor)
            tabBtnScheduled.setTypeface(tabBtnScheduled.typeface, Typeface.NORMAL)

            cardToggleChain.visibility = View.VISIBLE

            ivSortIcon.setImageDrawable(
                EtherUiIconDrawable(EtherUiIconDrawable.Type.SORT_ARROWS, darkIconColor, (20 * density).toInt())
            )
            tvSortLabel.text = if (currentActiveSortMode == ActiveSortMode.NEWEST) "нові" else "км"
        } else {
            tabBtnScheduled.background = selectedTabBg
            tabBtnScheduled.setTextColor(primaryColor)
            tabBtnScheduled.setTypeface(tabBtnScheduled.typeface, Typeface.BOLD)

            tabBtnNow.background = null
            tabBtnNow.setTextColor(secondaryColor)
            tabBtnNow.setTypeface(tabBtnNow.typeface, Typeface.NORMAL)

            cardToggleChain.visibility = View.INVISIBLE

            ivSortIcon.setImageDrawable(
                EtherUiIconDrawable(EtherUiIconDrawable.Type.CLOCK, darkIconColor, (20 * density).toInt())
            )
            tvSortLabel.text = "час"
        }
    }

    private fun updateChainButtonVisuals() {
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val darkText = Color.parseColor("#1A1C1E")

        if (isChainActive) {
            cardToggleChain.setCardBackgroundColor(neonTeal)
            tvChainButtonText.text = "Вимкнути ланцюг"
            tvChainButtonText.setTextColor(darkText)
            ivChainIcon.setImageDrawable(
                EtherUiIconDrawable(EtherUiIconDrawable.Type.CHAIN_LINK, darkText, (22 * density).toInt())
            )
        } else {
            cardToggleChain.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_black_bg))
            tvChainButtonText.text = "Увімкнути ланцюг"
            tvChainButtonText.setTextColor(primaryColor)
            ivChainIcon.setImageDrawable(
                EtherUiIconDrawable(EtherUiIconDrawable.Type.CHAIN_LINK, primaryColor, (22 * density).toInt())
            )
        }
    }

    private fun removeOrderFromList(orderId: String) {
        val removed = allOrdersList.removeAll { it.id == orderId }
        if (removed) {
            filterAndShowOrders()
        }
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

    private fun scheduleFilteredSync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            delay(Random.nextLong(300L, 750L))
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
                    val orderJson = msgObj.optJSONObject("order")?.toString()

                    when (action) {
                        "REMOVE" -> {
                            removeOrderFromList(orderId)
                        }
                        "UPDATE" -> {
                            val existingIdx = allOrdersList.indexOfFirst { it.id == orderId }
                            if (existingIdx >= 0 && orderJson != null) {
                                val updatedOrder = gson.fromJson(orderJson, Order::class.java)
                                allOrdersList[existingIdx] = updatedOrder
                                filterAndShowOrders()
                            } else {
                                scheduleFilteredSync()
                            }
                        }
                        "ADD" -> {
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

    private fun getOrderDayOffset(order: Order): Int {
        val scheduledDate = order.getScheduledDate() ?: return 0
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
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    private fun filterAndShowOrders() {
        val snapshot = allOrdersList.toList()
        val rawActiveList = snapshot.filter { !it.isScheduled() }
        val rawScheduledList = snapshot.filter { it.isScheduled() }

        scheduledTotalCount = rawScheduledList.size

        // Оновлюємо лічильники у вкладках "На зараз (N)" та "На час (N)"
        tabBtnNow.text = "На зараз (${rawActiveList.size})"
        tabBtnScheduled.text = "На час (${rawScheduledList.size})"

        // Сортування активних замовлень ("нові" або за відстанню подачі)
        val sortedActiveList = when (currentActiveSortMode) {
            ActiveSortMode.NEWEST -> rawActiveList.sortedByDescending { it.idLong ?: 0L }
            ActiveSortMode.DISTANCE -> {
                val dLat = driverLat
                val dLng = driverLng
                if (dLat != null && dLng != null && dLat != 0.0) {
                    rawActiveList.sortedBy { order ->
                        val oLat = order.originLat ?: 0.0
                        val oLng = order.originLng ?: 0.0
                        if (oLat == 0.0) Float.MAX_VALUE else {
                            val res = FloatArray(1)
                            Location.distanceBetween(dLat, dLng, oLat, oLng, res)
                            res[0]
                        }
                    }
                } else {
                    rawActiveList.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
                }
            }
        }

        // Фільтрація запланованих замовлень по обраному чіпу ("Всі", "Сьогодні", "Завтра", "Пізніше")
        val filteredScheduledList = rawScheduledList.filter { order ->
            val dayOffset = getOrderDayOffset(order)
            when (currentScheduledFilter) {
                ScheduledDateFilter.ALL -> true
                ScheduledDateFilter.TODAY -> dayOffset <= 0
                ScheduledDateFilter.TOMORROW -> dayOffset == 1
                ScheduledDateFilter.LATER -> dayOffset > 1
            }
        }.sortedBy { it.getScheduledDate()?.time ?: Long.MAX_VALUE }

        activeAdapter.submitList(sortedActiveList)
        scheduledAdapter.submitList(filteredScheduledList)

        pagerAdapter.updatePage(0, sortedActiveList.isNotEmpty())
        pagerAdapter.updatePage(1, filteredScheduledList.isNotEmpty())
        pagerAdapter.refreshScheduledChips()
    }

    private inner class EtherPagerAdapter : RecyclerView.Adapter<EtherPagerAdapter.PageViewHolder>() {

        private val viewHolders = SparseArray<PageViewHolder>()

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val hsvScheduledChips: HorizontalScrollView = view.findViewById(R.id.hsv_scheduled_chips)
            val chipSchedAll: TextView = view.findViewById(R.id.chip_sched_all)
            val chipSchedToday: TextView = view.findViewById(R.id.chip_sched_today)
            val chipSchedTomorrow: TextView = view.findViewById(R.id.chip_sched_tomorrow)
            val chipSchedLater: TextView = view.findViewById(R.id.chip_sched_later)
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

            if (position == 1) {
                // На сторінці "На час" чіпи фільтрів завжди видимі як частина сторінки (виїжджають разом зі свайпом)
                holder.hsvScheduledChips.visibility = View.VISIBLE
                holder.chipSchedAll.setOnClickListener {
                    currentScheduledFilter = ScheduledDateFilter.ALL
                    bindScheduledChipsVisuals(holder)
                    filterAndShowOrders()
                }
                holder.chipSchedToday.setOnClickListener {
                    currentScheduledFilter = ScheduledDateFilter.TODAY
                    bindScheduledChipsVisuals(holder)
                    filterAndShowOrders()
                }
                holder.chipSchedTomorrow.setOnClickListener {
                    currentScheduledFilter = ScheduledDateFilter.TOMORROW
                    bindScheduledChipsVisuals(holder)
                    filterAndShowOrders()
                }
                holder.chipSchedLater.setOnClickListener {
                    currentScheduledFilter = ScheduledDateFilter.LATER
                    bindScheduledChipsVisuals(holder)
                    filterAndShowOrders()
                }
                bindScheduledChipsVisuals(holder)
            } else {
                holder.hsvScheduledChips.visibility = View.GONE
            }

            val hasItems = if (position == 0) activeAdapter.itemCount > 0 else scheduledAdapter.itemCount > 0
            updatePageVisibility(holder, hasItems)
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val index = viewHolders.indexOfValue(holder)
            if (index >= 0) {
                viewHolders.removeAt(index)
            }
            super.onViewRecycled(holder)
        }

        fun updatePage(position: Int, hasItems: Boolean) {
            val holder = viewHolders.get(position)
            if (holder != null) {
                updatePageVisibility(holder, hasItems)
            }
        }

        fun refreshScheduledChips() {
            val scheduledHolder = viewHolders.get(1)
            if (scheduledHolder != null) {
                bindScheduledChipsVisuals(scheduledHolder)
            }
        }

        private fun bindScheduledChipsVisuals(holder: PageViewHolder) {
            val density = resources.displayMetrics.density
            val primaryColor = ContextCompat.getColor(this@EtherActivity, R.color.driver_text_primary)

            holder.chipSchedAll.text = "Всі ($scheduledTotalCount)"

            val chips = listOf(
                holder.chipSchedAll to ScheduledDateFilter.ALL,
                holder.chipSchedToday to ScheduledDateFilter.TODAY,
                holder.chipSchedTomorrow to ScheduledDateFilter.TOMORROW,
                holder.chipSchedLater to ScheduledDateFilter.LATER
            )

            for ((chipView, filterType) in chips) {
                val isSelected = currentScheduledFilter == filterType
                if (isSelected) {
                    val neonTeal = ContextCompat.getColor(this@EtherActivity, R.color.driver_neon_teal)
                    chipView.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 11f * density
                        setColor(Color.parseColor("#1B3E37"))
                        setStroke((1.2f * density).toInt().coerceAtLeast(1), neonTeal)
                    }
                    chipView.setTextColor(primaryColor)
                    chipView.setTypeface(chipView.typeface, Typeface.BOLD)
                } else {
                    chipView.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 11f * density
                        setColor(Color.parseColor("#2B2D33"))
                    }
                    chipView.setTextColor(primaryColor)
                    chipView.setTypeface(chipView.typeface, Typeface.NORMAL)
                }
            }
        }

        private fun updatePageVisibility(holder: PageViewHolder, hasItems: Boolean) {
            if (hasItems) {
                holder.rvOrdersList.visibility = View.VISIBLE
                holder.llEmptyState.visibility = View.GONE
            } else {
                holder.rvOrdersList.visibility = View.GONE
                holder.llEmptyState.visibility = View.VISIBLE
            }
        }

        override fun getItemCount(): Int = 2
    }

    /**
     * Кастомний векторний Drawable для іконок шапки та нижньої панелі Ефіру,
     * намальований 1-в-1 за референсними скріншотами:
     * - FILTER_AUTO: приціл авто-фільтра (коло з 4 насічками та центральною точкою)
     * - FILTER_CYCLE: дві горизонтальні стрілки циклу (⇄)
     * - FILTER_ETHER: три горизонтальні лінії фільтра-воронки (▽)
     * - CHAIN_LINK: горизонтальна скріпка ланцюга (🔗)
     * - SORT_ARROWS: вертикальні стрілки сортування (↑↓)
     * - CLOCK: годинник сортування за часом (🕒)
     */
    private class EtherUiIconDrawable(
        private val type: Type,
        private val color: Int,
        private val sizePx: Int
    ) : Drawable() {

        enum class Type {
            FILTER_AUTO,
            FILTER_CYCLE,
            FILTER_ETHER,
            CHAIN_LINK,
            SORT_ARROWS,
            CLOCK
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@EtherUiIconDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@EtherUiIconDrawable.color
            style = Paint.Style.FILL
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = minOf(w, h) / 24f

            canvas.save()
            canvas.translate(b.left + (w - 24f * s) / 2f, b.top + (h - 24f * s) / 2f)
            canvas.scale(s, s)

            strokePaint.strokeWidth = 2.0f

            when (type) {
                Type.FILTER_AUTO -> {
                    canvas.drawCircle(12f, 12f, 6.2f, strokePaint)
                    canvas.drawCircle(12f, 12f, 1.6f, fillPaint)
                    canvas.drawLine(12f, 2.2f, 12f, 5.8f, strokePaint)
                    canvas.drawLine(12f, 18.2f, 12f, 21.8f, strokePaint)
                    canvas.drawLine(2.2f, 12f, 5.8f, 12f, strokePaint)
                    canvas.drawLine(18.2f, 12f, 21.8f, 12f, strokePaint)
                }

                Type.FILTER_CYCLE -> {
                    val path = Path().apply {
                        moveTo(4f, 8.5f)
                        lineTo(19.5f, 8.5f)
                        moveTo(16f, 5f)
                        lineTo(19.5f, 8.5f)
                        lineTo(16f, 12f)

                        moveTo(20f, 15.5f)
                        lineTo(4.5f, 15.5f)
                        moveTo(8f, 12f)
                        lineTo(4.5f, 15.5f)
                        lineTo(8f, 19f)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                Type.FILTER_ETHER -> {
                    canvas.drawLine(3.5f, 6.5f, 20.5f, 6.5f, strokePaint)
                    canvas.drawLine(6.8f, 12f, 17.2f, 12f, strokePaint)
                    canvas.drawLine(10.0f, 17.5f, 14.0f, 17.5f, strokePaint)
                }

                Type.CHAIN_LINK -> {
                    val leftLink = Path().apply {
                        addArc(RectF(2.5f, 7.5f, 11.5f, 16.5f), 90f, 180f)
                        lineTo(10.2f, 7.5f)
                        moveTo(7f, 16.5f)
                        lineTo(10.2f, 16.5f)
                    }
                    val rightLink = Path().apply {
                        addArc(RectF(12.5f, 7.5f, 21.5f, 16.5f), -90f, 180f)
                        lineTo(13.8f, 16.5f)
                        moveTo(17f, 7.5f)
                        lineTo(13.8f, 7.5f)
                    }
                    canvas.drawPath(leftLink, strokePaint)
                    canvas.drawPath(rightLink, strokePaint)
                    canvas.drawLine(8.2f, 12f, 15.8f, 12f, strokePaint)
                }

                Type.SORT_ARROWS -> {
                    val path = Path().apply {
                        moveTo(8f, 19f)
                        lineTo(8f, 5f)
                        moveTo(4.5f, 8.5f)
                        lineTo(8f, 5f)
                        lineTo(11.5f, 8.5f)

                        moveTo(16f, 5f)
                        lineTo(16f, 19f)
                        moveTo(12.5f, 15.5f)
                        lineTo(16f, 19f)
                        lineTo(19.5f, 15.5f)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                Type.CLOCK -> {
                    canvas.drawCircle(12f, 12f, 8.5f, strokePaint)
                    val hands = Path().apply {
                        moveTo(12f, 7.2f)
                        lineTo(12f, 12.2f)
                        lineTo(15.2f, 14.2f)
                    }
                    canvas.drawPath(hands, strokePaint)
                }
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}