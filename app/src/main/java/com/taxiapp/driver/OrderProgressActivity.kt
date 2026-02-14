package com.taxiapp.driver

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CancellationReason
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.network.RateClientRequest
import com.taxiapp.driver.service.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Date

class OrderProgressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var currentOrder: Order? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var driverMarker: Marker? = null

    private lateinit var btnAction: MaterialButton
    private lateinit var btnOptions: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDestinationLabel: TextView
    private lateinit var tvClientName: TextView
    private lateinit var tvOrderInfo: TextView

    // Додав стан SCHEDULED
    private enum class RideState { SCHEDULED, TO_CLIENT, WAITING, TO_DESTINATION, COMPLETED }
    private var currentState = RideState.TO_CLIENT

    private var cancellationReasons: List<CancellationReason> = emptyList()

    private var locationService: LocationService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            locationService?.setTargetOrder(currentOrder)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
        }
    }

    // Хендлер для таймера (перевірка часу кожну хвилину)
    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            if (currentState == RideState.SCHEDULED) {
                updateScheduledUi()
            }
            timeHandler.postDelayed(this, 30000) // Перевірка кожні 30 сек
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_progress)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initViews()

        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (currentOrder != null) {
            setupOrderData()
        } else {
            loadActiveOrderFromServer()
        }

        setupLocationListener()
        fetchCancellationReasons()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        timeHandler.post(timeRunnable)
    }

    override fun onStop() {
        super.onStop()
        if (locationService != null) {
            unbindService(serviceConnection)
            locationService = null
        }
        timeHandler.removeCallbacks(timeRunnable)
    }

    private fun initViews() {
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvDestinationLabel = findViewById(R.id.tv_destination_label)
        tvClientName = findViewById(R.id.tv_client_name)
        tvOrderInfo = findViewById(R.id.tv_order_info)
        btnAction = findViewById(R.id.btn_action)
        btnOptions = findViewById(R.id.btn_options)

        findViewById<View>(R.id.btn_back_progress).setOnClickListener {
            val session = com.taxiapp.driver.utils.SessionManager(this)
            session.setOrderMinimized(true)
            finish()
        }

        btnAction.setOnClickListener { handleActionButton() }
        btnOptions.setOnClickListener { showStylishActions() }
    }

    // --- ЛОГІКА ДІАЛОГІВ ---
    data class SheetOption(
        val id: Long,
        val text: String,
        val iconRes: Int? = null,
        val isDestructive: Boolean = false
    )

    private fun showStylishActions() {
        val options = listOf(
            SheetOption(1, "Звернення у підтримку", R.drawable.ic_headset),
            SheetOption(2, "Проблеми із замовленням", R.drawable.ic_settings),
            SheetOption(3, "Скасувати замовлення", R.drawable.ic_circle_red, true)
        )

        showStylishBottomSheet("Дії із замовленням", options) { selected ->
            when (selected.id) {
                1L -> showSupportDialog()
                2L -> Toast.makeText(this, "В розробці", Toast.LENGTH_SHORT).show()
                3L -> showStylishCancellationReasons()
            }
        }
    }

    private fun showStylishCancellationReasons() {
        if (cancellationReasons.isEmpty()) {
            Toast.makeText(this, "Завантаження списку...", Toast.LENGTH_SHORT).show()
            fetchCancellationReasons()
            return
        }

        val options = cancellationReasons.map { reason ->
            val text = if (reason.penaltyScore > 0) "${reason.reasonText} (Штраф ${reason.penaltyScore})" else reason.reasonText
            SheetOption(reason.id, text, null)
        }

        showStylishBottomSheet("Чому скасовуєте?", options) { selected ->
            performCancellation(selected.id)
        }
    }

    private fun showStylishBottomSheet(
        title: String,
        options: List<SheetOption>,
        onOptionClick: (SheetOption) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_generic, null)
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)

        val tvTitle = view.findViewById<TextView>(R.id.tv_sheet_title)
        val rvOptions = view.findViewById<RecyclerView>(R.id.rv_sheet_options)

        tvTitle.text = title
        rvOptions.layoutManager = LinearLayoutManager(this)
        rvOptions.adapter = GenericOptionsAdapter(options) { selected ->
            dialog.dismiss()
            onOptionClick(selected)
        }
        dialog.show()
    }

    inner class GenericOptionsAdapter(
        private val items: List<SheetOption>,
        private val onClick: (SheetOption) -> Unit
    ) : RecyclerView.Adapter<GenericOptionsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvText: TextView = view.findViewById(R.id.tv_option_text)
            val ivIcon: ImageView = view.findViewById(R.id.iv_option_icon)
            val divider: View = view.findViewById(R.id.divider_option)
            val container: View = view.findViewById(R.id.container_option)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bottom_sheet_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvText.text = item.text

            if (item.iconRes != null) {
                holder.ivIcon.visibility = View.VISIBLE
                holder.ivIcon.setImageResource(item.iconRes)
                if (item.isDestructive) {
                    holder.ivIcon.setColorFilter(Color.parseColor("#FF4444"))
                    holder.tvText.setTextColor(Color.parseColor("#FF4444"))
                } else {
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(this@OrderProgressActivity, R.color.driver_text_primary))
                    holder.tvText.setTextColor(ContextCompat.getColor(this@OrderProgressActivity, R.color.driver_text_primary))
                }
            } else {
                holder.ivIcon.visibility = View.GONE
                holder.tvText.setTextColor(ContextCompat.getColor(this@OrderProgressActivity, R.color.driver_text_primary))
            }

            holder.divider.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE
            holder.container.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }

    // ------------------------------------

    private fun fetchCancellationReasons() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).getCancellationReasons()
                if (response.isSuccessful && response.body() != null) {
                    cancellationReasons = response.body()!!
                }
            } catch (e: Exception) {}
        }
    }

    private fun showSupportDialog() {
        try {
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(R.layout.layout_bottom_sheet_support)
            dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка відкриття діалогу", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performCancellation(reasonId: Long) {
        val orderId = currentOrder?.id ?: return
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Скасування...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@OrderProgressActivity)
                val response = api.cancelOrder(orderId, reasonId)
                progressDialog.dismiss()
                if (response.isSuccessful) {
                    Toast.makeText(this@OrderProgressActivity, "Замовлення скасовано", Toast.LENGTH_LONG).show()
                    finishAndReturnToMap()
                } else {
                    Toast.makeText(this@OrderProgressActivity, "Помилка: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@OrderProgressActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performConfirmation() {
        val orderId = currentOrder?.id ?: return
        btnAction.isEnabled = false
        btnAction.text = "ПІДТВЕРДЖЕННЯ..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).confirmOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    // Сервер перевів статус в ACCEPTED
                    currentOrder = response.body()
                    setupOrderData() // Перемальовуємо UI під новий статус
                    Toast.makeText(this@OrderProgressActivity, "Підтверджено! Вирушайте до клієнта.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@OrderProgressActivity, "Помилка підтвердження", Toast.LENGTH_SHORT).show()
                    updateScheduledUi() // Повертаємо кнопку назад
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderProgressActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                updateScheduledUi()
            }
        }
    }

    private fun updateDriverMarker(location: Location) {
        if (!::map.isInitialized) return
        val currentLatLng = LatLng(location.latitude, location.longitude)
        if (driverMarker == null) {
            driverMarker = map.addMarker(MarkerOptions()
                .position(currentLatLng)
                .title("Ви")
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        } else {
            driverMarker?.position = currentLatLng
        }
    }

    private fun determineStateByStatus(status: String) {
        when (status) {
            "SCHEDULED" -> { currentState = RideState.SCHEDULED; updateScheduledUi() }
            "ACCEPTED" -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
            "DRIVER_ARRIVED" -> { currentState = RideState.WAITING; setupUiForWaiting() }
            "IN_PROGRESS" -> { currentState = RideState.TO_DESTINATION; setupUiForInTrip() }
            "COMPLETED" -> {
                currentState = RideState.COMPLETED
                if (currentOrder?.isRatedByDriver == false) showRatingDialog() else finishAndReturnToMap()
            }
            else -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
        }
    }

    // --- UI ДЛЯ ЗАПЛАНОВАНОГО ЗАМОВЛЕННЯ ---
    private fun updateScheduledUi() {
        val order = currentOrder ?: return
        val scheduledDate = order.getScheduledDate() ?: return
        val now = Date()
        val diffMillis = scheduledDate.time - now.time
        val diffMinutes = diffMillis / (60 * 1000)

        tvStatusTitle.text = "Заплановано на ${order.getFormattedScheduledTime()}"
        tvDestinationLabel.text = "Час подачі: ${order.getFormattedScheduledTime()}"

        if (diffMinutes > 35) {
            // Ще рано (більше 35 хв)
            btnAction.text = "ЧЕКАЙТЕ ЧАСУ"
            btnAction.isEnabled = false
            btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_black_bg)
        } else {
            // Час підтверджувати (<= 35 хв)
            // Перевіряємо, чи ми вже підтвердили
            if (order.isDriverConfirmed) {
                // Якщо підтвердили, але статус все ще SCHEDULED (сервер ще не перемикнув або лаг)
                btnAction.text = "ОЧІКУВАННЯ ПОЧАТКУ..."
                btnAction.isEnabled = false
            } else {
                // Треба підтвердити!
                btnAction.text = "ПІДТВЕРДИТИ ЗАМОВЛЕННЯ"
                btnAction.isEnabled = true
                btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)

                // Можна додати вібрацію або звук тут, якщо вікно відкрите
            }
        }
    }

    private fun setupOrderData() {
        val order = currentOrder ?: return
        determineStateByStatus(order.status ?: "")
        tvClientName.text = "Клієнт"
        tvOrderInfo.text = "${if(order.paymentMethod == "CASH") "Готівка" else "Картка"} • ${order.price.toInt()} ₴"
        locationService?.setTargetOrder(order)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onResume() { super.onResume(); startLocationUpdates() }
    override fun onPause() { super.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback) }

    private fun setupLocationListener() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                updateDriverMarker(location)

                // --- ПЕРЕВІРКА ДИСТАНЦІЇ ДЛЯ КНОПКИ "НА МІСЦІ" ---
                if (currentState == RideState.TO_CLIENT) {
                    checkDistanceForArrivedButton(location)
                }
            }
        }
    }

    // Блокування кнопки "НА МІСЦІ"
    private fun checkDistanceForArrivedButton(driverLoc: Location) {
        val order = currentOrder ?: return
        val targetLoc = Location("target")
        targetLoc.latitude = order.originLat ?: 0.0
        targetLoc.longitude = order.originLng ?: 0.0

        if (targetLoc.latitude == 0.0) return // Захист

        val distance = driverLoc.distanceTo(targetLoc) // В метрах

        if (distance <= 300) {
            if (!btnAction.isEnabled) {
                btnAction.isEnabled = true
                btnAction.text = "НА МІСЦІ"
                btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_neon_teal)
            }
        } else {
            // Заблоковано
            if (btnAction.isEnabled) {
                btnAction.isEnabled = false
                btnAction.text = "Ще їхати (${distance.toInt()} м)"
                btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_black_bg)
            } else {
                // Оновлюємо текст дистанції
                btnAction.text = "Ще їхати (${distance.toInt()} м)"
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateMapVisuals()
    }

    @SuppressLint("MissingPermission")
    private fun updateMapVisuals() {
        if (!::map.isInitialized || currentOrder == null) return
        map.clear()
        driverMarker = null
        val order = currentOrder!!

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val driverLoc = if (location != null) LatLng(location.latitude, location.longitude) else LatLng(50.45, 30.52)
            updateDriverMarker(location ?: Location("").apply { latitude = 50.45; longitude = 30.52 })

            // Якщо SCHEDULED або TO_CLIENT -> показуємо шлях до клієнта
            if (currentState == RideState.TO_CLIENT || currentState == RideState.WAITING || currentState == RideState.SCHEDULED) {
                val originLoc = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
                map.addMarker(MarkerOptions().position(originLoc).title("Клієнт").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
                drawRoadRoute(driverLoc, originLoc, R.color.driver_neon_teal)
            } else if (currentState == RideState.TO_DESTINATION) {
                val builder = LatLngBounds.Builder().include(driverLoc)
                if (!order.polyline.isNullOrEmpty()) {
                    val roadPoints = PolyUtil.decode(order.polyline)
                    map.addPolyline(PolylineOptions().addAll(roadPoints).width(14f).color(ContextCompat.getColor(this, R.color.driver_neon_teal)).jointType(JointType.ROUND).endCap(RoundCap()))
                    roadPoints.forEach { builder.include(it) }
                }
                val destLoc = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)
                map.addMarker(MarkerOptions().position(destLoc).title("Фініш").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                builder.include(destLoc)
                try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200)) } catch (e: Exception) { map.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLoc, 15f)) }
            }
        }
    }

    private fun drawRoadRoute(start: LatLng, end: LatLng, colorRes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiKey = getApiKeyFromManifest()
                val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.latitude},${start.longitude}&destination=${end.latitude},${end.longitude}&mode=driving&key=$apiKey"
                val result = URL(url).readText()
                val routes = JSONObject(result).getJSONArray("routes")
                if (routes.length() > 0) {
                    val points = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                    val path = PolyUtil.decode(points)
                    withContext(Dispatchers.Main) {
                        map.addPolyline(PolylineOptions().addAll(path).width(14f).color(ContextCompat.getColor(this@OrderProgressActivity, colorRes)).jointType(JointType.ROUND))
                        val builder = LatLngBounds.Builder()
                        path.forEach { builder.include(it) }; builder.include(start); builder.include(end)
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { map.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(start).include(end).build(), 200)) }
            }
        }
    }

    private fun handleActionButton() {
        val orderId = currentOrder?.id ?: return

        // --- ОБРОБКА SCHEDULED ---
        if (currentState == RideState.SCHEDULED) {
            performConfirmation()
            return
        }

        // --- ІНШІ СТАНИ ---
        btnAction.isEnabled = false
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@OrderProgressActivity)
                when (currentState) {
                    RideState.TO_CLIENT -> if (api.notifyArrived(orderId).isSuccessful) { currentOrder = currentOrder?.copy(status = "DRIVER_ARRIVED"); locationService?.setTargetOrder(currentOrder); currentState = RideState.WAITING; setupUiForWaiting(); updateMapVisuals() }
                    RideState.WAITING -> if (api.startTrip(orderId).isSuccessful) { currentOrder = currentOrder?.copy(status = "IN_PROGRESS"); locationService?.setTargetOrder(currentOrder); currentState = RideState.TO_DESTINATION; setupUiForInTrip(); updateMapVisuals() }
                    RideState.TO_DESTINATION -> if (api.completeOrder(orderId).isSuccessful) { currentOrder = currentOrder?.copy(status = "COMPLETED"); locationService?.setTargetOrder(null); currentState = RideState.COMPLETED; showRatingDialog() }
                    RideState.COMPLETED -> { }
                    else -> {}
                }
            } catch (e: Exception) { Toast.makeText(this@OrderProgressActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show() } finally { btnAction.isEnabled = true }
        }
    }

    private fun showRatingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_rate_client)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)
        val ratingBar = dialog.findViewById<RatingBar>(R.id.rating_bar)
        val etComment = dialog.findViewById<EditText>(R.id.et_comment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btn_submit_rating)
        btnSubmit.setOnClickListener {
            if (ratingBar.rating.toInt() == 0) { Toast.makeText(this, "Поставте оцінку", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            sendRating(ratingBar.rating.toInt(), etComment.text.toString(), dialog)
        }
        dialog.show()
    }

    private fun sendRating(score: Int, comment: String, dialog: Dialog) {
        val orderId = currentOrder?.id ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).rateClient(RateClientRequest(orderId, score, comment))
                if (response.isSuccessful) { dialog.dismiss(); finishAndReturnToMap() } else Toast.makeText(this@OrderProgressActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@OrderProgressActivity, "Помилка мережі", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun finishAndReturnToMap() {
        com.taxiapp.driver.utils.SessionManager(this).resetOrderMinimized()
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        startActivity(intent); finish()
    }

    private fun loadActiveOrderFromServer() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).getActiveOrder()
                if (response.isSuccessful && response.body() != null) { currentOrder = response.body(); setupOrderData(); if (::map.isInitialized) updateMapVisuals() } else finish()
            } catch (e: Exception) { finish() }
        }
    }

    private fun getApiKeyFromManifest(): String {
        return try { packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY") ?: "" } catch (e: Exception) { "" }
    }

    private fun setupUiForWaiting() {
        tvStatusTitle.text = "Очікування";
        // Якщо є час подачі (заплановане), показуємо його
        val order = currentOrder
        if (order != null && order.isScheduled()) {
            tvDestinationLabel.text = "Клієнт вийде о ${order.getFormattedScheduledTime()}"
        } else {
            tvDestinationLabel.text = "Клієнт виходить..."
        }
        btnAction.text = "ПОЧАТИ ПОЇЗДКУ"; btnAction.isEnabled = true; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.taxi_yellow)
    }

    private fun setupUiForInTrip() { tvStatusTitle.text = "В дорозі"; tvDestinationLabel.text = currentOrder?.toAddress ?: "Кінцева точка"; btnAction.text = "ЗАВЕРШИТИ"; btnAction.isEnabled = true; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_error) }

    private fun setupUiForToClient() {
        tvStatusTitle.text = "Їду до клієнта"; tvDestinationLabel.text = currentOrder?.fromAddress ?: "Адреса посадки";
        // Початковий стан кнопки (буде оновлено в checkDistanceForArrivedButton)
        btnAction.text = "ОЧІКУВАННЯ ПОЗИЦІЇ..."; btnAction.isEnabled = false; btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.driver_black_bg)
    }
}