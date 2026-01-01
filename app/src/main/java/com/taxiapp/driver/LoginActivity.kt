package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.LoginRequest
import com.taxiapp.driver.network.LoginResponse
import com.taxiapp.driver.utils.SessionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Якщо вже є токен - зразу йдемо на головний екран
        if (sessionManager.fetchAuthToken() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Знаходимо елементи по правильних ID з нового XML
        val etPhone = findViewById<EditText>(R.id.et_phone) // Було et_login
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        btnLogin.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Показуємо завантаження
            btnLogin.isEnabled = false
            btnLogin.alpha = 0.5f
            progressBar.visibility = View.VISIBLE

            // Відправляємо запит
            ApiClient.getInstance().getApiService(this)
                .login(LoginRequest(phone, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        progressBar.visibility = View.GONE
                        btnLogin.isEnabled = true
                        btnLogin.alpha = 1.0f

                        if (response.isSuccessful && response.body() != null) {
                            // Зберігаємо токен
                            sessionManager.saveAuthToken(response.body()!!.token)

                            Toast.makeText(this@LoginActivity, "Вхід виконано", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            val errorMsg = if (response.code() == 401) "Невірний логін або пароль" else "Помилка сервера: ${response.code()}"
                            Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        progressBar.visibility = View.GONE
                        btnLogin.isEnabled = true
                        btnLogin.alpha = 1.0f
                        Toast.makeText(this@LoginActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }
}