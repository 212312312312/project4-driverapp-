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
import com.taxiapp.driver.OrderConfirmationActivity // <--- НОВА ACTIVITY
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

        if (type == "ORDER_OFFER" || type == "ORDER_CONFIRMATION") {
            wakeUpScreen()
            val orderId = data["orderId"]?.toLongOrNull()
            if (orderId != null) {
                // Передаємо тип далі, щоб знати яку Activity відкрити
                fetchOrderAndShowNotification(orderId, type)
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
            wl.acquire(10000)
        }
    }

    private fun fetchOrderAndShowNotification(orderId: Long, type: String) {
        if (SessionManager(this).fetchAuthToken() == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = ApiClient.getInstance().getApiService(applicationContext).getOrderById(orderId)
                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!

                    if (type == "ORDER_CONFIRMATION") {
                        showConfirmationNotification(order)
                    } else {
                        showOfferNotification(order)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- ЗВИЧАЙНА ПРОПОЗИЦІЯ (15 сек) ---
    private fun showOfferNotification(order: Order) {
        val intent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        showFullScreen(intent, "offer_channel", "Пропозиція замовлення", "Нове замовлення!", order.id.toInt())
    }

    // --- ПІДТВЕРДЖЕННЯ ПОПЕРЕДНЬОГО (60 сек) ---
    private fun showConfirmationNotification(order: Order) {
        val intent = Intent(this, OrderConfirmationActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // 1. СПРОБА ПРИМУСОВОГО ЗАПУСКУ
        // Якщо версія Android < 10 АБО надано дозвіл "Поверх інших вікон" -> відкриваємо одразу
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || android.provider.Settings.canDrawOverlays(this)) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("FCM", "Не вдалося запустити Activity напряму: ${e.message}")
            }
        }

        // 2. FullScreen Notification (працює завжди: і для звуку, і як фолбек)
        showFullScreen(intent, "confirm_channel", "Підтвердження замовлення", "Підтвердіть поїздку!", order.id.toInt() + 1000)
    }

    private fun showFullScreen(intent: Intent, channelId: String, channelName: String, title: String, notifId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(this, notifId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Натисніть для деталей")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM) // ALARM краще будить
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(60000)

        notificationManager.notify(notifId, builder.build())
    }
}