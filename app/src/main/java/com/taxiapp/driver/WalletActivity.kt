package com.taxiapp.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import kotlinx.coroutines.launch

class WalletActivity : AppCompatActivity() {

    private lateinit var tvBalance: TextView
    private lateinit var adapter: WalletAdapter
    private lateinit var rvTransactions: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tvBalance = findViewById(R.id.tv_main_balance)
        rvTransactions = findViewById(R.id.rv_transactions)

        // Настраиваем список
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
        loadData()
    }

    // ... (Методы showTopUpDialog и initiatePayment оставляем как были в прошлом шаге) ...
    // Вставь их сюда, если нужно, я сократил для удобства чтения.
    private fun showTopUpDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "100"
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        params.leftMargin = 50; params.rightMargin = 50
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.wallet_btn_top_up))
            .setMessage("Введіть суму (мін 1 грн):")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount >= 1.0) initiatePayment(amount)
                else Toast.makeText(this, "Некоректна сума", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun initiatePayment(amount: Double) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Створення платежу...", Toast.LENGTH_SHORT).show()
                val response = ApiClient.getInstance().getApiService(this@WalletActivity).initPayment(InitPaymentRequest(amount))
                if (response.isSuccessful && response.body() != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(response.body()!!.paymentUrl))
                    startActivity(browserIntent)
                } else {
                    Toast.makeText(this@WalletActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                // 1. Загружаем баланс
                val profileResp = ApiClient.getInstance().getApiService(this@WalletActivity).getDriverProfile()
                if (profileResp.isSuccessful && profileResp.body() != null) {
                    val balance = profileResp.body()!!.balance
                    tvBalance.text = "%.2f ₴".format(balance)

                    // Меняем цвет баланса (Красный если < 0, Зеленый/Тиловый если >= 0)
                    if (balance < 0) {
                        tvBalance.setTextColor(getColor(R.color.driver_error_red))
                    } else {
                        tvBalance.setTextColor(getColor(R.color.driver_neon_teal))
                    }
                }

                // 2. Загружаем историю транзакций
                val txResp = ApiClient.getInstance().getApiService(this@WalletActivity).getWalletTransactions()
                if (txResp.isSuccessful && txResp.body() != null) {
                    val list = txResp.body()!!

                    if (list.isEmpty()) {
                        // Можно показать текст "История пуста" (если есть такой TextView в layout)
                    } else {
                        adapter.submitList(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Не показываем ошибку пользователю, если просто нет инета, чтобы не раздражать
            }
        }
    }
}