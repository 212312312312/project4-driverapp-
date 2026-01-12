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
            Toast.makeText(this, "Функція в розробці: Запит на видалення", Toast.LENGTH_SHORT).show()
        }

        // Кліки по полях
        findViewById<android.view.View>(R.id.btn_edit_phone).setOnClickListener {
            Toast.makeText(this, "Зміна телефону через диспетчера", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btn_edit_ipn).setOnClickListener {
            Toast.makeText(this, "Редагування РНОКПП (Скоро)", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btn_disability_status).setOnClickListener {
            Toast.makeText(this, "Статус інвалідності (Скоро)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfileData() {
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvPhone = findViewById<TextView>(R.id.tv_profile_phone)
        val imgAvatar = findViewById<ImageView>(R.id.img_profile_avatar)

        // Поля, яких ще немає на сервері (заглушки)
        val tvEmail = findViewById<TextView>(R.id.tv_profile_email)
        val tvIpn = findViewById<TextView>(R.id.tv_profile_ipn)
        val tvLicense = findViewById<TextView>(R.id.tv_profile_license)

        // 1. З кешу (ТІЛЬКИ ІМ'Я)
        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvName.text = extractFirstName(savedName)
        } else {
            tvName.text = "Водій"
        }

        // 2. Запит на сервер (Оновлення даних)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ProfileActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    val fullName = profile.fullName ?: "Водій"
                    // Зберігаємо повне ім'я в кеш (для майбутнього)
                    sessionManager.saveDriverName(fullName)

                    // А показуємо ТІЛЬКИ ІМ'Я (Конфіденційність)
                    tvName.text = extractFirstName(fullName)

                    tvPhone.text = profile.phoneNumber ?: "Не вказано"

                    // Фото
                    if (!profile.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(profile.photoUrl)
                            .placeholder(R.drawable.ic_driver_avatar_placeholder)
                            .error(R.drawable.ic_driver_avatar_placeholder)
                            .circleCrop()
                            .into(imgAvatar)
                    }

                    // --- Заглушки для нових полів ---
                    tvEmail.text = "driver@example.com"
                    tvIpn.text = "Не вказано"
                    tvLicense.text = "Не вказано"

                } else {
                    Toast.makeText(this@ProfileActivity, "Не вдалося оновити дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Метод для витягування імені (Такий самий, як у MainActivity)
    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())

        // Припускаємо формат "Прізвище Ім'я По-батькові" -> беремо "Ім'я" (індекс 1)
        // Якщо формат "Ім'я" -> беремо "Ім'я" (індекс 0)
        return when {
            parts.size >= 2 -> parts[1]
            parts.isNotEmpty() -> parts[0]
            else -> "Водій"
        }
    }
}