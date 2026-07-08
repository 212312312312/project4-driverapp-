package com.taxiapp.driver

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDisabilityRequest
import kotlinx.coroutines.launch

class DisabilityStatusActivity : AppCompatActivity() {

    // Чекбоксы (MaterialCardView) и внутренние галочки (ImageView)
    private lateinit var cardCheckboxMovement: MaterialCardView
    private lateinit var imgCheckMovement: ImageView

    private lateinit var cardCheckboxHearing: MaterialCardView
    private lateinit var imgCheckHearing: ImageView

    private lateinit var cardCheckboxDeaf: MaterialCardView
    private lateinit var imgCheckDeaf: ImageView

    private lateinit var cardCheckboxSpeech: MaterialCardView
    private lateinit var imgCheckSpeech: ImageView

    private lateinit var btnSave: Button

    // Локальные логические стейты выбранных опций
    private var isMovementChecked = false
    private var isHearingChecked = false
    private var isDeafChecked = false
    private var isSpeechChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disability_status)

        // 🛠️ ИСПРАВЛЕНО: Находим реальный корень твоей XML-разметки для сохранения Edge-to-Edge фона
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        loadCurrentStatus()
    }

    private fun setupUI() {
        cardCheckboxMovement = findViewById(R.id.card_checkbox_movement)
        imgCheckMovement = findViewById(R.id.img_check_movement)

        cardCheckboxHearing = findViewById(R.id.card_checkbox_hearing)
        imgCheckHearing = findViewById(R.id.img_check_hearing)

        cardCheckboxDeaf = findViewById(R.id.card_checkbox_deaf)
        imgCheckDeaf = findViewById(R.id.img_check_deaf)

        cardCheckboxSpeech = findViewById(R.id.card_checkbox_speech)
        imgCheckSpeech = findViewById(R.id.img_check_speech)

        btnSave = findViewById(R.id.btn_save)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // --- ЛОГИКА ТАПА ПО СТРОКАМ С ПРЕМИУМ-АНИМАЦИЕЙ ---

        findViewById<MaterialCardView>(R.id.row_movement).setOnClickListener {
            isMovementChecked = !isMovementChecked
            setCheckboxVisualState(cardCheckboxMovement, imgCheckMovement, isMovementChecked, animate = true)
        }

        findViewById<MaterialCardView>(R.id.row_hearing).setOnClickListener {
            isHearingChecked = !isHearingChecked
            setCheckboxVisualState(cardCheckboxHearing, imgCheckHearing, isHearingChecked, animate = true)
        }

        findViewById<MaterialCardView>(R.id.row_deaf).setOnClickListener {
            isDeafChecked = !isDeafChecked
            setCheckboxVisualState(cardCheckboxDeaf, imgCheckDeaf, isDeafChecked, animate = true)
        }

        findViewById<MaterialCardView>(R.id.row_speech).setOnClickListener {
            isSpeechChecked = !isSpeechChecked
            setCheckboxVisualState(cardCheckboxSpeech, imgCheckSpeech, isSpeechChecked, animate = true)
        }

        btnSave.setOnClickListener {
            saveStatus()
        }
    }

    /**
     * МАГИЯ UI-ДИЗАЙНА: Метод плавного анимирования кастомного квадратного чекбокса
     */
    private fun setCheckboxVisualState(card: MaterialCardView, checkImg: ImageView, isChecked: Boolean, animate: Boolean) {
        val targetColor = ContextCompat.getColor(this, if (isChecked) R.color.driver_neon_teal else R.color.driver_text_secondary)

        if (animate) {
            // 1. Анимация плавного перетекания цвета рамки (Морфинг цвета)
            val startColor = card.strokeColor
            android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), startColor, targetColor).apply {
                duration = 250 // Скорость изменения цвета бордера
                addUpdateListener { animator ->
                    card.strokeColor = animator.animatedValue as Int
                }
                start()
            }

            // 2. Премиальная анимация галочки
            if (isChecked) {
                // Сбрасываем и готовим галочку к прыжку из центра
                checkImg.alpha = 0f
                checkImg.scaleX = 0.3f
                checkImg.scaleY = 0.3f
                checkImg.visibility = android.view.View.VISIBLE

                // Эффект пружины (Spring Pop)
                checkImg.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.8f)) // Коэффициент упругости отскока
                    .setListener(null)
                    .start()
            } else {
                // Плавное сжатие и исчезновение галочки внутрь
                checkImg.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        checkImg.visibility = android.view.View.GONE
                    }
                    .start()
            }
        } else {
            // Мгновенная установка состояний без анимации (для первой загрузки экрана)
            card.strokeColor = targetColor
            checkImg.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            checkImg.alpha = 1f
            checkImg.scaleX = 1f
            checkImg.scaleY = 1f
        }
    }

    private fun loadCurrentStatus() {
        val btnContainer = findViewById<android.view.View>(R.id.btn_container_layout)
        btnSave.isEnabled = false
        btnContainer?.alpha = 0.4f
        btnSave.text = "Завантаження..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DisabilityStatusActivity).getDriverProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    isMovementChecked = profile.hasMovementIssue ?: false
                    isHearingChecked = profile.hasHearingIssue ?: false
                    isDeafChecked = profile.isDeaf ?: false
                    isSpeechChecked = profile.hasSpeechIssue ?: false

                    // Загружаем стейты мгновенно (animate = false), чтобы водитель не ждал отрисовки
                    setCheckboxVisualState(cardCheckboxMovement, imgCheckMovement, isMovementChecked, animate = false)
                    setCheckboxVisualState(cardCheckboxHearing, imgCheckHearing, isHearingChecked, animate = false)
                    setCheckboxVisualState(cardCheckboxDeaf, imgCheckDeaf, isDeafChecked, animate = false)
                    setCheckboxVisualState(cardCheckboxSpeech, imgCheckSpeech, isSpeechChecked, animate = false)
                } else {
                    Toast.makeText(this@DisabilityStatusActivity, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DisabilityStatusActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnContainer?.alpha = 1.0f
                btnSave.text = "Зберегти"
            }
        }
    }

    private fun saveStatus() {
        val btnContainer = findViewById<android.view.View>(R.id.btn_container_layout)
        btnSave.isEnabled = false
        btnContainer?.alpha = 0.4f
        btnSave.text = "Збереження..."

        val request = UpdateDisabilityRequest(
            hasMovementIssue = isMovementChecked,
            hasHearingIssue = isHearingChecked,
            isDeaf = isDeafChecked,
            hasSpeechIssue = isSpeechChecked
        )

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DisabilityStatusActivity)
                    .updateDisabilityStatus(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@DisabilityStatusActivity, "Дані успешно оновлено!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@DisabilityStatusActivity, "Помилка збереження: ${response.code()}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                    btnContainer?.alpha = 1.0f
                    btnSave.text = "Зберегти"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DisabilityStatusActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnContainer?.alpha = 1.0f
                btnSave.text = "Зберегти"
            }
        }
    }
}