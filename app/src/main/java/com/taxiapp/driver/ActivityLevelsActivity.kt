package com.taxiapp.driver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ActivityLevelsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_levels_info)

        // 1. Кнопка "Назад"
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 2. Логика отображения "Ви тут"
        // Получаем баллы из Intent (если не передали, считаем 0)
        val currentPoints = intent.getIntExtra(EXTRA_POINTS, 0)
        updateLevelBadges(currentPoints)
    }

    private fun updateLevelBadges(points: Int) {
        val badgeGreen = findViewById<View>(R.id.badgeGreen)
        val badgeYellow = findViewById<View>(R.id.badgeYellow)
        val badgeRed = findViewById<View>(R.id.badgeRed)

        // Сначала скрываем все бейджи
        badgeGreen?.visibility = View.GONE
        badgeYellow?.visibility = View.GONE
        badgeRed?.visibility = View.GONE

        // Показываем нужный в зависимости от диапазона
        when {
            points > 700 -> {
                badgeGreen?.visibility = View.VISIBLE
            }
            points > 400 -> {
                badgeYellow?.visibility = View.VISIBLE
            }
            else -> {
                // От 0 до 400 (или меньше 0)
                badgeRed?.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val EXTRA_POINTS = "extra_points"

        // Удобный метод для запуска Activity
        fun start(context: Context, currentPoints: Int) {
            val intent = Intent(context, ActivityLevelsActivity::class.java)
            intent.putExtra(EXTRA_POINTS, currentPoints)
            context.startActivity(intent)
        }
    }
}