package com.taxiapp.driver

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CreateFilterRequest
import com.taxiapp.driver.network.DriverFilter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class CreateFilterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var tvNameCounter: TextView
    private lateinit var rgFromType: RadioGroup
    private lateinit var segFromDistance: MaterialCardView
    private lateinit var segFromSector: MaterialCardView
    private lateinit var tvSegFromDistance: TextView
    private lateinit var tvSegFromSector: TextView

    private lateinit var containerDistance: View
    private lateinit var cardFromSectorsSelector: View
    private lateinit var cardToSectorsSelector: View
    private lateinit var btnSelectFromSectors: Button
    private lateinit var sbDistance: SeekBar
    private lateinit var tvDistanceVal: TextView
    private lateinit var btnDistanceMinus: View
    private lateinit var btnDistancePlus: View

    private lateinit var btnSelectToSectors: Button
    private lateinit var tvSelectedToCount: TextView

    private lateinit var rgTariffType: RadioGroup
    private lateinit var segTariffSimple: MaterialCardView
    private lateinit var segTariffComplex: MaterialCardView
    private lateinit var tvSegTariffSimple: TextView
    private lateinit var tvSegTariffComplex: TextView

    private lateinit var containerSimple: View
    private lateinit var containerComplex: View
    private lateinit var cbCityOnly: MaterialCheckBox
    private lateinit var cardComplexSuburbsInput: View

    private lateinit var spPayment: Spinner
    private lateinit var segPayCash: MaterialCardView
    private lateinit var segPayAny: MaterialCardView
    private lateinit var segPayCard: MaterialCardView
    private lateinit var tvSegPayCash: TextView
    private lateinit var tvSegPayAny: TextView
    private lateinit var tvSegPayCard: TextView

    private lateinit var btnCreate: Button

    private var selectedFromIds = mutableListOf<Long>()
    private var selectedToIds = mutableListOf<Long>()
    private var selectedDistance = 3.0
    private var selectedPaymentType = "ANY" // "CASH", "ANY", "CARD"

    private var editingFilterId: Long? = null
    private var savedIsEther: Boolean = false
    private var savedIsAuto: Boolean = false
    private var savedIsCycle: Boolean = false

    private var isPickingFrom = true

    private val sectorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val ids = result.data?.getLongArrayExtra("SELECTED_IDS")?.toMutableList() ?: mutableListOf()
            if (isPickingFrom) {
                selectedFromIds = ids
                updateFromSectorsButtonText()
            } else {
                selectedToIds = ids
                updateToSectorsButtonText()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_filter)

        val rootView = findViewById<View>(R.id.create_filter_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_create_inner)
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
        renderDistanceText(selectedDistance)
        updateFromTypeSegments(isDistance = true)
        updateTariffTypeSegments(isSimple = true)
        updatePaymentSegments("ANY")

        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("FILTER_DATA", DriverFilter::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("FILTER_DATA") as? DriverFilter
        }
        if (filter != null) {
            setupEditMode(filter)
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.et_filter_name)
        tvNameCounter = findViewById(R.id.tv_name_counter)
        rgFromType = findViewById(R.id.rg_from_type)
        segFromDistance = findViewById(R.id.seg_from_distance)
        segFromSector = findViewById(R.id.seg_from_sector)
        tvSegFromDistance = findViewById(R.id.tv_seg_from_distance)
        tvSegFromSector = findViewById(R.id.tv_seg_from_sector)

        containerDistance = findViewById(R.id.container_distance)
        cardFromSectorsSelector = findViewById(R.id.card_from_sectors_selector)
        cardToSectorsSelector = findViewById(R.id.card_to_sectors_selector)
        btnSelectFromSectors = findViewById(R.id.btn_select_from_sectors)
        sbDistance = findViewById(R.id.sb_distance)
        tvDistanceVal = findViewById(R.id.tv_distance_val)
        btnDistanceMinus = findViewById(R.id.btn_distance_minus)
        btnDistancePlus = findViewById(R.id.btn_distance_plus)

        btnSelectToSectors = findViewById(R.id.btn_select_to_sectors)
        tvSelectedToCount = findViewById(R.id.tv_selected_to_count)

        rgTariffType = findViewById(R.id.rg_tariff_type)
        segTariffSimple = findViewById(R.id.seg_tariff_simple)
        segTariffComplex = findViewById(R.id.seg_tariff_complex)
        tvSegTariffSimple = findViewById(R.id.tv_seg_tariff_simple)
        tvSegTariffComplex = findViewById(R.id.tv_seg_tariff_complex)

        containerSimple = findViewById(R.id.container_simple_tariff)
        containerComplex = findViewById(R.id.container_complex_tariff)
        cbCityOnly = findViewById(R.id.cb_city_only)
        cardComplexSuburbsInput = findViewById(R.id.card_complex_suburbs_input)

        spPayment = findViewById(R.id.sp_payment_type)
        segPayCash = findViewById(R.id.seg_pay_cash)
        segPayAny = findViewById(R.id.seg_pay_any)
        segPayCard = findViewById(R.id.seg_pay_card)
        tvSegPayCash = findViewById(R.id.tv_seg_pay_cash)
        tvSegPayAny = findViewById(R.id.tv_seg_pay_any)
        tvSegPayCard = findViewById(R.id.tv_seg_pay_card)

        btnCreate = findViewById(R.id.btn_create_filter)

        // Встановлюємо точну векторну іконку знака питання (?) у кружечку як на скріншоті
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        findViewById<ImageView>(R.id.btn_city_only_help)?.apply {
            imageTintList = null
            setImageDrawable(QuestionCircleDrawable(secondaryColor))
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Будь-який", "Готівка", "Картка"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPayment.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_filter_info)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Налаштування фільтра")
                .setMessage("Фільтр дозволяє автоматично підбирати замовлення за радіусом подачі або секторами, бажаним тарифом та типом оплати в режимах Ефір, Авто або Цикл.")
                .setPositiveButton(getString(R.string.btn_understood), null)
                .show()
        }

        findViewById<View>(R.id.btn_city_only_help)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Лише по місту")
                .setMessage("Якщо увімкнено, фільтр підбиратиме лише замовлення в межах міста без виїзду в передмістя.")
                .setPositiveButton(getString(R.string.btn_understood), null)
                .show()
        }

        // Лічильник символів назви фільтра (0/100)
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                tvNameCounter.text = "$len/100"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Перемикач "Звідки": [ Відстань | Сектор ]
        segFromDistance.setOnClickListener {
            rgFromType.check(R.id.rb_distance)
            updateFromTypeSegments(isDistance = true)
        }
        segFromSector.setOnClickListener {
            rgFromType.check(R.id.rb_from_sectors)
            updateFromTypeSegments(isDistance = false)
        }
        rgFromType.setOnCheckedChangeListener { _, checkedId ->
            updateFromTypeSegments(isDistance = (checkedId == R.id.rb_distance))
        }

        // Повзунок та кнопки [-] / [+] для радіуса подачі (від 0.5 км до 30.0 км з кроком 0.5 км)
        sbDistance.max = 59
        sbDistance.progress = 5 // 3.0 км за замовчуванням ((5 / 2.0) + 0.5 = 3.0)
        sbDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                selectedDistance = (p / 2.0) + 0.5
                renderDistanceText(selectedDistance)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnDistanceMinus.setOnClickListener {
            val nextProgress = (sbDistance.progress - 1).coerceAtLeast(0)
            sbDistance.progress = nextProgress
        }
        btnDistancePlus.setOnClickListener {
            val nextProgress = (sbDistance.progress + 1).coerceAtMost(sbDistance.max)
            sbDistance.progress = nextProgress
        }

        // Перемикач "Тариф": [ Простий | Складний ]
        segTariffSimple.setOnClickListener {
            rgTariffType.check(R.id.rb_simple)
            updateTariffTypeSegments(isSimple = true)
        }
        segTariffComplex.setOnClickListener {
            rgTariffType.check(R.id.rb_complex)
            updateTariffTypeSegments(isSimple = false)
        }
        rgTariffType.setOnCheckedChangeListener { _, checkedId ->
            updateTariffTypeSegments(isSimple = (checkedId == R.id.rb_simple))
        }

        cbCityOnly.setOnCheckedChangeListener { _, isChecked ->
            cardComplexSuburbsInput.alpha = if (isChecked) 0.45f else 1.0f
            findViewById<EditText>(R.id.et_complex_suburbs)?.isEnabled = !isChecked
        }

        // Перемикач "Тип оплати": [ Готівка | Будь-який | Безготівка ]
        segPayCash.setOnClickListener { updatePaymentSegments("CASH") }
        segPayAny.setOnClickListener { updatePaymentSegments("ANY") }
        segPayCard.setOnClickListener { updatePaymentSegments("CARD") }

        cardFromSectorsSelector.setOnClickListener { openSectorPicker(true) }
        btnSelectFromSectors.setOnClickListener { openSectorPicker(true) }
        cardToSectorsSelector.setOnClickListener { openSectorPicker(false) }
        btnSelectToSectors.setOnClickListener { openSectorPicker(false) }

        btnCreate.setOnClickListener { saveFilter() }
    }

    private fun renderDistanceText(distanceKm: Double) {
        val numStr = String.format(Locale.US, "%.1f", distanceKm)
        val fullStr = "$numStr км"
        val spannable = SpannableString(fullStr)
        spannable.setSpan(
            RelativeSizeSpan(0.75f),
            numStr.length,
            fullStr.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvDistanceVal.text = spannable
    }

    private fun updateFromTypeSegments(isDistance: Boolean) {
        val density = resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        val primaryText = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val activeBg = Color.parseColor("#5A5E6D")

        if (isDistance) {
            segFromDistance.setCardBackgroundColor(activeBg)
            segFromDistance.strokeWidth = strokePx
            segFromDistance.strokeColor = primaryText
            tvSegFromDistance.setTextColor(primaryText)

            segFromSector.setCardBackgroundColor(Color.TRANSPARENT)
            segFromSector.strokeWidth = 0
            tvSegFromSector.setTextColor(secondaryText)

            containerDistance.visibility = View.VISIBLE
            cardFromSectorsSelector.visibility = View.GONE
        } else {
            segFromSector.setCardBackgroundColor(activeBg)
            segFromSector.strokeWidth = strokePx
            segFromSector.strokeColor = primaryText
            tvSegFromSector.setTextColor(primaryText)

            segFromDistance.setCardBackgroundColor(Color.TRANSPARENT)
            segFromDistance.strokeWidth = 0
            tvSegFromDistance.setTextColor(secondaryText)

            containerDistance.visibility = View.GONE
            cardFromSectorsSelector.visibility = View.VISIBLE
        }
    }

    private fun updateTariffTypeSegments(isSimple: Boolean) {
        val density = resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        val primaryText = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val activeBg = Color.parseColor("#5A5E6D")

        if (isSimple) {
            segTariffSimple.setCardBackgroundColor(activeBg)
            segTariffSimple.strokeWidth = strokePx
            segTariffSimple.strokeColor = primaryText
            tvSegTariffSimple.setTextColor(primaryText)

            segTariffComplex.setCardBackgroundColor(Color.TRANSPARENT)
            segTariffComplex.strokeWidth = 0
            tvSegTariffComplex.setTextColor(secondaryText)

            containerSimple.visibility = View.VISIBLE
            containerComplex.visibility = View.GONE
        } else {
            segTariffComplex.setCardBackgroundColor(activeBg)
            segTariffComplex.strokeWidth = strokePx
            segTariffComplex.strokeColor = primaryText
            tvSegTariffComplex.setTextColor(primaryText)

            segTariffSimple.setCardBackgroundColor(Color.TRANSPARENT)
            segTariffSimple.strokeWidth = 0
            tvSegTariffSimple.setTextColor(secondaryText)

            containerSimple.visibility = View.GONE
            containerComplex.visibility = View.VISIBLE
        }
    }

    private fun updatePaymentSegments(paymentType: String) {
        selectedPaymentType = paymentType
        val density = resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        val primaryText = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val activeBg = Color.parseColor("#5A5E6D")

        fun styleSeg(card: MaterialCardView, tv: TextView, active: Boolean) {
            if (active) {
                card.setCardBackgroundColor(activeBg)
                card.strokeWidth = strokePx
                card.strokeColor = primaryText
                tv.setTextColor(primaryText)
            } else {
                card.setCardBackgroundColor(Color.TRANSPARENT)
                card.strokeWidth = 0
                tv.setTextColor(secondaryText)
            }
        }

        styleSeg(segPayCash, tvSegPayCash, paymentType == "CASH")
        styleSeg(segPayAny, tvSegPayAny, paymentType == "ANY")
        styleSeg(segPayCard, tvSegPayCard, paymentType == "CARD")

        val spinnerPos = when (paymentType) {
            "CASH" -> 1
            "CARD" -> 2
            else -> 0
        }
        spPayment.setSelection(spinnerPos)
    }

    private fun updateFromSectorsButtonText() {
        val primaryText = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.driver_text_secondary)
        if (selectedFromIds.isEmpty()) {
            btnSelectFromSectors.text = "Сектори подачі"
            btnSelectFromSectors.setTextColor(secondaryText)
        } else {
            btnSelectFromSectors.text = "Вибрано секторів: ${selectedFromIds.size}"
            btnSelectFromSectors.setTextColor(primaryText)
        }
    }

    private fun updateToSectorsButtonText() {
        val primaryText = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.driver_text_secondary)
        if (selectedToIds.isEmpty()) {
            btnSelectToSectors.text = "Сектори призначення"
            btnSelectToSectors.setTextColor(secondaryText)
            tvSelectedToCount.text = "Вибрано секторів: 0"
        } else {
            btnSelectToSectors.text = "Вибрано секторів: ${selectedToIds.size}"
            btnSelectToSectors.setTextColor(primaryText)
            tvSelectedToCount.text = "Вибрано секторів: ${selectedToIds.size}"
        }
    }

    private fun setupEditMode(f: DriverFilter) {
        editingFilterId = f.id
        savedIsEther = f.isEther
        savedIsAuto = f.isAuto
        savedIsCycle = f.isCycle

        btnCreate.text = "Зберегти зміни"
        findViewById<TextView>(R.id.tv_header_title).text = "Редагування фільтра"

        etName.setText(f.name)
        tvNameCounter.text = "${f.name.length}/100"

        if (f.fromType == "DISTANCE") {
            rgFromType.check(R.id.rb_distance)
            updateFromTypeSegments(isDistance = true)
            selectedDistance = f.fromDistance ?: 3.0
            sbDistance.progress = (((selectedDistance - 0.5) * 2.0).roundToInt()).coerceIn(0, sbDistance.max)
            renderDistanceText(selectedDistance)
        } else {
            rgFromType.check(R.id.rb_from_sectors)
            updateFromTypeSegments(isDistance = false)
            selectedFromIds = f.fromSectors.toMutableList()
            updateFromSectorsButtonText()
        }

        selectedToIds = f.toSectors.toMutableList()
        updateToSectorsButtonText()

        if (f.tariffType == "SIMPLE") {
            rgTariffType.check(R.id.rb_simple)
            updateTariffTypeSegments(isSimple = true)
            findViewById<EditText>(R.id.et_min_price).setText(f.minPrice?.let { formatEditableNumber(it) } ?: "")
            findViewById<EditText>(R.id.et_min_price_km).setText(f.minPricePerKm?.let { formatEditableNumber(it) } ?: "")
        } else {
            rgTariffType.check(R.id.rb_complex)
            updateTariffTypeSegments(isSimple = false)
            findViewById<EditText>(R.id.et_complex_min_price).setText(f.complexMinPrice?.let { formatEditableNumber(it) } ?: "")
            findViewById<EditText>(R.id.et_complex_city).setText(f.complexPriceKmCity?.let { formatEditableNumber(it) } ?: "")
            findViewById<EditText>(R.id.et_complex_km_in_min).setText(f.complexKmInMin?.let { formatEditableNumber(it) } ?: "")
            findViewById<EditText>(R.id.et_complex_suburbs).setText(f.complexPriceKmSuburbs?.let { formatEditableNumber(it) } ?: "")
        }

        val normalizedPayment = when (f.paymentType) {
            "CASH" -> "CASH"
            "CARD" -> "CARD"
            else -> "ANY"
        }
        updatePaymentSegments(normalizedPayment)
    }

    private fun formatEditableNumber(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else value.toString()
    }

    private fun openSectorPicker(pickingFrom: Boolean) {
        isPickingFrom = pickingFrom
        val currentSelection = if (isPickingFrom) selectedFromIds else selectedToIds

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("START_SECTOR_SELECTION", true)
            putExtra("IS_FROM", isPickingFrom)
            putExtra("CURRENT_IDS", currentSelection.toLongArray())
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.hasExtra("SECTOR_RESULT_IDS")) {
            val ids = intent.getLongArrayExtra("SECTOR_RESULT_IDS")?.toMutableList() ?: mutableListOf()
            val isFrom = intent.getBooleanExtra("IS_PICKING_FROM", true)

            if (isFrom) {
                selectedFromIds = ids
                updateFromSectorsButtonText()
            } else {
                selectedToIds = ids
                updateToSectorsButtonText()
            }
        }
    }

    private fun saveFilter() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Введіть назву фільтра", Toast.LENGTH_SHORT).show()
            return
        }

        val isDistanceMode = rgFromType.checkedRadioButtonId == R.id.rb_distance
        val isSimpleTariff = rgTariffType.checkedRadioButtonId == R.id.rb_simple

        val suburbsRate = if (cbCityOnly.isChecked) {
            null
        } else {
            findViewById<EditText>(R.id.et_complex_suburbs).text.toString().toDoubleOrNull()
        }

        val request = CreateFilterRequest(
            name = name,
            isEther = if (editingFilterId != null) savedIsEther else false,
            isAuto = if (editingFilterId != null) savedIsAuto else false,
            isCycle = if (editingFilterId != null) savedIsCycle else false,

            fromType = if (isDistanceMode) "DISTANCE" else "SECTORS",
            fromDistance = if (isDistanceMode) selectedDistance else null,
            fromSectors = selectedFromIds,
            toSectors = selectedToIds,
            tariffType = if (isSimpleTariff) "SIMPLE" else "COMPLEX",

            minPrice = findViewById<EditText>(R.id.et_min_price).text.toString().toDoubleOrNull(),
            minPricePerKm = findViewById<EditText>(R.id.et_min_price_km).text.toString().toDoubleOrNull(),

            complexMinPrice = findViewById<EditText>(R.id.et_complex_min_price).text.toString().toDoubleOrNull(),
            complexKmInMin = findViewById<EditText>(R.id.et_complex_km_in_min).text.toString().toDoubleOrNull(),
            complexPriceKmCity = findViewById<EditText>(R.id.et_complex_city).text.toString().toDoubleOrNull(),
            complexPriceKmSuburbs = suburbsRate,

            paymentType = selectedPaymentType
        )

        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@CreateFilterActivity)
                val editId = editingFilterId

                val res = if (editId != null) {
                    api.updateFilter(editId, request)
                } else {
                    api.createFilter(request)
                }

                if (res.isSuccessful) {
                    Toast.makeText(
                        this@CreateFilterActivity,
                        if (editId != null) "Зміни збережено!" else "Фільтр створено!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(this@CreateFilterActivity, "Помилка сервера: ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CreateFilterActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun finish() {
        super.finish()
    }

    /**
     * Малює акуратну іконку знака питання в кружечку (?) біля чекбокса "Лише по місту",
     * як на скріншоті image_e61be0.jpg.
     */
    private class QuestionCircleDrawable(private val strokeColor: Int) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = minOf(w, h) / 24f

            canvas.save()
            canvas.translate(b.left + (w - 24f * s) / 2f, b.top + (h - 24f * s) / 2f)
            canvas.scale(s, s)

            strokePaint.strokeWidth = 1.8f
            canvas.drawCircle(12f, 12f, 9.2f, strokePaint)

            val qPath = Path().apply {
                moveTo(9.6f, 9.4f)
                cubicTo(9.6f, 7.2f, 14.5f, 7.2f, 14.5f, 9.5f)
                cubicTo(14.5f, 11.2f, 12.0f, 11.5f, 12.0f, 13.5f)
            }
            canvas.drawPath(qPath, strokePaint)
            canvas.drawCircle(12.0f, 16.5f, 1.1f, dotPaint)

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            dotPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            dotPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}