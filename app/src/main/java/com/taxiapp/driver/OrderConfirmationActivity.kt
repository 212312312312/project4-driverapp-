package com.taxiapp.driver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.AppCompatButton // ИСПРАВЛЕНО: Импортируем AppCompatButton вместо MaterialButton
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import kotlinx.coroutines.launch
import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager

class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var btnConfirm: AppCompatButton // ИСПРАВЛЕНО: тип изменен на AppCompatButton
    private lateinit var btnReject: AppCompatButton  // ИСПРАВЛЕНО: тип изменен на AppCompatButton
    private lateinit var timer: CountDownTimer
    private var currentOrder: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- БЛОК 1: Пробудження экрана и показ поверх блокировки ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        // -----------------------------------------------------------

        setContentView(R.layout.activity_order_confirmation)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ для сохранения Edge-to-Edge фона на Android 15
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ХАРДКОРНАЯ БЛОКИРОВКА КНОПКИ НАЗАД (БЕЗ ВОРНИНГОВ)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Оставляем пустым. Перехватывает нажатие и ничего не делает, блокируя шаг назад.
            }
        })

        // --- БЛОК 2: Получение данных заказа ---
        currentOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_ORDER", Order::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_ORDER") as? Order
        }

        if (currentOrder == null) {
            finish()
            return
        }

        initViews()
        startTimer()
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tv_timer)
        btnConfirm = findViewById(R.id.btn_confirm)
        btnReject = findViewById(R.id.btn_reject)

        findViewById<TextView>(R.id.tv_address).text = currentOrder?.fromAddress ?: "Адреса"
        findViewById<TextView>(R.id.tv_price).text = currentOrder?.getFormattedPrice() ?: "0 ₴"

        btnConfirm.setOnClickListener {
            confirmOrder()
        }

        btnReject.setOnClickListener {
            rejectOrder()
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                tvTimer.text = "0"
                rejectOrder(isTimeout = true)
            }
        }.start()
    }

    private fun confirmOrder() {
        timer.cancel()
        btnConfirm.isEnabled = false
        btnConfirm.text = "..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderConfirmationActivity)
                    .confirmOrder(currentOrder!!.id)

                if (response.isSuccessful) {
                    Toast.makeText(this@OrderConfirmationActivity, "Успішно підтверджено!", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@OrderConfirmationActivity, OrderProgressActivity::class.java)
                    intent.putExtra("EXTRA_ORDER", response.body())
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@OrderConfirmationActivity, "Помилка підтвердження", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrderConfirmationActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun rejectOrder(isTimeout: Boolean = false) {
        timer.cancel()
        btnReject.isEnabled = false
        btnReject.text = "..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@OrderConfirmationActivity)
                    .cancelOrder(currentOrder!!.id, null)

                if (response.isSuccessful) {
                    Toast.makeText(this@OrderConfirmationActivity, if(isTimeout) "Час вийшов. Замовлення знято." else "Ви відмовились від замовлення", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@OrderConfirmationActivity, "Замовлення скасовано локально", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Ignore network error on reject
            } finally {
                val intent = Intent(this@OrderConfirmationActivity, EtherActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::timer.isInitialized) timer.cancel()
    }

    override fun onBackPressed() {
        // Блокуємо кнопку назад
    }
}