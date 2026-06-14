package com.taxiapp.driver

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.SessionManager

class EtherSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var previewAdapter: OrderAdapter
    private lateinit var switchSectorFirst: SwitchMaterial
    private lateinit var switchHidePrice: SwitchMaterial

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ether_settings)
        sessionManager = SessionManager(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        switchSectorFirst = findViewById(R.id.switch_sector_first)
        switchHidePrice = findViewById(R.id.switch_hide_price)

        // Завантажуємо поточні налаштування
        switchSectorFirst.isChecked = sessionManager.isEtherSectorFirst()
        switchHidePrice.isChecked = sessionManager.isEtherPricePerKmHidden()

        setupPreview()

        // Слухачі змін
        switchSectorFirst.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setEtherSectorFirst(isChecked)
            updatePreview()
        }

        switchHidePrice.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setEtherHidePricePerKm(isChecked)
            updatePreview()
        }
    }

    private fun setupPreview() {
        val rvPreview = findViewById<RecyclerView>(R.id.rv_preview)
        rvPreview.layoutManager = LinearLayoutManager(this)

        // Тільки один аргумент у конструкторі адаптера
        previewAdapter = OrderAdapter { }
        rvPreview.adapter = previewAdapter

        // Створюємо фейкове замовлення для прикладу
        val demoOrder = Order(
            id = "9999",     // ІСПРАВЛЕНО: Тепер передаємо String (UUID)
            idLong = 9999L,  // ІСПРАВЛЕНО: Передаємо обов'язковий числовий idLong
            price = 245.0,

            // Вказуємо метри (5200 м = 5.2 км)
            distanceMeters = 5200,

            fromAddress = "вул. Хрещатик, 1",
            fromSector = "Центр",

            originLat = 50.4501,
            originLng = 30.5234,

            toAddress = "аеропорт Бориспіль",
            toSector = "Аеропорт",
            destLat = 50.3450,
            destLng = 30.8900,

            tariffName = "Standard",

            client = com.taxiapp.driver.network.OrderClient(
                id = 101,
                fullName = "Олександр",
                rating = 4.9,
                completedRides = 42
            ),

            status = "OFFERING",
            paymentMethod = "CASH"
        )

        previewAdapter.submitList(listOf(demoOrder))
        updatePreview()
    }

    private fun updatePreview() {
        // Оновлюємо налаштування в адаптері, щоб він перемалював картку
        previewAdapter.updateDisplaySettings(
            sectorFirst = switchSectorFirst.isChecked,
            hidePricePerKm = switchHidePrice.isChecked
        )
    }
}