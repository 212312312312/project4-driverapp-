package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ActivityHistoryItemDto
import com.taxiapp.driver.network.ApiClient
import kotlinx.coroutines.launch

class DriverActivityHistoryActivity : AppCompatActivity() {

    companion object {
        // HIGH-LOAD КЕШ ІСТОРІЇ АКТИВНОСТІ (TTL 60 сек)
        private const val ACTIVITY_CACHE_TTL_MS = 60_000L

        @Volatile private var cachedHistory: List<ActivityHistoryItemDto>? = null
        @Volatile private var lastFetchTimeMs: Long = 0L

        fun invalidateCache() {
            lastFetchTimeMs = 0L
        }
    }

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var adapter: HistoryActivityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_history)

        val rootView = findViewById<View>(R.id.driver_history_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_history_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
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
        adapter = HistoryActivityAdapter(cachedHistory ?: emptyList()) { uuid ->
            loadOrderDetailsAndOpen(uuid)
        }
        recyclerHistory.adapter = adapter
    }

    private fun loadOrderDetailsAndOpen(uuid: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance()
                    .getApiService(this@DriverActivityHistoryActivity)
                    .getOrderById(uuid)

                val order = response.body()
                if (response.isSuccessful && order != null) {
                    val intent = Intent(this@DriverActivityHistoryActivity, HistoryDetailsActivity::class.java).apply {
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

    private fun loadHistory(forceNetwork: Boolean = false) {
        val now = System.currentTimeMillis()
        val cached = cachedHistory
        if (!forceNetwork && cached != null && (now - lastFetchTimeMs) < ACTIVITY_CACHE_TTL_MS) {
            adapter.updateData(cached)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DriverActivityHistoryActivity).getDriverActivity()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    cachedHistory = body.history
                    lastFetchTimeMs = System.currentTimeMillis()
                    adapter.updateData(body.history)
                } else if (cached == null) {
                    Toast.makeText(this@DriverActivityHistoryActivity, "Не вдалося завантажити історію", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (cached == null) {
                    Toast.makeText(this@DriverActivityHistoryActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}