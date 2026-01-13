package com.taxiapp.driver

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)

        setupUI()
        loadProfileData()
    }

    private fun setupUI() {
        // Кнопка Назад
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Кнопка Видалення акаунту
        findViewById<TextView>(R.id.btn_delete_account).setOnClickListener {
            Toast.makeText(this, "Щоб видалити акаунт, зверніться до диспетчера", Toast.LENGTH_LONG).show()
        }

        // Кліки по полях (інформативні повідомлення)
        findViewById<android.view.View>(R.id.btn_edit_phone).setOnClickListener {
            Toast.makeText(this, "Зміна телефону через диспетчера", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btn_edit_ipn).setOnClickListener {
            // Можна показувати повний номер у діалозі, якщо він обрізаний, або просто повідомлення
            Toast.makeText(this, "Зміна РНОКПП через диспетчера", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btn_disability_status).setOnClickListener {
            Toast.makeText(this, "Статус інвалідності (Скоро)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfileData() {
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvPhone = findViewById<TextView>(R.id.tv_profile_phone)
        val imgAvatar = findViewById<ImageView>(R.id.img_profile_avatar)

        val tvEmail = findViewById<TextView>(R.id.tv_profile_email)
        val tvIpn = findViewById<TextView>(R.id.tv_profile_ipn)
        val tvLicense = findViewById<TextView>(R.id.tv_profile_license)

        // 1. Спочатку показуємо ім'я з кешу (щоб не було порожньо)
        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvName.text = extractFirstName(savedName)
        } else {
            tvName.text = "Водій"
        }

        // 2. Запит на сервер (Оновлення даних)
        lifecycleScope.launch {
            try {
                // Виконуємо запит
                val response = ApiClient.getInstance().getApiService(this@ProfileActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    // --- ОНОВЛЕННЯ ДАНИХ UI ---

                    val fullName = profile.fullName ?: "Водій"
                    sessionManager.saveDriverName(fullName) // Оновлюємо кеш
                    tvName.text = extractFirstName(fullName)

                    tvPhone.text = profile.phoneNumber ?: "Не вказано"

                    // НОВІ ПОЛЯ (тепер реальні дані)
                    tvEmail.text = profile.email ?: "Не вказано"
                    tvIpn.text = profile.rnokpp ?: "Не вказано"
                    tvLicense.text = profile.driverLicense ?: "Не вказано"

                    // Фото
                    if (!profile.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(profile.photoUrl)
                            .placeholder(R.drawable.ic_driver_avatar_placeholder)
                            .error(R.drawable.ic_driver_avatar_placeholder)
                            .circleCrop()
                            .into(imgAvatar)
                    }

                } else {
                    // Якщо токен протух або помилка сервера
                    Toast.makeText(this@ProfileActivity, "Не вдалося завантажити профіль", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ProfileActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Метод для витягування імені
    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> parts[1] // Якщо "Прізвище Ім'я", беремо Ім'я
            parts.isNotEmpty() -> parts[0]
            else -> "Водій"
        }
    }
}