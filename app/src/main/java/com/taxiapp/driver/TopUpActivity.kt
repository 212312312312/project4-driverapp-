package com.taxiapp.driver

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class TopUpActivity : AppCompatActivity() {

    private lateinit var tvBalanceInteger: TextView
    private lateinit var tvBalanceFractional: TextView
    private lateinit var etAmount: EditText
    private lateinit var amountInputCard: MaterialCardView
    private lateinit var tvAmountHint: TextView

    private lateinit var btnPayGoogle: MaterialCardView
    private lateinit var btnPayCard: MaterialCardView
    private lateinit var checkGoogle: MaterialCardView
    private lateinit var checkCard: MaterialCardView
    private lateinit var imgCheckGoogle: ImageView
    private lateinit var imgCheckCard: ImageView

    private lateinit var btnContinueContainer: View
    private lateinit var btnContinue: TextView

    private var selectedMethod: String = "CARD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_up)

        val rootView = findViewById<View>(R.id.top_up_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_top_up_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        initViews()
        setupListeners()
        parseAndRenderBalance()

        animateCheckboxChange(btnPayCard, checkCard, imgCheckCard, true, false)
        validateForm()
    }

    private fun initViews() {
        tvBalanceInteger = findViewById(R.id.tv_balance_integer)
        tvBalanceFractional = findViewById(R.id.tv_balance_fractional)
        etAmount = findViewById(R.id.et_amount)
        amountInputCard = findViewById(R.id.amount_input_card)
        tvAmountHint = findViewById(R.id.tv_amount_hint)

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
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnPayCard.setOnClickListener {
            if (selectedMethod != "CARD") {
                selectedMethod = "CARD"
                animateCheckboxChange(btnPayCard, checkCard, imgCheckCard, true, true)
                validateForm()
            }
        }

        // Швидкі пресети суми поповнення (+200, +300, +500, 1000)
        findViewById<View>(R.id.chip_amount_200)?.setOnClickListener { setPresetAmount(200) }
        findViewById<View>(R.id.chip_amount_300)?.setOnClickListener { setPresetAmount(300) }
        findViewById<View>(R.id.chip_amount_500)?.setOnClickListener { setPresetAmount(500) }
        findViewById<View>(R.id.chip_amount_1000)?.setOnClickListener { setPresetAmount(1000) }

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnContinue.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            if (amount != null && amount in 200.0..1000.0) {
                initiatePayment(amount)
            }
        }
    }

    private fun setPresetAmount(preset: Int) {
        val text = preset.toString()
        etAmount.setText(text)
        etAmount.setSelection(text.length)
        validateForm()
    }

    /**
     * Відображає поточний баланс у стилі екрану "Баланс" (ціла частина білим + копійки сірим + знак ₴ білим).
     */
    private fun parseAndRenderBalance() {
        val balance = intent.getDoubleExtra("CURRENT_BALANCE", 0.0)
        val primaryColor = if (balance < 0) {
            ContextCompat.getColor(this, R.color.driver_error_red)
        } else {
            ContextCompat.getColor(this, R.color.driver_text_primary)
        }
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)

        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }
        val intPartFormat = DecimalFormat("#,##0", symbols)
        val signPrefix = if (balance < -0.004) "-" else ""
        val intPartStr = signPrefix + intPartFormat.format(abs(balance).toLong()) + "."
        val cents = ((abs(balance) * 100.0) + 0.5).toLong() % 100L
        val fracPartStr = String.format(Locale.US, "%02d ₴", cents)

        tvBalanceInteger.text = intPartStr
        tvBalanceInteger.setTextColor(primaryColor)

        val spannableFrac = SpannableString(fracPartStr)
        if (fracPartStr.length >= 2) {
            spannableFrac.setSpan(
                ForegroundColorSpan(secondaryColor),
                0,
                2,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableFrac.setSpan(
                ForegroundColorSpan(primaryColor),
                2,
                fracPartStr.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvBalanceFractional.text = spannableFrac
    }

    private fun validateForm() {
        val raw = etAmount.text.toString().trim()
        val amount = raw.toIntOrNull()
        val isValid = amount != null && amount in 200..1000

        val neonTeal = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val errorRed = ContextCompat.getColor(this, R.color.driver_error_red)
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)

        if (raw.isEmpty()) {
            amountInputCard.strokeColor = secondaryColor
            tvAmountHint.setTextColor(secondaryColor)
        } else if (isValid) {
            amountInputCard.strokeColor = neonTeal
            tvAmountHint.setTextColor(secondaryColor)
        } else {
            amountInputCard.strokeColor = errorRed
            tvAmountHint.setTextColor(errorRed)
        }

        if (isValid) {
            btnContinueContainer.alpha = 1.0f
            btnContinue.isEnabled = true
        } else {
            btnContinueContainer.alpha = 0.4f
            btnContinue.isEnabled = false
        }
    }

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
                    val body = response.body()!!
                    com.taxiapp.driver.utils.SessionManager(this@TopUpActivity).savePendingPaymentId(body.paymentId)
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(body.paymentUrl))
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