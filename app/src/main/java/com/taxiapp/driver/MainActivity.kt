package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var switchStatus: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация карты
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupTopBar()
        setupSideButtons()
        setupBottomNav()
    }

    private fun setupTopBar() {
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu)
        switchStatus = findViewById(R.id.switch_status)

        // Меню (Бургер)
        btnMenu.setOnClickListener {
            // В будущем тут будет открытие DrawerLayout
            Toast.makeText(this, "Меню", Toast.LENGTH_SHORT).show()
        }

        // Переключатель Статуса
        switchStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchStatus.text = "ОНЛАЙН"
                // Тут запрос на сервер: updateStatus(ONLINE)
                Toast.makeText(this, "Ви вийшли на лінію", Toast.LENGTH_SHORT).show()
            } else {
                switchStatus.text = "ОФЛАЙН"
                // Тут запрос на сервер: updateStatus(OFFLINE)
                Toast.makeText(this, "Ви офлайн", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSideButtons() {
        val btnLocation = findViewById<ImageButton>(R.id.btn_my_location)
        val btnSectors = findViewById<ImageButton>(R.id.btn_sectors)
        val btnHotspots = findViewById<ImageButton>(R.id.btn_hotspots)

        btnLocation.setOnClickListener {
            // Центрирование карты (заглушка на Киев)
            if (::map.isInitialized) {
                val kiev = LatLng(50.45, 30.52)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(kiev, 15f))
            }
        }

        btnSectors.setOnClickListener {
            Toast.makeText(this, "Сектори (в розробці)", Toast.LENGTH_SHORT).show()
        }

        btnHotspots.setOnClickListener {
            Toast.makeText(this, "Рибні місця (в розробці)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNav() {
        val btnEther = findViewById<LinearLayout>(R.id.btn_nav_ether)
        val btnOrders = findViewById<LinearLayout>(R.id.btn_nav_orders)
        val btnFilters = findViewById<LinearLayout>(R.id.btn_nav_filters)

        // 1. ЕФИР -> Открывает EtherActivity (Список заказов)
        btnEther.setOnClickListener {
            startActivity(Intent(this, EtherActivity::class.java))
        }

        // 2. ЗАКАЗЫ (История) -> Заглушка
        btnOrders.setOnClickListener {
            Toast.makeText(this, "Історія замовлень", Toast.LENGTH_SHORT).show()
        }

        // 3. ФИЛЬТРЫ -> Заглушка
        btnFilters.setOnClickListener {
            Toast.makeText(this, "Налаштування фільтрів", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Стилизация карты (Темная тема)
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
            )
            if (!success) {
                // Лог ошибки
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Стартовая позиция (Киев)
        val kiev = LatLng(50.45, 30.52)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(kiev, 12f))

        // Включаем слой "Мое местоположение" (если есть права)
        // map.isMyLocationEnabled = true (нужна проверка прав, добавим позже)

        // Кастомный маркер водителя мы добавим позже через map.addMarker(),
        // который будет двигаться по GPS координатам
    }
}