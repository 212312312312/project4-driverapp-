package com.taxiapp.driver

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.Animatable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.databinding.ActivityLoginBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.SmsRequestDto
import com.taxiapp.driver.network.SmsVerifyDto
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    private var currentMode = LoginMode.PHONE_INPUT
    private var formattedPhone = ""
    private var isFormattingPhone = false

    private var resendTimer: CountDownTimer? = null
    private var cursorJob: Job? = null
    private var loaderAnimator: ObjectAnimator? = null

    private val codeViews: List<TextView> by lazy {
        listOf(
            binding.tvCode1, binding.tvCode2, binding.tvCode3,
            binding.tvCode4, binding.tvCode5, binding.tvCode6
        )
    }

    enum class LoginMode {
        PHONE_INPUT,
        SMS_VERIFY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
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

        setupPhoneInputFormatting()
        setupListeners()
        setupOtpInput()
        showPhoneInputStep()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
        cursorJob?.cancel()
        stopLoaderAnimation()
    }

    private fun setupPhoneInputFormatting() {
        updatePhoneButtonState(false)

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

                updatePhoneButtonState(cleanDigits.length == 9)
                isFormattingPhone = false
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

    private fun updatePhoneButtonState(enabled: Boolean) {
        binding.btnGetCode.isEnabled = enabled
        binding.btnGetCode.setTextColor(
            if (enabled) Color.parseColor("#1A1C1E") else Color.parseColor("#3B3D46")
        )
    }

    private fun updateVerifyButtonState(enabled: Boolean) {
        binding.btnVerifyCode.isEnabled = enabled
        binding.btnVerifyCode.setTextColor(
            if (enabled) Color.parseColor("#1A1C1E") else Color.parseColor("#3B3D46")
        )
    }

    private fun setupOtpInput() {
        updateVerifyButtonState(false)

        binding.etCodeHidden.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before && start < 6) {
                    animateCellJump(codeViews[start])
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val code = s?.toString() ?: ""
                updateOtpVisuals(code)
                if (code.length == 6 && currentMode == LoginMode.SMS_VERIFY) {
                    verifySms(formattedPhone, code)
                }
            }
        })

        val clickListener = View.OnClickListener {
            binding.etCodeHidden.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etCodeHidden, InputMethodManager.SHOW_IMPLICIT)
            binding.etCodeHidden.setSelection(binding.etCodeHidden.text.length)
        }

        codeViews.forEach { it.setOnClickListener(clickListener) }
    }

    private fun updateOtpVisuals(input: String) {
        val len = input.length

        if (len == 6) {
            updateVerifyButtonState(true)
            stopCursorBlink()
        } else {
            updateVerifyButtonState(false)
            startCursorBlink(len)
        }

        codeViews.forEachIndexed { index, textView ->
            if (index < len) {
                textView.text = input[index].toString()
                textView.isSelected = true
            } else if (index == len) {
                textView.isSelected = true
            } else {
                textView.text = ""
                textView.isSelected = false
            }
        }
    }

    private fun animateCellJump(view: View) {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()
    }

    private fun startCursorBlink(position: Int) {
        cursorJob?.cancel()
        if (position >= 6) return

        cursorJob = lifecycleScope.launch {
            val targetView = codeViews[position]
            targetView.isSelected = true

            while (isActive) {
                targetView.text = "|"
                delay(600)
                if (!isActive) break
                targetView.text = ""
                delay(500)
                targetView.isSelected = true
            }
        }
    }

    private fun stopCursorBlink() {
        cursorJob?.cancel()
        val len = binding.etCodeHidden.text.length
        for (i in len until 6) {
            codeViews[i].text = ""
            codeViews[i].isSelected = false
        }
    }

    private fun startResendTimer() {
        binding.tvResendCode.isEnabled = false
        binding.tvResendCode.setTextColor(Color.parseColor("#8A8D97"))

        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendCode.text = "Надіслати код повторно ($seconds)"
            }

            override fun onFinish() {
                binding.tvResendCode.isEnabled = true
                binding.tvResendCode.text = "Надіслати код повторно"
                binding.tvResendCode.setTextColor(getColor(R.color.driver_neon_teal))
            }
        }.start()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { handleBackPress() }

        binding.btnSupport.setOnClickListener { showSupportBottomSheet() }

        binding.btnGetCode.setOnClickListener {
            val digits = getCleanPhoneDigits()
            if (digits.length < 9) {
                Toast.makeText(this, "Введіть коректний номер", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            formattedPhone = "+380$digits"
            requestSms(formattedPhone)
        }

        binding.btnVerifyCode.setOnClickListener {
            val code = binding.etCodeHidden.text.toString().trim()
            if (code.length == 6) {
                verifySms(formattedPhone, code)
            }
        }

        binding.tvSwitchToPassword.setOnClickListener {
            val intent = Intent(this, LoginPasswordActivity::class.java)
            val currentInput = binding.etPhone.text.toString().trim()
            if (currentInput.isNotEmpty()) {
                intent.putExtra("phone_input", currentInput)
            }
            startActivity(intent)
        }

        binding.tvResendCode.setOnClickListener {
            requestSms(formattedPhone)
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

    private fun showPhoneInputStep() {
        currentMode = LoginMode.PHONE_INPUT
        binding.layoutStepPhone.visibility = View.VISIBLE
        binding.layoutStepSms.visibility = View.GONE
        setLoading(false)
        resendTimer?.cancel()

        binding.etPhone.requestFocus()
        binding.etPhone.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etPhone, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun showSmsVerifyStep(phone: String) {
        currentMode = LoginMode.SMS_VERIFY
        binding.layoutStepPhone.visibility = View.GONE
        binding.layoutStepSms.visibility = View.VISIBLE
        setLoading(false)
        binding.tvSmsSubtitle.text = "Ми відправили код на номер\n$phone"

        binding.etCodeHidden.text.clear()
        binding.etCodeHidden.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etCodeHidden, InputMethodManager.SHOW_IMPLICIT)

        startResendTimer()
    }

    private fun handleBackPress() {
        if (currentMode == LoginMode.SMS_VERIFY) {
            showPhoneInputStep()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleBackPress()
    }

    private fun requestSms(phone: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@LoginActivity)
                    .requestDriverLoginSms(SmsRequestDto(phone))

                if (response.isSuccessful) {
                    if (currentMode == LoginMode.SMS_VERIFY) {
                        startResendTimer()
                        Toast.makeText(this@LoginActivity, "Код відправлено", Toast.LENGTH_SHORT).show()
                    } else {
                        showSmsVerifyStep(phone)
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        429 -> getString(R.string.error_too_many_requests)
                        404 -> "Водія не знайдено"
                        else -> "Помилка: ${response.code()}"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun verifySms(phone: String, code: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@LoginActivity)
                    .verifyDriverLoginSms(SmsVerifyDto(phone, code))

                if (response.isSuccessful && response.body() != null) {
                    val loginData = response.body()!!
                    sessionManager.setPendingDeletion(loginData.isPendingDeletion == true)
                    saveSession(loginData)
                } else {
                    val errorMsg = if (response.code() == 429) {
                        getString(R.string.error_too_many_requests)
                    } else {
                        "Невірний код"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    setLoading(false)
                    binding.etCodeHidden.text.clear()
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginError", "Error parsing", e)
                Toast.makeText(this@LoginActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
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
        sessionManager.saveDriverPhone(data.phoneNumber ?: formattedPhone)
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
            binding.btnGetCode.isEnabled = false
            binding.btnVerifyCode.isEnabled = false
        } else {
            stopLoaderAnimation()
            binding.loadingOverlay.visibility = View.GONE
            updatePhoneButtonState(getCleanPhoneDigits().length == 9)
            val codeLen = binding.etCodeHidden.text?.length ?: 0
            updateVerifyButtonState(codeLen == 6)
        }
    }

    private fun startLoaderAnimation() {
        (binding.ivLoader.drawable as? Animatable)?.start()
    }

    private fun stopLoaderAnimation() {
        (binding.ivLoader.drawable as? Animatable)?.stop()
    }
}
