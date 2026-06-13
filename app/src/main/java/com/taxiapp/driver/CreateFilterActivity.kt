package com.taxiapp.driver

import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityOptionsCompat
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CreateFilterRequest
import com.taxiapp.driver.network.DriverFilter
import kotlinx.coroutines.launch

class CreateFilterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var rgFromType: RadioGroup
    private lateinit var containerDistance: View
    private lateinit var btnSelectFromSectors: Button
    private lateinit var sbDistance: SeekBar
    private lateinit var tvDistanceVal: TextView
    private lateinit var btnSelectToSectors: Button
    private lateinit var tvSelectedToCount: TextView
    private lateinit var rgTariffType: RadioGroup
    private lateinit var containerSimple: View
    private lateinit var containerComplex: View
    private lateinit var spPayment: Spinner
    private lateinit var btnCreate: Button

    private var selectedFromIds = mutableListOf<Long>()
    private var selectedToIds = mutableListOf<Long>()
    private var selectedDistance = 5.0

    // Для режима редактирования
    private var editingFilterId: Long? = null
    private var savedIsAuto: Boolean = false
    private var savedIsCycle: Boolean = false

    private var isPickingFrom = true

    private val sectorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val ids = result.data?.getLongArrayExtra("SELECTED_IDS")?.toMutableList() ?: mutableListOf()
            if (isPickingFrom) {
                selectedFromIds = ids
                btnSelectFromSectors.text = "Вибрано секторів: ${ids.size}"
            } else {
                selectedToIds = ids
                tvSelectedToCount.text = "Вибрано секторів: ${ids.size}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_filter)

        initViews()
        setupListeners()

        // ПРОВЕРКА НА РЕДАКТИРОВАНИЕ
        // Если передали объект фильтра, включаем режим редактирования
        val filter = intent.getSerializableExtra("FILTER_DATA") as? DriverFilter
        if (filter != null) {
            setupEditMode(filter)
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.et_filter_name)
        rgFromType = findViewById(R.id.rg_from_type)
        containerDistance = findViewById(R.id.container_distance)
        btnSelectFromSectors = findViewById(R.id.btn_select_from_sectors)
        sbDistance = findViewById(R.id.sb_distance)
        tvDistanceVal = findViewById(R.id.tv_distance_val)
        btnSelectToSectors = findViewById(R.id.btn_select_to_sectors)
        tvSelectedToCount = findViewById(R.id.tv_selected_to_count)
        rgTariffType = findViewById(R.id.rg_tariff_type)
        containerSimple = findViewById(R.id.container_simple_tariff)
        containerComplex = findViewById(R.id.container_complex_tariff)
        spPayment = findViewById(R.id.sp_payment_type)
        btnCreate = findViewById(R.id.btn_create_filter)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Будь-який", "Готівка", "Картка"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPayment.adapter = adapter
    }

    private fun setupListeners() {
        // Изменили ID с btn_back_create на btn_back в соответствии с новым общим стилем хедера
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        rgFromType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_distance) {
                containerDistance.visibility = View.VISIBLE
                btnSelectFromSectors.visibility = View.GONE
            } else {
                containerDistance.visibility = View.GONE
                btnSelectFromSectors.visibility = View.VISIBLE
            }
        }

        sbDistance.max = 59
        sbDistance.progress = 9
        sbDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                selectedDistance = (p / 2.0) + 0.5
                tvDistanceVal.text = "Радіус: $selectedDistance км"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        rgTariffType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_simple) {
                containerSimple.visibility = View.VISIBLE
                containerComplex.visibility = View.GONE
            } else {
                containerSimple.visibility = View.GONE
                containerComplex.visibility = View.VISIBLE
            }
        }

        btnSelectFromSectors.setOnClickListener { openSectorPicker(true) }
        btnSelectToSectors.setOnClickListener { openSectorPicker(false) }
        btnCreate.setOnClickListener { saveFilter() }
    }

    private fun setupEditMode(f: DriverFilter) {
        editingFilterId = f.id
        savedIsAuto = f.isAuto
        savedIsCycle = f.isCycle

        btnCreate.text = "ЗБЕРЕГТИ ЗМІНИ"
        findViewById<TextView>(R.id.tv_header_title).text = "Редагування фільтра"

        // Заполняем поля
        etName.setText(f.name)

        // Звідки
        if (f.fromType == "DISTANCE") {
            rgFromType.check(R.id.rb_distance)
            selectedDistance = f.fromDistance ?: 5.0
            sbDistance.progress = ((selectedDistance - 0.5) * 2).toInt()
            tvDistanceVal.text = "Радіус: $selectedDistance км"
        } else {
            rgFromType.check(R.id.rb_from_sectors)
            selectedFromIds = f.fromSectors.toMutableList()
            btnSelectFromSectors.text = "Вибрано секторів: ${selectedFromIds.size}"
        }

        // Куди
        selectedToIds = f.toSectors.toMutableList()
        tvSelectedToCount.text = "Вибрано секторів: ${selectedToIds.size}"

        // Тариф
        if (f.tariffType == "SIMPLE") {
            rgTariffType.check(R.id.rb_simple)
            findViewById<EditText>(R.id.et_min_price).setText(f.minPrice?.toString() ?: "")
            findViewById<EditText>(R.id.et_min_price_km).setText(f.minPricePerKm?.toString() ?: "")
        } else {
            rgTariffType.check(R.id.rb_complex)
            findViewById<EditText>(R.id.et_complex_min_price).setText(f.complexMinPrice?.toString() ?: "")
            findViewById<EditText>(R.id.et_complex_city).setText(f.complexPriceKmCity?.toString() ?: "")

            // Заполняем новые поля, если они пришли с сервера
            findViewById<EditText>(R.id.et_complex_km_in_min).setText(f.complexKmInMin?.toString() ?: "")
            findViewById<EditText>(R.id.et_complex_suburbs).setText(f.complexPriceKmSuburbs?.toString() ?: "")
        }

        // Оплата
        val paymentIdx = when (f.paymentType) {
            "CASH" -> 1
            "CARD" -> 2
            else -> 0
        }
        spPayment.setSelection(paymentIdx)
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
                btnSelectFromSectors.text = "Вибрано секторів: ${ids.size}"
            } else {
                selectedToIds = ids
                tvSelectedToCount.text = "Вибрано секторів: ${ids.size}"
            }
        }
    }

    private fun saveFilter() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Введіть назву", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentType = when(spPayment.selectedItemPosition) {
            1 -> "CASH"; 2 -> "CARD"; else -> "ANY"
        }

        val request = CreateFilterRequest(
            name = name,
            isAuto = if (editingFilterId != null) savedIsAuto else false,
            isCycle = if (editingFilterId != null) savedIsCycle else false,

            fromType = if (rgFromType.checkedRadioButtonId == R.id.rb_distance) "DISTANCE" else "SECTORS",
            fromDistance = if (rgFromType.checkedRadioButtonId == R.id.rb_distance) selectedDistance else null,
            fromSectors = selectedFromIds,
            toSectors = selectedToIds,
            tariffType = if (rgTariffType.checkedRadioButtonId == R.id.rb_simple) "SIMPLE" else "COMPLEX",

            // Простий
            minPrice = findViewById<EditText>(R.id.et_min_price).text.toString().toDoubleOrNull(),
            minPricePerKm = findViewById<EditText>(R.id.et_min_price_km).text.toString().toDoubleOrNull(),

            // Складний
            complexMinPrice = findViewById<EditText>(R.id.et_complex_min_price).text.toString().toDoubleOrNull(),
            complexKmInMin = findViewById<EditText>(R.id.et_complex_km_in_min).text.toString().toDoubleOrNull(),
            complexPriceKmCity = findViewById<EditText>(R.id.et_complex_city).text.toString().toDoubleOrNull(),
            complexPriceKmSuburbs = findViewById<EditText>(R.id.et_complex_suburbs).text.toString().toDoubleOrNull(),

            paymentType = paymentType
        )

        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@CreateFilterActivity)

                val res = if (editingFilterId != null) {
                    api.updateFilter(editingFilterId!!, request)
                } else {
                    api.createFilter(request)
                }

                if (res.isSuccessful) {
                    Toast.makeText(this@CreateFilterActivity,
                        if (editingFilterId != null) "Зміни збережено!" else "Фільтр створено!",
                        Toast.LENGTH_SHORT).show()
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
        val intent = Intent(this, FiltersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        // Переносим FiltersActivity на передний план мгновенно с нулевой анимацией
        val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())

        // Полностью гасим анимацию "открытия вперед" для всех версий Android
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                0,
                0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Вызываем родной finish, чтобы система сама красиво и нативно закрыла этот экран вправо
        super.finish()
    }
}