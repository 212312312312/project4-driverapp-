package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    // Таймер и корутина для курсора
    private var resendTimer: CountDownTimer? = null
    private var cursorJob: Job? = null

    // Массив ячеек
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
        sessionManager = SessionManager(this)

        setupListeners()
        setupOtpInput()
        showPhoneInputStep()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
        cursorJob?.cancel()
    }

    // --- ЛОГИКА ВВОДА SMS (С АНИМАЦИЕЙ И КУРСОРОМ) ---

    private fun setupOtpInput() {
        // Изначально отключаем кнопку
        binding.btnVerifyCode.isEnabled = false
        binding.btnVerifyCode.alpha = 0.5f

        binding.etCodeHidden.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Если добавили символ (ввод цифры), запускаем анимацию
                if (count > before && start < 6) {
                    animateCellJump(codeViews[start])
                }
            }

            override fun afterTextChanged(s: Editable?) {
                updateOtpVisuals(s?.toString() ?: "")
            }
        })

        // Фокус на скрытое поле при клике на ячейки
        val clickListener = View.OnClickListener {
            binding.etCodeHidden.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etCodeHidden, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            binding.etCodeHidden.setSelection(binding.etCodeHidden.text.length)
        }

        codeViews.forEach { it.setOnClickListener(clickListener) }
    }

    // Главный метод отрисовки ячеек
    private fun updateOtpVisuals(input: String) {
        val len = input.length

        // 1. Управляем активностью кнопки
        if (len == 6) {
            binding.btnVerifyCode.isEnabled = true
            binding.btnVerifyCode.alpha = 1.0f
            stopCursorBlink() // Код введен, курсор не нужен
        } else {
            binding.btnVerifyCode.isEnabled = false
            binding.btnVerifyCode.alpha = 0.5f
            startCursorBlink(len) // Запускаем мигание на текущей позиции
        }

        // 2. Отрисовка цифр и состояний
        codeViews.forEachIndexed { index, textView ->
            if (index < len) {
                // Цифра уже введена
                textView.text = input[index].toString()
                textView.isSelected = true // Подсветка (тиловая рамка)
            } else if (index == len) {
                // Это текущая ячейка ввода (здесь будет курсор)
                // ВАЖНО: Мы ставим isSelected = true, чтобы она светилась
                textView.isSelected = true
                // Текст здесь управляется корутиной startCursorBlink
            } else {
                // Пустая ячейка впереди
                textView.text = ""
                textView.isSelected = false // Серая рамка
            }
        }
    }

    // Анимация "Прыжок" при вводе
    private fun animateCellJump(view: View) {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator(2.0f)) // Эффект пружины
            .start()
    }

    // Логика мигающего курсора "|"
    private fun startCursorBlink(position: Int) {
        cursorJob?.cancel()
        if (position >= 6) return

        cursorJob = lifecycleScope.launch {
            val targetView = codeViews[position]

            // ПРИНУДИТЕЛЬНО включаем подсветку для активной ячейки курсора
            targetView.isSelected = true

            while (isActive) {
                targetView.text = "|"
                delay(600)
                if (!isActive) break
                targetView.text = "" // Пустота
                delay(500)

                // На всякий случай обновляем состояние, чтобы не слетело
                targetView.isSelected = true
            }
        }
    }

    private fun stopCursorBlink() {
        cursorJob?.cancel()
        // Очищаем курсоры во всех ячейках, которые еще пустые (начиная с текущей позиции ввода)
        val len = binding.etCodeHidden.text.length
        for (i in len until 6) {
            codeViews[i].text = ""
            // Неактивные ячейки не должны быть выбраны
            codeViews[i].isSelected = false
        }
    }

    // --- ТАЙМЕР ОТПРАВКИ ---

    private fun startResendTimer() {
        binding.tvResendCode.isEnabled = false
        binding.tvResendCode.setTextColor(getColor(android.R.color.darker_gray)) // Серый цвет

        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendCode.text = "Надіслати код повторно ($seconds)"
            }

            override fun onFinish() {
                binding.tvResendCode.isEnabled = true
                binding.tvResendCode.text = "Надіслати код повторно"
                // Возвращаем тиловый цвет
                binding.tvResendCode.setTextColor(getColor(R.color.driver_neon_teal))
            }
        }.start()
    }

    // --- СТАНДАРТНАЯ ЛОГИКА ---

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { handleBackPress() }

        binding.btnGetCode.setOnClickListener {
            val rawInput = binding.etPhone.text.toString().trim()
            if (rawInput.length < 9) {
                Toast.makeText(this, "Введіть коректний номер", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            formattedPhone = if (rawInput.startsWith("+380")) rawInput else "+380${rawInput.removePrefix("0")}"
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

    private fun showPhoneInputStep() {
        currentMode = LoginMode.PHONE_INPUT
        binding.layoutStepPhone.visibility = View.VISIBLE
        binding.layoutStepSms.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        resendTimer?.cancel() // Сбрасываем таймер если вышли назад
    }

    private fun showSmsVerifyStep(phone: String) {
        currentMode = LoginMode.SMS_VERIFY
        binding.layoutStepPhone.visibility = View.GONE
        binding.layoutStepSms.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvSmsSubtitle.text = "Ми відправили код на номер\n$phone"

        // Очистка и фокус
        binding.etCodeHidden.text.clear()
        binding.etCodeHidden.requestFocus()

        // Запускаем таймер при переходе на экран
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
                    val errorMsg = if (response.code() == 404) "Водія не знайдено" else "Помилка: ${response.code()}"
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
                    saveSession(response.body()!!)
                } else {
                    Toast.makeText(this@LoginActivity, "Невірний код", Toast.LENGTH_SHORT).show()
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
        sessionManager.saveDriverId(data.userId)
        sessionManager.saveDriverName(data.fullName)
        sessionManager.saveDriverPhone(data.userPhone)

        val intent = Intent(this, AccountSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGetCode.isEnabled = !loading

        if (!loading) {
            val codeLen = binding.etCodeHidden.text?.length ?: 0
            binding.btnVerifyCode.isEnabled = (codeLen == 6)
            binding.btnVerifyCode.alpha = if (codeLen == 6) 1.0f else 0.5f
        }
    }
}