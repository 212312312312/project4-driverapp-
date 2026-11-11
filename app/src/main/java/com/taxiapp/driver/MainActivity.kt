package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.messaging.FirebaseMessaging
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverFilter
import com.taxiapp.driver.network.DriverSearchMode
import com.taxiapp.driver.network.DriverSearchSettingsDto
import com.taxiapp.driver.network.HeatmapZoneDto
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.network.Sector
import com.taxiapp.driver.network.SectorPointDto
import com.taxiapp.driver.network.UpdateDriverStatusRequest
import com.taxiapp.driver.network.UpdateLocationRequest
import com.taxiapp.driver.service.FloatingWidgetService
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.SessionManager
import com.taxiapp.driver.utils.toFullImageUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    enum class SelectionTarget { FILTER_FROM, FILTER_TO, HOME }
    private var currentSelectionTarget = SelectionTarget.FILTER_FROM

    private var allSectors = listOf<Sector>()
    private val selectedIds = mutableSetOf<Long>()
    private val polygons = mutableMapOf<Long, Polygon>()
    private val sectorMarkersList = mutableListOf<Marker>()
    private var sectorsListAdapter: SectorsListAdapter? = null

    private lateinit var ivSearchModeIcon: ImageView
    private lateinit var tvSearchModeTitle: TextView
    private lateinit var tvSearchModeSubtitle: TextView
    private lateinit var btnToggleSearchMode: LinearLayout

    // Элементы нижней панели (иконки и счетчики как на скриншоте)
    private lateinit var ivEtherFilterIcon: ImageView
    private lateinit var tvEtherFilterCount: TextView
    private lateinit var ivEtherListIcon: ImageView
    private lateinit var tvEtherListCount: TextView
    private lateinit var ivNavOrdersIcon: ImageView
    private lateinit var ivFilterAutoIcon: ImageView
    private lateinit var tvFilterAutoCount: TextView
    private lateinit var ivFilterCycleIcon: ImageView
    private lateinit var tvFilterCycleCount: TextView

    private var currentDisplayedSearchMode: DriverSearchMode = DriverSearchMode.CHAIN

    private var isLeavingSectorSelection = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var btnStatusToggle: View
    private var driverLocationMarker: Marker? = null
    private var cachedDriverMarkerIcon: BitmapDescriptor? = null
    private val cachedGlowBitmaps = mutableMapOf<Int, BitmapDescriptor>()

    private lateinit var switchThumbCard: com.google.android.material.card.MaterialCardView
    private lateinit var tvSwitchStatusText: TextView
    private var isDriverOnline = false
    private var searchBorderAnimator: android.animation.ObjectAnimator? = null

    private lateinit var cardPhotoControl: com.google.android.material.card.MaterialCardView
    private lateinit var tvPhotoControlTitle: TextView
    private lateinit var tvPhotoControlSubtitle: TextView
    private lateinit var btnPassPhotoControl: androidx.appcompat.widget.AppCompatButton
    private var activePhotoControlData: com.taxiapp.driver.network.PhotoControlStatusDto? = null

    private var defaultMapPaddingBottom = 0
    private lateinit var mainScreenUiGroup: androidx.constraintlayout.widget.Group
    private lateinit var btnSaveSelection: ImageView
    private lateinit var sectorOverlay: View
    private lateinit var selectionTabs: com.google.android.material.tabs.TabLayout
    private lateinit var etSectorSearch: EditText
    private lateinit var rvSectorsList: androidx.recyclerview.widget.RecyclerView

    private lateinit var map: GoogleMap
    private lateinit var btnLockLocation: ImageButton
    private lateinit var btnHotspots: ImageButton
    private lateinit var btnSectors: ImageButton

    private lateinit var btnNavOrders: LinearLayout
    private lateinit var orderBadgeDot: View

    private lateinit var navViewContent: View

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var boundLocationService: LocationService? = null
    private var isLocationServiceBound = false

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as LocationService.LocalBinder
            val locationService = binder.getService()
            boundLocationService = locationService
            isLocationServiceBound = true

            locationService.onLocationUpdated = { lat, lng ->
                runOnUiThread {
                    if (!sessionManager.isManualLocationActive()) {
                        currentDriverLocation = LatLng(lat, lng)
                        drawSearchRadius()
                    }
                    updateCustomDriverMarker(lat, lng)
                }
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            boundLocationService?.onLocationUpdated = null
            boundLocationService = null
            isLocationServiceBound = false
        }
    }

    private var overlayBackPressedCallback: androidx.activity.OnBackPressedCallback? = null
    private var originalContainerElevation: Float = -1f
    private var searchRadiusCircle: Circle? = null

    private var currentSearchRadiusKm: Double = 3.0
    private var currentHomeSectorIds: List<Long> = emptyList()
    private var currentDriverLocation: LatLng? = null

    private var isSectorsVisible = false
    private val sectorPolygons = mutableListOf<Polygon>()
    private val sectorMarkers = mutableListOf<Marker>()

    private var isHeatmapVisible = false
    private val heatmapOverlays = mutableListOf<GroundOverlay>()

    private var manualLocationMarker: Marker? = null

    private var systemBarsTopInset = 0
    private var systemBarsBottomInset = 0

    internal var isSearchActive = false
    private var restoreDialog: android.app.Dialog? = null

    companion object {
        private const val REQUEST_CODE_HOME_SECTOR = 1001
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postNotifs = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            if (!postNotifs) {
                Toast.makeText(this, "Увімкніть сповіщення, щоб бачити замовлення!", Toast.LENGTH_LONG).show()
            }
        }

        if (fineLocation || coarseLocation) {
            updateMapUI()
            startLocationService()
            if (::map.isInitialized) centerMapOnUser()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)
        this.isDriverOnline = sessionManager.isDriverOnline()
        currentSearchRadiusKm = sessionManager.getSearchRadius()
        currentHomeSectorIds = sessionManager.getHomeSectorIds()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (sessionManager.fetchAuthToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_container)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density

            systemBarsTopInset = systemBars.top
            systemBarsBottomInset = systemBars.bottom

            findViewById<View>(R.id.header_selection)?.let { view ->
                view.setPadding(
                    (24 * density).toInt(),
                    systemBars.top + (16 * density).toInt(),
                    (24 * density).toInt(),
                    (16 * density).toInt()
                )
            }
            findViewById<View>(R.id.btn_menu_container)?.let { view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = systemBars.top + (16 * density).toInt()
                view.layoutParams = params
            }
            findViewById<View>(R.id.btn_status_toggle)?.let { view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = systemBars.top + (16 * density).toInt()
                view.layoutParams = params
            }

            findViewById<View>(R.id.bottom_nav_container)?.let { view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = systemBars.bottom + (12 * density).toInt()
                view.layoutParams = params
            }

            updateHeaderSelectionPadding()

            findViewById<LinearLayout>(R.id.ll_menu_scroll_content)?.let { menuScrollContent ->
                menuScrollContent.setPadding(
                    menuScrollContent.paddingLeft,
                    systemBars.top,
                    menuScrollContent.paddingRight,
                    menuScrollContent.paddingBottom
                )
            }

            findViewById<View>(R.id.btn_logout)?.let { btnLogout ->
                val logoutParams = btnLogout.layoutParams as ViewGroup.MarginLayoutParams
                logoutParams.bottomMargin = systemBars.bottom
                btnLogout.layoutParams = logoutParams
            }

            defaultMapPaddingBottom = systemBars.bottom + ((80 + 12 + 68 + 8 + 4) * density).toInt()
            if (::map.isInitialized && !sectorOverlay.isShown) {
                map.setPadding(0, 0, 0, defaultMapPaddingBottom)
            }

            insets
        }

        loadUserProfile()

        checkPermissionsAndStart()
        initSectorSelectionOverlay()
        handleSectorSelectionRequest(intent)
        loadSectorsDataSilently()
        updateFcmToken()
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result
            sessionManager.saveFcmToken(token)
            lifecycleScope.launch {
                try {
                    ApiClient.getInstance().getApiService(this@MainActivity).updateFcmToken(mapOf("token" to token))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isPendingDeletion()) {
            showRestoreDialog()
            return
        }

        this.isDriverOnline = sessionManager.isDriverOnline()
        setOnlineVisualState(this.isDriverOnline, animate = false)

        updateLockIconState()
        syncActiveOrderAndBadge()
        syncBottomNavCounters()
        updateCommissionInfo()

        if (hasLocationPermission()) {
            startLocationService()
        }

        if (::map.isInitialized) {
            updateMapUI()
        }
        updateSearchStatusUI()
        checkPhotoControlStatus()
    }

    override fun onStop() {
        super.onStop()
        if (isLeavingSectorSelection) {
            isLeavingSectorSelection = false
            if (::sectorOverlay.isInitialized) {
                sectorOverlay.visibility = View.GONE
                mainScreenUiGroup.visibility = View.VISIBLE
                if (::map.isInitialized) {
                    map.setPadding(0, 0, 0, defaultMapPaddingBottom)
                    clearOverlayPolygons()
                }
            }
        }
    }

    override fun onDestroy() {
        searchBorderAnimator?.cancel()
        restoreDialog?.dismiss()
        boundLocationService?.onLocationUpdated = null
        boundLocationService = null
        if (isLocationServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLocationServiceBound = false
        }
        super.onDestroy()
    }

    fun onSearchSettingsUpdated() {
        updateSearchStatusUI()
    }

    private fun formatRadiusKm(radius: Double): String {
        return String.format(Locale.US, "%.1f км", radius)
    }

    private fun drawSearchRadius() {
        if (!::map.isInitialized) return
        val center = if (sessionManager.isManualLocationActive()) {
            val manual = sessionManager.getManualLocation() ?: return
            LatLng(manual.first, manual.second)
        } else {
            currentDriverLocation
        }
        if (center == null) return
        val radiusMeters = currentSearchRadiusKm * 1000
        val strokeColor = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val fillColor = Color.argb(40, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))

        if (searchRadiusCircle == null) {
            searchRadiusCircle = map.addCircle(CircleOptions()
                .center(center)
                .radius(radiusMeters)
                .strokeColor(strokeColor)
                .fillColor(fillColor)
                .strokeWidth(3f))
        } else {
            searchRadiusCircle?.center = center
            searchRadiusCircle?.radius = radiusMeters
            searchRadiusCircle?.fillColor = fillColor
        }
        searchRadiusCircle?.isVisible = !sectorOverlay.isShown
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_HOME_SECTOR && resultCode == RESULT_OK) {
            val selectedIdsArray = data?.getLongArrayExtra("SELECTED_IDS")
            val selectedIdsList = selectedIdsArray?.toList()
            if (!selectedIdsList.isNullOrEmpty()) {
                updateHomeSectors(selectedIdsList)
            }
        }
    }

    private fun updateHomeSectors(sectorIds: List<Long>) {
        lifecycleScope.launch {
            try {
                val req = DriverSearchSettingsDto(DriverSearchMode.HOME, currentSearchRadiusKm, sectorIds)
                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateSearchSettings(req)
                if (response.isSuccessful && response.body() != null) {
                    displaySearchState(response.body()!!)
                    Toast.makeText(this@MainActivity, "Сектори 'Додому' збережено!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navViewContent = findViewById(R.id.nav_view_content)

        ivSearchModeIcon = findViewById(R.id.ivSearchModeIcon)
        tvSearchModeTitle = findViewById(R.id.tvSearchModeTitle)
        tvSearchModeSubtitle = findViewById(R.id.tvSearchModeSubtitle)
        btnToggleSearchMode = findViewById(R.id.btnToggleSearchMode)

        ivEtherFilterIcon = findViewById(R.id.iv_ether_filter_icon)
        tvEtherFilterCount = findViewById(R.id.tv_ether_filter_count)
        ivEtherListIcon = findViewById(R.id.iv_ether_list_icon)
        tvEtherListCount = findViewById(R.id.tv_ether_list_count)
        ivNavOrdersIcon = findViewById(R.id.iv_nav_orders_icon)
        ivFilterAutoIcon = findViewById(R.id.iv_filter_auto_icon)
        tvFilterAutoCount = findViewById(R.id.tv_filter_auto_count)
        ivFilterCycleIcon = findViewById(R.id.iv_filter_cycle_icon)
        tvFilterCycleCount = findViewById(R.id.tv_filter_cycle_count)

        setupBottomBarIcons()

        tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
        tvSearchModeSubtitle.text = formatRadiusKm(currentSearchRadiusKm)

        updateSearchBlockVisuals(false)

        btnToggleSearchMode.setOnClickListener { toggleSearchActivation() }

        findViewById<View>(R.id.btnSearchSettings).setOnClickListener {
            if (!isDriverOnline) {
                Toast.makeText(this, "Переключіть режим на Онлайн", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bottomSheet = SearchSettingsBottomSheet { updateSearchStatusUI() }
            bottomSheet.show(supportFragmentManager, "SearchSettings")
        }

        btnStatusToggle = findViewById(R.id.btn_status_toggle)
        switchThumbCard = findViewById(R.id.switch_thumb_card)
        tvSwitchStatusText = findViewById(R.id.tv_switch_status_text)

        btnStatusToggle.setOnClickListener {
            isDriverOnline = !isDriverOnline
            setOnlineVisualState(isDriverOnline, animate = true)
            updateDriverStatus(isDriverOnline)
        }

        cardPhotoControl = findViewById(R.id.cardPhotoControl)
        tvPhotoControlTitle = findViewById(R.id.tvPhotoControlTitle)
        tvPhotoControlSubtitle = findViewById(R.id.tvPhotoControlSubtitle)
        btnPassPhotoControl = findViewById(R.id.btnPassPhotoControl)

        btnPassPhotoControl.setOnClickListener {
            activePhotoControlData?.let { pc ->
                val intent = Intent(this, PhotoControlActivity::class.java).apply {
                    putExtra("PHOTO_CONTROL_ID", pc.id)
                    putExtra("DRIVER_ID", pc.driverId)
                }
                startActivity(intent)
            }
        }

        findViewById<View>(R.id.btn_nav_ether).setOnClickListener {
            if (!isDriverOnline) {
                Toast.makeText(this, "Переключіть режим на Онлайн", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, EtherActivity::class.java))
        }
        btnNavOrders = findViewById(R.id.btn_nav_orders)
        orderBadgeDot = findViewById(R.id.order_badge_dot)
        btnNavOrders.setOnClickListener { startActivity(Intent(this, OrdersActivity::class.java)) }
        findViewById<View>(R.id.btn_nav_filters).setOnClickListener { startActivity(Intent(this, FiltersActivity::class.java)) }

        btnLockLocation = findViewById(R.id.btn_lock_location)
        btnLockLocation.setOnClickListener { handleLockLocationClick() }

        findViewById<View>(R.id.btn_my_location).setOnClickListener { centerMapOnUser() }

        btnSectors = findViewById(R.id.btn_sectors)
        btnSectors.setOnClickListener { toggleSectors() }

        btnHotspots = findViewById(R.id.btn_hotspots)
        btnHotspots.setOnClickListener { toggleHeatmap() }

        findViewById<View>(R.id.btn_menu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navViewContent.findViewById<View>(R.id.btn_open_profile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        navViewContent.findViewById<View>(R.id.btn_menu_dispatcher).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showDispatcherBottomSheet()
        }
        navViewContent.findViewById<View>(R.id.btn_menu_sos).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showSosConfirmationDialog()
        }
        navViewContent.findViewById<View>(R.id.menu_item_car).setOnClickListener { startActivity(Intent(this, CarActivity::class.java)); drawerLayout.closeDrawer(GravityCompat.START) }
        navViewContent.findViewById<View>(R.id.menu_item_news).setOnClickListener {
            startActivity(Intent(this, NewsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navViewContent.findViewById<View>(R.id.menu_item_balance).setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navViewContent.findViewById<View>(R.id.menu_item_activity).setOnClickListener { startActivity(Intent(this, DriverScoreActivity::class.java)); drawerLayout.closeDrawer(GravityCompat.START) }
        navViewContent.findViewById<View>(R.id.menu_item_stats)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navViewContent.findViewById<View>(R.id.menu_item_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navViewContent.findViewById<View>(R.id.menu_item_notifications).setOnClickListener {
            startActivity(Intent(this, ServiceMessagesActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navViewContent.findViewById<View>(R.id.btn_logout).setOnClickListener {
            setOnlineVisualState(false, animate = false)
            sessionManager.saveDriverOnlineStatus(false)
            lifecycleScope.launch {
                try {
                    ApiClient.getInstance().getApiService(this@MainActivity).deleteLocation()
                } catch (_: Exception) {
                } finally {
                    boundLocationService?.onLocationUpdated = null
                    stopService(Intent(this@MainActivity, LocationService::class.java))
                    stopService(Intent(this@MainActivity, FloatingWidgetService::class.java))

                    val intent = Intent(this@MainActivity, AccountSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
        updateSearchStatusUI()
    }

    /**
     * Инициализирует векторные иконки нижней панели и карточки поиска в едином стиле со скриншотом.
     */
    private fun setupBottomBarIcons() {
        val density = resources.displayMetrics.density
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val iconSize22 = (22 * density).toInt()
        val iconSize24 = (24 * density).toInt()

        ivEtherFilterIcon.imageTintList = null
        ivEtherFilterIcon.setImageDrawable(
            BottomBarIconDrawable(BottomBarIconDrawable.Type.ETHER_FILTER, primaryColor, iconSize22)
        )

        ivEtherListIcon.imageTintList = null
        ivEtherListIcon.setImageDrawable(
            BottomBarIconDrawable(BottomBarIconDrawable.Type.ETHER_LIST, primaryColor, iconSize22)
        )

        ivNavOrdersIcon.imageTintList = null
        ivNavOrdersIcon.setImageDrawable(
            BottomBarIconDrawable(BottomBarIconDrawable.Type.HISTORY_CLOCK, primaryColor, iconSize24)
        )

        ivFilterAutoIcon.imageTintList = null
        ivFilterAutoIcon.setImageDrawable(
            BottomBarIconDrawable(BottomBarIconDrawable.Type.FILTER_AUTO, primaryColor, iconSize22)
        )

        ivFilterCycleIcon.imageTintList = null
        ivFilterCycleIcon.setImageDrawable(
            BottomBarIconDrawable(BottomBarIconDrawable.Type.FILTER_CYCLE, primaryColor, iconSize22)
        )
    }

    /**
     * Синхронизирует 4 счетчика на нижней панели:
     * - Ефір: [заказы по эфир-фильтрам] [всего доступных заказов сейчас в эфире]
     * - Фільтри: [активных Авто-фильтров] [активных Цикл-фильтров]
     */
    private fun syncBottomNavCounters() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@MainActivity)
                coroutineScope {
                    val filtersDeferred = async {
                        try { api.getFilters() } catch (_: Exception) { null }
                    }
                    val ordersDeferred = async {
                        if (isDriverOnline) {
                            try { api.getAvailableOrders() } catch (_: Exception) { null }
                        } else null
                    }

                    val filtersRes = filtersDeferred.await()
                    val ordersRes = ordersDeferred.await()

                    val filters = if (filtersRes?.isSuccessful == true) filtersRes.body().orEmpty() else emptyList()
                    val activeOrders = if (ordersRes?.isSuccessful == true) {
                        ordersRes.body().orEmpty().filter { !it.isScheduled() }
                    } else {
                        emptyList()
                    }

                    val autoCount = filters.count { it.isActive && it.isAuto }
                    val cycleCount = filters.count { it.isActive && it.isCycle }
                    val activeEtherFilters = filters.filter { it.isActive && it.isEther }

                    val etherTotalCount = if (isDriverOnline) activeOrders.size else 0
                    val etherFilteredCount = if (!isDriverOnline || activeEtherFilters.isEmpty()) {
                        0
                    } else {
                        activeOrders.count { order ->
                            activeEtherFilters.any { filter -> matchesEtherFilter(order, filter) }
                        }
                    }

                    tvEtherFilterCount.text = etherFilteredCount.toString()
                    tvEtherListCount.text = etherTotalCount.toString()
                    tvFilterAutoCount.text = autoCount.toString()
                    tvFilterCycleCount.text = cycleCount.toString()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Не вдалося оновити лічильники нижньої панелі: ${e.message}")
            }
        }
    }

    private fun matchesEtherFilter(order: Order, filter: DriverFilter): Boolean {
        if (filter.paymentType != "ANY" && !filter.paymentType.equals(order.paymentMethod, ignoreCase = true)) {
            return false
        }
        val orderPrice = order.getTotalFullPrice()
        if (filter.tariffType == "SIMPLE") {
            val minP = filter.minPrice ?: 0.0
            if (minP > 0.0 && orderPrice < minP) return false
            val minKm = filter.minPricePerKm ?: 0.0
            if (minKm > 0.0) {
                val distKm = (order.distanceMeters ?: 0) / 1000.0
                if (distKm > 0.0 && (orderPrice / distKm) < minKm) return false
            }
        } else {
            val cMin = filter.complexMinPrice ?: 0.0
            if (cMin > 0.0 && orderPrice < cMin) return false
        }
        return true
    }

    private fun setOnlineVisualState(online: Boolean, animate: Boolean) {
        this.isDriverOnline = online
        val trackContainer = findViewById<ViewGroup>(R.id.switch_track_container)
        if (animate) {
            android.transition.TransitionManager.beginDelayedTransition(trackContainer)
        }
        val params = switchThumbCard.layoutParams as android.widget.FrameLayout.LayoutParams
        if (online) {
            params.gravity = android.view.Gravity.END
            switchThumbCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSwitchStatusText.text = "Онлайн"
            tvSwitchStatusText.setTextColor(Color.BLACK)
        } else {
            params.gravity = android.view.Gravity.START
            val offlineBgColor = ContextCompat.getColor(this, R.color.driver_surface_elevated)
            switchThumbCard.setCardBackgroundColor(offlineBgColor)
            tvSwitchStatusText.text = "Офлайн"
            tvSwitchStatusText.setTextColor(ContextCompat.getColor(this, R.color.driver_text_primary))
        }
        switchThumbCard.layoutParams = params
    }

    private fun showDispatcherBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_support, null)
        bottomSheetDialog.setContentView(view)
        view.findViewById<View>(R.id.bs_btn_telegram)?.setOnClickListener { bottomSheetDialog.dismiss(); openTelegramSupport() }
        view.findViewById<View>(R.id.bs_btn_call)?.setOnClickListener { bottomSheetDialog.dismiss(); callDispatcher() }
        bottomSheetDialog.show()
    }

    private fun openTelegramSupport() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TaxiSupport"))) } catch (e: Exception) { Toast.makeText(this, "Telegram не встановлено", Toast.LENGTH_SHORT).show() }
    }

    private fun callDispatcher() {
        try { startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:+380999999999") }) } catch (e: Exception) { Toast.makeText(this, "Помилка дзвінка", Toast.LENGTH_SHORT).show() }
    }

    private fun showSosConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        val customView = layoutInflater.inflate(R.layout.dialog_sos_confirm, null)
        builder.setView(customView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val btnCancel = customView.findViewById<View>(R.id.btnCancelSos)
        val btnConfirm = customView.findViewById<View>(R.id.btnConfirmSos)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener { dialog.dismiss(); sendSosSignal() }
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSectorSelectionRequest(intent)
    }

    private fun sendSosSignal() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Немає доступу до геолокації!", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch {
                    try {
                        val req = UpdateLocationRequest(location.latitude, location.longitude)
                        val response = ApiClient.getInstance().getApiService(this@MainActivity).sendSos(req)
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "SOS СИГНАЛ ВІДПРАВЛЕНО ДИСПЕТЧЕРУ!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Помилка відправки SOS", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Помилка мережі (SOS)", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Неможливо визначити місцезнаходження", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSearchActivation() {
        if (!isDriverOnline) {
            setOnlineVisualState(true, animate = true)
            isDriverOnline = true
            sessionManager.saveDriverOnlineStatus(true)

            isSearchActive = true
            updateSearchBlockVisuals(true)

            btnStatusToggle.isEnabled = false
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.getInstance().getApiService(this@MainActivity)
                            .updateStatus(UpdateDriverStatusRequest(true, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0))
                        if (response.isSuccessful) {
                            val targetMode = sessionManager.getSearchMode()
                            val req = DriverSearchSettingsDto(mode = targetMode, radius = currentSearchRadiusKm, homeSectorIds = currentHomeSectorIds)
                            val settingsResponse = ApiClient.getInstance().getApiService(this@MainActivity).updateSearchSettings(req)

                            if (settingsResponse.isSuccessful && settingsResponse.body() != null) {
                                displaySearchState(settingsResponse.body()!!)
                                syncBottomNavCounters()
                                Toast.makeText(this@MainActivity, "Вийшли в онлайн та розпочали пошук!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            revertOfflineState()
                            Toast.makeText(this@MainActivity, "Помилка активації мережі", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        revertOfflineState()
                    } finally {
                        btnStatusToggle.isEnabled = true
                    }
                }
            }.addOnFailureListener {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.getInstance().getApiService(this@MainActivity)
                            .updateStatus(UpdateDriverStatusRequest(true, 0.0, 0.0))
                        if (response.isSuccessful) {
                            val targetMode = sessionManager.getSearchMode()
                            val req = DriverSearchSettingsDto(mode = targetMode, radius = currentSearchRadiusKm, homeSectorIds = currentHomeSectorIds)
                            val settingsResponse = ApiClient.getInstance().getApiService(this@MainActivity).updateSearchSettings(req)

                            if (settingsResponse.isSuccessful && settingsResponse.body() != null) {
                                displaySearchState(settingsResponse.body()!!)
                                syncBottomNavCounters()
                            }
                        } else {
                            revertOfflineState()
                        }
                    } catch (e: Exception) {
                        revertOfflineState()
                    } finally {
                        btnStatusToggle.isEnabled = true
                    }
                }
            }
            return
        }

        isSearchActive = !isSearchActive
        updateSearchBlockVisuals(isSearchActive)

        lifecycleScope.launch {
            try {
                val targetMode = if (isSearchActive) sessionManager.getSearchMode() else DriverSearchMode.MANUAL

                if (isSearchActive) {
                    boundLocationService?.forceSendLocationNow()
                    val lat = currentDriverLocation?.latitude ?: 0.0
                    val lng = currentDriverLocation?.longitude ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        try {
                            ApiClient.getInstance().getApiService(this@MainActivity)
                                .updateStatus(UpdateDriverStatusRequest(true, lat, lng))
                        } catch (_: Exception) {}
                    }
                }

                val req = DriverSearchSettingsDto(
                    mode = targetMode,
                    radius = currentSearchRadiusKm,
                    homeSectorIds = currentHomeSectorIds
                )

                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateSearchSettings(req)
                if (response.isSuccessful && response.body() != null) {
                    displaySearchState(response.body()!!)
                    if (isSearchActive) Toast.makeText(this@MainActivity, "Пошук розпочато...", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this@MainActivity, "Пошук зупинено.", Toast.LENGTH_SHORT).show()
                } else {
                    isSearchActive = !isSearchActive
                    updateSearchBlockVisuals(isSearchActive)
                    Toast.makeText(this@MainActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isSearchActive = !isSearchActive
                updateSearchBlockVisuals(isSearchActive)
                Toast.makeText(this@MainActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displaySearchState(state: com.taxiapp.driver.network.DriverSearchStateDto) {
        currentSearchRadiusKm = state.radius
        currentHomeSectorIds = state.homeSectorIds ?: emptyList()
        sessionManager.saveSearchRadius(state.radius)
        sessionManager.saveHomeSectorIds(currentHomeSectorIds)
        drawSearchRadius()

        val rawMode = state.mode ?: DriverSearchMode.MANUAL
        val displayedMode: DriverSearchMode = if (rawMode == DriverSearchMode.MANUAL || rawMode == DriverSearchMode.OFFLINE) {
            isSearchActive = false
            val saved = sessionManager.getSearchMode()
            if (saved == DriverSearchMode.HOME) DriverSearchMode.HOME else DriverSearchMode.CHAIN
        } else {
            isSearchActive = true
            if (!isDriverOnline) {
                isDriverOnline = true
                sessionManager.saveDriverOnlineStatus(true)
                setOnlineVisualState(true, animate = false)
            }
            rawMode
        }

        currentDisplayedSearchMode = displayedMode

        when (displayedMode) {
            DriverSearchMode.OFFLINE,
            DriverSearchMode.BUSY,
            DriverSearchMode.MANUAL,
            DriverSearchMode.CHAIN -> {
                tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                tvSearchModeSubtitle.text = formatRadiusKm(state.radius)
                if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = !sectorOverlay.isShown
            }
            DriverSearchMode.HOME -> {
                val sectorsText = if (state.homeSectorNames.isNullOrEmpty()) "не обрано" else state.homeSectorNames
                tvSearchModeTitle.text = "Додому ($sectorsText)"
                tvSearchModeSubtitle.text = formatRadiusKm(state.radius)
                if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = !sectorOverlay.isShown
            }
        }

        updateSearchBlockVisuals(isSearchActive)
    }

    private fun updateSearchBlockVisuals(isActive: Boolean) {
        val cardSearchContainer = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSearchContainer)
        val cardSearchMode = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSearchMode)
        val borderAnimView = findViewById<View>(R.id.search_border_animator)
        val density = resources.displayMetrics.density
        val iconSize = (28 * density).toInt()

        if (originalContainerElevation == -1f) {
            originalContainerElevation = cardSearchContainer.cardElevation
        }

        val iconType = if (currentDisplayedSearchMode == DriverSearchMode.HOME) {
            BottomBarIconDrawable.Type.HOME_MODE
        } else {
            BottomBarIconDrawable.Type.CHAIN_LINK
        }

        if (isActive) {
            val activeDarkColor = Color.parseColor("#1A1C1E")
            cardSearchContainer.setCardBackgroundColor(Color.TRANSPARENT)
            cardSearchContainer.cardElevation = 0f
            cardSearchContainer.clipToOutline = true
            cardSearchMode.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSearchModeTitle.setTextColor(activeDarkColor)
            tvSearchModeSubtitle.setTextColor(activeDarkColor)
            ivSearchModeIcon.imageTintList = null
            ivSearchModeIcon.setImageDrawable(BottomBarIconDrawable(iconType, activeDarkColor, iconSize))

            borderAnimView.visibility = View.VISIBLE
            if (searchBorderAnimator == null) {
                searchBorderAnimator = android.animation.ObjectAnimator.ofFloat(borderAnimView, "rotation", 360f, 0f).apply {
                    duration = 2000
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                }
            }
            if (searchBorderAnimator?.isRunning == false) {
                searchBorderAnimator?.start()
            }
        } else {
            val tealColor = ContextCompat.getColor(this, R.color.driver_neon_teal)
            val secondaryTextColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
            cardSearchContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_black_bg))
            cardSearchContainer.cardElevation = originalContainerElevation
            cardSearchMode.setCardBackgroundColor(ContextCompat.getColor(this, R.color.driver_black_bg))
            tvSearchModeTitle.setTextColor(tealColor)
            tvSearchModeSubtitle.setTextColor(secondaryTextColor)
            ivSearchModeIcon.imageTintList = null
            ivSearchModeIcon.setImageDrawable(BottomBarIconDrawable(iconType, tealColor, iconSize))

            searchBorderAnimator?.cancel()
            borderAnimView.visibility = View.GONE
        }
    }

    private fun updateSearchStatusUI() {
        lifecycleScope.launch {
            if (!isDriverOnline) {
                isSearchActive = false

                val cachedMode = sessionManager.getSearchMode()
                currentDisplayedSearchMode = if (cachedMode == DriverSearchMode.HOME) DriverSearchMode.HOME else DriverSearchMode.CHAIN
                currentHomeSectorIds = sessionManager.getHomeSectorIds()

                try {
                    val response = ApiClient.getInstance().getApiService(this@MainActivity).getSearchSettings()
                    if (response.isSuccessful && response.body() != null) {
                        val state = response.body()!!
                        currentSearchRadiusKm = state.radius
                        currentHomeSectorIds = state.homeSectorIds ?: emptyList()

                        if (cachedMode == DriverSearchMode.HOME) {
                            val sectorsText = if (state.homeSectorNames.isNullOrEmpty()) "не обрано" else state.homeSectorNames
                            tvSearchModeTitle.text = "Додому ($sectorsText)"
                            tvSearchModeSubtitle.text = formatRadiusKm(state.radius)
                        } else {
                            tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                            tvSearchModeSubtitle.text = formatRadiusKm(state.radius)
                        }
                    } else {
                        if (cachedMode == DriverSearchMode.HOME) {
                            tvSearchModeTitle.text = "Додому"
                            tvSearchModeSubtitle.text = formatRadiusKm(currentSearchRadiusKm)
                        } else {
                            tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                            tvSearchModeSubtitle.text = formatRadiusKm(currentSearchRadiusKm)
                        }
                    }
                } catch (e: Exception) {
                    if (cachedMode == DriverSearchMode.HOME) {
                        tvSearchModeTitle.text = "Додому"
                        tvSearchModeSubtitle.text = formatRadiusKm(currentSearchRadiusKm)
                    } else {
                        tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                        tvSearchModeSubtitle.text = formatRadiusKm(currentSearchRadiusKm)
                    }
                }

                updateSearchBlockVisuals(false)
                return@launch
            }

            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getSearchSettings()
                if (response.isSuccessful && response.body() != null) {
                    displaySearchState(response.body()!!)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun revertOfflineState() {
        setOnlineVisualState(false, animate = true)
        isDriverOnline = false
        sessionManager.saveDriverOnlineStatus(false)
        isSearchActive = false
        updateSearchBlockVisuals(false)
    }

    private fun checkPhotoControlStatus() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActivePhotoControl()
                if (response.isSuccessful && response.body() != null) {
                    val pc = response.body()!!
                    activePhotoControlData = pc

                    if (pc.photoControlRestricted) {
                        cardPhotoControl.visibility = View.VISIBLE
                        cardPhotoControl.setCardBackgroundColor(Color.parseColor("#e74c3c"))
                        tvPhotoControlTitle.text = "Обмеження в роботі"
                        tvPhotoControlSubtitle.text = pc.rejectReason ?: "Пройдіть повторний фотоконтроль для відновлення"
                        btnPassPhotoControl.visibility = View.VISIBLE
                    } else if (pc.status == "PENDING") {
                        cardPhotoControl.visibility = View.VISIBLE
                        cardPhotoControl.setCardBackgroundColor(Color.parseColor("#e67e22"))
                        tvPhotoControlTitle.text = "Потрібно пройти фотоконтроль"

                        val timeFormatted = try {
                            val rawDate = pc.deadlineAt
                            if (!rawDate.isNullOrBlank()) {
                                val timePart = when {
                                    rawDate.contains("T") -> rawDate.substringAfter("T")
                                    rawDate.contains(" ") -> rawDate.substringAfter(" ")
                                    else -> rawDate
                                }
                                timePart.take(5)
                            } else ""
                        } catch (e: Exception) { "" }

                        tvPhotoControlSubtitle.text = if (timeFormatted.isNotBlank()) "Пройдіть фотоконтроль до $timeFormatted" else "Потрібно пройти фотоконтроль"
                        btnPassPhotoControl.visibility = View.VISIBLE
                    } else if (pc.status == "SUBMITTED") {
                        cardPhotoControl.visibility = View.VISIBLE
                        cardPhotoControl.setCardBackgroundColor(Color.parseColor("#3498db"))
                        tvPhotoControlTitle.text = "Фото на перевірці"
                        tvPhotoControlSubtitle.text = "Очікуйте рішення диспетчера"
                        btnPassPhotoControl.visibility = View.GONE
                    } else {
                        cardPhotoControl.visibility = View.GONE
                    }
                } else {
                    cardPhotoControl.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("PHOTO_CONTROL", "Помилка перевірки фотоконтролю: ${e.message}")
            }
        }
    }

    private fun loadUserProfile() {
        val tvDriverName = navViewContent.findViewById<TextView>(R.id.tv_driver_name)
        val tvRating = navViewContent.findViewById<TextView>(R.id.tv_menu_rating)
        val imgAvatar = navViewContent.findViewById<ImageView>(R.id.img_avatar)
        val tvPlateNumber = navViewContent.findViewById<TextView>(R.id.tv_menu_plate_number)

        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvDriverName.text = extractFirstName(savedName)
        } else {
            val token = sessionManager.fetchAuthToken()
            tvDriverName.text = if (token != null && token.length > 4) "ID ${token.takeLast(4)}" else "Водій"
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getDriverProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    sessionManager.saveDriverId(profile.id)
                    profile.isOnline?.let { srvOnline ->
                        if (srvOnline != isDriverOnline) {
                            isDriverOnline = srvOnline
                            sessionManager.saveDriverOnlineStatus(srvOnline)
                            setOnlineVisualState(srvOnline, animate = false)
                            updateSearchStatusUI()
                            syncBottomNavCounters()
                        }
                    }
                    val serverName = profile.fullName ?: "Водій"
                    sessionManager.saveDriverName(serverName)
                    tvDriverName.text = extractFirstName(serverName)
                    if (tvRating != null) {
                        tvRating.text = String.format("%.1f", profile.rating)
                    }
                    val fullAvatarUrl = toFullImageUrl(profile.photoUrl)
                    if (!fullAvatarUrl.isNullOrEmpty()) {
                        Glide.with(this@MainActivity)
                            .load(fullAvatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_driver_avatar_placeholder)
                            .into(imgAvatar)
                    }
                    if (profile.car != null && !profile.car.plateNumber.isNullOrEmpty()) {
                        tvPlateNumber.text = profile.car.plateNumber
                        tvPlateNumber.visibility = View.VISIBLE
                    } else {
                        tvPlateNumber.visibility = View.GONE
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnUser() {
        if (!::map.isInitialized) return
        if (sectorOverlay.isShown) return

        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (_: Exception) {}
            with(map.uiSettings) {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isZoomControlsEnabled = false
                isMapToolbarEnabled = false
            }
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                val latLng = LatLng(manualLoc.first, manualLoc.second)
                currentDriverLocation = latLng
                drawSearchRadius()
                if (manualLocationMarker == null) {
                    manualLocationMarker = map.addMarker(MarkerOptions().position(latLng).title("Фіксована позиція").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
                } else manualLocationMarker?.position = latLng

                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.5f))
            }
        } else {
            manualLocationMarker?.remove()
            manualLocationMarker = null
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        currentDriverLocation = LatLng(it.latitude, it.longitude)
                        drawSearchRadius()
                        updateCustomDriverMarker(it.latitude, it.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.5f))
                    }
                }
            }
        }
    }

    private fun getCachedDriverDotDescriptor(): BitmapDescriptor? {
        cachedDriverMarkerIcon?.let { return it }
        val drawable = ContextCompat.getDrawable(this, R.drawable.bg_driver_location_dot) ?: return null
        val size = (12 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { cachedDriverMarkerIcon = it }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMapToolbarEnabled = false
        try {
            val isNightMode = when (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

            val styleRes = if (isNightMode) R.raw.map_style_dark else R.raw.map_style_standard
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
        } catch (_: Exception) {}

        if (defaultMapPaddingBottom == 0) {
            val density = resources.displayMetrics.density
            defaultMapPaddingBottom = (172 * density).toInt()
        }
        map.setPadding(0, 0, 0, defaultMapPaddingBottom)

        map.setOnPolygonClickListener { polygon ->
            if (sectorOverlay.isShown) {
                val id = polygon.tag as? Long ?: return@setOnPolygonClickListener
                toggleSectorSelection(id)
            }
        }

        updateMapUI()
        centerMapOnUser()
        drawSearchRadius()
    }

    private fun updateCustomDriverMarker(lat: Double, lng: Double) {
        if (!::map.isInitialized) return

        val latLng = LatLng(lat, lng)
        if (driverLocationMarker == null) {
            val customIcon = getCachedDriverDotDescriptor()
            driverLocationMarker = map.addMarker(MarkerOptions()
                .position(latLng)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(150f)
                .icon(customIcon))
        } else {
            driverLocationMarker?.position = latLng
        }

        driverLocationMarker?.isVisible = !sessionManager.isManualLocationActive()
    }

    private fun resetHotspotsButton() {
        isHeatmapVisible = false
        clearHeatmapFromMap()
        btnHotspots.backgroundTintList = null
        btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_text_primary))
    }

    private fun resetSectorsButton() {
        isSectorsVisible = false
        clearSectorsFromMap()
        btnSectors.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_text_primary))
    }

    private fun generateGlowBitmap(color: Int): BitmapDescriptor {
        cachedGlowBitmaps[color]?.let { return it }
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint()
        val radius = size / 2f
        val gradient = android.graphics.RadialGradient(radius, radius, radius, intArrayOf(color, color, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP)
        paint.shader = gradient
        paint.isAntiAlias = true
        canvas.drawCircle(radius, radius, radius, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { cachedGlowBitmaps[color] = it }
    }

    private fun toggleHeatmap() {
        if (isSectorsVisible) {
            resetSectorsButton()
        }
        if (isHeatmapVisible) {
            resetHotspotsButton()
            Toast.makeText(this, "Рибні місця приховано", Toast.LENGTH_SHORT).show()
        } else {
            isHeatmapVisible = true
            btnHotspots.backgroundTintList = null
            btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            loadAndDrawHeatmap()
        }
    }

    private fun loadAndDrawHeatmap() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getHeatmap()
                if (response.isSuccessful && response.body() != null) {
                    val zones = response.body()!!
                    if (zones.isNotEmpty()) {
                        drawHeatmapGlow(zones)
                        Toast.makeText(this@MainActivity, "Знайдено ${zones.size} активних зон", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Зараз немає скупчень замовлень", Toast.LENGTH_SHORT).show()
                        resetHotspotsButton()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Не вдалося отримати дані Heatmap", Toast.LENGTH_SHORT).show()
                    resetHotspotsButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Помилка завантаження Heatmap", Toast.LENGTH_SHORT).show()
                resetHotspotsButton()
            }
        }
    }

    private fun drawHeatmapGlow(zones: List<HeatmapZoneDto>) {
        clearHeatmapFromMap()
        for (zone in zones) {
            val center = LatLng(zone.centerLat, zone.centerLng)
            val baseColor = when (zone.level) { 3 -> 0xCCFF3D00.toInt(); 2 -> 0xCCFFD600.toInt(); else -> 0xCC00BFA5.toInt() }
            val overlay = map.addGroundOverlay(GroundOverlayOptions().position(center, 2500f).image(generateGlowBitmap(baseColor)).transparency(0.3f).zIndex(100f))
            overlay?.let { heatmapOverlays.add(it) }
        }
    }

    private fun clearHeatmapFromMap() { heatmapOverlays.forEach { it.remove() }; heatmapOverlays.clear() }

    private fun toggleSectors() {
        if (isHeatmapVisible) resetHotspotsButton()
        if (isSectorsVisible) {
            resetSectorsButton()
            Toast.makeText(this, "Сектори приховано", Toast.LENGTH_SHORT).show()
        } else {
            isSectorsVisible = true
            btnSectors.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            loadAndDrawSectors()
        }
    }

    private fun loadAndDrawSectors() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getSectors()
                if (response.isSuccessful && response.body() != null) {
                    drawSectorsOnMap(response.body()!!)
                } else {
                    Toast.makeText(this@MainActivity, "Помилка завантаження секторів", Toast.LENGTH_SHORT).show()
                    resetSectorsButton()
                }
            } catch (e: Exception) {
                resetSectorsButton()
            }
        }
    }

    private fun drawSectorsOnMap(sectors: List<Sector>) {
        clearSectorsFromMap()
        for (sector in sectors) {
            if (sector.points.isEmpty()) continue
            val polygonOptions = PolygonOptions().addAll(sector.points.map { LatLng(it.lat, it.lng) }).fillColor(Color.argb(45, 0, 255, 170)).strokeColor(ContextCompat.getColor(this, R.color.driver_neon_teal)).strokeWidth(4f)
            sectorPolygons.add(map.addPolygon(polygonOptions))
            val marker = map.addMarker(MarkerOptions().position(getPolygonCenter(sector.points)).icon(createTextIcon(sector.name)).anchor(0.5f, 0.5f).flat(true))
            marker?.let { sectorMarkers.add(it) }
        }
    }

    private fun createTextIcon(text: String): BitmapDescriptor {
        val textView = TextView(this)
        textView.text = text
        textView.setTextColor(Color.parseColor("#00ffaa"))
        textView.textSize = 14f
        textView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textView.setShadowLayer(3f, 1f, 1f, Color.BLACK)
        textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)
        val bitmap = Bitmap.createBitmap(textView.measuredWidth, textView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        textView.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clearSectorsFromMap() { sectorPolygons.forEach { it.remove() }; sectorPolygons.clear(); sectorMarkers.forEach { it.remove() }; sectorMarkers.clear() }
    private fun getPolygonCenter(points: List<SectorPointDto>): LatLng { val builder = LatLngBounds.Builder(); for (p in points) builder.include(LatLng(p.lat, p.lng)); return builder.build().center }

    private fun updateMapUI() {
        if (!::map.isInitialized) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (_: Exception) {}
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                val latLng = LatLng(manualLoc.first, manualLoc.second)
                currentDriverLocation = latLng
                drawSearchRadius()

                driverLocationMarker?.isVisible = false

                if (manualLocationMarker == null) {
                    val customIcon = getCachedDriverDotDescriptor()
                    manualLocationMarker = map.addMarker(MarkerOptions()
                        .position(latLng)
                        .title("Фіксована позиція")
                        .anchor(0.5f, 0.5f)
                        .zIndex(150f)
                        .icon(customIcon))
                } else {
                    manualLocationMarker?.position = latLng
                }
            }
        } else {
            manualLocationMarker?.remove()
            manualLocationMarker = null
            driverLocationMarker?.isVisible = true

            if (hasPermission) {
                map.isMyLocationEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
            }
        }
    }

    private fun updateLockIconState() {
        btnLockLocation.backgroundTintList = null
        if (sessionManager.isManualLocationActive()) {
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
        } else {
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_text_primary))
        }
    }

    private data class BottomSheetOptionDto(val text: String, val onClick: () -> Unit)

    private fun handleLockLocationClick() {
        if (sessionManager.isManualLocationActive()) {
            showDisableManualLocationDialog()
        } else {
            startActivity(Intent(this, LockLocationActivity::class.java))
        }
    }

    private fun showDisableManualLocationDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_generic, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.tv_sheet_title).text = "Вимкнути ручне закріплення?"

        val options = listOf(
            BottomSheetOptionDto("Змінити позицію") {
                bottomSheetDialog.dismiss()
                startActivity(Intent(this, LockLocationActivity::class.java))
            },
            BottomSheetOptionDto("Вимкнути закріплення") {
                bottomSheetDialog.dismiss()
                sessionManager.clearManualLocation()
                updateLockIconState()
                updateMapUI()
                centerMapOnUser()
            },
            BottomSheetOptionDto("Скасувати") {
                bottomSheetDialog.dismiss()
            }
        )

        val rvOptions = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_sheet_options)
        rvOptions.layoutManager = LinearLayoutManager(this)

        rvOptions.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

            inner class OptionVH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
                val container: View = v.findViewById(R.id.container_option)
                val icon: ImageView = v.findViewById(R.id.iv_option_icon)
                val text: TextView = v.findViewById(R.id.tv_option_text)
                val divider: View = v.findViewById(R.id.divider_option)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bottom_sheet_option, parent, false)
                return OptionVH(v)
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val h = holder as OptionVH
                val item = options[position]
                h.text.text = item.text
                h.icon.visibility = View.GONE
                h.container.setOnClickListener { item.onClick() }
                h.divider.visibility = if (position == options.size - 1) View.GONE else View.VISIBLE
            }

            override fun getItemCount() = options.size
        }

        bottomSheetDialog.show()
    }

    private fun syncActiveOrderAndBadge() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActiveOrder()
                val order = if (response.isSuccessful) response.body() else null

                orderBadgeDot.visibility = if (order != null) View.VISIBLE else View.GONE

                if (order != null) {
                    if (order.status == "OFFERING") {
                        val intent = Intent(this@MainActivity, OrderOfferActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", order)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    } else if (
                        order.status == "ACCEPTED" ||
                        order.status == "DRIVER_ARRIVED" ||
                        order.status == "IN_PROGRESS" ||
                        order.status == "ARRIVED_AT_WAYPOINT"
                    ) {
                        if (!sessionManager.isOrderMinimized()) {
                            val intent = Intent(this@MainActivity, OrderProgressActivity::class.java)
                            intent.putExtra("EXTRA_ORDER", order)
                            startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                orderBadgeDot.visibility = View.GONE
            }
        }
    }

    private fun updateDriverStatus(isOnline: Boolean) {
        btnStatusToggle.isEnabled = false
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            sendStatusRequest(isOnline, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        }.addOnFailureListener {
            sendStatusRequest(isOnline, 0.0, 0.0)
        }
    }

    private fun sendStatusRequest(isOnline: Boolean, lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateStatus(UpdateDriverStatusRequest(isOnline, lat, lng))
                if (!response.isSuccessful) {
                    setOnlineVisualState(!isOnline, animate = true)
                    sessionManager.saveDriverOnlineStatus(!isOnline)
                } else {
                    sessionManager.saveDriverOnlineStatus(isOnline)
                    updateSearchStatusUI()
                    syncBottomNavCounters()
                }
            } catch (e: Exception) {
                setOnlineVisualState(!isOnline, animate = true)
                sessionManager.saveDriverOnlineStatus(!isOnline)
            } finally {
                btnStatusToggle.isEnabled = true
            }
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        if (!isLocationServiceBound) {
            try {
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        val allGranted = permissionsToRequest.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            startLocationService()
        } else requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when { parts.size >= 2 -> parts[1]; parts.isNotEmpty() -> parts[0]; else -> "Водій" }
    }

    private fun updateCommissionInfo() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getCommission()
                if (response.isSuccessful && response.body() != null) {
                    val percent = response.body()!!.percent
                    val tvCommission = navViewContent.findViewById<TextView>(R.id.tv_menu_commission)
                    tvCommission.text = "Комісія сервісу: ${String.format("%.1f", percent)}%"
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateSectorSaveButtonState(selectedCount: Int) {
        if (::btnSaveSelection.isInitialized) {
            if (selectedCount > 0) {
                btnSaveSelection.isEnabled = true
                btnSaveSelection.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.driver_neon_teal)
                )
            } else {
                btnSaveSelection.isEnabled = false
                btnSaveSelection.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.driver_text_secondary)
                )
            }
        }
    }

    private fun showRestoreDialog() {
        if (restoreDialog?.isShowing == true) return
        restoreDialog = android.app.Dialog(this)
        restoreDialog?.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        restoreDialog?.setContentView(R.layout.dialog_restore_account)
        restoreDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        restoreDialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        restoreDialog?.setCancelable(false)

        val btnCancel = restoreDialog?.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelRestore)
        val btnConfirm = restoreDialog?.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmRestore)

        btnCancel?.setOnClickListener {
            restoreDialog?.dismiss()
            sessionManager.saveAuthToken("")
            val intent = Intent(this@MainActivity, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        btnConfirm?.setOnClickListener {
            btnConfirm.isEnabled = false
            btnConfirm.text = "Відновлення..."
            restoreAccount()
        }
        restoreDialog?.show()
    }

    private fun restoreAccount() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).restoreAccount()
                if (response.isSuccessful) {
                    sessionManager.setPendingDeletion(false)
                    restoreDialog?.dismiss()
                    Toast.makeText(this@MainActivity, "Акаунт успішно відновлено!", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this@MainActivity, "Помилка відновлення", Toast.LENGTH_SHORT).show()
                    restoreDialog?.dismiss()
                    showRestoreDialog()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                restoreDialog?.dismiss()
                showRestoreDialog()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun startHomeSectorSelection(preSelectedIds: List<Long>?) {
        currentSelectionTarget = SelectionTarget.HOME
        selectedIds.clear()
        preSelectedIds?.forEach { selectedIds.add(it) }

        selectionTabs.getTabAt(0)?.select()
        sectorOverlay.setBackgroundColor(Color.TRANSPARENT)
        etSectorSearch.setText("")

        sectorOverlay.visibility = View.VISIBLE
        mainScreenUiGroup.visibility = View.GONE

        if (::map.isInitialized) {
            map.setPadding(0, 0, 0, systemBarsBottomInset)
            if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = false
            drawOverlaySectorsOnMap()
        }
        updateList()
        updateSectorSaveButtonState(selectedIds.size)
        overlayBackPressedCallback?.isEnabled = true
    }

    private fun updateHeaderSelectionPadding() {
        val header = findViewById<View>(R.id.header_selection) ?: return
        val density = resources.displayMetrics.density
        val baseHorizontal = (24 * density).toInt()
        val baseBottom = (16 * density).toInt()
        val baseTop = (12 * density).toInt()

        val topInset = if (systemBarsTopInset > 0) {
            systemBarsTopInset
        } else {
            val windowInsets = ViewCompat.getRootWindowInsets(findViewById(R.id.root_container))
            windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: (28 * density).toInt()
        }

        header.setPadding(baseHorizontal, topInset + baseTop, baseHorizontal, baseBottom)
    }

    private fun loadSectorsDataSilently() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@MainActivity).getSectors()
                if (res.isSuccessful) {
                    allSectors = res.body() ?: emptyList()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun toggleSectorSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)

        polygons[id]?.let { poly ->
            val isSelected = selectedIds.contains(id)
            poly.fillColor = if (isSelected) Color.argb(120, 0, 255, 170) else Color.argb(40, 128, 128, 128)
            poly.strokeColor = if (isSelected) Color.parseColor("#00ffaa") else Color.GRAY
        }
        if (rvSectorsList.visibility == View.VISIBLE) {
            sectorsListAdapter?.notifyDataSetChanged()
        }
        updateSectorSaveButtonState(selectedIds.size)
    }

    private fun drawOverlaySectorsOnMap() {
        if (!::map.isInitialized || allSectors.isEmpty()) return
        clearOverlayPolygons()

        for (sector in allSectors) {
            if (sector.points.isEmpty()) continue
            val options = PolygonOptions()
                .addAll(sector.points.map { LatLng(it.lat, it.lng) })
                .clickable(true)
                .strokeWidth(4f)

            val isSelected = selectedIds.contains(sector.id)
            options.fillColor(if (isSelected) Color.argb(120, 0, 255, 170) else Color.argb(40, 128, 128, 128))
            options.strokeColor(if (isSelected) Color.parseColor("#00ffaa") else Color.GRAY)

            val poly = map.addPolygon(options)
            poly.tag = sector.id
            polygons[sector.id] = poly

            val center = getPolygonCenter(sector.points)
            val textIcon = createTextIcon(sector.name)
            val marker = map.addMarker(MarkerOptions().position(center).icon(textIcon).anchor(0.5f, 0.5f).flat(true))
            marker?.let { sectorMarkersList.add(it) }
        }
    }

    private fun clearOverlayPolygons() {
        polygons.values.forEach { it.remove() }
        polygons.clear()
        sectorMarkersList.forEach { it.remove() }
        sectorMarkersList.clear()
    }

    private fun updateList() {
        val query = etSectorSearch.text.toString().lowercase()
        val filtered = allSectors.filter { it.name.lowercase().contains(query) }
        sectorsListAdapter = SectorsListAdapter(filtered, selectedIds) { sectorId ->
            toggleSectorSelection(sectorId)
        }
        rvSectorsList.adapter = sectorsListAdapter
    }

    private fun initSectorSelectionOverlay() {
        mainScreenUiGroup = findViewById(R.id.main_screen_ui_group)
        btnSaveSelection = findViewById(R.id.btn_save_selection)
        sectorOverlay = findViewById(R.id.sector_selection_overlay)
        selectionTabs = findViewById(R.id.selection_tabs)
        etSectorSearch = findViewById(R.id.et_search_query)
        rvSectorsList = findViewById(R.id.rv_sectors_list)

        rvSectorsList.layoutManager = LinearLayoutManager(this)

        selectionTabs.addTab(selectionTabs.newTab().setText("Карта"))
        selectionTabs.addTab(selectionTabs.newTab().setText("Список"))

        selectionTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (tab.position == 0) {
                    sectorOverlay.setBackgroundColor(Color.TRANSPARENT)
                    etSectorSearch.visibility = View.GONE
                    rvSectorsList.visibility = View.GONE
                } else {
                    sectorOverlay.setBackgroundResource(R.color.driver_black_bg)
                    etSectorSearch.visibility = View.VISIBLE
                    rvSectorsList.visibility = View.VISIBLE
                    updateList()
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        etSectorSearch.addTextChangedListener { updateList() }

        findViewById<View>(R.id.btn_back_selection).setOnClickListener {
            isLeavingSectorSelection = true
            if (currentSelectionTarget == SelectionTarget.HOME) {
                overlayBackPressedCallback?.isEnabled = false
                sectorOverlay.visibility = View.GONE
                mainScreenUiGroup.visibility = View.VISIBLE
                if (::map.isInitialized) {
                    map.setPadding(0, 0, 0, defaultMapPaddingBottom)
                    if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = true
                }
                clearOverlayPolygons()
                val bottomSheet = SearchSettingsBottomSheet { updateSearchStatusUI() }
                bottomSheet.show(supportFragmentManager, "SearchSettings")
            } else {
                val intentBack = Intent(this, CreateFilterActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intentBack)
            }
        }

        overlayBackPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                findViewById<View>(R.id.btn_back_selection).performClick()
            }
        }
        onBackPressedDispatcher.addCallback(this, overlayBackPressedCallback!!)

        findViewById<View>(R.id.btn_save_selection).setOnClickListener {
            isLeavingSectorSelection = true
            val finalSelectedIds = selectedIds.toLongArray()

            if (currentSelectionTarget == SelectionTarget.HOME) {
                overlayBackPressedCallback?.isEnabled = false
                updateHomeSectors(finalSelectedIds.toList())
                sectorOverlay.visibility = View.GONE
                mainScreenUiGroup.visibility = View.VISIBLE
                if (::map.isInitialized) {
                    map.setPadding(0, 0, 0, defaultMapPaddingBottom)
                    if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = true
                }
                clearOverlayPolygons()
            } else {
                val isPickingFrom = currentSelectionTarget == SelectionTarget.FILTER_FROM
                val intentBack = Intent(this, CreateFilterActivity::class.java).apply {
                    putExtra("SECTOR_RESULT_IDS", finalSelectedIds)
                    putExtra("IS_PICKING_FROM", isPickingFrom)
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intentBack)
            }
        }
    }

    private fun handleSectorSelectionRequest(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("START_SECTOR_SELECTION", false)) {
            val isFrom = intent.getBooleanExtra("IS_FROM", true)
            currentSelectionTarget = if (isFrom) SelectionTarget.FILTER_FROM else SelectionTarget.FILTER_TO
            val initialArray = intent.getLongArrayExtra("CURRENT_IDS") ?: LongArray(0)

            selectedIds.clear()
            initialArray.forEach { selectedIds.add(it) }

            selectionTabs.getTabAt(0)?.select()
            sectorOverlay.setBackgroundColor(Color.TRANSPARENT)
            etSectorSearch.setText("")

            sectorOverlay.visibility = View.VISIBLE
            mainScreenUiGroup.visibility = View.GONE

            if (::map.isInitialized) {
                map.setPadding(0, 0, 0, systemBarsBottomInset)
                if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = false
                drawOverlaySectorsOnMap()
            }
            updateList()
            updateSectorSaveButtonState(selectedIds.size)
            overlayBackPressedCallback?.isEnabled = true
        }
    }

    /**
     * Отрисовывает точные векторные иконки нижней панели и карточки "Ланцюг замовлень"
     * один-в-один по референсному скриншоту без необходимости вручную добавлять внешние SVG.
     */
    private class BottomBarIconDrawable(
        private val type: Type,
        private val color: Int,
        private val sizePx: Int
    ) : Drawable() {

        enum class Type {
            CHAIN_LINK,
            HOME_MODE,
            ETHER_FILTER,
            ETHER_LIST,
            HISTORY_CLOCK,
            FILTER_AUTO,
            FILTER_CYCLE
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@BottomBarIconDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@BottomBarIconDrawable.color
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
            strokePaint.strokeWidth = 2.1f

            when (type) {
                Type.CHAIN_LINK -> {
                    // Два горизонтальных звена цепи с центральной перемычкой (как на скриншоте "Ланцюг замовлень")
                    val leftLink = Path().apply {
                        moveTo(10.2f, 8.2f)
                        lineTo(6.8f, 8.2f)
                        arcTo(RectF(2.5f, 8.2f, 10.1f, 15.8f), 270f, -180f, false)
                        lineTo(10.2f, 15.8f)
                    }
                    val rightLink = Path().apply {
                        moveTo(13.8f, 8.2f)
                        lineTo(17.2f, 8.2f)
                        arcTo(RectF(13.9f, 8.2f, 21.5f, 15.8f), 270f, 180f, false)
                        lineTo(13.8f, 15.8f)
                    }
                    canvas.drawPath(leftLink, strokePaint)
                    canvas.drawPath(rightLink, strokePaint)
                    canvas.drawLine(8.2f, 12.0f, 15.8f, 12.0f, strokePaint)
                }

                Type.HOME_MODE -> {
                    val roof = Path().apply {
                        moveTo(3.5f, 11.5f)
                        lineTo(12f, 4.2f)
                        lineTo(20.5f, 11.5f)
                    }
                    val walls = Path().apply {
                        moveTo(6.2f, 10.2f)
                        lineTo(6.2f, 19.5f)
                        lineTo(17.8f, 19.5f)
                        lineTo(17.8f, 10.2f)
                    }
                    canvas.drawPath(roof, strokePaint)
                    canvas.drawPath(walls, strokePaint)
                }

                Type.ETHER_FILTER -> {
                    // Пирамидка фильтра (3 горизонтальные линии разной ширины)
                    canvas.drawLine(3.2f, 6.5f, 20.8f, 6.5f, strokePaint)
                    canvas.drawLine(6.5f, 12.0f, 17.5f, 12.0f, strokePaint)
                    canvas.drawLine(9.8f, 17.5f, 14.2f, 17.5f, strokePaint)
                }

                Type.ETHER_LIST -> {
                    // 3 горизонтальные полосы слева и 3 точки справа (как на скриншоте рядом с "Ефір")
                    canvas.drawLine(3.0f, 6.5f, 15.2f, 6.5f, strokePaint)
                    canvas.drawLine(3.0f, 12.0f, 15.2f, 12.0f, strokePaint)
                    canvas.drawLine(3.0f, 17.5f, 15.2f, 17.5f, strokePaint)

                    canvas.drawCircle(19.8f, 6.5f, 1.35f, fillPaint)
                    canvas.drawCircle(19.8f, 12.0f, 1.35f, fillPaint)
                    canvas.drawCircle(19.8f, 17.5f, 1.35f, fillPaint)
                }

                Type.HISTORY_CLOCK -> {
                    // Круглая стрелка истории против часовой стрелки + стрелки часов внутри
                    val arcRect = RectF(3.5f, 3.5f, 20.5f, 20.5f)
                    canvas.drawArc(arcRect, -135f, 305f, false, strokePaint)

                    // Наконечник стрелки в верхней левой части
                    val arrowHead = Path().apply {
                        moveTo(3.5f, 4.2f)
                        lineTo(3.5f, 8.8f)
                        lineTo(8.1f, 8.8f)
                    }
                    canvas.drawPath(arrowHead, strokePaint)

                    // Стрелки часов в центре
                    val clockHands = Path().apply {
                        moveTo(12.0f, 7.8f)
                        lineTo(12.0f, 12.2f)
                        lineTo(15.2f, 14.2f)
                    }
                    canvas.drawPath(clockHands, strokePaint)
                }

                Type.FILTER_AUTO -> {
                    // Прицел (кольцо с 4 засечками по сторонам)
                    canvas.drawCircle(12f, 12f, 5.8f, strokePaint)
                    canvas.drawLine(12f, 2.6f, 12f, 6.0f, strokePaint)
                    canvas.drawLine(12f, 18.0f, 12f, 21.4f, strokePaint)
                    canvas.drawLine(2.6f, 12f, 6.0f, 12f, strokePaint)
                    canvas.drawLine(18.0f, 12f, 21.4f, 12f, strokePaint)
                }

                Type.FILTER_CYCLE -> {
                    // Две противоположные стрелки цикла
                    val topPath = Path().apply {
                        moveTo(4.8f, 10.5f)
                        quadTo(4.8f, 6.8f, 8.5f, 6.8f)
                        lineTo(18.8f, 6.8f)
                    }
                    canvas.drawPath(topPath, strokePaint)
                    canvas.drawLine(15.6f, 4.0f, 19.0f, 6.8f, strokePaint)
                    canvas.drawLine(19.0f, 6.8f, 15.6f, 9.6f, strokePaint)

                    val bottomPath = Path().apply {
                        moveTo(19.2f, 13.5f)
                        quadTo(19.2f, 17.2f, 15.5f, 17.2f)
                        lineTo(5.2f, 17.2f)
                    }
                    canvas.drawPath(bottomPath, strokePaint)
                    canvas.drawLine(8.4f, 14.4f, 5.0f, 17.2f, strokePaint)
                    canvas.drawLine(5.0f, 17.2f, 8.4f, 20.0f, strokePaint)
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

    private inner class SectorsListAdapter(
        private val sectors: List<Sector>,
        private val selected: Set<Long>,
        private val onClick: (Long) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SectorsListAdapter.SectorVH>() {

        inner class SectorVH(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_sector_name)
            val ivCheck: ImageView = view.findViewById(R.id.iv_selected_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectorVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sector_selectable, parent, false)
            return SectorVH(view)
        }

        override fun onBindViewHolder(holder: SectorVH, position: Int) {
            val sector = sectors[position]
            holder.tvName.text = sector.name

            val isSelected = selected.contains(sector.id)
            holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.tvName.setTextColor(if (isSelected) Color.parseColor("#00ffaa") else Color.WHITE)

            holder.itemView.setOnClickListener { onClick(sector.id) }
        }

        override fun getItemCount() = sectors.size
    }
}