package com.taxiapp.driver

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDriverRequest
import kotlinx.coroutines.launch

class ChangeRnokppActivity : AppCompatActivity() {

    private lateinit var etRnokpp: EditText
    private lateinit var btnSave: TextView
    private var currentRnokpp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_rnokpp)

        currentRnokpp = intent.getStringExtra("CURRENT_RNOKPP")

        setupUI()
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        etRnokpp = findViewById(R.id.et_rnokpp)
        btnSave = findViewById(R.id.btn_save_rnokpp)

        // 1. Отображаем маскированный код (первые 2 и последние 2 видны)
        if (!currentRnokpp.isNullOrEmpty() && currentRnokpp!!.length == 10) {
            val masked = currentRnokpp!!.substring(0, 2) + "******" + currentRnokpp!!.substring(8, 10)
            etRnokpp.setText(masked)
        }

        // 2. Логика ввода
        etRnokpp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateInput(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Изначально проверяем кнопку
        validateInput(etRnokpp.text.toString())

        // 3. Сохранение
        btnSave.setOnClickListener {
            saveRnokpp(etRnokpp.text.toString())
        }
    }

    private fun validateInput(input: String) {
        // Кнопка активна, только если 10 цифр и нет звездочек (*)
        val isValid = input.length == 10 && input.all { it.isDigit() }

        btnSave.isEnabled = isValid
        if (isValid) {
            btnSave.setTextColor(getColor(R.color.white))
            btnSave.setBackgroundResource(R.drawable.bg_round_button) // Зеленый или активный фон
        } else {
            // ИСПРАВЛЕНО: используем существующий цвет
            btnSave.setTextColor(getColor(R.color.driver_text_secondary))
            btnSave.setBackgroundResource(R.drawable.bg_round_button_gray) // Серый фон
        }
    }

    private fun saveRnokpp(newRnokpp: String) {
        lifecycleScope.launch {
            try {
                val request = UpdateDriverRequest(rnokpp = newRnokpp)
                val response = ApiClient.getInstance().getApiService(this@ChangeRnokppActivity).updateRnokpp(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@ChangeRnokppActivity, "РНОКПП успішно збережено", Toast.LENGTH_SHORT).show()
                    finish() // Закрываем экран
                } else {
                    Toast.makeText(this@ChangeRnokppActivity, "Помилка: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChangeRnokppActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }
}