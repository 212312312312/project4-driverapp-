package com.taxiapp.driver

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // ВАЖНО
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch // ВАЖНО

class OrderProgressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private lateinit var btnAction: MaterialButton
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDestinationLabel: TextView

    private enum class RideState { TO_CLIENT, WAITING, TO_DESTINATION }
    private var currentState = RideState.TO_CLIENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_progress)

        currentOrder = intent.getSerializableExtra("EXTRA_ORDER") as? Order
        if (currentOrder == null) {
            finish()
            return
        }

        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvDestinationLabel = findViewById(R.id.tv_destination_label)
        btnAction = findViewById(R.id.btn_action)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupInitialState()

        btnAction.setOnClickListener {
            handleActionButton()
        }
    }

    private fun setupInitialState() {
        currentState = RideState.TO_CLIENT
        tvStatusTitle.text = "Їду до клієнта"
        tvDestinationLabel.text = currentOrder?.fromAddress
        btnAction.text = "НА МІСЦІ"
    }

    private fun handleActionButton() {
        val orderId = currentOrder?.id ?: return

        lifecycleScope.launch {
            try {
                when (currentState) {
                    RideState.TO_CLIENT -> {
                        // 1. Водитель нажал "НА МЕСТЕ"
                        btnAction.isEnabled = false

                        // Вызов сервера
                        val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).notifyArrived(orderId)

                        if (response.isSuccessful) {
                            currentState = RideState.WAITING
                            tvStatusTitle.text = "Очікування клієнта"
                            tvDestinationLabel.text = "Клієнт скоро вийде..."
                            btnAction.text = "ПОЧАТИ ПОЇЗДКУ"
                        } else {
                            Toast.makeText(this@OrderProgressActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                        }
                    }

                    RideState.WAITING -> {
                        // 2. Водитель нажал "НАЧАТЬ ПОЕЗДКУ"
                        btnAction.isEnabled = false

                        val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).startTrip(orderId)

                        if (response.isSuccessful) {
                            currentState = RideState.TO_DESTINATION
                            tvStatusTitle.text = "В дорозі"
                            tvDestinationLabel.text = currentOrder?.toAddress
                            btnAction.text = "ЗАВЕРШИТИ"
                            btnAction.backgroundTintList = ContextCompat.getColorStateList(this@OrderProgressActivity, R.color.driver_error)
                            updateMapRouteToDestination()
                        } else {
                            Toast.makeText(this@OrderProgressActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                        }
                    }

                    RideState.TO_DESTINATION -> {
                        // 3. Водитель нажал "ЗАВЕРШИТЬ"
                        completeOrder()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderProgressActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                // Разблокируем кнопку, если она не завершает активити
                if (currentState != RideState.TO_DESTINATION || !btnAction.isEnabled) {
                    // Логика разблокировки зависит от успеха,
                    // но для простоты разблокируем всегда при ошибке или смене статуса
                    btnAction.isEnabled = true
                }
            }
        }
    }

    private fun completeOrder() {
        val orderId = currentOrder?.id ?: return

        // Внутри completeOrder нам не нужно запускать новую launch, так как мы уже внутри handleActionButton's launch
        // Но для чистоты вынесем логику:
        // (Примечание: здесь я убрал launch, потому что функция вызывается из handleActionButton, где уже есть launch.
        // Если вызывать отдельно - нужен launch. Тут работает контекст родителя)

        lifecycleScope.launch {
            try {
                btnAction.isEnabled = false
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).completeOrder(orderId)

                if (response.isSuccessful) {
                    Toast.makeText(this@OrderProgressActivity, "Замовлення завершено!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    btnAction.isEnabled = true
                    Toast.makeText(this@OrderProgressActivity, "Помилка завершення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnAction.isEnabled = true
                Toast.makeText(this@OrderProgressActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMapRouteToDestination() {
        // Упрощенно: можно добавить маркер Точки Б
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val kiev = LatLng(50.45, 30.52)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(kiev, 14f))

        map.addMarker(MarkerOptions()
            .position(kiev)
            .title("Точка А")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
    }
}