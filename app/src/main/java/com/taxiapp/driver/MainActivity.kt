package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.HeatmapZoneDto
import com.taxiapp.driver.network.UpdateDriverStatusRequest
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.HexagonUtils
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var switchOnline: SwitchMaterial
    private lateinit var map: GoogleMap
    private lateinit var btnLockLocation: ImageButton
    private lateinit var btnHotspots: ImageButton // Кнопка зірки (Heatmap)

    private lateinit var btnNavOrders: LinearLayout
    private lateinit var orderBadgeDot: View

    // --- СТАН СЕКТОРІВ (Sectors) ---
    private var isSectorsVisible = false
    private val sectorPolygons = mutableListOf<Polygon>()
    private val sectorMarkers = mutableListOf<Marker>()

    // --- СТАН HEATMAP (Рибні місця) ---
    private var isHeatmapVisible = false
    // Используем GroundOverlay для эффекта свечения (Glow)
    private val heatmapOverlays = mutableListOf<com.google.android.gms.maps.model.GroundOverlay>()

    private var manualLocationMarker: Marker? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            updateMapUI()
            startLocationService()
            if (::map.isInitialized) centerMapOnUser()
        } else {
            Toast.makeText(this, "Потрібен доступ до геолокації!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (sessionManager.fetchAuthToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        checkPermissionsAndStart()
        checkActiveOrderOnStart()
    }

    override fun onResume() {
        super.onResume()
        updateLockIconState()
        updateOrdersBadge()
        if (::map.isInitialized) {
            updateMapUI()
            centerMapOnUser()
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnUser() {
        if (!::map.isInitialized) return
        // Якщо карта рухається водієм або увімкнено Heatmap/Сектори - не центруємо примусово,
        // щоб не збивати перегляд
        if (isHeatmapVisible || isSectorsVisible) return

        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.first, it.second), 17f))
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                location?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)) }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // --- ЗАСТОСУВАННЯ СТИЛЮ ---
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
            )
            if (!success) {
                Log.e("MapsActivity", "Style parsing failed.")
            }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Can't find style. Error: ", e)
        }
        // ---------------------------

        updateMapUI()
        centerMapOnUser()
    }

    private fun generateGlowBitmap(color: Int): BitmapDescriptor {
        val size = 512 // Розмір текстури (чим більше, тим якісніше, але важче)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = android.graphics.Paint()
        // Радіальний градієнт: Центр -> Колір, Край -> Прозорий
        val radius = size / 2f
        val gradient = android.graphics.RadialGradient(
            radius, radius, radius,
            intArrayOf(color, color, Color.TRANSPARENT), // Центр густий, край прозорий
            floatArrayOf(0f, 0.4f, 1f), // 0-40% радіусу колір тримається, потім зникає
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        paint.isAntiAlias = true

        canvas.drawCircle(radius, radius, radius, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun setupUI() {
        // 1. Ініціалізація DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)

        // --- ЛОГІКА ШИРИНИ МЕНЮ (4/5 ЕКРАНУ) ---
        val navView = findViewById<View>(R.id.nav_view_content)
        val displayMetrics = resources.displayMetrics
        val drawerWidth = (displayMetrics.widthPixels * 0.8).toInt()
        navView.layoutParams.width = drawerWidth
        // ---------------------------------------

        // 2. ЕЛЕМЕНТИ ГОЛОВНОГО ЕКРАНУ
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

        findViewById<View>(R.id.btn_menu).setOnClickListener { drawerLayout.openDrawer(androidx.core.view.GravityCompat.START) }


        // 3. ЛОГІКА ВСЕРЕДИНІ БОКОВОГО МЕНЮ (ІМ'Я + АВАТАР)
        val tvDriverName = navView.findViewById<TextView>(R.id.tv_driver_name)
        val imgAvatar = navView.findViewById<android.widget.ImageView>(R.id.img_avatar)

        // А. Завантажуємо з пам'яті (швидко)
        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvDriverName.text = extractFirstName(savedName)
        } else {
            val token = sessionManager.fetchAuthToken()
            tvDriverName.text = if (token != null && token.length > 4) "ID ${token.takeLast(4)}" else "Водій"
        }

        val btnOpenProfile = navView.findViewById<View>(R.id.btn_open_profile)
        btnOpenProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            // Не закриваємо меню, або закриваємо - як зручніше
            // drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Б. Запит на сервер (Оновлення даних)
        lifecycleScope.launch {
            try {
                // ВАЖЛИВО: Перевір, що в ApiService стоїть @GET("/api/v1/driver/me")
                val response = ApiClient.getInstance().getApiService(this@MainActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val serverName = profile.fullName ?: "Водій"

                    // 1. Зберігаємо та оновлюємо ім'я
                    sessionManager.saveDriverName(serverName)
                    tvDriverName.text = extractFirstName(serverName)

                    // 2. Завантажуємо АВАТАРКУ (якщо є посилання)
                    if (!profile.photoUrl.isNullOrEmpty()) {
                        com.bumptech.glide.Glide.with(this@MainActivity)
                            .load(profile.photoUrl)
                            .placeholder(R.drawable.ic_driver_avatar_placeholder) // Поки вантажиться
                            .error(R.drawable.ic_driver_avatar_placeholder) // Якщо помилка
                            .circleCrop() // Робимо круглою
                            .into(imgAvatar)
                    }
                } else {
                    // ДІАГНОСТИКА: Якщо ти бачиш цей тост, значить сервер відповів помилкою
                    Log.e("Profile", "Error: ${response.code()}")
                    // Розкоментуй для тесту: Toast.makeText(this@MainActivity, "Помилка профілю: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Розкоментуй для тесту: Toast.makeText(this@MainActivity, "Немає зв'язку з сервером", Toast.LENGTH_SHORT).show()
            }
        }

        // В. Кнопки меню
        navView.findViewById<View>(R.id.btn_menu_dispatcher).setOnClickListener {
            Toast.makeText(this, "Зв'язок з диспетчером...", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        }
        navView.findViewById<View>(R.id.btn_menu_sos).setOnClickListener {
            Toast.makeText(this, "SOS сигнал відправлено!", Toast.LENGTH_LONG).show()
        }

        val menuItems = mapOf(
            R.id.menu_item_car to "Моє Авто",
            R.id.menu_item_balance to "Баланс",
            R.id.menu_item_activity to "Активність",
            R.id.menu_item_stats to "Статистика"
        )
        for ((id, title) in menuItems) {
            navView.findViewById<View>(id).setOnClickListener {
                Toast.makeText(this, "$title (В розробці)", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            }
        }

        // Г. Кнопка ВИХОДУ
        navView.findViewById<View>(R.id.btn_logout).setOnClickListener {
            switchOnline.isChecked = false
            lifecycleScope.launch {
                try {
                    ApiClient.getInstance().getApiService(this@MainActivity)
                        .updateStatus(UpdateDriverStatusRequest(false, 0.0, 0.0))
                } catch (e: Exception) { e.printStackTrace() }
                finally {
                    sessionManager.clearSession()
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    // --- ЛОГІКА HEATMAP (РИБНІ МІСЦЯ) ---

    private fun toggleHeatmap() {
        // Якщо увімкнені сектори, вимикаємо їх, щоб не накладалися
        if (isSectorsVisible) {
            clearSectorsFromMap()
            isSectorsVisible = false
        }

        if (isHeatmapVisible) {
            // Вимикаємо Heatmap
            clearHeatmapFromMap()
            isHeatmapVisible = false
            btnHotspots.backgroundTintList = null // Повертаємо стандартний колір кнопки
            Toast.makeText(this, "Рибні місця приховано", Toast.LENGTH_SHORT).show()
        } else {
            // Вмикаємо Heatmap
            loadAndDrawHeatmap()
            isHeatmapVisible = true
            // Підсвічуємо кнопку активним кольором (наприклад, teal)
            btnHotspots.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))
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
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Не вдалося отримати дані Heatmap", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Heatmap Error", e)
                Toast.makeText(this@MainActivity, "Помилка завантаження Heatmap", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Метод малювання "Сяйва" (Glow)
    private fun drawHeatmapGlow(zones: List<HeatmapZoneDto>) {
        clearHeatmapFromMap()

        for (zone in zones) {
            val center = LatLng(zone.centerLat, zone.centerLng)

            val baseColor = when (zone.level) {
                3 -> 0xCCFF3D00.toInt() // Гаряче
                2 -> 0xCCFFD600.toInt() // Середньо
                else -> 0xCC00BFA5.toInt() // Холодно
            }

            val imageDescriptor = generateGlowBitmap(baseColor)

            val diameter = 2500f // 1.25 км радіус

            val overlayOptions = GroundOverlayOptions()
                .position(center, diameter)
                .image(imageDescriptor)
                .transparency(0.3f)
                .zIndex(100f)

            val overlay = map.addGroundOverlay(overlayOptions)
            overlay?.tag = zone.count

            overlay?.let { heatmapOverlays.add(it) }
        }
    }

    private fun clearHeatmapFromMap() {
        heatmapOverlays.forEach { it.remove() }
        heatmapOverlays.clear()
    }

    // --- ЛОГІКА СЕКТОРІВ ---

    private fun toggleSectors() {
        if (isHeatmapVisible) {
            clearHeatmapFromMap()
            isHeatmapVisible = false
            btnHotspots.backgroundTintList = null
            btnHotspots.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
        }

        if (isSectorsVisible) {
            clearSectorsFromMap()
            isSectorsVisible = false
            Toast.makeText(this, "Сектори приховано", Toast.LENGTH_SHORT).show()
        } else {
            loadAndDrawSectors()
            isSectorsVisible = true
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
                    isSectorsVisible = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                isSectorsVisible = false
            }
        }
    }

    private fun drawSectorsOnMap(sectors: List<com.taxiapp.driver.network.Sector>) {
        clearSectorsFromMap()
        for (sector in sectors) {
            if (sector.points.isEmpty()) continue

            val polygonOptions = PolygonOptions()
                .addAll(sector.points.map { LatLng(it.lat, it.lng) })
                .fillColor(Color.argb(45, 0, 255, 170))
                .strokeColor(ContextCompat.getColor(this, R.color.driver_neon_teal))
                .strokeWidth(4f)

            sectorPolygons.add(map.addPolygon(polygonOptions))

            val center = getPolygonCenter(sector.points)
            val textIcon = createTextIcon(sector.name)

            val marker = map.addMarker(MarkerOptions()
                .position(center)
                .icon(textIcon)
                .anchor(0.5f, 0.5f)
                .flat(true))

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

    private fun clearSectorsFromMap() {
        sectorPolygons.forEach { it.remove() }
        sectorPolygons.clear()
        sectorMarkers.forEach { it.remove() }
        sectorMarkers.clear()
    }

    private fun getPolygonCenter(points: List<com.taxiapp.driver.network.SectorPointDto>): LatLng {
        val builder = LatLngBounds.Builder()
        for (p in points) builder.include(LatLng(p.lat, p.lng))
        return builder.build().center
    }

    // --- ОСТАЛЬНАЯ ЛОГИКА ---

    @SuppressLint("MissingPermission")
    private fun updateMapUI() {
        if (!::map.isInitialized) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (sessionManager.isManualLocationActive()) {
            try { map.isMyLocationEnabled = false } catch (e: Exception) {}
            val manualLoc = sessionManager.getManualLocation()
            if (manualLoc != null) {
                val latLng = LatLng(manualLoc.first, manualLoc.second)
                if (manualLocationMarker == null) {
                    manualLocationMarker = map.addMarker(MarkerOptions()
                        .position(latLng)
                        .title("Фіксована позиція")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
                } else {
                    manualLocationMarker?.position = latLng
                }
            }
        } else {
            manualLocationMarker?.remove()
            manualLocationMarker = null
            if (hasPermission) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
            }
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
        AlertDialog.Builder(this)
            .setTitle("Геолокація")
            .setMessage("Вимкнути ручне закріплення?")
            .setPositiveButton("Змінити") { _, _ -> startActivity(Intent(this, LocationPickerActivity::class.java)) }
            .setNegativeButton("Вимкнути") { _, _ ->
                sessionManager.clearManualLocation()
                updateLockIconState(); updateMapUI(); centerMapOnUser()
            }
            .setNeutralButton("Скасувати", null)
            .show()
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
                    if (!sessionManager.isOrderMinimized()) {
                        val activeOrder = response.body()!!
                        val intent = Intent(this@MainActivity, OrderProgressActivity::class.java)
                        intent.putExtra("EXTRA_ORDER", activeOrder)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateDriverStatus(isOnline: Boolean) {
        switchOnline.isEnabled = false
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            sendStatusRequest(isOnline, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        }.addOnFailureListener { sendStatusRequest(isOnline, 0.0, 0.0) }
    }

    private fun sendStatusRequest(isOnline: Boolean, lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@MainActivity)
                    .updateStatus(UpdateDriverStatusRequest(isOnline, lat, lng))
                if (response.isSuccessful) switchOnline.text = if (isOnline) "ОНЛАЙН" else "ОФЛАЙН"
                else switchOnline.isChecked = !isOnline
            } catch (e: Exception) { switchOnline.isChecked = !isOnline
            } finally { switchOnline.isEnabled = true }
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Допоміжна функція для отримання Імені (Залишаємо ТІЛЬКИ ЦЮ одну в кінці)
    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> parts[1] // Беремо друге слово (Ім'я), якщо є Прізвище Ім'я
            parts.isNotEmpty() -> parts[0]
            else -> "Водій"
        }
    }
}