package com.taxiapp.driver

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import kotlinx.coroutines.launch

class WalletActivity : AppCompatActivity() {

    private lateinit var walletTabs: TabLayout
    private lateinit var tvBalanceLabel: TextView
    private lateinit var tvBalance: TextView
    private lateinit var btnTopUp: View
    private lateinit var layoutFundsButtons: View // Ссылка на контейнер кнопок
    private lateinit var adapter: WalletAdapter
    private lateinit var rvTransactions: RecyclerView

    private var commissionBalance: Double = 0.00
    private var orderEarningsBalance: Double = 0.00 // Зависит от данных с сервера

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        initViews()
        setupListeners()
        setupRecyclerView()

        loadData()
    }

    private fun initViews() {
        walletTabs = findViewById(R.id.wallet_tabs)
        tvBalanceLabel = findViewById(R.id.tv_balance_label)
        tvBalance = findViewById(R.id.tv_main_balance)
        btnTopUp = findViewById(R.id.btn_top_up)
        layoutFundsButtons = findViewById(R.id.layout_funds_buttons)
        rvTransactions = findViewById(R.id.rv_transactions)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnTopUp.setOnClickListener { showTopUpDialog() }

        walletTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUIState(tab?.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.btn_my_cards).setOnClickListener {
            // Переход на экран "Ваші картки"
            startActivity(Intent(this, CardsActivity::class.java))
        }
        findViewById<View>(R.id.btn_my_funds).setOnClickListener {
            // Переход на экран "Ваші кошти"
            startActivity(Intent(this, PendingFundsActivity::class.java))
        }
        findViewById<View>(R.id.btn_top_up).setOnClickListener {
            val intent = Intent(this, TopUpActivity::class.java)
            intent.putExtra("CURRENT_BALANCE", commissionBalance) // Передаем double баланса для красивого парсинга
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = WalletAdapter()
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    // --- УПРАВЛЕНИЕ ОТОБРАЖЕНИЕМ И ВИДИМОСТЬЮ БЛОКОВ (Исправление по примечанию) ---
    private fun updateUIState(isCommissionTab: Boolean) {
        if (isCommissionTab) {
            tvBalanceLabel.text = "Поточний баланс" // Текст с большой буквы, без капса
            btnTopUp.visibility = View.VISIBLE
            layoutFundsButtons.visibility = View.GONE // Кнопки Скрыты на вкладке Баланс
            renderBalance(commissionBalance)
        } else {
            tvBalanceLabel.text = "Гроші за замовлення" // Текст с большой буквы, без капса
            btnTopUp.visibility = View.GONE // Кнопка пополнения Скрыта на Операциях
            layoutFundsButtons.visibility = View.VISIBLE // Кнопки карт и выплат Доступны ТОЛЬКО тут
            renderBalance(orderEarningsBalance)
        }
    }

    private fun renderBalance(amount: Double) {
        tvBalance.text = "%.2f ₴".format(amount)
        if (amount < 0) {
            tvBalance.setTextColor(getColor(R.color.driver_error_red))
        } else {
            tvBalance.setTextColor(getColor(R.color.driver_neon_teal))
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
                val profileResp = ApiClient.getInstance().getApiService(this@WalletActivity).getDriverProfile()
                if (profileResp.isSuccessful && profileResp.body() != null) {
                    val profile = profileResp.body()!!
                    commissionBalance = profile.balance
                    orderEarningsBalance = profile.payoutBalance // Настоящий баланс выплат из БД бэкенда
                    updateUIState(walletTabs.selectedTabPosition == 0)
                }

                val txResp = ApiClient.getInstance().getApiService(this@WalletActivity).getWalletTransactions()
                if (txResp.isSuccessful && txResp.body() != null) {
                    val list = txResp.body()!!
                    if (list.isNotEmpty()) {
                        adapter.submitList(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}