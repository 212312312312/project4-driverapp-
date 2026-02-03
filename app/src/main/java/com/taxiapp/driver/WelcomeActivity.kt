package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.taxiapp.driver.databinding.ActivityWelcomeBinding
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // 1. ПРОВЕРКА СЕССИИ: Если уже вошел — сразу на карту
        if (sessionManager.isLoggedIn()) {
            goToMainActivity()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        // Кнопка "УВІЙТИ" -> Открывает экран логина
        binding.btnLoginNav.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Кнопка "ЗАРЕЄСТРУВАТИСЯ" -> Открывает WebView
        binding.btnRegisterNav.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            // Убедись, что URL правильный и сервер доступен с телефона
            // Мы убрали "/" перед driver-register, так как он останется от BASE_URL после удаления "api/v1/"
            intent.putExtra("url", "${BuildConfig.BASE_URL.replace("api/v1/", "")}driver-register")
            startActivity(intent)
        }
    }

    private fun goToMainActivity() {
        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}