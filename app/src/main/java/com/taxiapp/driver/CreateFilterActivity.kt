package com.taxiapp.driver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CreateFilterRequest
import com.taxiapp.driver.network.Sector
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

    // Змінна, щоб розуміти, для якого блоку ми зараз вибираємо сектори
    private var isPickingFrom = true

    // Лаунчер для відкриття екрана вибору секторів та обробки результату
    private val sectorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val ids = result.data?.getLongArrayExtra("SELECTED_IDS")?.toMutableList() ?: mutableListOf()
            if (isPickingFrom) {
                selectedFromIds = ids
                // Оновлюємо текст на кнопці "Звідки"
                btnSelectFromSectors.text = "Вибрано секторів: ${ids.size}"
            } else {
                selectedToIds = ids
                // Оновлюємо текст лічильника "Куди"
                tvSelectedToCount.text = "Вибрано секторів: ${ids.size}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_filter)

        initViews()
        setupListeners()
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

        // Налаштування спиннера оплати
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Будь-який", "Готівка", "Картка"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPayment.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back_create).setOnClickListener { finish() }

        // Перемикач ЗВІДКИ (Радіус або Сектори)
        rgFromType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_distance) {
                containerDistance.visibility = View.VISIBLE
                btnSelectFromSectors.visibility = View.GONE
            } else {
                containerDistance.visibility = View.GONE
                btnSelectFromSectors.visibility = View.VISIBLE
            }
        }

        // SeekBar відстані (від 0.5 до 30.0 км з кроком 0.5)
        sbDistance.max = 59
        sbDistance.progress = 9 // 5.0 км
        sbDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                selectedDistance = (p / 2.0) + 0.5
                tvDistanceVal.text = "Радіус: $selectedDistance км"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Перемикач ТАРИФУ (Простий або Складний)
        rgTariffType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_simple) {
                containerSimple.visibility = View.VISIBLE
                containerComplex.visibility = View.GONE
            } else {
                containerSimple.visibility = View.GONE
                containerComplex.visibility = View.VISIBLE
            }
        }

        // Кнопка вибору секторів "Звідки" -> тепер відкриває екран з картою
        btnSelectFromSectors.setOnClickListener {
            openSectorPicker(true)
        }

        // Кнопка вибору секторів "Куди" -> тепер відкриває екран з картою
        btnSelectToSectors.setOnClickListener {
            openSectorPicker(false)
        }

        btnCreate.setOnClickListener { saveFilter() }
    }

    /**
     * Відкриває SectorSelectionActivity та передає поточний вибір
     */
    private fun openSectorPicker(pickingFrom: Boolean) {
        isPickingFrom = pickingFrom
        val intent = Intent(this, SectorSelectionActivity::class.java)

        // Передаємо вже вибрані ID, щоб вони підсвітилися на карті відразу
        val currentSelection = if (isPickingFrom) selectedFromIds else selectedToIds
        intent.putExtra("SELECTED_IDS", currentSelection.toLongArray())

        sectorPickerLauncher.launch(intent)
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
            fromType = if (rgFromType.checkedRadioButtonId == R.id.rb_distance) "DISTANCE" else "SECTORS",
            fromDistance = if (rgFromType.checkedRadioButtonId == R.id.rb_distance) selectedDistance else null,
            fromSectors = selectedFromIds,
            toSectors = selectedToIds,
            tariffType = if (rgTariffType.checkedRadioButtonId == R.id.rb_simple) "SIMPLE" else "COMPLEX",
            minPrice = findViewById<EditText>(R.id.et_min_price).text.toString().toDoubleOrNull(),
            minPricePerKm = findViewById<EditText>(R.id.et_min_price_km).text.toString().toDoubleOrNull(),
            complexMinPrice = findViewById<EditText>(R.id.et_complex_min_price).text.toString().toDoubleOrNull(),
            complexPriceKmCity = findViewById<EditText>(R.id.et_complex_city).text.toString().toDoubleOrNull(),
            paymentType = paymentType
        )

        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@CreateFilterActivity).createFilter(request)
                if (res.isSuccessful) {
                    Toast.makeText(this@CreateFilterActivity, "Фільтр створено!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@CreateFilterActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateFilterActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }
}