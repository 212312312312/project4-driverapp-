package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.SendMessageRequest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private var orderId: String = ""
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var layoutQuickPhrases: View
    private lateinit var btnBack: ImageView

    private val chatAdapter = ChatAdapter(mutableListOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ✅ ИСПРАВЛЕНО: Безопасное извлечение ORDER_ID как String (UUID) с фолбеком на Long
        orderId = intent.getStringExtra("ORDER_ID")
            ?: intent.getLongExtra("ORDER_ID", -1L).takeIf { it != -1L }?.toString()
                    ?: ""

        if (orderId.isEmpty()) {
            Toast.makeText(this, "Помилка: невірний ID замовлення", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initUI()
        observeRealTimeChatUpdates()
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

        findViewById<View>(R.id.btn_phrase_on_my_way).setOnClickListener {
            sendMessage("Вже у дорозі")
            layoutQuickPhrases.visibility = View.GONE
        }

        findViewById<View>(R.id.btn_phrase_where_waiting).setOnClickListener {
            sendMessage("Де Вас очікувати?")
            layoutQuickPhrases.visibility = View.GONE
        }

        findViewById<View>(R.id.btn_phrase_arrived).setOnClickListener {
            sendMessage("Я на місці")
            layoutQuickPhrases.visibility = View.GONE
        }
    }

    // ✅ ИСПРАВЛЕНО: Реальная подписка на пуши чата и отмены заказа через ChatEventBus
    private fun observeRealTimeChatUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ChatEventBus.newMessages.collect {
                        loadMessageHistory(silent = true)
                    }
                }
                launch {
                    ChatEventBus.cancelledOrders.collect { cancelledUuid ->
                        if (cancelledUuid == null || cancelledUuid == orderId) {
                            Toast.makeText(this@ChatActivity, "Замовлення було скасовано клієнтом", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatEventBus.isChatScreenOpen = true
    }

    override fun onResume() {
        super.onResume()
        loadMessageHistory(silent = true)
    }

    override fun onStop() {
        super.onStop()
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

                        if (newMessages.any { it.senderRole == "DRIVER" }) {
                            layoutQuickPhrases.visibility = View.GONE
                        }

                        if (isUpdated) {
                            scrollToBottom()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(this@ChatActivity, "Помилка завантаження чату", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        etMessage.text.clear()
        btnSend.isEnabled = false

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
                    etMessage.setText(text)
                    Toast.makeText(this@ChatActivity, "Помилка відправки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                etMessage.setText(text)
                Toast.makeText(this@ChatActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                btnSend.isEnabled = true
            }
        }
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}