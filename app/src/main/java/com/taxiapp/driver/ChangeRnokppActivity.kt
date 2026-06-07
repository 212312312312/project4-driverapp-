package com.taxiapp.driver

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDriverRequest
import kotlinx.coroutines.launch

class ChangeRnokppActivity : AppCompatActivity() {

    private lateinit var etRnokpp: EditText
    private lateinit var btnSave: Button
    private lateinit var btnContainer: View
    private var currentRnokpp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_rnokpp)

        currentRnokpp = intent.getStringExtra("CURRENT_RNOKPP")

        initViews()
        setupUI()
        validateInput(etRnokpp.text.toString()) // Первичная проверка при старте
    }

    private fun initViews() {
        etRnokpp = findViewById(R.id.et_rnokpp)
        btnSave = findViewById(R.id.btn_save_rnokpp)
        btnContainer = findViewById(R.id.btn_container_layout)
    }

    private fun setupUI() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // 1. Отображаем маскированный код (первые 2 и последние 2 видны)
        if (!currentRnokpp.isNullOrEmpty() && currentRnokpp!!.length == 10) {
            val masked = currentRnokpp!!.substring(0, 2) + "******" + currentRnokpp!!.substring(8, 10)
            etRnokpp.setText(masked)
        }

        // 2. Логика ввода данных
        etRnokpp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateInput(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 3. Сохранение изменений
        btnSave.setOnClickListener {
            val inputCode = etRnokpp.text.toString().trim()

            // Если водитель ничего не менял и нажал сохранить на маске — просто закрываем экран
            if (inputCode.contains("*")) {
                finish()
            } else {
                saveRnokpp(inputCode)
            }
        }
    }

    // --- ИЗМЕНЕНО: Текст кнопки ВСЕГДА черный, валидация управляет только альфой контейнера ---
    private fun validateInput(input: String) {
        val cleaned = input.trim()

        // Валидно, если это 10 цифр ОЛИБО оригинальная маска со звездочками
        val isValid = (cleaned.length == 10 && cleaned.all { it.isDigit() }) ||
                (cleaned.length == 10 && cleaned.contains("*"))

        btnSave.isEnabled = isValid

        if (isValid) {
            btnContainer.alpha = 1.0f
        } else {
            btnContainer.alpha = 0.4f
        }
    }

    // --- ФИКС ОШИБКИ: Используем правильный UpdateDriverRequest из твоей сетевой модели ---
    private fun saveRnokpp(newRnokpp: String) {
        btnContainer.alpha = 0.4f
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = UpdateDriverRequest(rnokpp = newRnokpp)
                val response = ApiClient.getInstance().getApiService(this@ChangeRnokppActivity).updateRnokpp(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@ChangeRnokppActivity, "РНОКПП успішно збережено", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ChangeRnokppActivity, "Помилка: ${response.message()}", Toast.LENGTH_SHORT).show()
                    validateInput(etRnokpp.text.toString())
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChangeRnokppActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
                validateInput(etRnokpp.text.toString())
            }
        }
    }
}