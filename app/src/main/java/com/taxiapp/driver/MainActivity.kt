package com.taxiapp.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var switchStatus: SwitchCompat
    private var isCameraLocked = false

    // --- НОВОЕ: Клиент для работы с геолокацией ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // --- НОВОЕ: Инициализация клиента локации ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupWindowInsets()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupTopBar()
        setupSideButtons() // Тут логика кнопок
        setupBottomNav()
    }

    private fun setupWindowInsets() {
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu)
        val switchStatus = findViewById<SwitchCompat>(R.id.switch_status)
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_nav_container)
        val leftControls = findViewById<LinearLayout>(R.id.left_controls_container)
        val rightControls = findViewById<LinearLayout>(R.id.right_controls_container)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_container)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            btnMenu.updateLayoutParams<MarginLayoutParams> { topMargin = bars.top + dpToPx(16) }
            switchStatus.updateLayoutParams<MarginLayoutParams> { topMargin = bars.top + dpToPx(16) }

            // Поднимаем боковые кнопки, чтобы они не перекрывались нижним островком (если экран маленький)
            // Или оставляем как есть, если ConstraintLayout справляется

            bottomNav.updateLayoutParams<MarginLayoutParams> { bottomMargin = bars.bottom + dpToPx(24) }

            insets
        }
    }

    private fun setupTopBar() {
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu)
        switchStatus = findViewById(R.id.switch_status)

        btnMenu.setOnClickListener {
            Toast.makeText(this, "Меню", Toast.LENGTH_SHORT).show()
        }

        switchStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchStatus.text = "ОНЛАЙН"
                Toast.makeText(this, "Ви вийшли на лінію", Toast.LENGTH_SHORT).show()
            } else {
                switchStatus.text = "ОФЛАЙН"
                Toast.makeText(this, "Ви офлайн", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSideButtons() {
        val btnLocation = findViewById<ImageButton>(R.id.btn_my_location)
        val btnLock = findViewById<ImageButton>(R.id.btn_lock_location)
        val btnSectors = findViewById<ImageButton>(R.id.btn_sectors)
        val btnHotspots = findViewById<ImageButton>(R.id.btn_hotspots)

        // --- НОВОЕ: Логика кнопки "Мое местоположение" ---
        btnLocation.setOnClickListener {
            getDeviceLocation()
        }

        btnLock.setOnClickListener {
            isCameraLocked = !isCameraLocked
            val color = if (isCameraLocked) R.color.driver_neon_teal else R.color.driver_text_primary
            btnLock.setColorFilter(getColor(color))
            Toast.makeText(this, if(isCameraLocked) "Камера закріплена" else "Вільна камера", Toast.LENGTH_SHORT).show()
        }

        btnSectors.setOnClickListener {
            Toast.makeText(this, "Сектори", Toast.LENGTH_SHORT).show()
        }

        btnHotspots.setOnClickListener {
            Toast.makeText(this, "Рибні місця", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNav() {
        val btnEther = findViewById<LinearLayout>(R.id.btn_nav_ether)
        val btnOrders = findViewById<LinearLayout>(R.id.btn_nav_orders)
        val btnFilters = findViewById<LinearLayout>(R.id.btn_nav_filters)

        btnEther.setOnClickListener {
            startActivity(Intent(this, EtherActivity::class.java))
        }
        btnOrders.setOnClickListener {
            Toast.makeText(this, "Історія замовлень", Toast.LENGTH_SHORT).show()
        }
        btnFilters.setOnClickListener {
            Toast.makeText(this, "Фільтри", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Отступы для логотипа Google (чтобы он был над нижней панелью)
        map.setPadding(0, dpToPx(100), 0, dpToPx(150))

        // --- НОВОЕ: Включаем слой местоположения ---
        enableMyLocation()
    }

    // --- НОВОЕ: Метод запроса прав и включения слоя на карте ---
    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false // Выключаем стандартную кнопку Google, у нас своя

            // Сразу пробуем получить позицию при старте
            getDeviceLocation()
        } else {
            // Запрашиваем права
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    // --- НОВОЕ: Получение координат и анимация камеры ---
    private fun getDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful && task.result != null) {
                        val lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            val latLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f)) // 16f - удобный зум для города
                        }
                    } else {
                        Toast.makeText(this, "Не вдалося визначити місцезнаходження", Toast.LENGTH_SHORT).show()
                        // Если не нашли, можно кинуть камеру на Киев по дефолту
                        val kiev = LatLng(50.45, 30.52)
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(kiev, 12f))
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // --- НОВОЕ: Обработка ответа пользователя на запрос прав ---
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Потрібен дозвіл на геолокацію для роботи", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}