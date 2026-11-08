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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var layoutFundsButtons: View
    private lateinit var adapter: WalletAdapter
    private lateinit var rvTransactions: RecyclerView

    private var commissionBalance: Double = 0.00
    private var orderEarningsBalance: Double = 0.00

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        val rootView = findViewById<View>(R.id.wallet_root) ?: findViewById(android.R.id.content)
        val headerContent = findViewById<View>(R.id.header_content)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerContent != null) {
                headerContent.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (28 * density).toInt(),
                    (16 * density).toInt(),
                    (12 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        initViews()
        setupListeners()
        setupRecyclerView()
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

        walletTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUIState(tab?.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.btn_my_cards).setOnClickListener {
            startActivity(Intent(this, CardsActivity::class.java))
        }
        findViewById<View>(R.id.btn_my_funds).setOnClickListener {
            startActivity(Intent(this, PendingFundsActivity::class.java))
        }
        findViewById<View>(R.id.btn_top_up).setOnClickListener {
            val intent = Intent(this, TopUpActivity::class.java)
            intent.putExtra("CURRENT_BALANCE", commissionBalance)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = WalletAdapter { orderId ->
            loadOrderDetailsAndOpen(orderId)
        }
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
    }

    private fun loadOrderDetailsAndOpen(orderId: Long) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Завантаження деталей замовлення...", Toast.LENGTH_SHORT).show()

                val response = ApiClient.getInstance()
                    .getApiService(this@WalletActivity)
                    .getOrderByInternalId(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    val intent = Intent(this@WalletActivity, HistoryDetailsActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", order)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@WalletActivity, "Не вдалося завантажити деталі замовлення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Помилка мережі при завантаженні замовлення", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        verifyPendingPaymentAndLoadData()
    }

    private fun verifyPendingPaymentAndLoadData() {
        lifecycleScope.launch {
            val session = com.taxiapp.driver.utils.SessionManager(this@WalletActivity)
            val pendingPaymentId = session.getPendingPaymentId()
            if (pendingPaymentId > 0L) {
                try {
                    val checkResp = ApiClient.getInstance().getApiService(this@WalletActivity).checkPaymentStatus(pendingPaymentId)
                    if (checkResp.isSuccessful) {
                        val status = checkResp.body()?.get("status")
                        if (status == "SUCCESS") {
                            session.clearPendingPaymentId()
                            Toast.makeText(this@WalletActivity, "Баланс успішно поповнено!", Toast.LENGTH_LONG).show()
                        } else if (status == "FAILED") {
                            session.clearPendingPaymentId()
                        }
                    }
                } catch (_: Exception) {}
            }
            loadData()
        }
    }

    private fun updateUIState(isCommissionTab: Boolean) {
        if (isCommissionTab) {
            tvBalanceLabel.text = "Поточний баланс"
            btnTopUp.visibility = View.VISIBLE
            layoutFundsButtons.visibility = View.GONE
            renderBalance(commissionBalance)
        } else {
            tvBalanceLabel.text = "Гроші за замовлення"
            btnTopUp.visibility = View.GONE
            layoutFundsButtons.visibility = View.VISIBLE
            renderBalance(orderEarningsBalance)
        }
    }

    private fun renderBalance(amount: Double) {
        val balanceText = "%.2f ₴".format(amount)
        val spannable = android.text.SpannableString(balanceText)

        val dotIndex = balanceText.indexOf(".")
        val commaIndex = balanceText.indexOf(",")
        val separatorIndex = if (dotIndex != -1) dotIndex else commaIndex

        if (separatorIndex != -1) {
            spannable.setSpan(
                android.text.style.RelativeSizeSpan(0.65f),
                separatorIndex,
                balanceText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvBalance.text = spannable

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
                    orderEarningsBalance = profile.payoutBalance
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