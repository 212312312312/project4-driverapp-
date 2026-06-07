package com.taxiapp.driver

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CarDto
import com.taxiapp.driver.network.CarTariffDto
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class CarActivity : AppCompatActivity() {

    private lateinit var carTabs: TabLayout
    private lateinit var scrollViewDetails: View
    private lateinit var recyclerCars: RecyclerView
    private lateinit var btnAddCar: View
    private lateinit var progressBar: View

    private lateinit var carAdapter: CarAdapter
    private var activeCarId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        initViews()
        setupListeners()
        setupRecyclerView()

        loadActiveCarData()
    }

    private fun initViews() {
        carTabs = findViewById(R.id.car_tabs)
        scrollViewDetails = findViewById(R.id.scroll_details)
        recyclerCars = findViewById(R.id.recycler_cars)
        btnAddCar = findViewById(R.id.btn_add_car)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        carTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                switchTab(tab?.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnAddCar.setOnClickListener { openAddCarForm() }

        findViewById<View>(R.id.btn_branding).setOnClickListener {
            Toast.makeText(this, "Брендування: в розробці", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_services).setOnClickListener {
            Toast.makeText(this, "Послуги: в розробці", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_documents).setOnClickListener {
            val intent = Intent(this, CarDocumentsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        carAdapter = CarAdapter { selectedCar ->
            changeActiveCar(selectedCar)
        }
        recyclerCars.layoutManager = LinearLayoutManager(this)
        recyclerCars.adapter = carAdapter
    }

    private fun switchTab(showActive: Boolean) {
        val targetPosition = if (showActive) 0 else 1
        if (carTabs.selectedTabPosition != targetPosition) {
            carTabs.getTabAt(targetPosition)?.select()
        }

        if (showActive) {
            scrollViewDetails.visibility = View.VISIBLE
            recyclerCars.visibility = View.GONE
            btnAddCar.visibility = View.GONE
            loadActiveCarData()
        } else {
            scrollViewDetails.visibility = View.GONE
            recyclerCars.visibility = View.VISIBLE
            btnAddCar.visibility = View.VISIBLE
            loadCarList()
        }
    }

    private fun loadActiveCarData() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@CarActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val car = profile.car
                    activeCarId = car?.id

                    if (car != null) {
                        bindCarDetails(car)
                    } else {
                        findViewById<TextView>(R.id.tv_car_model).text = "Авто не призначено"
                        findViewById<TextView>(R.id.tv_plate_number).text = "---"
                        val imgCar = findViewById<ImageView>(R.id.img_car_photo)
                        imgCar.setImageResource(R.drawable.ic_car)
                        imgCar.setColorFilter(Color.parseColor("#444444"))
                    }
                    setupTariffs(profile.allowedTariffs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun bindCarDetails(car: CarDto) {
        findViewById<TextView>(R.id.tv_car_model).text = "${car.make} ${car.model}"
        findViewById<TextView>(R.id.tv_plate_number).text = car.plateNumber
        findViewById<TextView>(R.id.tv_car_year).text = car.year.toString()
        findViewById<TextView>(R.id.tv_car_type).text = car.carType ?: "Седан"

        // Исправлен тип элемента на MaterialCardView для поддержки закругленного квадратика
        val colorView = findViewById<com.google.android.material.card.MaterialCardView>(R.id.view_car_color)
        val colorStr = car.color.trim()

        val parsedColor = try {
            if (colorStr.startsWith("#")) {
                Color.parseColor(colorStr)
            } else {
                parseColorName(colorStr)
            }
        } catch (e: Exception) {
            Color.LTGRAY
        }

        // Чистая нативная перекраска фона закругленного квадрата
        colorView.setCardBackgroundColor(parsedColor)

        val imgCar = findViewById<ImageView>(R.id.img_car_photo)
        val finalPhotoUrl = if (!car.photoUrl.isNullOrEmpty()) car.photoUrl else car.photoRight

        if (!finalPhotoUrl.isNullOrEmpty()) {
            imgCar.clearColorFilter()
            Glide.with(this).load(finalPhotoUrl).centerCrop().into(imgCar)
        } else {
            imgCar.setImageResource(R.drawable.ic_car)
            imgCar.setColorFilter(Color.parseColor("#444444"))
        }
    }

    private fun loadCarList() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val profileResp = ApiClient.getInstance().getApiService(this@CarActivity).getDriverProfile()
                activeCarId = profileResp.body()?.car?.id

                val listResp = ApiClient.getInstance().getApiService(this@CarActivity).getMyCars()
                if (listResp.isSuccessful && listResp.body() != null) {
                    carAdapter.submitList(listResp.body()!!, activeCarId)
                } else {
                    Toast.makeText(this@CarActivity, "Список порожній", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CarActivity, "Помилка завантаження списку", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // --- ОШИБКА ИСПРАВЛЕНА: лишняя битая строка View.grid@VISIBLE удалена полностью ---
    private fun changeActiveCar(car: CarDto) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@CarActivity).selectActiveCar(car.id)
                if (response.isSuccessful) {
                    Toast.makeText(this@CarActivity, "Авто успішно змінено!", Toast.LENGTH_SHORT).show()
                    loadCarList()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Помилка"
                    Toast.makeText(this@CarActivity, "Не вдалося змінити: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CarActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun openAddCarForm() {
        val token = SessionManager(this).fetchAuthToken()
        if (token.isNullOrEmpty()) return

        val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
        val fullUrl = "$baseUrl/api/v1/driver/forms/add-car?token=$token"

        Log.d("CarActivity", "Opening Form: $fullUrl")

        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("URL", fullUrl)
        startActivity(intent)
    }

    private fun setupTariffs(tariffs: List<CarTariffDto>?) {
        val container = findViewById<LinearLayout>(R.id.layout_tariffs_container)
        container.removeAllViews()
        if (tariffs.isNullOrEmpty()) return

        val thumbStates = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.driver_neon_teal),
                androidx.core.content.ContextCompat.getColor(this, R.color.driver_text_secondary)
            )
        )

        val trackStates = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                Color.parseColor("#4D00E5FF"),
                Color.parseColor("#33FFFFFF")
            )
        )

        for (tariff in tariffs) {
            val tariffView = LayoutInflater.from(this).inflate(R.layout.item_car_tariff, container, false)

            val tvName = tariffView.findViewById<TextView>(R.id.tv_tariff_name)
            val switchToggle = tariffView.findViewById<SwitchMaterial>(R.id.switch_tariff_toggle)

            tvName.text = tariff.name

            switchToggle.thumbTintList = thumbStates
            switchToggle.trackTintList = trackStates

            switchToggle.isChecked = true

            container.addView(tariffView)
        }
    }

    private fun parseColorName(name: String): Int {
        // Приводим первую букву к заглавной, остальные — к строчным (например: "Белый", "White")
        val cleaned = name.trim().lowercase().replaceFirstChar { it.uppercase() }

        return when (cleaned) {
            "Білий", "Белый", "White" -> Color.WHITE
            "Чорний", "Черный", "Black" -> Color.BLACK
            "Червоний", "Красный", "Red" -> Color.RED
            "Синій", "Голубой", "Синий", "Blue" -> Color.BLUE
            "Зелений", "Зеленый", "Green" -> Color.GREEN
            "Жовтий", "Желтый", "Yellow" -> Color.YELLOW
            "Сірий", "Серый", "Gray", "Grey" -> Color.GRAY
            "Сріблястий", "Серебристый", "Silver" -> Color.LTGRAY
            else -> {
                // Для HEX-кодов (например, "FF5722") используем полный верхний регистр
                val hexCleaned = name.trim().uppercase()
                if (hexCleaned.matches(Regex("[0-9A-F]{6}")) || hexCleaned.matches(Regex("[0-9A-F]{8}"))) {
                    try {
                        Color.parseColor("#$hexCleaned")
                    } catch (e: Exception) {
                        Color.LTGRAY
                    }
                } else {
                    Color.LTGRAY
                }
            }
        }
    }
}