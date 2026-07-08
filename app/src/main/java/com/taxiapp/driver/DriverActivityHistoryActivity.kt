package com.taxiapp.driver

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import kotlinx.coroutines.launch

class DriverActivityHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var adapter: HistoryActivityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_history)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ для сохранения Edge-to-Edge фона на Android 15
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerHistory = findViewById(R.id.recyclerHistory)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryActivityAdapter(emptyList()) { uuid ->
            loadOrderDetailsAndOpen(uuid) // 👈 Вызов метода загрузки заказа
        }
        recyclerHistory.adapter = adapter
    }
    private fun loadOrderDetailsAndOpen(uuid: String) {
        lifecycleScope.launch {
            try {
                // Делаем сетевой запрос к твоему API для получения полного объекта заказа
                val response = ApiClient.getInstance()
                    .getApiService(this@DriverActivityHistoryActivity)
                    .getOrderById(uuid)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    // Бесшовно открываем экран деталей архивного заказа
                    val intent = android.content.Intent(this@DriverActivityHistoryActivity, HistoryDetailsActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", order)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@DriverActivityHistoryActivity, "Не вдалося завантажити деталі замовлення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DriverActivityHistoryActivity, "Помилка мережі при завантаженні замовлення", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                // Використовуємо той самий endpoint, сервер повертає і бали, і історію
                val response = ApiClient.getInstance().getApiService(this@DriverActivityHistoryActivity).getDriverActivity()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    adapter.updateData(data.history)
                } else {
                    Toast.makeText(this@DriverActivityHistoryActivity, "Не вдалося завантажити історію", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DriverActivityHistoryActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }
}