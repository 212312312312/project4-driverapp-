package com.taxiapp.driver

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import kotlinx.coroutines.launch

class TopUpActivity : AppCompatActivity() {

    private lateinit var tvBalanceInteger: TextView
    private lateinit var tvBalanceFractional: TextView
    private lateinit var etAmount: EditText

    // Чекбоксы переведены на ссылки MaterialCardView для управления свечением обводки инпутов
    private lateinit var btnPayGoogle: MaterialCardView
    private lateinit var btnPayCard: MaterialCardView
    private lateinit var checkGoogle: MaterialCardView
    private lateinit var checkCard: MaterialCardView
    private lateinit var imgCheckGoogle: ImageView
    private lateinit var imgCheckCard: ImageView

    // Главная кнопка действия
    private lateinit var btnContinueContainer: View
    private lateinit var btnContinue: TextView

    private var selectedMethod: String = "CARD" // По дефолту выбрана карта ЛикПей

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_up)

        initViews()
        setupListeners()
        parseAndRenderBalance()

        // Стартовое состояние выбора оплаты по умолчанию (Карта светится как активный инпут)
        animateCheckboxChange(btnPayCard, checkCard, imgCheckCard, true, false)
        validateForm()
    }

    private fun initViews() {
        tvBalanceInteger = findViewById(R.id.tv_balance_integer)
        tvBalanceFractional = findViewById(R.id.tv_balance_fractional)
        etAmount = findViewById(R.id.et_amount)

        btnPayGoogle = findViewById(R.id.btn_pay_google)
        btnPayCard = findViewById(R.id.btn_pay_card)
        checkGoogle = findViewById(R.id.check_google)
        checkCard = findViewById(R.id.check_card)
        imgCheckGoogle = findViewById(R.id.img_check_google)
        imgCheckCard = findViewById(R.id.img_check_card)

        btnContinueContainer = findViewById(R.id.btn_continue_container)
        btnContinue = findViewById(R.id.btn_continue)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Слушатели тапов на строки выбора оплаты
        btnPayGoogle.setOnClickListener {
            if (selectedMethod != "GOOGLE_PAY") {
                selectedMethod = "GOOGLE_PAY"
                animateCheckboxChange(btnPayGoogle, checkGoogle, imgCheckGoogle, true, true)
                animateCheckboxChange(btnPayCard, checkCard, imgCheckCard, false, true)
                validateForm()
            }
        }

        btnPayCard.setOnClickListener {
            if (selectedMethod != "CARD") {
                selectedMethod = "CARD"
                animateCheckboxChange(btnPayCard, checkCard, imgCheckCard, true, true)
                animateCheckboxChange(btnPayGoogle, checkGoogle, imgCheckGoogle, false, true)
                validateForm()
            }
        }

        // Валидация при вводе цифр
        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Клик по кнопке продолжить
        btnContinue.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            if (amount != null) {
                if (selectedMethod == "CARD") {
                    initiatePayment(amount)
                } else {
                    Toast.makeText(this, "Google Pay: в розробці", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Парсинг и разделение суммы баланса счета
    private fun parseAndRenderBalance() {
        val balance = intent.getDoubleExtra("CURRENT_BALANCE", 0.0)
        val integerPart = balance.toInt()
        val fractionalPart = String.format(".%02d ₴", ((Math.abs(balance) - Math.abs(integerPart)) * 100).toInt())

        tvBalanceInteger.text = integerPart.toString()
        tvBalanceFractional.text = fractionalPart

        if (balance < 0) {
            tvBalanceInteger.setTextColor(getColor(R.color.driver_error_red))
            tvBalanceFractional.setTextColor(getColor(R.color.driver_error_red))
        } else {
            tvBalanceInteger.setTextColor(getColor(R.color.driver_neon_teal))
            tvBalanceFractional.setTextColor(getColor(R.color.driver_neon_teal))
        }
    }

    // Валидация формы (ограничение от 200 до 1000 грн)
    private fun validateForm() {
        val amount = etAmount.text.toString().toIntOrNull()
        val isValid = amount != null && amount in 200..1000

        if (isValid) {
            btnContinueContainer.alpha = 1.0f
            btnContinue.isEnabled = true
        } else {
            btnContinueContainer.alpha = 0.4f
            btnContinue.isEnabled = false
        }
    }

    // Анимация строк выбора, имитирующая фокус EditText
    private fun animateCheckboxChange(
        rowCard: MaterialCardView,
        checkCard: MaterialCardView,
        checkImg: ImageView,
        select: Boolean,
        animate: Boolean
    ) {
        val colorStart = if (select) ContextCompat.getColor(this, R.color.driver_text_secondary) else ContextCompat.getColor(this, R.color.driver_neon_teal)
        val colorEnd = if (select) ContextCompat.getColor(this, R.color.driver_neon_teal) else ContextCompat.getColor(this, R.color.driver_text_secondary)

        if (animate) {
            // Плавное перетекание цвета обводки всей строки-инпута за 250мс
            ValueAnimator.ofObject(ArgbEvaluator(), colorStart, colorEnd).apply {
                duration = 250
                addUpdateListener { animator ->
                    val animatedColor = animator.animatedValue as Int
                    rowCard.strokeColor = animatedColor
                    checkCard.strokeColor = animatedColor
                }
                start()
            }

            if (select) {
                checkImg.visibility = View.VISIBLE
                checkImg.scaleX = 0f
                checkImg.scaleY = 0f
                checkImg.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.8f))
                    .start()
            } else {
                checkImg.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(200)
                    .withEndAction { checkImg.visibility = View.GONE }
                    .start()
            }
        } else {
            rowCard.strokeColor = colorEnd
            checkCard.strokeColor = colorEnd

            checkImg.visibility = if (select) View.VISIBLE else View.GONE
            if (select) {
                checkImg.scaleX = 1f
                checkImg.scaleY = 1f
            }
        }
    }

    private fun initiatePayment(amount: Double) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@TopUpActivity, "Створення платежу...", Toast.LENGTH_SHORT).show()
                val response = ApiClient.getInstance().getApiService(this@TopUpActivity).initPayment(InitPaymentRequest(amount))
                if (response.isSuccessful && response.body() != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(response.body()!!.paymentUrl))
                    startActivity(browserIntent)
                    finish()
                } else {
                    Toast.makeText(this@TopUpActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TopUpActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }
}