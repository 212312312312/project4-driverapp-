package com.taxiapp.driver

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.IBinder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

    private lateinit var layoutWaitingInfo: LinearLayout
    private lateinit var tvWaitingTimer: TextView
    private var waitingTimerHandler = Handler(Looper.getMainLooper())
    private var waitingTimerRunnable: Runnable? = null

    // ЧАТ ПЕРЕМЕННЫЕ
    private lateinit var btnChatClient: View
    private lateinit var tvChatBadge: TextView
    private var unreadChatMessages = 0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var driverMarker: Marker? = null

    private var driverPositionAnimator: android.animation.ValueAnimator? = null
    private var driverRotationAnimator: android.animation.ValueAnimator? = null
    private var previousDriverLocation: android.location.Location? = null

    // КНОПКИ ВЗАИМОДЕЙСТВИЯ (ОБНОВЛЕНО ПОД ТВОЮ СТРУКТУРУ)
    private lateinit var btnSaveAction: Button
    private lateinit var btnContainerLayout: ConstraintLayout
    private lateinit var btnOptions: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvDestinationLabel: TextView
    private lateinit var tvOrderInfo: TextView

    private lateinit var llPriceBackground: LinearLayout
    private lateinit var ivPaymentIcon: ImageView

    private enum class RideState { SCHEDULED, TO_CLIENT, WAITING, TO_DESTINATION, ARRIVED_AT_WAYPOINT, COMPLETED }
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

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            if (currentState == RideState.SCHEDULED) {
                updateScheduledUi()
            }
            timeHandler.postDelayed(this, 30000)
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

        if (currentOrder?.id == null) {
            val backupId = intent.getStringExtra("EXTRA_ORDER_ID")
            if (backupId != null) {
                currentOrder = currentOrder?.copy(id = backupId)
            }
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
        stopWaitingTimer()
    }

    private fun initViews() {
        layoutWaitingInfo = findViewById(R.id.layout_waiting_info)
        tvWaitingTimer = findViewById(R.id.tv_waiting_timer)
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvDestinationLabel = findViewById(R.id.tv_destination_label)
        tvOrderInfo = findViewById(R.id.tv_order_info)

        // Связываем новые ID из твоей структуры макета
        btnSaveAction = findViewById(R.id.btn_save_action)
        btnContainerLayout = findViewById(R.id.btn_container_layout)

        llPriceBackground = findViewById(R.id.ll_price_background)
        ivPaymentIcon = findViewById(R.id.iv_payment_icon)
        tvOrderInfo = findViewById(R.id.tv_order_info)

        btnOptions = findViewById(R.id.btn_options)
        btnChatClient = findViewById(R.id.btn_chat_client)
        tvChatBadge = findViewById(R.id.tv_chat_badge)
        findViewById<View>(R.id.btn_navigation).setOnClickListener { openExternalNavigator() }

        btnChatClient.setOnClickListener {
            // Берем idLong (Long) или пробуем распарсить текстовый id в Long для ChatActivity
            val correctLongId = currentOrder?.idLong ?: currentOrder?.id?.toLongOrNull()

            if (correctLongId != null) {
                unreadChatMessages = 0
                updateChatBadgeUI()

                // Открываем экран чата с корректным числовым типом данных
                val intent = Intent(this@OrderProgressActivity, ChatActivity::class.java)
                intent.putExtra("ORDER_ID", correctLongId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Помилка: невірний ID замовлення", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_back_progress).setOnClickListener {
            val session = com.taxiapp.driver.utils.SessionManager(this)
            session.setOrderMinimized(true)
            finish()
        }

        btnSaveAction.setOnClickListener { handleActionButton() }
        btnOptions.setOnClickListener { showStylishActions() }
        findViewById<View>(R.id.btn_call_client).setOnClickListener {
            val clientPhone = currentOrder?.client?.phoneNumber
            if (!clientPhone.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$clientPhone"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Не вдалося відкрити звонилку", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Номер телефону клієнта відсутній", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btn_navigation).setOnClickListener { openExternalNavigator() }
    }

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

    private fun startWaitingTimer(order: Order) {
        stopWaitingTimer()
        // 💡 Берем waitingStartTime от сервера. Если заказ обычный или водитель опоздал — там будет arrivedAt
        val timeToParse = order.waitingStartTime ?: order.arrivedAt ?: return
        layoutWaitingInfo.visibility = View.VISIBLE

        val cleanTime = timeToParse.substringBefore(".").substringBefore("Z")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())

        val arrivedTime = try {
            format.parse(cleanTime)?.time ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        waitingTimerRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val diffMs = now - arrivedTime

                if (diffMs < 0) {
                    // 💡 Время подачи еще не пришло! Показываем водителю обратный отсчет до старта ожидания
                    val absDiffMs = Math.abs(diffMs)
                    val remMin = (absDiffMs / (1000 * 60)).toInt()
                    val remSec = ((absDiffMs / 1000) % 60).toInt()

                    layoutWaitingInfo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EBF5FF")) // Красивый синий фон ожидания времени
                    tvWaitingTimer.setTextColor(Color.parseColor("#1C7ED6"))
                    tvWaitingTimer.text = String.format("⏱ До початку очікування: %02d:%02d", remMin, remSec)

                    waitingTimerHandler.postDelayed(this, 1000)
                    return
                }

                val diffMinutesFull = diffMs / (1000 * 60).toDouble()
                val freeMins = order.freeWaitingMinutes

                if (diffMinutesFull <= freeMins) {
                    val remainingMs = (freeMins * 60 * 1000) - diffMs
                    val remMin = (remainingMs / (1000 * 60)).toInt()
                    val remSec = ((remainingMs / 1000) % 60).toInt()

                    layoutWaitingInfo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EBFBEE"))
                    tvWaitingTimer.setTextColor(Color.parseColor("#2B8A3E"))
                    tvWaitingTimer.text = String.format("⏱ Безкоштовне очікування: %02d:%02d", remMin, remSec)
                } else {
                    val paidMins = Math.floor(diffMinutesFull - freeMins).toInt()
                    val extraCost = paidMins * order.pricePerWaitingMinute

                    layoutWaitingInfo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF5F5"))
                    tvWaitingTimer.setTextColor(Color.parseColor("#C92A2A"))
                    tvWaitingTimer.text = String.format("⏳ Платне очікування: %d хв (+%.2f ₴)", paidMins, extraCost)
                }
                waitingTimerHandler.postDelayed(this, 1000)
            }
        }
        waitingTimerHandler.post(waitingTimerRunnable!!)
    }

    private fun stopWaitingTimer() {
        waitingTimerRunnable?.let { waitingTimerHandler.removeCallbacks(it) }
        waitingTimerRunnable = null
    }

    private fun showStylishBottomSheet(title: String, options: List<SheetOption>, onOptionClick: (SheetOption) -> Unit) {
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

    inner class GenericOptionsAdapter(private val items: List<SheetOption>, private val onClick: (SheetOption) -> Unit) : RecyclerView.Adapter<GenericOptionsAdapter.ViewHolder>() {
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

    private fun fetchCancellationReasons() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).getCancellationReasons()
                if (response.isSuccessful && response.body() != null) { cancellationReasons = response.body()!! }
            } catch (e: Exception) {}
        }
    }

    private fun showSupportDialog() {
        try {
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(R.layout.layout_bottom_sheet_support)
            dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
            dialog.show()
        } catch (e: Exception) { Toast.makeText(this, "Помилка", Toast.LENGTH_SHORT).show() }
    }

    private fun performCancellation(reasonId: Long) {
        val orderId = currentOrder?.id ?: return
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this).setMessage("Скасування...").setCancelable(false).create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).cancelOrder(orderId, reasonId)
                progressDialog.dismiss()
                if (response.isSuccessful) {
                    Toast.makeText(this@OrderProgressActivity, "Замовлення скасовано", Toast.LENGTH_LONG).show()
                    finishAndReturnToMap()
                }
            } catch (e: Exception) { progressDialog.dismiss() }
        }
    }

    private fun performConfirmation() {
        val orderId = currentOrder?.id ?: return
        btnSaveAction.isEnabled = false
        btnSaveAction.text = "ПІДТВЕРДЖЕННЯ..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).confirmOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    currentOrder = response.body()
                    setupOrderData()
                    Toast.makeText(this@OrderProgressActivity, "Підтверджено!", Toast.LENGTH_LONG).show()
                } else {
                    updateScheduledUi()
                }
            } catch (e: Exception) { updateScheduledUi() }
        }
    }

    private fun interpolateLatLng(fraction: Float, start: LatLng, end: LatLng): LatLng {
        val lat = (end.latitude - start.latitude) * fraction + start.latitude
        val lng = (end.longitude - start.longitude) * fraction + start.longitude
        return LatLng(lat, lng)
    }

    // Умный расчет угла разворота (чтобы машинка не разворачивалась через всю ось)
    private fun interpolateRotation(fraction: Float, start: Float, end: Float): Float {
        var diff = end - start
        while (diff < -180) diff += 360
        while (diff >= 180) diff -= 360
        return start + fraction * diff
    }

    private fun updateDriverMarker(location: Location) {
        if (!::map.isInitialized) return
        val newLatLng = LatLng(location.latitude, location.longitude)
// --- ОБНОВЛЕНО: Парсим и передаем кастомный HEX-цвет #00bfff ---
        val driverIcon = getBitmapDescriptorFromVector(this, R.drawable.ic_driver_icon, Color.parseColor("#00bfff"))

        // Шаг А: Вычисляем угол поворота (системный или расчетный между точками)
        val targetRotation = if (location.hasBearing()) {
            location.bearing
        } else if (previousDriverLocation != null && previousDriverLocation!!.distanceTo(location) > 1.5) {
            // Рассчитываем курс, только если машинка проехала больше 1.5 метров (защита от микро-дрожания GPS на месте)
            previousDriverLocation!!.bearingTo(location)
        } else {
            // Если стоим на месте, сохраняем текущий разворот машинки
            driverMarker?.rotation ?: 0f
        }

        if (driverMarker == null) {
            // Первая инициализация при входе на экран
            driverMarker = map.addMarker(MarkerOptions()
                .position(newLatLng)
                .title("Ви")
                .anchor(0.5f, 0.5f)
                .flat(true)
                .icon(driverIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            driverMarker?.rotation = targetRotation
            previousDriverLocation = location
            return
        }

        // Мгновенно останавливаем запущенные анимации перед началом новых
        driverPositionAnimator?.cancel()
        driverRotationAnimator?.cancel()

        val startLatLng = driverMarker?.position ?: newLatLng
        val startRotation = driverMarker?.rotation ?: 0f

        // Шаг Б: Анимация плавного скольжения позиции маркера
        driverPositionAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                driverMarker?.position = interpolateLatLng(fraction, startLatLng, newLatLng)
            }
            start()
        }

        // Шаг В: Анимация плавного разворота иконки машинки на целевой угол
        driverRotationAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                driverMarker?.rotation = interpolateRotation(fraction, startRotation, targetRotation)
            }
            start()
        }

        // Шаг Г: Запоминаем текущую локацию как прошлую для расчета курса на следующем шаге GPS
        previousDriverLocation = location
    }

    private fun determineStateByStatus(status: String) {
        when (status) {
            "SCHEDULED" -> { currentState = RideState.SCHEDULED; updateScheduledUi() }
            "ACCEPTED" -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
            "DRIVER_ARRIVED" -> { currentState = RideState.WAITING; setupUiForWaiting() }
            "ARRIVED_AT_WAYPOINT" -> { currentState = RideState.ARRIVED_AT_WAYPOINT; setupUiForWaypointWaiting() }
            "IN_PROGRESS" -> { currentState = RideState.TO_DESTINATION; setupUiForInTrip() }
            "COMPLETED" -> {
                currentState = RideState.COMPLETED
                if (currentOrder?.isRatedByDriver == false) showRatingDialog() else finishAndReturnToMap()
            }
            else -> { currentState = RideState.TO_CLIENT; setupUiForToClient() }
        }
    }

    private fun setupUiForWaiting() {
        tvStatusTitle.text = "Очікування"
        val order = currentOrder
        tvDestinationLabel.text = if (order != null && order.isScheduled()) "Клієнт вийде о ${order.getFormattedScheduledTime()}" else "Клієнт виходить..."

        btnSaveAction.text = "ПОЧАТИ ПОЇЗДКУ"
        btnSaveAction.isEnabled = true
        btnSaveAction.setTextColor(Color.BLACK)
        btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.taxi_yellow))

        currentOrder?.let { startWaitingTimer(it) }
    }

    private fun createCustomLocationDot(context: Context, color: Int): BitmapDescriptor {
        val density = context.resources.displayMetrics.density

        // Задаем радиусы с учетом плотности пикселей устройства
        val baseRadius = 7f * density    // Внутренний цветной круг
        val strokeRadius = 9.5f * density // Внешний белый контур
        val totalSize = (24 * density).toInt() // Общий размер холста для маркера

        val bitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = totalSize / 2f

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // 1. Рисуем внешний белый контур (подложку)
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, strokeRadius, paint)

        // 2. Рисуем внутренний лазурный круг (#00bfff)
        paint.color = color
        canvas.drawCircle(center, center, baseRadius, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun updateScheduledUi() {
        val order = currentOrder ?: return
        val scheduledDate = order.getScheduledDate() ?: return
        val now = Date()
        val diffMillis = scheduledDate.time - now.time
        val diffMinutes = diffMillis / (60 * 1000)

        tvStatusTitle.text = "Заплановано на ${order.getFormattedScheduledTime()}"
        tvDestinationLabel.text = "Час подачі: ${order.getFormattedScheduledTime()}"

        if (diffMinutes > 35) {
            btnSaveAction.text = "ЧЕКАЙТЕ ЧАСУ"
            btnSaveAction.isEnabled = false
            btnSaveAction.setTextColor(Color.GRAY)
            btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))
        } else {
            if (order.isDriverConfirmed) {
                btnSaveAction.text = "ОЧІКУВАННЯ ПОЧАТКУ..."
                btnSaveAction.isEnabled = false
                btnSaveAction.setTextColor(Color.GRAY)
            } else {
                btnSaveAction.text = "ПІДТВЕРДИТИ ЗАМОВЛЕННЯ"
                btnSaveAction.isEnabled = true
                btnSaveAction.setTextColor(ContextCompat.getColor(this, R.color.driver_text_black))
                btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
            }
        }
    }

    private fun setupOrderData() {
        val order = currentOrder ?: return

        // Выводим только сумму
        tvOrderInfo.text = "${order.price.toInt()} ₴"

        // --- ДОБАВЛЕНО: Синхронизируем UI экрана с текущим статусом заказа при загрузке ---
        determineStateByStatus(order.status ?: "")

        // Твой оригинальный блок логики (адаптированный под Активити)
        val method = order.paymentMethod ?: "CASH"
        if (method == "CASH") {
            val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
            llPriceBackground.backgroundTintList = ColorStateList.valueOf(neonTeal)
            ivPaymentIcon.setImageResource(R.drawable.ic_payment_cash)
        } else {
            llPriceBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#54b1f0"))
            ivPaymentIcon.setImageResource(R.drawable.ic_payment_card)
        }

        // Делаем цвет текста и иконки всегда черным, как в комментарии
        tvOrderInfo.setTextColor(Color.BLACK)
        ivPaymentIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)

        locationService?.setTargetOrder(order)
    }
    private fun openExternalNavigator() {
        val order = currentOrder ?: return

        // Автоматически определяем целевую точку:
        // Если мы уже везем клиента (TO_DESTINATION) — строим до Точки Б.
        // Если мы только едем к нему или ждем — строим до Точки А.
        val targetLat = if (currentState == RideState.TO_DESTINATION) order.destLat else order.originLat
        val targetLng = if (currentState == RideState.TO_DESTINATION) order.destLng else order.originLng

        if (targetLat == null || targetLng == null || targetLat == 0.0 || targetLng == 0.0) {
            Toast.makeText(this, "Координати точки замовлення відсутні", Toast.LENGTH_SHORT).show()
            return
        }

        // Достаем из настроек выбранный водителем навигатор (по умолчанию google_maps)
        val session = com.taxiapp.driver.utils.SessionManager(this)
        // Если в приложении уже есть выбор навигатора, замени строку ниже на: session.getChosenNavigator()
        val chosenNavigator = "google_maps"

        try {
            if (chosenNavigator == "waze") {
                // Интенты Waze с флагом navigate=yes мгновенно открывают ведение по маршруту
                val wazeUri = "waze://?ll=$targetLat,$targetLng&navigate=yes"
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wazeUri)))
            } else {
                // google.navigation:q= принудительно включает пошаговый режим «В путь» вместо обычного просмотра карты
                val mapsUri = "google.navigation:q=$targetLat,$targetLng&mode=d"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(mapsUri)).apply {
                    setPackage("com.google.android.apps.maps") // Гарантируем открытие именно в приложении Google Maps
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Запасной вариант: если выбранный навигатор не установлен, открываем стандартный системный диалог выбора карт
            try {
                val genericUri = "geo:$targetLat,$targetLng?q=$targetLat,$targetLng"
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(genericUri)))
            } catch (ex: Exception) {
                Toast.makeText(this, "Не вдалося знайти встановлений навігатор", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).setMinUpdateDistanceMeters(2f).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onResume() { super.onResume(); startLocationUpdates() }
    override fun onPause() { super.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback) }

    private fun setupLocationListener() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                updateDriverMarker(location)
                if (currentState == RideState.TO_CLIENT) { checkDistanceForArrivedButton(location) }
            }
        }
    }

    private fun checkDistanceForArrivedButton(driverLoc: Location) {
        val order = currentOrder ?: return
        val targetLoc = Location("target").apply {
            latitude = order.originLat ?: 0.0
            longitude = order.originLng ?: 0.0
        }
        if (targetLoc.latitude == 0.0) return
        val distance = driverLoc.distanceTo(targetLoc)

        if (distance <= 300) {
            btnSaveAction.isEnabled = true
            btnSaveAction.text = "НА МІСЦІ"
            btnSaveAction.setTextColor(ContextCompat.getColor(this, R.color.driver_text_black))
            btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
        } else {
            btnSaveAction.isEnabled = false
            btnSaveAction.text = "Ще їхати (${distance.toInt()} м)"
            btnSaveAction.setTextColor(Color.GRAY)
            btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // --- ДОБАВЛЕНО: Полное отключение элементов управления Google Карты ---
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
            isIndoorLevelPickerEnabled = false
        }

        // Внедряем динамический стиль карты под тему приложения

        // Внедряем динамический стиль карты под тему приложения
        try {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val styleRes = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) R.raw.map_style_dark else R.raw.map_style_standard
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
        } catch (e: Exception) {}

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

            // Подготавливаем кастомные иконки для точек маршрута
