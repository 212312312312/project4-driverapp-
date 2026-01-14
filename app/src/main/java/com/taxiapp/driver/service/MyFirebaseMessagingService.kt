package com.taxiapp.driver.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.taxiapp.driver.OrderOfferActivity
import com.taxiapp.driver.network.ApiClient
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
                fetchOrderAndOpenScreen(orderId)
            }
        }
    }

    private fun fetchOrderAndOpenScreen(orderId: Long) {
        val sessionManager = SessionManager(this)
        // Если водитель не залогинен, игнорируем пуш
        if (sessionManager.fetchAuthToken() == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // ИСПРАВЛЕНИЕ: getOrderById (с большой I), а не getOrderByld
                val response = ApiClient.getInstance().getApiService(applicationContext).getOrderById(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!

                    val intent = Intent(applicationContext, OrderOfferActivity::class.java)
                    // Теперь это сработает, так как Order : Serializable
                    intent.putExtra("EXTRA_ORDER", order)

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}