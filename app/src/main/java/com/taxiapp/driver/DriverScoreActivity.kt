package com.taxiapp.driver

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverActivityDto
import kotlinx.coroutines.launch

class DriverScoreActivity : AppCompatActivity() {

    private lateinit var ivCarMarker: ImageView
    private lateinit var tvScoreValue: TextView
    private lateinit var tvLevelTitle: TextView
    private lateinit var tvLevelDesc: TextView
    private lateinit var viewScoreGlow: View
    private lateinit var nestedScrollView: NestedScrollView

    private var currentScore: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_score)

        initViews()
        loadData()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {
        ivCarMarker = findViewById(R.id.ivCarMarker)
        tvScoreValue = findViewById(R.id.tvScoreValue)
        tvLevelTitle = findViewById(R.id.tvLevelTitle)
        tvLevelDesc = findViewById(R.id.tvLevelDesc)
        viewScoreGlow = findViewById(R.id.viewScoreGlow)
        nestedScrollView = findViewById(R.id.nestedScrollView)

        // БЛОКИРОВКА СКРОЛЛА: экран сидит намертво и не двигается пальцем во избежание багов
        nestedScrollView.setOnTouchListener { _, event ->
            event.action == MotionEvent.ACTION_MOVE
        }

        // Кнопка Назад
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // История активности
        findViewById<View>(R.id.btnOpenHistory).setOnClickListener {
            startActivity(Intent(this, DriverActivityHistoryActivity::class.java))
        }

        // Кнопка 1: Уровни активности
        findViewById<View>(R.id.btnInfoLevels).setOnClickListener {
            ActivityLevelsActivity.start(this, currentScore)
        }

        // Кнопка 2: Расчет баллов
        findViewById<View>(R.id.btnInfoCalculation).setOnClickListener {
            startActivity(Intent(this, ActivityCalculationInfo::class.java))
        }

        // Кнопка 3: Распределение
        findViewById<View>(R.id.btnInfoDistribution).setOnClickListener {
            startActivity(Intent(this, ActivityDistributionInfo::class.java))
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
                e.printStackTrace()
            }
        }
    }

    private fun updateUI(data: DriverActivityDto) {
        currentScore = data.score

        // 1. Обновляем число
        tvScoreValue.text = currentScore.toString()

        // 2. Рассчитываем координаты машинки
        val safeScore = currentScore.coerceIn(0, 1000)
        val params = ivCarMarker.layoutParams as ConstraintLayout.LayoutParams
        params.horizontalBias = safeScore / 1000f
        ivCarMarker.layoutParams = params

        val color: Int
        val title: String
        val desc: String

        // Подставляем цвета и тексты под уровень активности водителя
        when (data.level) {
            "GREEN", "HIGH" -> {
                color = Color.parseColor("#33CCA1") // Фирменная бирюза
                title = "Високий рівень"
                desc = "Чудова робота! Вам доступні всі методы пошуку замовлень."
            }
            "YELLOW", "MEDIUM" -> {
                color = Color.parseColor("#FBC02D") // Янтарно-желтый
                title = "Середній рівень"
                desc = "Увага! Деякі фільтри (Авто, Цикл) заблоковані."
            }
            "RED", "LOW" -> {
                color = Color.parseColor("#ff7373") // Красный ошибки
                title = "Низький рівень"
                desc = "Критично! Блокування близько. Доступні лише Ефір та Сектори."
            }
            else -> {
                color = Color.BLACK
                title = "ЗАБЛОКОВАНО"
                desc = "Ваш акаунт заблоковано через низьку активність."
            }
        }

        // Перекрашиваем машинку-маркер
        ivCarMarker.imageTintList = ColorStateList.valueOf(color)

        // 3. ДИНАМИЧЕСКОЕ СВЕЧЕНИЕ: Аура автоматически принимает цвет текущей зоны!
        val density = resources.displayMetrics.density
        val radiusPx = 100 * density

        val dynamicGlow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientRadius(radiusPx)

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            // 20% прозрачности в центре, 0% на краях для мягкого рассеивания
            val startColor = Color.argb(51, r, g, b)
            val endColor = Color.argb(0, r, g, b)

            colors = intArrayOf(startColor, endColor)
        }
        viewScoreGlow.background = dynamicGlow

        // Обновляем текстовые блоки под треком шкал
        tvLevelTitle.text = title
        tvLevelTitle.setTextColor(color)
        tvLevelDesc.text = desc
    }
}