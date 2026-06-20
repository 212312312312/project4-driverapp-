package com.taxiapp.driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.SendMessageRequest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private var orderId: String = "" // 👈 ИСПРАВЛЕНО: Переведено на String для поддержки UUID
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView

    private lateinit var layoutQuickPhrases: android.view.View
    private lateinit var btnBack: ImageView

    private val chatAdapter = ChatAdapter(mutableListOf())

    // Слухач, який чекає на сигнал від FirebaseService
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Як тільки прийшов сигнал - оновлюємо історію без повідомлень про помилку
            loadMessageHistory(silent = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        orderId = intent.getStringExtra("ORDER_ID") ?: "" // 👈 ИСПРАВЛЕНО: Извлекаем String UUID
        if (orderId.isEmpty()) {
            finish()
            return
        }

        initUI()
        loadMessageHistory()
    }

    private fun initUI() {
        rvChat = findViewById(R.id.rv_chat)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnBack = findViewById(R.id.btn_back)

        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        btnBack.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        layoutQuickPhrases = findViewById(R.id.layout_quick_phrases)

        findViewById<android.view.View>(R.id.btn_phrase_on_my_way).setOnClickListener {
            sendMessage("Вже у дорозі")
            layoutQuickPhrases.visibility = android.view.View.GONE
        }

        findViewById<android.view.View>(R.id.btn_phrase_where_waiting).setOnClickListener {
            sendMessage("Де Вас очікувати?")
            layoutQuickPhrases.visibility = android.view.View.GONE
        }

        findViewById<android.view.View>(R.id.btn_phrase_arrived).setOnClickListener {
            sendMessage("Я на місці")
            layoutQuickPhrases.visibility = android.view.View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        // Кажемо системі, що екран відкрито (глушимо пуші)
        ChatEventBus.isChatScreenOpen = true
    }

    override fun onResume() {
        super.onResume()
        // Щоразу, коли ми повертаємося в додаток (розгортаємо його),
        // автоматично і тихо підтягуємо свіжі повідомлення.
        loadMessageHistory(silent = true)
    }

    override fun onStop() {
        super.onStop()
        // Коли згортаємо додаток - дозволяємо Firebase показувати пуші
        ChatEventBus.isChatScreenOpen = false
    }

    private fun loadMessageHistory(silent: Boolean = false) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@ChatActivity)
                val response = api.getChatMessages(orderId)
                if (response.isSuccessful) {
                    response.body()?.let { newMessages ->
                        val isUpdated = chatAdapter.updateMessages(newMessages)

                        // Если в истории переписки уже есть хоть одно сообщение от DRIVER — скрываем блок шаблонов
                        if (newMessages.any { it.senderRole == "DRIVER" }) {
                            layoutQuickPhrases.visibility = android.view.View.GONE
                        }

                        if (isUpdated) {
                            scrollToBottom()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(this@ChatActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        etMessage.text.clear()

        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@ChatActivity)
                val response = api.sendChatMessage(orderId, SendMessageRequest(text))

                if (response.isSuccessful) {
                    response.body()?.let { msg ->
                        chatAdapter.addMessage(msg)
                        scrollToBottom()
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "Помилка відправки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}