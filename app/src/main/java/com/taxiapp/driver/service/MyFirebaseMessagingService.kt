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
import com.taxiapp.driver.OrderConfirmationActivity
import com.taxiapp.driver.OrderOfferActivity
import com.taxiapp.driver.R
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val type = data["type"]
        Log.d("FCM_UNIT", "🔥 Push received. Type: $type, Data payload: $data")

        if (type == "CHAT_MESSAGE") {
            CoroutineScope(Dispatchers.IO).launch {
                ChatEventBus.triggerUpdate()
            }
            return
        }

        if (type == "ORDER_OFFER" || type == "ORDER_CONFIRMATION") {
            // 1. Сразу будим экран физически (актуально для заблокированных устройств)
            wakeUpScreen()

            // 2. Мгновенно собираем объект заказа из пуша без единого сетевого запроса!
            processOrderPushDirectly(data, type)
        }
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = if (Build.VERSION.SDK_INT >= 20) pm.isInteractive else pm.isScreenOn
            if (!isScreenOn) {
                val wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "TaxiDriver:WakeUpLock"
                )
                wl.acquire(10000) // Держим экран активным 10 секунд
            }
        } catch (e: Exception) {
            Log.e("FCM_UNIT", "Ошибка пробуждения экрана WakeLock: ${e.message}")
        }
    }

    // --- ГЕНИАЛЬНЫЙ ОБХОД ЗАМОРОЗКИ СЕТИ: СБОРКА ОБЪЕКТА ИЗ DATA PAYLOAD ---
    private fun processOrderPushDirectly(data: Map<String, String>, type: String) {
        // Проверяем сессию: если водитель разлогинен, игнорируем
        if (SessionManager(this).fetchAuthToken() == null) return

        val orderId = data["orderId"]
        if (orderId.isNullOrEmpty()) {
            Log.e("FCM_UNIT", "Помилка: orderId отсутствует в пуше!")
            return
        }

        try {
            // Извлекаем и парсим все отправленные сервером данные по контракту
            val priceValue = data["price"]?.toDoubleOrNull() ?: 0.0
            val addressValue = data["address"] ?: "Адреса не вказана"

            // Сервер шлет дистанцию в километрах как строку (например "5.5").
            // Класс Order на клиенте ждет дистанцию в МЕТРАХ (distanceMeters: Int). Переводим.
            val distanceKm = data["distance"]?.toDoubleOrNull() ?: 0.0
            val distanceMetersValue = (distanceKm * 1000).toInt()

            // Для предварительных заказов шлется "time"
            val scheduledAtValue = data["time"]

            // Собираем полноценный объект Order на лету (Offline-first)
            val order = Order(
                id = orderId,
                idLong = orderId.toLongOrNull(),
                price = priceValue,
                fromAddress = addressValue,
                distanceMeters = distanceMetersValue,
                scheduledAt = scheduledAtValue,
                status = if (type == "ORDER_CONFIRMATION") "SCHEDULED" else "PENDING"
            )

            Log.d("FCM_UNIT", "✅ Объект Order успешно собран локально: ID=${order.id}, Price=${order.price}")

            // Направляем объект в нужное русло
            if (type == "ORDER_CONFIRMATION") {
                showConfirmationNotification(order)
            } else {
                showOfferNotification(order)
            }

        } catch (e: Exception) {
            Log.e("FCM_UNIT", "Критическая ошибка сборки Order из пуша: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showOfferNotification(order: Order) {
        val intent = Intent(this, OrderOfferActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Прямой фоновый запуск Activity (работает мгновенно, если включены всплывающие окна)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || android.provider.Settings.canDrawOverlays(this)) {
            try {
                startActivity(intent)
                Log.d("FCM_UNIT", "🚀 OrderOfferActivity успешно запущена напрямую из фона!")
            } catch (e: Exception) {
                Log.e("FCM_UNIT", "Не удалось запустить OrderOfferActivity напрямую, сработает fullScreenIntent: ${e.message}")
            }
        }

        // Полноэкранный системный интент (Heads-Up баннер + разворот на Lockscreen)
        showFullScreen(intent, "offer_channel_v3", "Пропозиція замовлення", "Нове замовлення!", order.idLong?.toInt() ?: 0)
    }

    private fun showConfirmationNotification(order: Order) {
        val intent = Intent(this, OrderConfirmationActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || android.provider.Settings.canDrawOverlays(this)) {
            try {
                startActivity(intent)
                Log.d("FCM_UNIT", "🚀 OrderConfirmationActivity успешно запущена напрямую из фона!")
            } catch (e: Exception) {
                Log.e("FCM_UNIT", "Не удалось запустить OrderConfirmationActivity напрямую: ${e.message}")
            }
        }

        showFullScreen(intent, "confirm_channel_v3", "Підтвердження замовлення", "Підтвердіть поїздку!", (order.idLong?.toInt() ?: 0) + 1000)
    }

    private fun showFullScreen(intent: Intent, channelId: String, channelName: String, title: String, notifId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Пересоздаем каналы с суффиксом _v3, чтобы Android жестко выставил наивысший приоритет
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_RINGTONE_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
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
            .setContentText("Натисніть для деталей")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Категория ALARM пробивает режимы тишины
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true) // Пробивает Lockscreen и выводит баннер
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(20000) // Автоотмена через 20 секунд (время жизни оффера)

        notificationManager.notify(notifId, builder.build())
        Log.d("FCM_UNIT", "🔔 Системное FullScreen-уведомление отправлено в менеджер. ID: $notifId")
    }
}