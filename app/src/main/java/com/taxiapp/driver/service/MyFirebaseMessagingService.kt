package com.taxiapp.driver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.taxiapp.driver.MainActivity
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

        if (type == "ORDER_CANCEL") {
            // Пробуждаем экран, если заблокирован
            wakeUpScreen()
            // Вызываем метод отображения уведомления в шторке/баннере
            showCancelNotification(data)
            return
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
        if (SessionManager(this).fetchAuthToken() == null) return

        val orderId = data["orderId"] // Тепер сюди прилітає чистий UUID (наприклад: 8eb7a78c-...)
        val idLongStr = data["idLong"] // Числовий ID (наприклад: 617)

        if (orderId.isNullOrEmpty()) {
            Log.e("FCM_UNIT", "Помилка: orderId (UUID) відсутній у пуші!")
            return
        }

        try {
            val priceValue = data["price"]?.toDoubleOrNull() ?: 0.0
            val addressValue = data["address"] ?: "Адреса не вказана"

            val distanceKm = data["distance"]?.toDoubleOrNull() ?: 0.0
            val distanceMetersValue = (distanceKm * 1000).toInt()
            val scheduledAtValue = data["time"]

            // 🔥 ВИПРАВЛЕНО: id отримує чистий UUID, а idLong отримує числовий Long
            val order = Order(
                id = orderId, // Передаємо строковий UUID для Retrofit-запитів
                idLong = idLongStr?.toLongOrNull(), // Передаємо число для ID нотифікацій
                price = priceValue,
                fromAddress = addressValue,
                distanceMeters = distanceMetersValue,
                scheduledAt = scheduledAtValue,
                status = if (type == "ORDER_CONFIRMATION") "SCHEDULED" else "PENDING"
            )

            Log.d("FCM_UNIT", "✅ Об'єкт Order успішно зібрано з UUID: ID=${order.id}, idLong=${order.idLong}")

            if (type == "ORDER_CONFIRMATION") {
                showConfirmationNotification(order)
            } else {
                showOfferNotification(order)
            }

        } catch (e: Exception) {
            Log.e("FCM_UNIT", "Критична помилка збирання Order: ${e.message}")
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
        // Было: showFullScreen(intent, "offer_channel_v3", ...)
// Стало: передаем новый ID тихого канала
        showFullScreen(intent, "offer_silent_channel_v4", "Пропозиція замовлення", "Нове замовлення!", order.idLong?.toInt() ?: 0)
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
        // ЗАМЕНИТЬ БЛОК СОЗДАНИЯ КАНАЛА В МЕТОДЕ showCancelNotification:

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Скасування замовлень", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, // Поменяли рингтон на короткое уведомление
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION) // Меняем USAGE_NOTIFICATION_RINGTONE на обычный USAGE_NOTIFICATION
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

    // ДОБАВИТЬ КАК НОВЫЙ МЕТОД В КЛАСС MyFirebaseMessagingService:

    private fun showCancelNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Замовлення скасовано"
        val body = data["body"] ?: "Клієнт скасував замовлення"
        val orderUuid = data["orderId"]

        // ЗАМЕНИТЬ КУСОК В НАЧАЛЕ МЕТОДА showCancelNotification НА ЭТОТ:

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cancel_channel_v2" // Обязательно сменили ID на v2, чтобы сбросить кэш старого рингтона в телефоне

// Создаем чистый канал высокой важности без принудительных URI звуков
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Скасування замовлень", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // Никаких setSound! Система сама выдаст дефолтный короткий писк шторки
            }
            notificationManager.createNotificationChannel(channel)
        }

        // При клике на уведомление плавно переводим на MainActivity и чистим стек старых экранов
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CANCELLED_ORDER_UUID", orderUuid)
        }

        // Генерируем уникальный ID на основе хэша UUID, чтобы уведомления не затирали друг друга
        val notifId = orderUuid?.hashCode() ?: System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Твоя иконка приложения
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Пробивает фоновый режим
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notifId, builder.build())
        Log.d("FCM_UNIT", "🔔 Системное уведомление об отмене успешно выведено! ID: $notifId")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_UNIT", "🔄 Google generated a new FCM token: $token")

        val sessionManager = SessionManager(applicationContext)
        sessionManager.saveFcmToken(token)

        // Если водитель уже вошел в аккаунт, сразу обновляем токен в базе данных бэкенда
        if (sessionManager.fetchAuthToken() != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    ApiClient.getInstance().getApiService(applicationContext).updateFcmToken(mapOf("token" to token))
                    Log.d("FCM_UNIT", "✅ Automatically updated new FCM token on server via onNewToken.")
                } catch (e: Exception) {
                    Log.e("FCM_UNIT", "❌ Failed to update fcm token via onNewToken: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}