package com.taxiapp.driver

import android.content.Intent
import android.os.Build
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
        // ВИПРАВЛЕНО: Показуємо тільки номер телефону, як раніше.
        val phone = sessionManager.getDriverPhone() ?: "Невідомий номер"
        binding.tvPhoneNumber.text = phone
    }

    private fun setupListeners() {
        // Кнопка ВОЙТИ (ПРОДОВЖИТИ) - повертає на карту
        binding.btnContinue.setOnClickListener {
            goToMainActivity()
        }

        // ЗМІНИТИ АКАУНТ - повний розлогін і перехід на Welcome
        binding.btnSwitchAccount.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun goToMainActivity() {
        // Перезапускаємо сервіс локації, щоб гарантувати його роботу
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Забороняємо повернення назад системною кнопкою "Back",
    // щоб водій не міг повернутися на попередній екран "виходу".
    // Замість закриття - згортаємо додаток.
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}