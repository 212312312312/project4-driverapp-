package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.databinding.ActivityLoginPasswordBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.LoginRequest
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class LoginPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginPasswordBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)

        // Получаем номер, если он был введен на прошлом экране
        val preFilledPhone = intent.getStringExtra("phone_input")
        if (!preFilledPhone.isNullOrEmpty()) {
            binding.etPhone.setText(preFilledPhone)
        }

        setupListeners()
    }

    private fun setupListeners() {


        binding.tvSwitchToSms.setOnClickListener {
            // Просто закрываем эту активити, возвращаясь к LoginActivity (SMS)
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val rawPhone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (rawPhone.length < 9) {
                Toast.makeText(this, "Введіть коректний номер", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Введіть пароль", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedPhone = if (rawPhone.startsWith("+380")) rawPhone else "+380${rawPhone.removePrefix("0")}"
            loginWithPassword(formattedPhone, password)
        }
    }

    private fun loginWithPassword(phone: String, pass: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@LoginPasswordActivity)
                    .login(LoginRequest(phone, pass))

                if (response.isSuccessful && response.body() != null) {
                    val loginData = response.body()!!

                    // Зберігаємо статус видалення для головного екрану!
                    sessionManager.setPendingDeletion(loginData.isPendingDeletion == true)

                    // І просто пускаємо далі (в AccountSelection / MainActivity)
                    saveSession(loginData)
                } else {
                    val errorMsg = if (response.code() == 401) "Невірний номер або пароль" else "Помилка входу"
                    Toast.makeText(this@LoginPasswordActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginPasswordActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun saveSession(data: com.taxiapp.driver.network.LoginResponse) {
        sessionManager.saveAuthToken(data.token)
        sessionManager.saveDriverId(data.userId)

        // ИСПРАВЛЕНО: phoneNumber вместо userPhone и проверка на null
        sessionManager.saveDriverName(data.fullName ?: "Водій")
        sessionManager.saveDriverPhone(data.phoneNumber ?: "")

        val intent = Intent(this, AccountSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.etPhone.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
    }


    private fun showRestoreDialog(loginData: com.taxiapp.driver.network.LoginResponse) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_restore_account)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false) // Щоб не можна було закрити, клікнувши поруч

        val btnCancel = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelRestore)
        val btnConfirm = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmRestore)

        btnCancel.setOnClickListener {
            dialog.dismiss()
            sessionManager.saveAuthToken("") // Очищаємо токен, якщо водій передумав
            setLoading(false)
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            restoreAccountAndLogin(loginData)
        }

        dialog.show()
    }

    private fun restoreAccountAndLogin(loginData: com.taxiapp.driver.network.LoginResponse) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                // Щоб сервер нас пропустив для відновлення, нам потрібен токен з loginData
                sessionManager.saveAuthToken(loginData.token)

                val response = ApiClient.getInstance().getApiService(this@LoginPasswordActivity).restoreAccount()
                if (response.isSuccessful) {
                    saveSession(loginData) // Продовжуємо стандартний логін
                } else {
                    sessionManager.saveAuthToken("") // Очищаємо токен у разі помилки
                    Toast.makeText(this@LoginPasswordActivity, "Помилка відновлення акаунту", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            } catch (e: Exception) {
                sessionManager.saveAuthToken("")
                Toast.makeText(this@LoginPasswordActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }
}