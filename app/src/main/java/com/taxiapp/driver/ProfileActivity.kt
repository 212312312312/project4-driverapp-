package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
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

    // Сохраняем данные, чтобы передать их в экраны редактирования
    private var currentRnokpp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)

        setupUI()
        loadProfileData()
    }

    override fun onResume() {
        super.onResume()
        // Перезагружаем данные при возвращении (например, после смены РНОКПП или телефона)
        loadProfileData()
    }

    private fun setupUI() {
        // ИСПРАВЛЕНО: Теперь ищем как ImageView, чтобы соответствовать новому XML хедера
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // АДАПТИРОВАНО: Тип изменен на MaterialButton (или универсальный View) под новую верстку
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_account).setOnClickListener {
            val intent = Intent(this, DeleteAccountActivity::class.java)
            startActivity(intent)
        }

        // --- ОСТАЛЬНАЯ ИНТЕРАКТИВНАЯ ЛОГИКА ---

        // 1. Смена телефона
        findViewById<android.view.View>(R.id.btn_edit_phone).setOnClickListener {
            val intent = Intent(this, ChangePhoneActivity::class.java)
            startActivity(intent)
        }

        // 2. Смена РНОКПП
        findViewById<android.view.View>(R.id.btn_edit_ipn).setOnClickListener {
            val intent = Intent(this, ChangeRnokppActivity::class.java)
            // Передаем текущий (возможно маскированный или полный) ИПН, если он есть
            intent.putExtra("CURRENT_RNOKPP", currentRnokpp)
            startActivity(intent)
        }

        // 3. Статус инвалидности
        findViewById<android.view.View>(R.id.btn_disability_status).setOnClickListener {
            val intent = Intent(this, DisabilityStatusActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadProfileData() {
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvPhone = findViewById<TextView>(R.id.tv_profile_phone)
        val imgAvatar = findViewById<ImageView>(R.id.img_profile_avatar)

        val tvEmail = findViewById<TextView>(R.id.tv_profile_email)
        val tvIpn = findViewById<TextView>(R.id.tv_profile_ipn)
        val tvLicense = findViewById<TextView>(R.id.tv_profile_license)

        // Предзагрузка из кэша для быстрого отображения
        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvName.text = extractFirstName(savedName)
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ProfileActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    val fullName = profile.fullName ?: "Водій"
                    sessionManager.saveDriverName(fullName)
                    tvName.text = extractFirstName(fullName)

                    tvPhone.text = profile.phoneNumber ?: "Не вказано"
                    tvEmail.text = profile.email ?: "Не вказано"

                    // Сохраняем реальный РНОКПП в переменную для Intent
                    currentRnokpp = profile.rnokpp

                    // Маскируем в UI
                    if (!profile.rnokpp.isNullOrEmpty() && profile.rnokpp.length == 10) {
                        tvIpn.text = profile.rnokpp.substring(0, 2) + "******" + profile.rnokpp.substring(8, 10)
                    } else {
                        tvIpn.text = "Додати"
                    }

                    tvLicense.text = profile.driverLicense ?: "Не вказано"

                    if (!profile.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(profile.photoUrl)
                            .placeholder(R.drawable.ic_driver_avatar_placeholder)
                            .error(R.drawable.ic_driver_avatar_placeholder)
                            .circleCrop()
                            .into(imgAvatar)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> parts[1]
            parts.isNotEmpty() -> parts[0]
            else -> "Водій"
        }
    }

    private fun requestDeletion() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ProfileActivity).requestAccountDeletion()
                if (response.isSuccessful) {
                    Toast.makeText(this@ProfileActivity, "Акаунт в черзі на видалення", Toast.LENGTH_LONG).show()

                    // Очищаем сессию (токены и т.д.)
                    sessionManager.saveAuthToken("")
                    sessionManager.saveDriverId(-1L)

                    // Выбрасываем на экран логина
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@ProfileActivity, "Помилка при видаленні", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }
}