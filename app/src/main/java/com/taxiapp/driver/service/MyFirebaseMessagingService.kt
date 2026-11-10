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
import com.taxiapp.driver.ChatEventBus
import com.taxiapp.driver.MainActivity
import com.taxiapp.driver.OrderConfirmationActivity
import com.taxiapp.driver.OrderOfferActivity
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val type = data["type"]
        Log.d("FCM_UNIT", "🔥 Push received. Type: $type, Data payload: $data")

        when (type) {
            "CHAT_MESSAGE" -> {
                ChatEventBus.tryTriggerUpdate()
                if (!ChatEventBus.isChatScreenOpen) {
                    showChatMessageNotification(data)
                }
            }
            "PHOTO_CONTROL_REQUEST",
            "PHOTO_CONTROL_APPROVED",
            "PHOTO_CONTROL_REJECTED",
            "PHOTO_CONTROL_EXPIRED" -> {
                wakeUpScreen()
                showPhotoControlNotification(data)
            }
            "ORDER_OFFER",
            "ORDER_CONFIRMATION" -> {
                wakeUpScreen()
                processOrderPushDirectly(data, type)
            }
            "ORDER_CANCEL" -> {
                wakeUpScreen()
                val orderUuid = data["orderId"]
                ChatEventBus.triggerOrderCancelled(orderUuid)
                val cancelBroadcast = Intent("com.taxiapp.driver.ORDER_CANCELLED").apply {
                    setPackage(packageName)
                    putExtra("CANCELLED_ORDER_UUID", orderUuid)
                    putExtra("EXTRA_ORDER_ID", orderUuid)
                }
                sendBroadcast(cancelBroadcast)

                try {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (orderUuid != null) {
                        nm.cancel(orderUuid.hashCode())
                    }
                } catch (_: Exception) {}

                showCancelNotification(data)
            }
        }
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = pm.isInteractive
            if (!isScreenOn) {
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "TaxiDriver:WakeUpLock"
                )
                wl.acquire(10000L)
            }
        } catch (e: Exception) {
            Log.e("FCM_UNIT", "Ошибка пробуждения экрана WakeLock: ${e.message}")
        }
    }

    private fun processOrderPushDirectly(data: Map<String, String>, type: String) {
        if (SessionManager(this).fetchAuthToken() == null) return

        val orderId = data["orderId"]
        val idLongStr = data["idLong"]

        if (orderId.isNullOrEmpty()) {
            Log.e("FCM_UNIT", "Помилка: orderId (UUID) відсутній у пуші!")
            return
        }

        try {
            val priceValue = data["price"]?.toDoubleOrNull() ?: 0.0
            val addressValue = data["address"] ?: "Адреса не вказана"
            val toAddressValue = data["toAddress"]
            val distanceKm = data["distance"]?.toDoubleOrNull() ?: 0.0
            val distanceMetersValue = data["distanceMeters"]?.toIntOrNull() ?: (distanceKm * 1000).toInt()
            val scheduledAtValue = data["time"]

            // ИСПРАВЛЕНО: Извлекаем полный набор полей заказа из обогащенного FCM-пуша
            val order = Order(
                id = orderId,
                idLong = idLongStr?.toLongOrNull(),
                price = priceValue,
                tariffName = data["tariffName"],
                fromAddress = addressValue,
                toAddress = toAddressValue,
                polyline = data["polyline"]?.takeIf { it.isNotBlank() },
                distanceMeters = distanceMetersValue,
                originLat = data["originLat"]?.toDoubleOrNull(),
                originLng = data["originLng"]?.toDoubleOrNull(),
                destLat = data["destLat"]?.toDoubleOrNull(),
                destLng = data["destLng"]?.toDoubleOrNull(),
                paymentMethod = data["paymentMethod"] ?: "CASH",
                fromSector = data["fromSector"]?.takeIf { it.isNotBlank() },
                toSector = data["toSector"]?.takeIf { it.isNotBlank() },
                comment = data["comment"]?.takeIf { it.isNotBlank() },
                scheduledAt = scheduledAtValue,
                status = if (type == "ORDER_CONFIRMATION") "SCHEDULED" else "OFFERING"
            )

            if (type == "ORDER_CONFIRMATION") {
                showConfirmationNotification(order)
            } else {
                showOfferNotification(order)
            }
        } catch (e: Exception) {
            Log.e("FCM_UNIT", "Критична помилка збирання Order: ${e.message}", e)
        }
    }

    private fun showChatMessageNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Нове повідомлення від клієнта"
        val body = data["body"] ?: data["content"] ?: "Відкрийте чат замовлення"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_message_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Чат з клієнтом",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            9002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(9002, builder.build())
    }

    private fun showPhotoControlNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Фотоконтроль"
        val body = data["body"] ?: "Оновлено статус фотоконтролю"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "photo_control_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Фотоконтроль", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(9001, builder.build())
    }

    private fun showOfferNotification(order: Order) {
        val intent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("FCM_UNIT", "Фоновый запуск через FullScreenIntent: ${e.message}")
        }

        val notifId = order.idLong?.toInt() ?: order.id.hashCode()
        showFullScreen(intent, "offer_fullscreen_channel_v5", "Пропозиція замовлення", "Нове замовлення!", notifId)
    }

    private fun showConfirmationNotification(order: Order) {
        val intent = Intent(this, OrderConfirmationActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("FCM_UNIT", "Фоновый запуск подтверждения через FullScreenIntent: ${e.message}")
        }

        val notifId = (order.idLong?.toInt() ?: order.id.hashCode()) + 1000
        showFullScreen(intent, "confirm_channel_v3", "Підтвердження замовлення", "Підтвердіть поїздку!", notifId)
    }

    private fun showFullScreen(intent: Intent, channelId: String, channelName: String, title: String, notifId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
            .setContentTitle(title)
            .setContentText("Натисніть для перегляду деталей замовлення")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setTimeoutAfter(20000L)

        notificationManager.notify(notifId, builder.build())
    }

    private fun showCancelNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Замовлення скасовано"
        val body = data["body"] ?: "Клієнт скасував замовлення"
        val orderUuid = data["orderId"]

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cancel_channel_v2"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Скасування замовлень", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CANCELLED_ORDER_UUID", orderUuid)
        }

        val notifId = orderUuid?.hashCode() ?: System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notifId, builder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        sessionManager.saveFcmToken(token)

        if (sessionManager.fetchAuthToken() != null) {
            serviceScope.launch {
                try {
                    ApiClient.getInstance().getApiService(applicationContext).updateFcmToken(mapOf("token" to token))
                } catch (e: Exception) {
                    Log.e("FCM_UNIT", "❌ Failed to update fcm token via onNewToken: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}