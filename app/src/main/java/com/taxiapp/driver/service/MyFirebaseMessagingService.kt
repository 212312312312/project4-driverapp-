package com.taxiapp.driver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.taxiapp.driver.OrderOfferActivity
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Теперь этот метод будет вызываться ВСЕГДА, даже если приложение закрыто
        val data = remoteMessage.data
        val type = data["type"]
        Log.d("FCM", "Message received type: $type")

        if (type == "ORDER_OFFER") {
            // Будим процессор на 10 секунд, чтобы успеть загрузить данные и показать экран
            wakeUpScreen()

            val orderId = data["orderId"]?.toLongOrNull()
            if (orderId != null) {
                fetchOrderAndShowNotification(orderId)
            }
        }
    }

    private fun wakeUpScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = if (Build.VERSION.SDK_INT >= 20) pm.isInteractive else pm.isScreenOn
        if (!isScreenOn) {
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "TaxiDriver:WakeUpLock"
            )
            wl.acquire(10000) // Держим 10 секунд
        }
    }

    private fun fetchOrderAndShowNotification(orderId: Long) {
        val sessionManager = SessionManager(this)
        if (sessionManager.fetchAuthToken() == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Если API ответит быстро - экран появится быстро.
                // Если интернет плохой, задержка будет здесь.
                val response = ApiClient.getInstance().getApiService(applicationContext).getOrderById(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    showFullScreenNotification(order)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showFullScreenNotification(order: Order) {
        val channelId = "order_offers_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Пропозиції замовлень", // Важно: название, которое видит юзер
                NotificationManager.IMPORTANCE_HIGH // Обязательно HIGH
            ).apply {
                description = "Показує нові замовлення на весь екран"
                enableVibration(true)
                // Звук лучше настраивать здесь, если нужен кастомный
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Интент на открытие OrderOfferActivity
        val fullScreenIntent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            // Эти флаги критически важны, чтобы активити открылась поверх всего
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            order.id.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distanceKm = "%.1f".format((order.distanceMeters ?: 0) / 1000.0)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Нове замовлення! (${order.tariffName})")
            .setContentText("${order.price.toInt()} ₴ • $distanceKm км")
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX для мгновенного показа
            .setCategory(NotificationCompat.CATEGORY_CALL) // Категория звонка лучше всего будит
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true) // TRUE - открывать сразу
            .setContentIntent(fullScreenPendingIntent)
            .setTimeoutAfter(20000) // Убрать уведомление через 20 сек (таймаут сервера)

        notificationManager.notify(order.id.toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SessionManager(this).saveFcmToken(token)
    }
}