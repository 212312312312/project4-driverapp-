package com.taxiapp.driver

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.UpdateDisabilityRequest
import kotlinx.coroutines.launch

class DisabilityStatusActivity : AppCompatActivity() {

    private lateinit var switchMovement: Switch
    private lateinit var switchHearing: Switch
    private lateinit var switchDeaf: Switch
    private lateinit var switchSpeech: Switch
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disability_status)

        setupUI()
        loadCurrentStatus()
    }

    private fun setupUI() {
        switchMovement = findViewById(R.id.switch_movement)
        switchHearing = findViewById(R.id.switch_hearing)
        switchDeaf = findViewById(R.id.switch_deaf)
        switchSpeech = findViewById(R.id.switch_speech)
        btnSave = findViewById(R.id.btn_save)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveStatus()
        }
    }

    private fun loadCurrentStatus() {
        // Блокируем кнопку пока грузим данные
        btnSave.isEnabled = false
        btnSave.text = "Завантаження..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DisabilityStatusActivity).getDriverProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    // Устанавливаем переключатели в соответствии с данными с сервера
                    switchMovement.isChecked = profile.hasMovementIssue
                    switchHearing.isChecked = profile.hasHearingIssue
                    switchDeaf.isChecked = profile.isDeaf
                    switchSpeech.isChecked = profile.hasSpeechIssue
                } else {
                    Toast.makeText(this@DisabilityStatusActivity, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DisabilityStatusActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnSave.text = "Зберегти"
            }
        }
    }

    private fun saveStatus() {
        btnSave.isEnabled = false
        btnSave.text = "Збереження..."

        val request = UpdateDisabilityRequest(
            hasMovementIssue = switchMovement.isChecked,
            hasHearingIssue = switchHearing.isChecked,
            isDeaf = switchDeaf.isChecked,
            hasSpeechIssue = switchSpeech.isChecked
        )

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DisabilityStatusActivity)
                    .updateDisabilityStatus(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@DisabilityStatusActivity, "Дані успішно оновлено!", Toast.LENGTH_LONG).show()
                    finish() // Закрываем экран после успешного сохранения
                } else {
                    Toast.makeText(this@DisabilityStatusActivity, "Помилка збереження: ${response.code()}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Зберегти"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DisabilityStatusActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Зберегти"
            }
        }
    }
}