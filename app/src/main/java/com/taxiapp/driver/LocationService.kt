package com.taxiapp.driver.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.taxiapp.driver.BuildConfig
import com.taxiapp.driver.ChatEventBus
import com.taxiapp.driver.MainActivity
import com.taxiapp.driver.OrderConfirmationActivity
import com.taxiapp.driver.OrderOfferActivity
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.network.UpdateLocationRequest
import com.taxiapp.driver.utils.SessionManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // ✅ ДОБАВЛЕНО: Постоянный STOMP WebSocket-клиент в фоновом сервисе для мгновенного приема заказов и отмен
    private var stompClient: StompClient? = null
    private val wsDisposable = CompositeDisposable()
    private val gson = Gson()

    private var isForegroundStarted = false
    private var isServiceRunning = false

    private var lastKnownLat = 0.0
    private var lastKnownLng = 0.0
    private var lastKnownBearing = 0f

    private var lastSentLat = 0.0
    private var lastSentLng = 0.0
    private var lastSentTime = 0L

    var onLocationUpdated: ((Double, Double) -> Unit)? = null
    private var trackingOrder: Order? = null
    private var isReminderTriggered = false

    inner class LocalBinder : android.os.Binder() {
        fun getService(): LocationService = this@LocationService
    }
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        isServiceRunning = true

        startForegroundService()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isServiceRunning) return
                for (location in locationResult.locations) {
                    lastKnownLat = location.latitude
                    lastKnownLng = location.longitude
                    lastKnownBearing = location.bearing

                    if (shouldSendLocation(location)) {
                        sendLocationToServer(location.latitude, location.longitude, location.bearing)

                        if (sessionManager.isManualLocationActive()) {
                            sessionManager.getManualLocation()?.let { manual ->
                                lastSentLat = manual.first
                                lastSentLng = manual.second
                            }
                        } else {
                            lastSentLat = location.latitude
                            lastSentLng = location.longitude
                        }
                        lastSentTime = System.currentTimeMillis()
                    }

                    checkDistanceToTarget(location)

                    var latToSend = location.latitude
                    var lngToSend = location.longitude

                    if (sessionManager.isManualLocationActive()) {
                        sessionManager.getManualLocation()?.let {
                            latToSend = it.first
                            lngToSend = it.second
                        }
                    }

                    onLocationUpdated?.invoke(latToSend, lngToSend)
                }
            }
        }

        sendInitialLocation()
        requestLocationUpdates()
        startHeartbeatLoop()
        connectDriverOrderWebSocket()
    }

    // ✅ ДОБАВЛЕНО: Независимый Heartbeat каждые 25 секунд, чтобы стоящий на месте водитель
    // никогда не удалялся сервером из Redis (drivers:geo / drivers:meta)!
    @SuppressLint("MissingPermission")
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                delay(25_000L)
                if (!sessionManager.isDriverOnline()) continue

                val now = System.currentTimeMillis()
                if (now - lastSentTime >= 22_000L) {
                    if (sessionManager.isManualLocationActive()) {
                        sessionManager.getManualLocation()?.let { (mLat, mLng) ->
                            sendLocationToServer(mLat, mLng, 0f)
                            lastSentLat = mLat
                            lastSentLng = mLng
                            lastSentTime = now
                        }
                    } else if (lastKnownLat != 0.0 && lastKnownLng != 0.0) {
                        sendLocationToServer(lastKnownLat, lastKnownLng, lastKnownBearing)
                        lastSentLat = lastKnownLat
                        lastSentLng = lastKnownLng
                        lastSentTime = now
                    } else {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    lastKnownLat = loc.latitude
                                    lastKnownLng = loc.longitude
                                    lastKnownBearing = loc.bearing
                                    sendLocationToServer(loc.latitude, loc.longitude, loc.bearing)
                                    lastSentTime = System.currentTimeMillis()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // ✅ ДОБАВЛЕНО: Подписка на персональный WebSocket-топик водителя /topic/drivers/{driverId}/orders
    // Работает в фоновом сервисе и мгновенно открывает OrderOfferActivity или закрывает заказ при отмене!
    private fun connectDriverOrderWebSocket() {
        val token = sessionManager.fetchAuthToken() ?: return
        val driverId = sessionManager.getDriverId()
        if (driverId <= 0L) return

        try {
            wsDisposable.clear()
            stompClient?.disconnect()
        } catch (_: Exception) {}

        val baseUrl = BuildConfig.BASE_URL
        val wsProtocol = if (baseUrl.startsWith("https")) "wss" else "ws"
        val cleanHost = baseUrl.replace("https://", "").replace("http://", "").removeSuffix("/")
        val url = "$wsProtocol://$cleanHost/ws-taxi/websocket?token=$token"

        val client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url).apply {
            withClientHeartbeat(15000).withServerHeartbeat(15000)
        }
        stompClient = client

        val topicPath = "/topic/drivers/$driverId/orders"
        Log.d("LocationService", "🔌 Подписка на персональный сокет заказов: $topicPath")

        val topicSub = client.topic(topicPath)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ topicMessage ->
                val payload = topicMessage.payload
                if (payload.isNullOrEmpty() || payload == "null") return@subscribe

                try {
                    val msgObj = JSONObject(payload)
                    val action = msgObj.optString("action")
                    val orderId = msgObj.optString("orderId")
                    val orderJson = msgObj.optJSONObject("order")?.toString()

                    if (action == "REMOVE") {
                        Log.d("LocationService", "🛑 [WS] Получен сигнал REMOVE для заказа $orderId")
                        handleRemoteOrderCancellation(orderId)
                        return@subscribe
                    }

                    if (orderJson != null) {
                        val order = gson.fromJson(orderJson, Order::class.java)
                        when (order.status) {
                            "OFFERING" -> {
                                Log.d("LocationService", "🚀 [WS] Прилетел персональный оффер заказа ${order.id}!")
                                launchOrderOfferFromBackground(order)
                            }
                            "CANCELLED" -> {
                                Log.d("LocationService", "🛑 [WS] Заказ ${order.id} отменен!")
                                handleRemoteOrderCancellation(order.id)
                            }
                            else -> {
                                val updateIntent = Intent("com.taxiapp.driver.ACTION_ORDER_UPDATED").apply {
                                    setPackage(packageName)
                                    putExtra("EXTRA_ORDER_ID", order.id)
                                    putExtra("EXTRA_ORDER_JSON", orderJson)
                                }
                                sendBroadcast(updateIntent)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LocationService", "Ошибка парсинга сообщения сокета водителя: ${e.message}")
                }
            }, { err ->
                Log.e("LocationService", "Ошибка подписки $topicPath: ${err.message}")
            })

        val lifecycleSub = client.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { event ->
                when (event.type) {
                    LifecycleEvent.Type.OPENED -> Log.d("LocationService", "✅ Персональный WebSocket водителя подключен")
                    LifecycleEvent.Type.CLOSED, LifecycleEvent.Type.ERROR -> {
                        Log.w("LocationService", "⚠️ WebSocket водителя отключен, переподключение через 5 сек...")
                        serviceScope.launch {
                            delay(5000L)
                            if (isServiceRunning && sessionManager.fetchAuthToken() != null) {
                                connectDriverOrderWebSocket()
                            }
                        }
                    }
                    else -> {}
                }
            }

        wsDisposable.add(topicSub)
        wsDisposable.add(lifecycleSub)

        val headers = listOf(StompHeader("Authorization", "Bearer $token"))
        client.connect(headers)
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "TaxiDriver:ServiceWakeLock"
                )
                wl.acquire(10000L)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Ошибка WakeLock: ${e.message}")
        }
    }

    private fun launchOrderOfferFromBackground(order: Order) {
        if (OrderOfferActivity.activeOfferedOrderId == order.id) {
            Log.d("LocationService", "Оффер ${order.id} уже открыт на экране, пропускаем дубликат.")
            return
        }

        wakeUpScreen()

        val intent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        try {
            startActivity(intent)
            Log.d("LocationService", "🚀 OrderOfferActivity запущена из LocationService!")
        } catch (e: Exception) {
            Log.e("LocationService", "Не удалось запустить OrderOfferActivity напрямую: ${e.message}")
        }

        // Дополнительно вешаем FullScreenIntent уведомление для гарантированного пробития блокировки экрана
        val notifId = order.idLong?.toInt() ?: order.id.hashCode()
        val channelId = "offer_fullscreen_channel_v5"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Пропозиція замовлення", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Пропозиція замовлення: ${order.getTotalFullPrice().toInt()} ₴")
            .setContentText(order.fromAddress ?: "Натисніть для перегляду")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setTimeoutAfter(20000L)

        notificationManager.notify(notifId, builder.build())
    }

    private fun handleRemoteOrderCancellation(orderUuid: String?) {
        wakeUpScreen()
        ChatEventBus.triggerOrderCancelled(orderUuid)

        val cancelBroadcast = Intent("com.taxiapp.driver.ORDER_CANCELLED").apply {
            setPackage(packageName)
            putExtra("CANCELLED_ORDER_UUID", orderUuid)
            putExtra("EXTRA_ORDER_ID", orderUuid)
        }
        sendBroadcast(cancelBroadcast)

        val closeOfferBroadcast = Intent("com.taxiapp.driver.ACTION_CLOSE_ORDER_OFFER").apply {
            setPackage(packageName)
            putExtra("EXTRA_ORDER_ID", orderUuid)
        }
        sendBroadcast(closeOfferBroadcast)
    }

    fun forceSendLocationNow() {
        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let { (mLat, mLng) ->
                sendLocationToServer(mLat, mLng, 0f)
                lastSentTime = System.currentTimeMillis()
                return
            }
        }
        if (lastKnownLat != 0.0 && lastKnownLng != 0.0) {
            sendLocationToServer(lastKnownLat, lastKnownLng, lastKnownBearing)
            lastSentTime = System.currentTimeMillis()
        } else {
            sendInitialLocation()
        }
    }

    fun setTargetOrder(order: Order?) {
        if (this.trackingOrder?.id != order?.id || this.trackingOrder?.status != order?.status) {
            this.isReminderTriggered = false
        }
        this.trackingOrder = order
        Log.d("LocationService", "Tracking order: ID=${order?.id}, Status=${order?.status}")

        requestLocationUpdates()
    }

    private fun checkDistanceToTarget(currentLoc: Location) {
        if (!sessionManager.isStatusReminderEnabled()) return

        val order = trackingOrder ?: return
        if (isReminderTriggered) return

        val targetLoc = Location("target")
        var shouldTrack = false

        when (order.status) {
            "ACCEPTED" -> {
                targetLoc.latitude = order.originLat ?: 0.0
                targetLoc.longitude = order.originLng ?: 0.0
                shouldTrack = true
            }
            "IN_PROGRESS" -> {
                targetLoc.latitude = order.destLat ?: 0.0
                targetLoc.longitude = order.destLng ?: 0.0
                shouldTrack = true
            }
        }

        if (!shouldTrack) return
        if (targetLoc.latitude == 0.0 && targetLoc.longitude == 0.0) return

        val distance = currentLoc.distanceTo(targetLoc)

        if (distance < 300) {
            isReminderTriggered = true
            sendProximityNotification()
        }
    }

    private fun sendProximityNotification() {
        val title = "Статус замовлення"
        val message = "Майже на місці"

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "driver_alert_channel"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_place_small)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(200, notification)
    }

    @SuppressLint("MissingPermission")
    private fun sendInitialLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    lastKnownLat = it.latitude
                    lastKnownLng = it.longitude
                    lastKnownBearing = it.bearing

                    sendLocationToServer(it.latitude, it.longitude, it.bearing)
                    lastSentLat = it.latitude
                    lastSentLng = it.longitude
                    lastSentTime = System.currentTimeMillis()

                    var latToSend = it.latitude
                    var lngToSend = it.longitude

                    if (sessionManager.isManualLocationActive()) {
                        sessionManager.getManualLocation()?.let { manual ->
                            latToSend = manual.first
                            lngToSend = manual.second
                        }
                    }

                    onLocationUpdated?.invoke(latToSend, lngToSend)
                }
            }
        } catch (e: SecurityException) {
            Log.e("LocationService", "Немає дозволу на локацію: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val isWithOrder = trackingOrder != null &&
                (trackingOrder?.status == "ACCEPTED" ||
                        trackingOrder?.status == "IN_PROGRESS" ||
                        trackingOrder?.status == "DRIVER_ARRIVED" ||
                        trackingOrder?.status == "ARRIVED")

        val interval = if (isWithOrder) 5000L else 10000L

        // ✅ КРИТИЧЕСКИЙ ФИКС: Ставим minUpdateDistanceMeters(0f)!
        // Раньше стояло 25f, из-за чего на стоящем телефоне FusedLocationProvider вообще не вызывал onLocationResult!
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "GPS интервал изменен: заказ=$isWithOrder, интервал=${interval}мс")
        } catch (e: SecurityException) {
            Log.e("LocationService", "SecurityException при requestLocationUpdates: ${e.message}")
        }
    }

    private fun sendLocationToServer(realLat: Double, realLng: Double, bearing: Float) {
        var latToSend = realLat
        var lngToSend = realLng

        if (sessionManager.isManualLocationActive()) {
            sessionManager.getManualLocation()?.let { manual ->
                latToSend = manual.first
                lngToSend = manual.second
            }
        }

        if (latToSend == 0.0 && lngToSend == 0.0) return
        if (sessionManager.fetchAuthToken() == null) {
            stopSelf()
            return
        }

        serviceScope.launch {
            try {
                val request = UpdateLocationRequest(latToSend, lngToSend, bearing)
                val response = ApiClient.getInstance().getApiService(applicationContext).updateLocation(request)

                if (!response.isSuccessful) {
                    Log.e("LocationService", "Сервер відхилив локацію: Код=${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Помилка мережі при відправці локації: ${e.message}")
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isServiceRunning = false
        // ✅ ИСПРАВЛЕНО: Сбрасываем локальный статус онлайн при смахивании приложения,
        // чтобы при следующем запуске тумблер на экране не обманывал водителя!
        sessionManager.saveDriverOnlineStatus(false)
        val token = sessionManager.fetchAuthToken()

        if (token != null) {
            try {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(2500L) {
                        try {
                            ApiClient.getInstance().getApiService(applicationContext).deleteLocation()
                            Log.d("LocationService", "✅ Сигнал выхода из приложения успешно отправлен")
                        } catch (e: Exception) {
                            Log.e("LocationService", "Помилка відправки статусу offline: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForegroundStarted) {
            startForegroundService()
        }
        if (stompClient == null || stompClient?.isConnected == false) {
            connectDriverOrderWebSocket()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (isForegroundStarted) return
        isForegroundStarted = true

        val channelId = "location_channel_v3"
        val channelName = "Фонова геолокація"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для відстеження геопозиції водія під час роботи"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Unit Driver")
            .setContentText("Трансляція геолокації та пошук замовлень активні")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Помилка запуску ForegroundService: ${e.message}")
            isForegroundStarted = false
        }
    }

    private fun shouldSendLocation(location: Location): Boolean {
        // ✅ ИСПРАВЛЕНО: В DEBUG-сборке разрешаем тестовые/эмуляторные координаты, чтобы можно было тестировать!
        if (location.isFromMockProvider && !BuildConfig.DEBUG) {
            Log.w("LocationService", "🚨 Обнаружена поддельная локация в релизе! Игнорируем.")
            return false
        }

        if (sessionManager.isManualLocationActive()) {
            val currentTime = System.currentTimeMillis()
            return currentTime - lastSentTime >= 15000L
        }

        if (lastSentLat == 0.0 && lastSentLng == 0.0) return true

        val isWithOrder = trackingOrder != null &&
                (trackingOrder?.status == "ACCEPTED" ||
                        trackingOrder?.status == "IN_PROGRESS" ||
                        trackingOrder?.status == "DRIVER_ARRIVED" ||
                        trackingOrder?.status == "ARRIVED")

        val lastLocation = Location("last").apply {
            latitude = lastSentLat
            longitude = lastSentLng
        }
        val distance = location.distanceTo(lastLocation)
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastSentTime

        val isStanding = location.speed < 1.0f

        if (isWithOrder) {
            if (isStanding && distance < 10f && timePassed < 20000L) {
                return false
            }
        } else {
            // ✅ ИСПРАВЛЕНО: Шлем Keep-Alive каждые 25 секунд (вместо 60 сек), чтобы Redis никогда не удалял водителя!
            if (isStanding && distance < 15f && timePassed < 25000L) {
                return false
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        isForegroundStarted = false
        isServiceRunning = false
        onLocationUpdated = null
        heartbeatJob?.cancel()
        try {
            wsDisposable.clear()
            stompClient?.disconnect()
            stompClient = null
        } catch (_: Exception) {}
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}