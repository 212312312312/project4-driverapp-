package com.taxiapp.driver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val data = remoteMessage.data
        val type = data["type"]
        Log.d("FCM", "Message received type: $type")

        if (type == "ORDER_OFFER") {
            val orderId = data["orderId"]?.toLongOrNull()
            if (orderId != null) {
                fetchOrderAndShowNotification(orderId)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Теперь метод saveFcmToken существует в SessionManager, ошибка уйдет
        SessionManager(this).saveFcmToken(token)
    }

    private fun fetchOrderAndShowNotification(orderId: Long) {
        val sessionManager = SessionManager(this)
        if (sessionManager.fetchAuthToken() == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = ApiClient.getInstance().getApiService(applicationContext).getOrderById(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    showFullScreenNotification(order)
                } else {
                    Log.e("FCM", "Не удалось загрузить заказ: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Ошибка сети: ${e.message}")
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
                "Нові замовлення",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Повідомлення про пропозицію замовлення"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            order.id.toInt(), // Уникальный ID запроса (важно!)
            intent,
            // Добавляем FLAG_ONE_SHOT или FLAG_UPDATE_CURRENT
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ИСПРАВЛЕНИЕ ОШИБКИ 2: Безопасное деление
        // Если distanceMeters == null, используем 0
        val distanceKm = (order.distanceMeters ?: 0) / 1000.0

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Нове замовлення!")
            .setContentText("Ціна: ${order.price.toInt()} ₴ • $distanceKm км") // Используем переменную
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)

        notificationManager.notify(order.id.toInt(), notificationBuilder.build())
    }
}