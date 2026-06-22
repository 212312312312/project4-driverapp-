package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import kotlinx.coroutines.launch

class PendingFundsActivity : AppCompatActivity() {

    private lateinit var rvPending: RecyclerView
    private lateinit var adapter: WalletAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_funds)

        findViewById<View>(R.id.btn_back_pending).setOnClickListener { finish() }

        rvPending = findViewById(R.id.rv_pending_transactions)
        rvPending.layoutManager = LinearLayoutManager(this)
        adapter = WalletAdapter { orderId ->
            loadOrderDetailsAndOpen(orderId) // 👈 Передаем обработчик клика по аналогии с основным кошельком
        }
        rvPending.adapter = adapter

        loadPendingTransactions()
    }

    private fun loadOrderDetailsAndOpen(orderId: Long) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@PendingFundsActivity, "Завантаження деталей замовлення...", Toast.LENGTH_SHORT).show()

                val response = ApiClient.getInstance()
                    .getApiService(this@PendingFundsActivity)
                    .getOrderByInternalId(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!

                    val intent = android.content.Intent(this@PendingFundsActivity, HistoryDetailsActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", order)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@PendingFundsActivity, "Не вдалося завантажити деталі замовлення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFundsActivity, "Помилка мережі при завантаженні замовлення", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPendingTransactions() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@PendingFundsActivity).getPendingWalletTransactions()
                if (response.isSuccessful && response.body() != null) {
                    val pendingList = response.body()!!
                    adapter.submitList(pendingList)
                    if (pendingList.isEmpty()) {
                        Toast.makeText(this@PendingFundsActivity, "Немає операцій в черзі", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFundsActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
            }
        }
    }
}