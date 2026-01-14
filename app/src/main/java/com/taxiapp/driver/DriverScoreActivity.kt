package com.taxiapp.driver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverActivityDto
import kotlinx.coroutines.launch

class DriverScoreActivity : AppCompatActivity() {

    private lateinit var progressBarScore: ProgressBar
    private lateinit var tvScoreValue: TextView
    private lateinit var tvLevelTitle: TextView
    private lateinit var tvLevelDesc: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_score)

        initViews()
        loadData()
    }

    private fun initViews() {
        progressBarScore = findViewById(R.id.progressBarScore)
        tvScoreValue = findViewById(R.id.tvScoreValue)
        tvLevelTitle = findViewById(R.id.tvLevelTitle)
        tvLevelDesc = findViewById(R.id.tvLevelDesc)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Кнопка відкриття історії (ЗНИЗУ)
        findViewById<TextView>(R.id.btnOpenHistory).setOnClickListener {
            startActivity(Intent(this, DriverActivityHistoryActivity::class.java))
        }

        // Інформаційні кнопки (Поки що просто Тости, пізніше зробиш діалоги або екрани)
        findViewById<LinearLayout>(R.id.btnInfoLevels).setOnClickListener {
            Toast.makeText(this, "Тут буде інфо про рівні", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.btnInfoCalculation).setOnClickListener {
            Toast.makeText(this, "Тут буде інфо про нарахування", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.btnInfoDistribution).setOnClickListener {
            Toast.makeText(this, "Тут буде інфо про розподіл", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DriverScoreActivity).getDriverActivity()
                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                } else {
                    Toast.makeText(this@DriverScoreActivity, "Не вдалося отримати дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DriverScoreActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(data: DriverActivityDto) {
        val score = data.score
        tvScoreValue.text = score.toString()
        progressBarScore.progress = score

        val color: Int
        val title: String
        val desc: String

        when (data.level) {
            "GREEN" -> {
                color = Color.parseColor("#4CAF50")
                title = "Високий рівень"
                desc = "Чудова робота! Вам доступні всі методи пошуку замовлень."
            }
            "YELLOW" -> {
                color = Color.parseColor("#FFC107")
                title = "Середній рівень"
                desc = "Увага! Деякі фільтри (Авто, Цикл) заблоковані."
            }
            "RED" -> {
                color = Color.parseColor("#F44336")
                title = "Низький рівень"
                desc = "Критично! Блокування близько. Доступні лише Ефір та Сектори."
            }
            else -> {
                color = Color.BLACK
                title = "ЗАБЛОКОВАНО"
                desc = "Ваш акаунт заблоковано через низьку активність."
            }
        }

        progressBarScore.progressTintList = ColorStateList.valueOf(color)
        tvLevelTitle.text = title
        tvLevelTitle.setTextColor(color)
        tvLevelDesc.text = desc
    }
}