// --- ОБНОВЛЕНО: Красим иконку клиента в основной цвет ---
            // Подготавливаем кастомные иконки для точек маршрута
// --- ОБНОВЛЕНО: Красим в primary и принудительно увеличиваем до 42dp только для карты ---
            val clientIcon = getBitmapDescriptorFromVector(
                this,
                R.drawable.ic_client_icon,
                ContextCompat.getColor(this, R.color.driver_text_primary),
                widthDp = 42,
                heightDp = 42
            )
            val waypointIcon = getBitmapDescriptorFromVector(this, R.drawable.ic_marker_waypoint)
            val destIcon = getBitmapDescriptorFromVector(this, R.drawable.ic_marker_to)

            if (currentState == RideState.TO_CLIENT || currentState == RideState.WAITING || currentState == RideState.SCHEDULED) {
                val originLoc = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)

                // Метка Точки А (Клиент)
                map.addMarker(MarkerOptions()
                    .position(originLoc)
                    .title("Клієнт")
                    .anchor(0.5f, 0.5f)
                    .icon(clientIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

                drawRoadRoute(driverLoc, originLoc, R.color.driver_neon_teal)

            } else if (currentState == RideState.TO_DESTINATION) {
                val builder = LatLngBounds.Builder().include(driverLoc)

                if (!order.polyline.isNullOrEmpty()) {
                    val roadPoints = PolyUtil.decode(order.polyline)
                    map.addPolyline(PolylineOptions().addAll(roadPoints).width(14f).color(ContextCompat.getColor(this, R.color.driver_neon_teal)).jointType(JointType.ROUND).endCap(RoundCap()))
                    roadPoints.forEach { builder.include(it) }
                }

                // Метка Конечной Точки Б (Финиш)
                val destLoc = LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)
                map.addMarker(MarkerOptions()
                    .position(destLoc)
                    .title("Фініш")
                    .anchor(0.5f, 0.5f)
                    .icon(destIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                builder.include(destLoc)

                // Метки Промежуточных Точек Остановок (Stops)
                order.stops?.sortedBy { it.stopOrder }?.forEach { stop ->
                    val stopLoc = LatLng(stop.lat, stop.lng)
                    map.addMarker(MarkerOptions()
                        .position(stopLoc)
                        .title("Проміжна точка")
                        .anchor(0.5f, 0.5f)
                        .icon(waypointIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)))
                    builder.include(stopLoc)
                }

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
        if (currentState == RideState.SCHEDULED) { performConfirmation(); return }

        btnSaveAction.isEnabled = false
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@OrderProgressActivity)
                when (currentState) {
                    RideState.TO_CLIENT -> {
                        val response = api.driverArrived(orderId)
                        if (response.isSuccessful) {
                            currentOrder = response.body() ?: currentOrder?.copy(
                                status = "DRIVER_ARRIVED",
                                arrivedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(Date())
                            )
                            locationService?.setTargetOrder(currentOrder)
                            currentState = RideState.WAITING
                            setupUiForWaiting()
                            updateMapVisuals()
                        }
                    }
                    RideState.WAITING -> {
                        val response = api.startTrip(orderId)
                        if (response.isSuccessful) {
                            currentOrder = response.body() ?: currentOrder?.copy(status = "IN_PROGRESS")
                            locationService?.setTargetOrder(currentOrder)
                            currentState = RideState.TO_DESTINATION
                            setupUiForInTrip()
                            updateMapVisuals()
                        }
                    }
                    RideState.TO_DESTINATION -> {
                        val order = currentOrder
                        if (order != null && order.hasRemainingWaypoints()) {
                            // 🛠️ Логика промежуточной точки: фиксируем прибытие
                            val response = api.arriveAtWaypoint(orderId)
                            if (response.isSuccessful) {
                                currentOrder = response.body()
                                currentState = RideState.ARRIVED_AT_WAYPOINT
                                setupUiForWaypointWaiting()
                                updateMapVisuals()
                            }
                        } else {
                            // Обычное завершение заказа на финальной точке
                            val response = api.completeOrder(orderId)
                            if (response.isSuccessful) {
                                currentOrder = currentOrder?.copy(status = "COMPLETED")
                                locationService?.setTargetOrder(null)
                                currentState = RideState.COMPLETED
                                unreadChatMessages = 0
                                updateChatBadgeUI()
                                showRatingDialog()
                            } else if (response.code() == 402) {
                                currentOrder = currentOrder?.copy(paymentMethod = "CASH")
                                setupOrderData()
                                showPaymentErrorDialog()
                            }
                        }
                    }
                    RideState.ARRIVED_AT_WAYPOINT -> {
                        // 🛠️ Продолжение движения после ожидания на промежуточной точке
                        val response = api.resumeTrip(orderId)
                        if (response.isSuccessful) {
                            currentOrder = response.body()
                            currentState = RideState.TO_DESTINATION
                            setupUiForInTrip()
                            updateMapVisuals()
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderProgressActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSaveAction.isEnabled = true
            }
        }
    }

    private fun showRatingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_rate_client)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)

        val etComment = dialog.findViewById<EditText>(R.id.et_comment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btn_submit_rating)

        var selectedRating = 0
        val starsList = listOf<ImageView>(
            dialog.findViewById(R.id.star1), dialog.findViewById(R.id.star2),
            dialog.findViewById(R.id.star3), dialog.findViewById(R.id.star4),
            dialog.findViewById(R.id.star5)
        )

        fun renderStars(rating: Int) {
            selectedRating = rating
            starsList.forEachIndexed { index, imageView ->
                if (index < rating) {
                    imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@OrderProgressActivity, R.color.driver_neon_teal))
                } else {
                    imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@OrderProgressActivity, R.color.driver_text_secondary))
                }
            }
        }

        renderStars(0)
        starsList.forEachIndexed { index, imageView ->
            imageView.setOnClickListener { renderStars(index + 1) }
        }

        btnSubmit.setOnClickListener {
            if (selectedRating == 0) { Toast.makeText(this, "Поставте оцінку", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            sendRating(selectedRating, etComment.text.toString(), dialog)
        }
        dialog.show()
    }

    private fun sendRating(score: Int, comment: String, dialog: Dialog) {
        val orderId = currentOrder?.idLong ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderProgressActivity).rateClient(RateClientRequest(orderId, score, comment))
                if (response.isSuccessful) { dialog.dismiss(); finishAndReturnToMap() }
            } catch (e: Exception) {}
        }
    }

    private fun showPaymentErrorDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.payment_failed_title))
            .setMessage(getString(R.string.payment_failed_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.btn_understood)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun incrementUnreadMessages() {
        unreadChatMessages++
        updateChatBadgeUI()
        Toast.makeText(this, "Нове повідомлення від клієнта!", Toast.LENGTH_SHORT).show()
    }

    private fun updateChatBadgeUI() {
        runOnUiThread {
            if (unreadChatMessages > 0) {
                tvChatBadge.visibility = View.VISIBLE
                tvChatBadge.text = unreadChatMessages.toString()
            } else {
                tvChatBadge.visibility = View.GONE
            }
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

    private fun setupUiForWaypointWaiting() {
        tvStatusTitle.text = "Очікування на точці"
        tvDestinationLabel.text = currentOrder?.getCurrentWaypointAddress() ?: "Проміжна зупинка"

        btnSaveAction.text = "ПРОДОВЖИТИ РУХ"
        btnSaveAction.isEnabled = true
        btnSaveAction.setTextColor(Color.BLACK)
        btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.taxi_yellow))

        currentOrder?.let { startWaitingTimer(it) }
    }

    private fun setupUiForInTrip() {
        tvStatusTitle.text = "В дорозі"

        val order = currentOrder
        if (order != null && order.hasRemainingWaypoints()) {
            tvDestinationLabel.text = "Їду до: ${order.getCurrentWaypointAddress()}"
            btnSaveAction.text = "НА МІСЦІ (ТОЧКА)"
            btnSaveAction.setTextColor(Color.BLACK)
            btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_neon_teal))
        } else {
            tvDestinationLabel.text = order?.toAddress ?: "Кінцева точка"
            btnSaveAction.text = "ЗАВЕРШИТИ"
            btnSaveAction.setTextColor(Color.WHITE)
            btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_error))
        }

        stopWaitingTimer()
        if (order != null && order.waitingPrice > 0) {
            layoutWaitingInfo.visibility = View.VISIBLE
            layoutWaitingInfo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF4E6"))
            tvWaitingTimer.setTextColor(Color.parseColor("#D9480F"))
            tvWaitingTimer.text = String.format("💰 Додано за очікування: %.2f ₴", order.waitingPrice)
        } else {
            layoutWaitingInfo.visibility = View.GONE
        }
    }

    private fun setupUiForToClient() {
        tvStatusTitle.text = "Їду до клієнта"
        tvDestinationLabel.text = currentOrder?.fromAddress ?: "Адреса посадки"

        btnSaveAction.text = "ОЧІКУВАННЯ ПОЗИЦІЇ..."
        btnSaveAction.isEnabled = false
        btnSaveAction.setTextColor(Color.GRAY)
        btnContainerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.driver_black_bg))

        stopWaitingTimer()
        layoutWaitingInfo.visibility = View.GONE
    }

    private fun getBitmapDescriptorFromVector(
        context: Context,
        vectorResId: Int,
        tintColor: Int? = null,
        widthDp: Int? = null,    // <-- Добавили кастомную ширину
        heightDp: Int? = null    // <-- Добавили кастомную высоту
    ): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null

        tintColor?.let { vectorDrawable.setTint(it) }

        // Вычисляем итоговые пиксели: если переданы DP — переводим, если нет — берем дефолт из XML
        val density = context.resources.displayMetrics.density
        val width = widthDp?.let { (it * density).toInt() } ?: vectorDrawable.intrinsicWidth
        val height = heightDp?.let { (it * density).toInt() } ?: vectorDrawable.intrinsicHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}