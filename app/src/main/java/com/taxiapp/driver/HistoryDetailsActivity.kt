package com.taxiapp.driver

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.taxiapp.driver.network.Order

class HistoryDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var order: Order

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_details)

        @Suppress("DEPRECATION")
        order = intent.getParcelableExtra("EXTRA_ORDER") ?: run { finish(); return }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_detail) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.tv_price_detail).text = "${order.price.toInt()} ₴"
        // Если в Order есть дистанция, выводим. Иначе заглушка.
        findViewById<TextView>(R.id.tv_distance_detail).text = "--- км"

        findViewById<TextView>(R.id.tv_from_detail).text = order.fromAddress
        findViewById<TextView>(R.id.tv_to_detail).text = order.toAddress
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Додаємо ?: 0.0, щоб прибрати помилку Type mismatch
        val origin = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
        val dest = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)

        map.addMarker(MarkerOptions().position(origin).title("Подача"))
        map.addMarker(MarkerOptions().position(dest).title("Кінцева"))

        val builder = LatLngBounds.Builder()
        builder.include(origin)
        builder.include(dest)

        try {
            val bounds = builder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13f))
        }
    }
}