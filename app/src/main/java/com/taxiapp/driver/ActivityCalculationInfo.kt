package com.taxiapp.driver

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ActivityCalculationInfo : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculation_info)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Заполняем Блок 1: Базовые
        val containerBasic = findViewById<LinearLayout>(R.id.containerBasicPoints)
        addPointRow(containerBasic, "Ланцюг замовлень", "+6", "#4CAF50")
        addPointRow(containerBasic, "Додому", "+6", "#4CAF50")
        addPointRow(containerBasic, "Цикл", "+5", "#4CAF50")
        addPointRow(containerBasic, "Авто (Призначення)", "+4", "#4CAF50")
        addPointRow(containerBasic, "Ефір (Загальний список)", "+3", "#4CAF50")

        // Заполняем Блок 2: Бонусы
        val containerBonus = findViewById<LinearLayout>(R.id.containerBonusPoints)
        addPointRow(containerBonus, "За місто", "+3", "#FFC107")
        addPointRow(containerBonus, "Із проміжними точками", "+3", "#FFC107")
        addPointRow(containerBonus, "Додаткове замовлення", "+3", "#FFC107")
        addPointRow(containerBonus, "Оплата на баланс", "+1", "#FFC107")

        // Заполняем Блок 3: Штрафы
        val containerPenalty = findViewById<LinearLayout>(R.id.containerPenaltyPoints)
        addPointRow(containerPenalty, "Скасоване (Після прийняття)", "-50", "#F44336")
        addPointRow(containerPenalty, "Скасоване заплановане (Підтв.)", "-50", "#F44336")
        addPointRow(containerPenalty, "Скасоване заплановане", "-30", "#F44336")
        addPointRow(containerPenalty, "Неприйняте (Відмова)", "-30", "#F44336")
    }

    private fun addPointRow(container: LinearLayout, label: String, points: String, colorHex: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 12, 0, 12) // Отступы сверху и снизу строки

        // Название
        val tvLabel = TextView(this)
        tvLabel.text = label
        tvLabel.textSize = 16f
        tvLabel.setTextColor(ContextCompat.getColor(this, R.color.driver_text_primary)) // Используем белый цвет текста
        val paramsLabel = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tvLabel.layoutParams = paramsLabel

        // Баллы
        val tvPoints = TextView(this)
        tvPoints.text = points
        tvPoints.textSize = 16f
        tvPoints.setTypeface(null, android.graphics.Typeface.BOLD)
        tvPoints.setTextColor(Color.parseColor(colorHex))
        tvPoints.gravity = Gravity.END

        row.addView(tvLabel)
        row.addView(tvPoints)
        container.addView(row)
    }
}