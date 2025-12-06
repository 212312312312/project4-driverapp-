package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.utils.SessionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etOrderId = findViewById<EditText>(R.id.et_order_id)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val btnLogout = findViewById<Button>(R.id.btn_logout) // Добавьте кнопку выхода в XML

        // ПРИНЯТЬ
        findViewById<Button>(R.id.btn_accept).setOnClickListener {
            val id = etOrderId.text.toString().toLongOrNull() ?: return@setOnClickListener

            // Токен добавляется АВТОМАТИЧЕСКИ через Interceptor
            ApiClient.getInstance().getApiService(this).acceptOrder(id).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) tvStatus.text = "Заказ #$id Принят!"
                    else tvStatus.text = "Ошибка: ${response.code()}"
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    tvStatus.text = "Ошибка сети"
                }
            })
        }

        // ЗАВЕРШИТЬ
        findViewById<Button>(R.id.btn_complete).setOnClickListener {
            val id = etOrderId.text.toString().toLongOrNull() ?: return@setOnClickListener

            ApiClient.getInstance().getApiService(this).completeOrder(id).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) tvStatus.text = "Заказ #$id Завершен!"
                    else tvStatus.text = "Ошибка: ${response.code()}"
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    tvStatus.text = "Ошибка сети"
                }
            })
        }

        // ВЫЙТИ
        btnLogout.setOnClickListener {
            SessionManager(this).clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}