package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.taxiapp.driver.databinding.ActivityLoginBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.ui.login.LoginViewModel
import com.taxiapp.driver.ui.login.LoginViewModelFactory
import com.taxiapp.driver.utils.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    // SessionManager здесь нужен только для ViewModel, проверку входа делаем в Welcome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Кнопка "Назад" в левом верхнем углу (если захочешь добавить стрелочку в XML)
        binding.btnBack?.setOnClickListener { finish() }

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
                goToMainActivity()
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
            if (phone.isNotBlank() && password.isNotBlank()) {
                viewModel.login(phone, password)
            } else {
                Toast.makeText(this, "Заповніть поля", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToMainActivity() {
        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val intent = Intent(this, MainActivity::class.java)
        // Очищаем стек, чтобы по кнопке "Назад" не вернуться в логин
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}