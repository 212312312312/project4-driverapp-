package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OrderProgressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null
    private lateinit var btnAction: MaterialButton
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDestinationLabel: TextView

    // Текущий статус на клиенте (локально)
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
        // Этап 1: Едем к точке А
        currentState = RideState.TO_CLIENT
        tvStatusTitle.text = "Їду до клієнта"
        tvDestinationLabel.text = currentOrder?.fromAddress
        btnAction.text = "НА МІСЦІ"
    }

    private fun handleActionButton() {
        when (currentState) {
            RideState.TO_CLIENT -> {
                // Водитель нажал "На месте" -> переходим в режим ожидания
                // Тут должен быть API вызов: notifyArrived()
                currentState = RideState.WAITING
                tvStatusTitle.text = "Очікування клієнта"
                tvDestinationLabel.text = "Клієнт виходить..."
                btnAction.text = "ПОЧАТИ ПОЇЗДКУ"
                // Можно запустить таймер тут
            }
            RideState.WAITING -> {
                // Водитель нажал "Начать" -> едем в точку Б
                // API вызов: startTrip()
                currentState = RideState.TO_DESTINATION
                tvStatusTitle.text = "В дорозі"
                tvDestinationLabel.text = currentOrder?.toAddress
                btnAction.text = "ЗАВЕРШИТИ"
                btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_error) // Красная кнопка

                updateMapRouteToDestination()
            }
            RideState.TO_DESTINATION -> {
                // Водитель нажал "Завершить"
                completeOrder()
            }
        }
    }

    private fun updateMapRouteToDestination() {
        // Тут нужно будет перерисовать карту: показать маршрут от текущего места до Точки Б
        // Пока просто поставим маркер
        val order = currentOrder ?: return
        // Упрощенно: можно добавить маркер Точки Б
    }

    private fun completeOrder() {
        val orderId = currentOrder?.id ?: return
        btnAction.isEnabled = false

        ApiClient.getInstance().getApiService(this).completeOrder(orderId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@OrderProgressActivity, "Замовлення завершено!", Toast.LENGTH_SHORT).show()
                    finish() // Возвращаемся в меню или эфир
                } else {
                    btnAction.isEnabled = true
                    Toast.makeText(this@OrderProgressActivity, "Помилка", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                btnAction.isEnabled = true
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Блокировать карту не будем, водителю нужно видеть куда ехать

        // Рисуем точку, куда ехать СЕЙЧАС (Точка А)
        val order = currentOrder ?: return

        // Пока просто центруем карту на Киеве или координатах заказа (если есть)
        // В реальном приложении тут нужно декодировать order.originLat/Lng
        val kiev = LatLng(50.45, 30.52)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(kiev, 14f))

        map.addMarker(MarkerOptions()
            .position(kiev) // Замените на координаты Точки А
            .title("Точка А")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
    }
}