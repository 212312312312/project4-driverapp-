package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.network.UpdateLocationRequest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.messaging.FirebaseMessaging
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.HeatmapZoneDto
import com.taxiapp.driver.network.UpdateDriverStatusRequest
import com.taxiapp.driver.network.DriverSearchMode
import com.taxiapp.driver.network.DriverSearchSettingsDto
import com.taxiapp.driver.network.FcmTokenDto
import com.taxiapp.driver.network.Sector
import com.taxiapp.driver.network.SectorPointDto
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch
import com.taxiapp.driver.service.FloatingWidgetService
import com.taxiapp.driver.ServiceMessagesActivity

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // --- Настройка целей выбора секторов ---
    enum class SelectionTarget { FILTER_FROM, FILTER_TO, HOME }
    private var currentSelectionTarget = SelectionTarget.FILTER_FROM

    private var allSectors = listOf<Sector>()
    private val selectedIds = mutableSetOf<Long>()
    private val polygons = mutableMapOf<Long, Polygon>()
    private val sectorMarkersList = mutableListOf<Marker>()
    private var sectorsListAdapter: SectorsListAdapter? = null
    // ----------------------------------------------

    private lateinit var tvSearchModeTitle: TextView
    private lateinit var tvSearchModeSubtitle: TextView
    private lateinit var btnToggleSearchMode: LinearLayout

    private var isLeavingSectorSelection = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var btnStatusToggle: View
    private lateinit var switchThumbCard: com.google.android.material.card.MaterialCardView
    private lateinit var tvSwitchStatusText: TextView
    private var isDriverOnline = false
    private var searchBorderAnimator: android.animation.ObjectAnimator? = null

    private var defaultMapPaddingBottom = 0
    private lateinit var mainScreenUiGroup: androidx.constraintlayout.widget.Group
    private lateinit var btnSaveSelection: android.widget.ImageView
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
    private lateinit var locationCallback: LocationCallback

    private var overlayBackPressedCallback: androidx.activity.OnBackPressedCallback? = null
    private var originalContainerElevation: Float = -1f
    private var searchRadiusCircle: Circle? = null
    private var currentSearchRadiusKm: Double = 3.0
    private var currentDriverLocation: LatLng? = null

    private var isSectorsVisible = false
    private val sectorPolygons = mutableListOf<Polygon>()
    private val sectorMarkers = mutableListOf<Marker>()

    private var isHeatmapVisible = false
    private val heatmapOverlays = mutableListOf<GroundOverlay>()

    private var manualLocationMarker: Marker? = null

    private var isSearchActive = false
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
            startUILocationUpdates()
            if (::map.isInitialized) centerMapOnUser()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації!", Toast.LENGTH_LONG).show()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (sessionManager.fetchAuthToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        loadUserProfile()
        setupLocationCallback()

        checkPermissionsAndStart()
        initSectorSelectionOverlay()
        handleSectorSelectionRequest(intent)
        loadSectorsDataSilently()
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result
            sessionManager.saveFcmToken(token)
            lifecycleScope.launch {
                try {
                    ApiClient.getInstance().getApiService(this@MainActivity).updateFcmToken(FcmTokenDto(token))
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
        updateLockIconState()
        updateOrdersBadge()
        checkActiveOrderOnStart()
        startUILocationUpdates()
        updateCommissionInfo()

        if (::map.isInitialized) {
            updateMapUI()
            updateSearchStatusUI()
        }
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

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                if (!sessionManager.isManualLocationActive()) {
                    currentDriverLocation = LatLng(location.latitude, location.longitude)
                    drawSearchRadius()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUILocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
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
                val currentResponse = ApiClient.getInstance().getApiService(this@MainActivity).getSearchSettings()
                val currentRadius = currentResponse.body()?.radius ?: 3.0
                val req = DriverSearchSettingsDto(DriverSearchMode.HOME, currentRadius, sectorIds)
                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateSearchSettings(req)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Сектори 'Додому' збережено!", Toast.LENGTH_SHORT).show()
                    updateSearchStatusUI()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navViewContent = findViewById(R.id.nav_view_content)

        tvSearchModeTitle = findViewById(R.id.tvSearchModeTitle)
        tvSearchModeSubtitle = findViewById(R.id.tvSearchModeSubtitle)
        btnToggleSearchMode = findViewById(R.id.btnToggleSearchMode)

        tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
        tvSearchModeSubtitle.text = "Натисніть для активації"

        updateSearchBlockVisuals(false)

        btnToggleSearchMode.setOnClickListener { toggleSearchActivation() }

        findViewById<View>(R.id.btnSearchSettings).setOnClickListener {
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

        findViewById<View>(R.id.btn_nav_ether).setOnClickListener { startActivity(Intent(this, EtherActivity::class.java)) }
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
            lifecycleScope.launch {
                try {
                    ApiClient.getInstance().getApiService(this@MainActivity).updateStatus(UpdateDriverStatusRequest(false, 0.0, 0.0))
                } catch (e: Exception) {
                } finally {
                    val intent = Intent(this@MainActivity, AccountSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
        updateSearchStatusUI()
    }

    private fun setOnlineVisualState(online: Boolean, animate: Boolean) {
        this.isDriverOnline = online
        val trackContainer = findViewById<android.view.ViewGroup>(R.id.switch_track_container)
        if (animate) {
            android.transition.TransitionManager.beginDelayedTransition(trackContainer)
        }
        val params = switchThumbCard.layoutParams as android.widget.FrameLayout.LayoutParams
        if (online) {
            params.gravity = android.view.Gravity.END
            switchThumbCard.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSwitchStatusText.text = "Онлайн"
            tvSwitchStatusText.setTextColor(android.graphics.Color.BLACK)
        } else {
            params.gravity = android.view.Gravity.START
            switchThumbCard.setCardBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
            tvSwitchStatusText.text = "Офлайн"
            tvSwitchStatusText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_text_primary))
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
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
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
        isSearchActive = !isSearchActive
        updateSearchBlockVisuals(isSearchActive)
        if (isSearchActive) Toast.makeText(this, "Пошук розпочато...", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "Пошук зупинено.", Toast.LENGTH_SHORT).show()
    }

    private fun updateSearchBlockVisuals(isActive: Boolean) {
        val cardSearchContainer = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSearchContainer)
        val cardSearchMode = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSearchMode)
        val borderAnimView = findViewById<View>(R.id.search_border_animator)

        if (originalContainerElevation == -1f) {
            originalContainerElevation = cardSearchContainer.cardElevation
        }

        if (isActive) {
            cardSearchContainer.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardSearchContainer.cardElevation = 0f
            cardSearchContainer.clipToOutline = true
            cardSearchMode.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSearchModeTitle.setTextColor(android.graphics.Color.WHITE)
            tvSearchModeSubtitle.setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            tvSearchModeSubtitle.text = "Пошук замовлень..."
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
            cardSearchContainer.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_black_bg))
            cardSearchContainer.cardElevation = originalContainerElevation
            cardSearchMode.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_black_bg))
            tvSearchModeTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSearchModeSubtitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.driver_text_secondary))
            tvSearchModeSubtitle.text = "Натисніть для активації"
            searchBorderAnimator?.cancel()
            borderAnimView.visibility = View.GONE
        }
    }

    private fun updateSearchStatusUI() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getSearchSettings()
                if (response.isSuccessful && response.body() != null) {
                    val state = response.body()!!
                    currentSearchRadiusKm = state.radius
                    drawSearchRadius()
                    val sessionManager = SessionManager(this@MainActivity)

                    when (state.mode) {
                        DriverSearchMode.MANUAL -> {
                            tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                            if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = !sectorOverlay.isShown
                            isSearchActive = false
                            sessionManager.saveSearchMode(DriverSearchMode.CHAIN)
                        }
                        DriverSearchMode.CHAIN -> {
                            tvSearchModeTitle.text = getString(R.string.main_search_chain_title)
                            if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = !sectorOverlay.isShown
                            sessionManager.saveSearchMode(DriverSearchMode.CHAIN)
                        }
                        DriverSearchMode.HOME -> {
                            val sectorsText = if (state.homeSectorNames.isNullOrEmpty()) "?" else state.homeSectorNames
                            tvSearchModeTitle.text = "Додому ($sectorsText)"
                            if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = !sectorOverlay.isShown
                            sessionManager.saveSearchMode(DriverSearchMode.HOME)
                        }
                    }

                    if (!isSearchActive && state.mode == DriverSearchMode.MANUAL) {
                        tvSearchModeSubtitle.text = "Радіус: ${state.radius} км • Натисніть для старту"
                        tvSearchModeTitle.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.driver_neon_teal))
                    } else if (!isSearchActive) {
                        tvSearchModeSubtitle.text = "Натисніть для активації"
                    } else {
                        tvSearchModeSubtitle.text = "Пошук замовлень..."
                    }

                    if (isSearchActive) updateSearchBlockVisuals(true)
                    else updateSearchBlockVisuals(false)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadUserProfile() {
        val tvDriverName = navViewContent.findViewById<TextView>(R.id.tv_driver_name)
        val tvRating = navViewContent.findViewById<TextView>(R.id.tv_menu_rating)
        val imgAvatar = navViewContent.findViewById<android.widget.ImageView>(R.id.img_avatar)
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
                    val serverName = profile.fullName ?: "Водій"
                    sessionManager.saveDriverName(serverName)
                    tvDriverName.text = extractFirstName(serverName)
                    if (tvRating != null) {
                        tvRating.text = String.format("%.1f", profile.rating)
                    }
                    if (!profile.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@MainActivity)
                            .load(profile.photoUrl)
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
            try { map.isMyLocationEnabled = false } catch (e: Exception) {}
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
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            }
        } else {
            manualLocationMarker?.remove()
            manualLocationMarker = null
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        currentDriverLocation = LatLng(it.latitude, it.longitude)
                        drawSearchRadius()
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f))
                    }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try {
            val isNightMode = when (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    // Если в приложении переключатель стоит на "Системная тема" — смотрим на настройки Android
                    val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

            val styleRes = if (isNightMode) {
                R.raw.map_style_dark
            } else {
                R.raw.map_style_standard
            }

            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, styleRes)
            )
            if (!success) {
                android.util.Log.e("UNIT_MAP", "Не удалось распарсить JSON стиля карты.")
            }
        } catch (e: android.content.res.Resources.NotFoundException) {
            android.util.Log.e("UNIT_MAP", "Файл стиля карты не найден в папке res/raw", e)
        }

        val density = resources.displayMetrics.density
        defaultMapPaddingBottom = (180 * density).toInt()
        map.setPadding(0, 0, 0, defaultMapPaddingBottom)

        map.setOnPolygonClickListener { polygon ->
            if (sectorOverlay.isShown) {
                val id = polygon.tag as? Long ?: return@setOnPolygonClickListener
                toggleSectorSelection(id)
            }
        }

        updateMapUI()
        centerMapOnUser()
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
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint()
        val radius = size / 2f
        val gradient = android.graphics.RadialGradient(radius, radius, radius, intArrayOf(color, color, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP)
        paint.shader = gradient; paint.isAntiAlias = true
        canvas.drawCircle(radius, radius, radius, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun toggleHeatmap() {
        // ✅ ИСПРАВЛЕНО: Используем новую чистую функцию сброса секторов
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
                        Toast.makeText(this@MainActivity, "Знайдено ${zones.size} active зон", Toast.LENGTH_SHORT).show()
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
            // ✅ ИСПРАВЛЕНО: Теперь при выключении кнопка корректно сбрасывает цвет в дефолтный
            resetSectorsButton()
            Toast.makeText(this, "Сектори приховано", Toast.LENGTH_SHORT).show()
        } else {
            isSectorsVisible = true
            // ✅ ИСПРАВЛЕНО: При активации мгновенно красим иконку пазла в бирюзовый (неоновый) цвет
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
                    // ✅ ИСПРАВЛЕНО: Если сервер ответил ошибкой — тушим бирюзовый цвет кнопки обратно
                    resetSectorsButton()
                }
            } catch (e: Exception) {
                // ✅ ИСПРАВЛЕНО: В случае падения сети также откатываем состояние кнопки
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
        val textView = TextView(this); textView.text = text; textView.setTextColor(Color.parseColor("#00ffaa")); textView.textSize = 14f; textView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textView.setShadowLayer(3f, 1f, 1f, Color.BLACK)
        textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED); textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)
        val bitmap = Bitmap.createBitmap(textView.measuredWidth, textView.measuredHeight, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); textView.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clearSectorsFromMap() { sectorPolygons.forEach { it.remove() }; sectorPolygons.clear(); sectorMarkers.forEach { it.remove() }; sectorMarkers.clear() }
    private fun getPolygonCenter(points: List<SectorPointDto>): LatLng { val builder = LatLngBounds.Builder(); for (p in points) builder.include(LatLng(p.lat, p.lng)); return builder.build().center }

    private fun updateMapUI() {
        if (!::map.isInitialized) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (e: Exception) {}
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                val latLng = LatLng(manualLoc.first, manualLoc.second)
                currentDriverLocation = latLng
                drawSearchRadius()
                if (manualLocationMarker == null) manualLocationMarker = map.addMarker(MarkerOptions().position(latLng).title("Фіксована позиція").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
                else manualLocationMarker?.position = latLng
            }
        } else {
            manualLocationMarker?.remove(); manualLocationMarker = null
            if (hasPermission) { map.isMyLocationEnabled = true; map.uiSettings.isMyLocationButtonEnabled = false }
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
    // Оновлений варіант (без іконки)
    private data class BottomSheetOptionDto(val text: String, val onClick: () -> Unit)
    private fun handleLockLocationClick() {
        if (sessionManager.isManualLocationActive()) {
            showDisableManualLocationDialog()

        } else {
            startActivity(Intent(this,  LockLocationActivity::class.java))
        }
    }

    private fun showDisableManualLocationDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_generic, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.tv_sheet_title).text = "Вимкнути ручне закріплення?"

        // Створюємо список опцій без прив'язки до drawable ресурсів іконок
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
        rvOptions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

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

                // КРИТИЧНО ВАЖЛИВО: повністю ховаємо іконку, щоб текст не мав зайвого відступу зліва
                h.icon.visibility = View.GONE

                h.container.setOnClickListener { item.onClick() }
                h.divider.visibility = if (position == options.size - 1) View.GONE else View.VISIBLE
            }

            override fun getItemCount() = options.size
        }

        bottomSheetDialog.show()
    }

    private fun updateOrdersBadge() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActiveOrder()
                orderBadgeDot.visibility = if (response.isSuccessful && response.body() != null) View.VISIBLE else View.GONE
            } catch (e: Exception) { orderBadgeDot.visibility = View.GONE }
        }
    }

    private fun checkActiveOrderOnStart() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getActiveOrder()
                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    if (order.status == "OFFERING") {
                        val intent = Intent(this@MainActivity, OrderOfferActivity::class.java); intent.putExtra("EXTRA_ORDER", order); intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP; startActivity(intent)
                    } else if (order.status == "ACCEPTED" || order.status == "DRIVER_ARRIVED" || order.status == "IN_PROGRESS") {
                        if (!sessionManager.isOrderMinimized()) {
                            val intent = Intent(this@MainActivity, OrderProgressActivity::class.java); intent.putExtra("EXTRA_ORDER", order); startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
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
                }
            } catch (e: Exception) {
                setOnlineVisualState(!isOnline, animate = true)
            } finally {
                btnStatusToggle.isEnabled = true
            }
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        val allGranted = permissionsToRequest.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            startLocationService()
            startUILocationUpdates()
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
                btnSaveSelection.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.driver_neon_teal)
                )
            } else {
                btnSaveSelection.isEnabled = false
                btnSaveSelection.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.driver_text_secondary)
                )
            }
        }
    }

    private fun showRestoreDialog() {
        if (restoreDialog?.isShowing == true) return
        restoreDialog = android.app.Dialog(this)
        restoreDialog?.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        restoreDialog?.setContentView(R.layout.dialog_restore_account)
        restoreDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        restoreDialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
                    Toast.makeText(this@MainActivity, "Акаунт успешно відновлено!", Toast.LENGTH_SHORT).show()
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

    fun startHomeSectorSelection(preSelectedIds: List<Long>?) {
        currentSelectionTarget = SelectionTarget.HOME
        selectedIds.clear()
        preSelectedIds?.forEach { selectedIds.add(it) }

        selectionTabs.getTabAt(0)?.select()
        sectorOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        etSectorSearch.setText("")

        sectorOverlay.visibility = View.VISIBLE
        mainScreenUiGroup.visibility = View.GONE

        if (::map.isInitialized) {
            map.setPadding(0, 0, 0, 0)
            if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = false
            drawOverlaySectorsOnMap()
        }
        updateList()
        updateSectorSaveButtonState(selectedIds.size)
        overlayBackPressedCallback?.isEnabled = true
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
        btnSaveSelection = findViewById<android.widget.ImageView>(R.id.btn_save_selection)
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
                    sectorOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            sectorOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            etSectorSearch.setText("")

            sectorOverlay.visibility = View.VISIBLE
            mainScreenUiGroup.visibility = View.GONE

            if (::map.isInitialized) {
                map.setPadding(0, 0, 0, 0)
                if (searchRadiusCircle != null) searchRadiusCircle?.isVisible = false
                drawOverlaySectorsOnMap()
            }
            updateList()
            updateSectorSaveButtonState(selectedIds.size)
            overlayBackPressedCallback?.isEnabled = true
        }
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