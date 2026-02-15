package com.taxiapp.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WalletActivity : AppCompatActivity() {

    private lateinit var tvBalance: TextView
    private lateinit var adapter: WalletAdapter

    // Храним ID последней попытки оплаты, чтобы проверить статус по возвращении
    private var pendingPaymentId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tvBalance = findViewById(R.id.tv_main_balance)
        val rvTransactions = findViewById<RecyclerView>(R.id.rv_transactions)

        adapter = WalletAdapter()
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_top_up).setOnClickListener {
            showTopUpDialog()
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Если у нас висит незавершенная оплата, предложим проверить её
        if (pendingPaymentId != null) {
            showCheckStatusDialog()
        } else {
            loadData()
        }
    }

    private fun showTopUpDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "100"

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 50
        params.rightMargin = 50
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.wallet_btn_top_up))
            .setMessage("Введіть суму (мін 1 грн):")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val amountStr = input.text.toString()
                if (amountStr.isNotEmpty()) {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount >= 1.0) {
                        initiatePayment(amount)
                    } else {
                        Toast.makeText(this, "Некоректна сума", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun initiatePayment(amount: Double) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Створення платежу...", Toast.LENGTH_SHORT).show()

                val request = InitPaymentRequest(amount)
                val response = ApiClient.getInstance().getApiService(this@WalletActivity).initPayment(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val paymentUrl = body.paymentUrl
                    pendingPaymentId = body.paymentId

                    // Открываем браузер
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                    startActivity(browserIntent)
                } else {
                    // --- ОБНОВЛЕННАЯ ОБРАБОТКА ОШИБКИ ---
                    val errorBody = response.errorBody()?.string() ?: "Невідома помилка"
                    val code = response.code()
                    Log.e("WalletActivity", "Payment Error: $code - $errorBody") // Смотри в Logcat (внизу Android Studio)
                    Toast.makeText(this@WalletActivity, "Помилка сервера: $code", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("WalletActivity", "Network Error", e)
                Toast.makeText(this@WalletActivity, "Помилка мережі: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCheckStatusDialog() {
        AlertDialog.Builder(this)
            .setTitle("Перевірка оплати")
            .setMessage("Ви успішно здійснили оплату в LiqPay?")
            .setPositiveButton("Так, перевірити") { _, _ ->
                checkPaymentStatus()
            }
            .setNegativeButton("Ні / Пізніше") { _, _ ->
                // Очищаем ID, чтобы диалог не вылезал постоянно
                // pendingPaymentId = null // Можно оставить, если хотим принуждать
                loadData()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPaymentStatus() {
        val id = pendingPaymentId ?: return

        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Перевірка...", Toast.LENGTH_SHORT).show()
                val response = ApiClient.getInstance().getApiService(this@WalletActivity).checkPaymentStatus(id)

                if (response.isSuccessful && response.body() != null) {
                    val status = response.body()!!["status"]
                    val message = response.body()!!["message"]

                    if (status == "SUCCESS") {
                        Toast.makeText(this@WalletActivity, "✅ $message", Toast.LENGTH_LONG).show()
                        pendingPaymentId = null // Оплата прошла, забываем ID
                        loadData() // Обновляем баланс
                    } else if (status == "FAILED") {
                        Toast.makeText(this@WalletActivity, "❌ $message", Toast.LENGTH_LONG).show()
                        pendingPaymentId = null
                    } else {
                        Toast.makeText(this@WalletActivity, "⏳ $message", Toast.LENGTH_SHORT).show()
                        // ID не очищаем, может еще пройдет время
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Не вдалося перевірити статус", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                // 1. Профиль (баланс)
                val profileResp = ApiClient.getInstance().getApiService(this@WalletActivity).getDriverProfile()
                if (profileResp.isSuccessful && profileResp.body() != null) {
                    val balance = profileResp.body()!!.balance
                    tvBalance.text = "%.2f ₴".format(balance)
                }

                // 2. История
                val txResp = ApiClient.getInstance().getApiService(this@WalletActivity).getWalletTransactions()
                if (txResp.isSuccessful && txResp.body() != null) {
                    adapter.submitList(txResp.body())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}