package com.taxiapp.driver

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch

class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var btnConfirm: AppCompatButton
    private lateinit var btnReject: AppCompatButton
    private var timer: CountDownTimer? = null
    private var currentOrder: Order? = null
    private var fallbackOrderId: String? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cancelledId = intent?.getStringExtra("EXTRA_ORDER_ID")
            val activeId = currentOrder?.id ?: fallbackOrderId
            if (cancelledId == null || activeId == null || cancelledId == activeId) {
                timer?.cancel()
                Toast.makeText(this@OrderConfirmationActivity, "Замовлення було скасовано клієнтом", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_order_confirmation)

        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Блокуємо випадкове закриття кнопкою Назад
            }
        })

        val filter = IntentFilter().apply {
            addAction("com.taxiapp.driver.ACTION_CLOSE_ORDER_OFFER")
            addAction("com.taxiapp.driver.ORDER_CANCELLED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cancelReceiver, filter)
        }

        initViews()

        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }
        fallbackOrderId = intent.getStringExtra("EXTRA_ORDER_ID") ?: currentOrder?.id

        if (currentOrder != null) {
            bindOrderDetails(currentOrder!!)
            startTimer()
        } else if (!fallbackOrderId.isNullOrEmpty()) {
            // ✅ ИСПРАВЛЕНО: Когда экран открывается из фонового FCM-пуша ORDER_CONFIRMATION,
            // передается только EXTRA_ORDER_ID. Подгружаем заказ с сервера вместо мгновенного finish()!
            loadOrderAndStart(fallbackOrderId!!)
        } else {
            finish()
        }
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tv_timer)
        btnConfirm = findViewById(R.id.btn_confirm)
        btnReject = findViewById(R.id.btn_reject)

        btnConfirm.setOnClickListener {
            confirmOrder()
        }

        btnReject.setOnClickListener {
            rejectOrder()
        }
    }

    private fun bindOrderDetails(order: Order) {
        findViewById<TextView>(R.id.tv_address_from).text = order.fromAddress ?: "Адреса подачі"
        findViewById<TextView>(R.id.tv_address_to).text = order.toAddress ?: "Кінцева точка"
        findViewById<TextView>(R.id.tv_price).text = "${order.getTotalFullPrice().toInt()} ₴"
        findViewById<TextView>(R.id.tv_scheduled_time).text = "На: ${order.getFormattedScheduledTime()}"
    }

    private fun loadOrderAndStart(orderId: String) {
        btnConfirm.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderConfirmationActivity).getActiveOrder()
                if (response.isSuccessful && response.body() != null) {
                    currentOrder = response.body()
                    bindOrderDetails(currentOrder!!)
                    btnConfirm.isEnabled = true
                    startTimer()
                } else {
                    // Если активный заказ не вернулся, пробуем отобразить данные из пуша и разрешить подтверждение по ID
                    findViewById<TextView>(R.id.tv_address_from).text = intent.getStringExtra("EXTRA_ADDRESS") ?: "Заплановане замовлення"
                    findViewById<TextView>(R.id.tv_price).text = "${intent.getStringExtra("EXTRA_PRICE") ?: ""} ₴"
                    btnConfirm.isEnabled = true
                    startTimer()
                }
            } catch (e: Exception) {
                btnConfirm.isEnabled = true
                startTimer()
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = String.format("00:%02d", seconds)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                rejectOrder(isTimeout = true)
            }
        }.start()
    }

    private fun confirmOrder() {
        val targetId = currentOrder?.id ?: fallbackOrderId ?: return
        timer?.cancel()
        btnConfirm.isEnabled = false
        btnReject.isEnabled = false
        btnConfirm.text = "Підтвердження..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderConfirmationActivity)
                    .confirmOrder(targetId)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@OrderConfirmationActivity, "Замовлення підтверджено! Виїжджайте.", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@OrderConfirmationActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", response.body())
                    intent.putExtra("EXTRA_ORDER_ID", targetId)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@OrderConfirmationActivity, "Помилка підтвердження: ${response.code()}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderConfirmationActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
                btnReject.isEnabled = true
                btnConfirm.text = "ПІДТВЕРДИТИ"
            }
        }
    }

    private fun rejectOrder(isTimeout: Boolean = false) {
        val targetId = currentOrder?.id ?: fallbackOrderId
        timer?.cancel()
        btnConfirm.isEnabled = false
        btnReject.isEnabled = false
        btnReject.text = "..."

        lifecycleScope.launch {
            if (!targetId.isNullOrEmpty()) {
                try {
                    val response = ApiClient.getInstance().getApiService(this@OrderConfirmationActivity)
                        .cancelOrder(targetId, null)

                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@OrderConfirmationActivity,
                            if (isTimeout) "Час вийшов. Замовлення знято." else "Ви відмовились від замовлення",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (_: Exception) {}
            }
            val intent = Intent(this@OrderConfirmationActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        try {
            unregisterReceiver(cancelReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}