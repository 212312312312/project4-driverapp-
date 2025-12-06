package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        setContentView(R.layout.activity_login) // Создайте этот layout (2 поля и кнопка)

        sessionManager = SessionManager(this)

        // Если уже есть токен - сразу идем на главный экран
        if (sessionManager.fetchAuthToken() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val etLogin = findViewById<EditText>(R.id.et_login)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val login = etLogin.text.toString()
            val password = etPassword.text.toString()

            // ВАЖНО: Отправляем login и password
            ApiClient.getInstance().getApiService(this)
                .login(LoginRequest(login, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        if (response.isSuccessful && response.body() != null) {
                            // СОХРАНЯЕМ ТОКЕН!
                            sessionManager.saveAuthToken(response.body()!!.token)

                            Toast.makeText(this@LoginActivity, "Вход выполнен", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Ошибка: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Toast.makeText(this@LoginActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }
}