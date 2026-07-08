package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.ChangePhoneConfirmRequest
import com.taxiapp.driver.network.CodeVerifyRequest
import com.taxiapp.driver.network.SmsRequestDto
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class ChangePhoneActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var etCurrentCode: EditText
    private lateinit var etNewPhone: EditText
    private lateinit var etNewCode: EditText
    private lateinit var sessionManager: SessionManager

    private var changeToken: String? = null
    private var newPhoneNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_phone)

        // 🛠️ ДОБАВЛЕНО: Автоматический отступ контента от системных панелей для Android 15
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        viewFlipper = findViewById(R.id.view_flipper)
        etCurrentCode = findViewById(R.id.et_current_code)
        etNewPhone = findViewById(R.id.et_new_phone)
        etNewCode = findViewById(R.id.et_new_code)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_send_current).setOnClickListener { requestCurrentCode() }
        findViewById<View>(R.id.btn_verify_current).setOnClickListener { verifyCurrentCode() }
        findViewById<View>(R.id.btn_send_new).setOnClickListener { requestNewCode() }
        findViewById<View>(R.id.btn_confirm_all).setOnClickListener { confirmChange() }
    }

    // ... (requestCurrentCode, verifyCurrentCode, requestNewCode - без изменений) ...
    private fun requestCurrentCode() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ChangePhoneActivity).requestCodeForCurrentPhone()
                if (response.isSuccessful) {
                    Toast.makeText(this@ChangePhoneActivity, "Код надіслано", Toast.LENGTH_SHORT).show()
                    viewFlipper.showNext()
                } else {
                    Toast.makeText(this@ChangePhoneActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChangePhoneActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifyCurrentCode() {
        val code = etCurrentCode.text.toString()
        if (code.length < 4) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ChangePhoneActivity)
                    .verifyCurrentPhoneCode(CodeVerifyRequest(code))
                if (response.isSuccessful && response.body() != null) {
                    changeToken = response.body()!!["changeToken"]
                    viewFlipper.showNext()
                } else {
                    Toast.makeText(this@ChangePhoneActivity, "Невірний код", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun requestNewCode() {
        val phone = etNewPhone.text.toString()
        if (phone.length < 10) {
            Toast.makeText(this, "Введіть коректний номер", Toast.LENGTH_SHORT).show()
            return
        }
        newPhoneNumber = phone
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ChangePhoneActivity)
                    .requestCodeForNewPhone(SmsRequestDto(phone))
                if (response.isSuccessful) viewFlipper.showNext()
                else Toast.makeText(this@ChangePhoneActivity, "Помилка", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChangePhoneActivity, "Помилка", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun confirmChange() {
        val code = etNewCode.text.toString()
        if (changeToken == null || newPhoneNumber == null) return

        lifecycleScope.launch {
            try {
                val request = ChangePhoneConfirmRequest(newPhoneNumber!!, code, changeToken!!)
                val response = ApiClient.getInstance().getApiService(this@ChangePhoneActivity).confirmNewPhone(request)

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!

                    // 1. Сохраняем НОВЫЙ токен
                    sessionManager.saveAuthToken(loginResponse.token)

                    // 2. Сохраняем номер телефона (теперь поле phoneNumber доступно)
                    loginResponse.phoneNumber?.let {
                        sessionManager.saveDriverPhone(it)
                    }

                    // 3. Сбрасываем Retrofit, чтобы он подхватил новый токен
                    ApiClient.getInstance().reset()

                    Toast.makeText(this@ChangePhoneActivity, "Номер успішно змінено!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@ChangePhoneActivity, "Невірний код", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChangePhoneActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}