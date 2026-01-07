package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Добавлено для запуска сервиса
import androidx.lifecycle.ViewModelProvider
import com.taxiapp.driver.databinding.ActivityLoginBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.service.LocationService // Импорт твоего сервиса
import com.taxiapp.driver.ui.login.LoginViewModel
import com.taxiapp.driver.ui.login.LoginViewModelFactory
import com.taxiapp.driver.utils.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupObservers()
        setupListeners()
    }

    private fun setupViewModel() {
        val apiService = ApiClient.getInstance().getApiService(this)
        val sessionManager = SessionManager(this)
        val factory = LoginViewModelFactory(apiService, sessionManager)
        viewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
            binding.btnLogin.text = if (isLoading) "ВХІД..." else "УВІЙТИ"
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                // --- ГЛАВНОЕ ИЗМЕНЕНИЕ ---
                // Запускаем сервис сразу после логина, чтобы водитель стал "Серым" на карте
                val serviceIntent = Intent(this, LocationService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                // -------------------------

                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            result.onFailure { error ->
                Toast.makeText(this, error.message ?: "Помилка входу", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val phone = binding.etPhone.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(phone, password)
        }
    }
}