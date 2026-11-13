package com.taxiapp.driver

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.graphics.drawable.Animatable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.databinding.ActivityLoginPasswordBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.LoginRequest
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class LoginPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginPasswordBinding
    private lateinit var sessionManager: SessionManager

    private var isFormattingPhone = false
    private var loaderAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        val padH = (16 * density).toInt()
        val padTop = (12 * density).toInt()
        val padBottom = (16 * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(
                bars.left + padH,
                bars.top + padTop,
                bars.right + padH,
                bars.bottom + padBottom
            )
            insets
        }

        sessionManager = SessionManager(this)

        binding.tvSwitchToSms.paintFlags =
            binding.tvSwitchToSms.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        setupInputWatchers()

        val preFilledPhone = intent.getStringExtra("phone_input")
        if (!preFilledPhone.isNullOrEmpty()) {
            binding.etPhone.setText(preFilledPhone)
            binding.etPassword.requestFocus()
        }

        setupListeners()
    }

    override fun onDestroy() {
        stopLoaderAnimation()
        super.onDestroy()
    }

    private fun setupInputWatchers() {
        updateLoginButtonState()

        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormattingPhone) return
                isFormattingPhone = true

                val rawDigits = s?.toString()?.filter { it.isDigit() } ?: ""
                val cleanDigits = if (rawDigits.startsWith("380")) {
                    rawDigits.removePrefix("380")
                } else if (rawDigits.startsWith("0") && rawDigits.length > 9) {
                    rawDigits.removePrefix("0")
                } else {
                    rawDigits
                }.take(9)

                val formatted = buildString {
                    for (i in cleanDigits.indices) {
                        if (i == 2 || i == 5 || i == 7) append(' ')
                        append(cleanDigits[i])
                    }
                }

                if (formatted != s?.toString()) {
                    binding.etPhone.setText(formatted)
                    binding.etPhone.setSelection(formatted.length)
                }

                isFormattingPhone = false
                updateLoginButtonState()
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLoginButtonState()
            }
        })
    }

    private fun getCleanPhoneDigits(): String {
        val digits = binding.etPhone.text?.toString()?.filter { it.isDigit() } ?: ""
        return if (digits.length == 10 && digits.startsWith("0")) {
            digits.removePrefix("0")
        } else {
            digits
        }
    }

    private fun updateLoginButtonState() {
        val valid = getCleanPhoneDigits().length == 9 &&
                binding.etPassword.text?.toString()?.trim()?.isNotEmpty() == true
        binding.btnLogin.isEnabled = valid
        binding.btnLogin.setTextColor(
            if (valid) Color.parseColor("#1A1C1E") else Color.parseColor("#3B3D46")
        )
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSupport.setOnClickListener { showSupportBottomSheet() }

        binding.tvSwitchToSms.setOnClickListener {
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val digits = getCleanPhoneDigits()
            val password = binding.etPassword.text.toString().trim()

            if (digits.length < 9) {
                Toast.makeText(this, "Введіть коректний номер", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Введіть пароль", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedPhone = "+380$digits"
            loginWithPassword(formattedPhone, password)
        }
    }

    private fun showSupportBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_support, null)
        bottomSheetDialog.setContentView(view)
        view.findViewById<View>(R.id.bs_btn_telegram)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TaxiSupport")))
            } catch (e: Exception) {
                Toast.makeText(this, "Telegram не встановлено", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.bs_btn_call)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            try {
                startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:+380999999999")
                })
            } catch (e: Exception) {
                Toast.makeText(this, "Помилка дзвінка", Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheetDialog.show()
    }

    private fun loginWithPassword(phone: String, pass: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@LoginPasswordActivity)
                    .login(LoginRequest(phone, pass))

                if (response.isSuccessful && response.body() != null) {
                    val loginData = response.body()!!
                    sessionManager.setPendingDeletion(loginData.isPendingDeletion == true)
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
        if (!data.refreshToken.isNullOrEmpty()) {
            sessionManager.saveRefreshToken(data.refreshToken)
        }
        sessionManager.saveDriverId(data.userId)
        sessionManager.saveDriverName(data.fullName ?: "Водій")
        sessionManager.saveDriverPhone(data.phoneNumber ?: "+380${getCleanPhoneDigits()}")
        ApiClient.getInstance().reset()

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            if (!fcmToken.isNullOrEmpty()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        ApiClient.getInstance().getApiService(applicationContext)
                            .updateFcmToken(mapOf("token" to fcmToken))
                    } catch (_: Exception) {}
                }
            }
        }

        val intent = Intent(this, AccountSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.loadingOverlay.visibility = View.VISIBLE
            startLoaderAnimation()
            binding.btnLogin.isEnabled = false
            binding.etPhone.isEnabled = false
            binding.etPassword.isEnabled = false
        } else {
            stopLoaderAnimation()
            binding.loadingOverlay.visibility = View.GONE
            binding.etPhone.isEnabled = true
            binding.etPassword.isEnabled = true
            updateLoginButtonState()
        }
    }

    private fun startLoaderAnimation() {
        (binding.ivLoader.drawable as? Animatable)?.start()
    }

    private fun stopLoaderAnimation() {
        (binding.ivLoader.drawable as? Animatable)?.stop()
    }

    private fun showRestoreDialog(loginData: com.taxiapp.driver.network.LoginResponse) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_restore_account)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val btnCancel = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelRestore)
        val btnConfirm = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmRestore)

        btnCancel.setOnClickListener {
            dialog.dismiss()
            sessionManager.saveAuthToken("")
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
                sessionManager.saveAuthToken(loginData.token)
                val response = ApiClient.getInstance().getApiService(this@LoginPasswordActivity).restoreAccount()
                if (response.isSuccessful) {
                    saveSession(loginData)
                } else {
                    sessionManager.saveAuthToken("")
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