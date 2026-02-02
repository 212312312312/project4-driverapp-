package com.taxiapp.driver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
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

    // Зберігаємо поточні бали, щоб передати їх на екран інфо
    private var currentScore: Int = 0

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

        // Кнопка Назад
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Кнопка відкриття історії (ЗНИЗУ)
        findViewById<View>(R.id.btnOpenHistory).setOnClickListener {
            startActivity(Intent(this, DriverActivityHistoryActivity::class.java))
        }

        // --- Кнопка 1: Рівні активності (ТЕПЕР ПРАЦЮЄ) ---
        findViewById<View>(R.id.btnInfoLevels).setOnClickListener {
            // Використовуємо метод start із ActivityLevelsActivity, передаючи поточні бали
            ActivityLevelsActivity.start(this, currentScore)
        }

        // Кнопка 2: Нарахування (Заглушка)
        findViewById<View>(R.id.btnInfoCalculation).setOnClickListener {
            startActivity(Intent(this, ActivityCalculationInfo::class.java))
        }

        // Кнопка 3: Розподіл (Заглушка)
        findViewById<View>(R.id.btnInfoDistribution).setOnClickListener {
            startActivity(Intent(this, ActivityDistributionInfo::class.java))
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                // Отримуємо дані з сервера
                val response = ApiClient.getInstance().getApiService(this@DriverScoreActivity).getDriverActivity()

                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                } else {
                    Toast.makeText(this@DriverScoreActivity, "Не вдалося отримати дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DriverScoreActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun updateUI(data: DriverActivityDto) {
        // 1. Зберігаємо бали у змінну класу
        currentScore = data.score

        // 2. Оновлюємо UI
        tvScoreValue.text = currentScore.toString()
        progressBarScore.progress = currentScore

        val color: Int
        val title: String
        val desc: String

        // Логіка кольорів та текстів (відповідає твоїй структурі)
        when (data.level) {
            "GREEN", "HIGH" -> { // Додав про всяк випадок варіант "HIGH", якщо сервер поверне інше
                color = Color.parseColor("#4CAF50") // Краще винести в colors.xml, але залишив як було
                title = "Високий рівень"
                desc = "Чудова робота! Вам доступні всі методи пошуку замовлень."
            }
            "YELLOW", "MEDIUM" -> {
                color = Color.parseColor("#FFC107")
                title = "Середній рівень"
                desc = "Увага! Деякі фільтри (Авто, Цикл) заблоковані."
            }
            "RED", "LOW" -> {
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