package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.taxiapp.driver.databinding.ActivityLoginBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.ui.login.LoginViewModel
import com.taxiapp.driver.ui.login.LoginViewModelFactory
import com.taxiapp.driver.utils.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация ViewBinding
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
        // Следим за состоянием загрузки (ProgressBar из XML)
        viewModel.isLoading.observe(this) { isLoading ->
            // id: progress_bar -> binding.progressBar
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // id: btn_login -> binding.btnLogin
            binding.btnLogin.isEnabled = !isLoading

            // Если идет загрузка, можно менять текст кнопки
            binding.btnLogin.text = if (isLoading) "ВХІД..." else "УВІЙТИ"
        }

        // Следим за результатом
        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                // Успешный вход -> идем на главную
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
        // id: btn_login -> binding.btnLogin
        binding.btnLogin.setOnClickListener {
            // id: et_phone -> binding.etPhone
            val phone = binding.etPhone.text.toString()

            // id: et_password -> binding.etPassword
            val password = binding.etPassword.text.toString()

            // Передаем данные во ViewModel
            viewModel.login(phone, password)
        }
    }
}