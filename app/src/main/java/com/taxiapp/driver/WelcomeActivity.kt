package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.taxiapp.driver.databinding.ActivityWelcomeBinding
import com.taxiapp.driver.utils.SessionManager

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // 1. ПРОВЕРКА СЕССИИ: Если уже вошел — на экран аккаунта
        if (sessionManager.isLoggedIn()) {
            goToAccountSelectionActivity()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLoginNav.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnRegisterNav.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            // Убираем api/v1/ для веб-ссылок, если они не через API
            intent.putExtra("url", "${BuildConfig.BASE_URL.replace("api/v1/", "")}driver-register")
            startActivity(intent)
        }
    }

    private fun goToAccountSelectionActivity() {
        val intent = Intent(this, AccountSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}