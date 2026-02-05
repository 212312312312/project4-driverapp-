package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.taxiapp.driver.databinding.ActivityAccountSelectionBinding
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager

class AccountSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSelectionBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Отображаем сохраненный телефон
        val phone = sessionManager.getDriverPhone() ?: "Невідомий номер"
        binding.tvPhoneNumber.text = phone
    }

    private fun setupListeners() {
        // Кнопка ВОЙТИ - идет в систему
        binding.btnContinue.setOnClickListener {
            goToMainActivity()
        }

        // СМЕНИТЬ АККАУНТ - разлогин и переход на Welcome
        binding.btnSwitchAccount.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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