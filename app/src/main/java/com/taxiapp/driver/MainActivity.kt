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
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
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
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch
import com.taxiapp.driver.service.FloatingWidgetService

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var tvSearchModeTitle: TextView
    private lateinit var tvSearchModeSubtitle: TextView
    private lateinit var btnToggleSearchMode: LinearLayout

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var switchOnline: SwitchMaterial
    private lateinit var map: GoogleMap
    private lateinit var btnLockLocation: ImageButton
    private lateinit var btnHotspots: ImageButton

    private lateinit var btnNavOrders: LinearLayout
    private lateinit var orderBadgeDot: View

    private lateinit var navViewContent: View

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
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
        checkActiveOrderOnStart()
        updateFcmToken()
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
        updateLockIconState()
        updateOrdersBadge()
        checkActiveOrderOnStart() 
        startUILocationUpdates()


        if (::map.isInitialized) {
            updateMapUI()
            updateSearchStatusUI() 
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
        
        searchRadiusCircle?.isVisible = true
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
        val displayMetrics = resources.displayMetrics
        val drawerWidth = (displayMetrics.widthPixels * 0.8).toInt()
        navViewContent.layoutParams.width = drawerWidth

        tvSearchModeTitle = findViewById(R.id.tvSearchModeTitle)
        tvSearchModeSubtitle = findViewById(R.id.tvSearchModeSubtitle)
        btnToggleSearchMode = findViewById(R.id.btnToggleSearchMode)

        // --- ВИПРАВЛЕНО ТУТ ---
        // Ставимо правильний текст за замовчуванням (Ланцюг + Неактивно)
        tvSearchModeTitle.text = "⚡ Ланцюг замовлень"
        tvSearchModeSubtitle.text = "Натисніть для активації"
        
        // Примусово оновлюємо візуал, щоб він відповідав статусу isSearchActive = false
        updateSearchBlockVisuals(false)
        // -----------------------

        btnToggleSearchMode.setOnClickListener { toggleSearchActivation() }

        findViewById<View>(R.id.btnSearchSettings).setOnClickListener {
            val bottomSheet = SearchSettingsBottomSheet { updateSearchStatusUI() }
            bottomSheet.show(supportFragmentManager, "SearchSettings")
        }

        switchOnline = findViewById(R.id.switch_online)
        switchOnline.setOnClickListener { updateDriverStatus(switchOnline.isChecked) }

        findViewById<View>(R.id.btn_nav_ether).setOnClickListener { startActivity(Intent(this, EtherActivity::class.java)) }
        btnNavOrders = findViewById(R.id.btn_nav_orders)
        orderBadgeDot = findViewById(R.id.order_badge_dot)
        btnNavOrders.setOnClickListener { startActivity(Intent(this, OrdersActivity::class.java)) }
        findViewById<View>(R.id.btn_nav_filters).setOnClickListener { startActivity(Intent(this, FiltersActivity::class.java)) }

        btnLockLocation = findViewById(R.id.btn_lock_location)
        btnLockLocation.setOnClickListener { handleLockLocationClick() }

        findViewById<View>(R.id.btn_my_location).setOnClickListener { centerMapOnUser() }
        findViewById<View>(R.id.btn_sectors).setOnClickListener { toggleSectors() }

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
        navViewContent.findViewById<View>(R.id.menu_item_activity).setOnClickListener { startActivity(Intent(this, DriverScoreActivity::class.java)); drawerLayout.closeDrawer(GravityCompat.START) }
        navViewContent.findViewById<View>(R.id.menu_item_stats)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navViewContent.findViewById<View>(R.id.menu_item_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navViewContent.findViewById<View>(R.id.btn_logout).setOnClickListener {
            switchOnline.isChecked = false
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
        if (isActive) {
            btnToggleSearchMode.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSearchModeTitle.setTextColor(Color.BLACK)
            tvSearchModeSubtitle.setTextColor(Color.DKGRAY)
            tvSearchModeSubtitle.text = "Пошук замовлень..."
        } else {
            btnToggleSearchMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CC1E1E1E"))
            tvSearchModeTitle.setTextColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
            tvSearchModeSubtitle.setTextColor(ContextCompat.getColor(this, R.color.driver_text_secondary))
            // Тут ми можемо залишити те, що вже є, або оновити, але setupUI тепер гарантує початковий стан.
            tvSearchModeSubtitle.text = "Натисніть для активації"
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
                            tvSearchModeTitle.text = "⚡ Ланцюг замовлень"
                            searchRadiusCircle?.isVisible = true
                            isSearchActive = false
                            sessionManager.saveSearchMode(DriverSearchMode.CHAIN)
                        }
                        DriverSearchMode.CHAIN -> {
                            tvSearchModeTitle.text = "⚡ Ланцюг замовлень"
                            searchRadiusCircle?.isVisible = true
                            sessionManager.saveSearchMode(DriverSearchMode.CHAIN)
                        }
                        DriverSearchMode.HOME -> {
                            val sectorsText = if (state.homeSectorNames.isNullOrEmpty()) "?" else state.homeSectorNames
                            tvSearchModeTitle.text = "🏠 Додому ($sectorsText)"
                            searchRadiusCircle?.isVisible = true
                            sessionManager.saveSearchMode(DriverSearchMode.HOME)
                        }
                    }

                    // Оновлення тексту в залежності від того, чи ми натиснули "Старт" (isSearchActive)
                    if (!isSearchActive && state.mode == DriverSearchMode.MANUAL) {
                        tvSearchModeSubtitle.text = "Радіус: ${state.radius} км • Натисніть для старту"
                        tvSearchModeTitle.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.driver_neon_teal))
                    } else if (!isSearchActive) {
                        // Якщо просто оновлюємо дані з сервера, але пошук вимкнено локально
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
        val imgAvatar = navViewContent.findViewById<android.widget.ImageView>(R.id.img_avatar)
        val tvPlateNumber = navViewContent.findViewById<TextView>(R.id.tv_menu_plate_number)
        val savedName = sessionManager.getDriverName()
        if (savedName != null) tvDriverName.text = extractFirstName(savedName)
        else {
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
                    if (!profile.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@MainActivity).load(profile.photoUrl).circleCrop().into(imgAvatar)
                    }
                    if (profile.car != null && !profile.car.plateNumber.isNullOrEmpty()) {
                        tvPlateNumber.text = profile.car.plateNumber; tvPlateNumber.visibility = View.VISIBLE
                    } else tvPlateNumber.visibility = View.GONE
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnUser() {
        if (!::map.isInitialized) return
        if (isHeatmapVisible || isSectorsVisible) return

        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (e: Exception) {}
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
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)) } catch (e: Exception) {}
        updateMapUI()
        centerMapOnUser()
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
        if (isSectorsVisible) { clearSectorsFromMap(); isSectorsVisible = false }
        if (isHeatmapVisible) { clearHeatmapFromMap(); isHeatmapVisible = false; btnHotspots.backgroundTintList = null; Toast.makeText(this, "Рибні місця приховано", Toast.LENGTH_SHORT).show() }
        else { loadAndDrawHeatmap(); isHeatmapVisible = true; btnHotspots.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal)); btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg)) }
    }

    private fun loadAndDrawHeatmap() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getHeatmap()
                if (response.isSuccessful && response.body() != null) {
                    val zones = response.body()!!
                    if (zones.isNotEmpty()) { drawHeatmapGlow(zones); Toast.makeText(this@MainActivity, "Знайдено ${zones.size} активних зон", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(this@MainActivity, "Зараз немає скупчень замовлень", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@MainActivity, "Не вдалося отримати дані Heatmap", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@MainActivity, "Помилка завантаження Heatmap", Toast.LENGTH_SHORT).show() }
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
        if (isHeatmapVisible) { clearHeatmapFromMap(); isHeatmapVisible = false; btnHotspots.backgroundTintList = null; btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal)) }
        if (isSectorsVisible) { clearSectorsFromMap(); isSectorsVisible = false; Toast.makeText(this, "Сектори приховано", Toast.LENGTH_SHORT).show() }
        else { loadAndDrawSectors(); isSectorsVisible = true }
    }

    private fun loadAndDrawSectors() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getSectors()
                if (response.isSuccessful && response.body() != null) drawSectorsOnMap(response.body()!!)
                else { Toast.makeText(this@MainActivity, "Помилка завантаження секторів", Toast.LENGTH_SHORT).show(); isSectorsVisible = false }
            } catch (e: Exception) { isSectorsVisible = false }
        }
    }

    private fun drawSectorsOnMap(sectors: List<com.taxiapp.driver.network.Sector>) {
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
    private fun getPolygonCenter(points: List<com.taxiapp.driver.network.SectorPointDto>): LatLng { val builder = LatLngBounds.Builder(); for (p in points) builder.include(LatLng(p.lat, p.lng)); return builder.build().center }

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
        if (sessionManager.isManualLocationActive()) {
            btnLockLocation.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))
        } else {
            btnLockLocation.backgroundTintList = null
            btnLockLocation.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_text_primary))
        }
    }

    private fun handleLockLocationClick() {
        if (sessionManager.isManualLocationActive()) showDisableManualLocationDialog()
        else startActivity(Intent(this, LocationPickerActivity::class.java))
    }

    private fun showDisableManualLocationDialog() {
        AlertDialog.Builder(this).setTitle("Геолокація").setMessage("Вимкнути ручне закріплення?").setPositiveButton("Змінити") { _, _ -> startActivity(Intent(this, LocationPickerActivity::class.java)) }
            .setNegativeButton("Вимкнути") { _, _ -> sessionManager.clearManualLocation(); updateLockIconState(); updateMapUI(); centerMapOnUser() }.setNeutralButton("Скасувати", null).show()
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
        switchOnline.isEnabled = false
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc -> sendStatusRequest(isOnline, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0) }
            .addOnFailureListener { sendStatusRequest(isOnline, 0.0, 0.0) }
    }

    private fun sendStatusRequest(isOnline: Boolean, lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity).updateStatus(UpdateDriverStatusRequest(isOnline, lat, lng))
                if (response.isSuccessful) switchOnline.text = if (isOnline) "ОНЛАЙН" else "ОФЛАЙН"
                else switchOnline.isChecked = !isOnline
            } catch (e: Exception) { switchOnline.isChecked = !isOnline } finally { switchOnline.isEnabled = true }
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
}