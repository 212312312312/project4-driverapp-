package com.taxiapp.driver

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CarDto // Используем DTO
import com.taxiapp.driver.network.CarTariffDto
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class CarActivity : AppCompatActivity() {

    // Элементы переключения
    private lateinit var tabActive: TextView
    private lateinit var tabList: TextView
    private lateinit var scrollViewDetails: View
    private lateinit var recyclerCars: RecyclerView
    private lateinit var btnAddCar: FloatingActionButton
    private lateinit var progressBar: View

    private lateinit var carAdapter: CarAdapter
    private var activeCarId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        initViews()
        setupListeners()
        setupRecyclerView()

        // По умолчанию грузим детали активного авто
        loadActiveCarData()
    }

    private fun initViews() {
        tabActive = findViewById(R.id.tab_active)
        tabList = findViewById(R.id.tab_list)
        scrollViewDetails = findViewById(R.id.scroll_details)
        recyclerCars = findViewById(R.id.recycler_cars)
        btnAddCar = findViewById(R.id.btn_add_car)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Переключение вкладок
        tabActive.setOnClickListener { switchTab(true) }
        tabList.setOnClickListener { switchTab(false) }

        // Кнопка добавить авто
        btnAddCar.setOnClickListener { openAddCarForm() }

        // Другие кнопки (заглушки или переходы)
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

    // --- ИСПРАВЛЕННЫЙ МЕТОД ПЕРЕКЛЮЧЕНИЯ ---
    private fun switchTab(showActive: Boolean) {
        if (showActive) {
            // Вкладка "Активне"
            tabActive.setTextColor(Color.WHITE)
            // Сначала устанавливаем ресурс, потому что background мог быть null
            tabActive.setBackgroundResource(R.drawable.bg_round_button)
            tabActive.background.setTint(Color.parseColor("#333333"))

            // Сбрасываем вторую вкладку
            tabList.setTextColor(Color.parseColor("#888888"))
            tabList.background = null

            scrollViewDetails.visibility = View.VISIBLE
            recyclerCars.visibility = View.GONE
            btnAddCar.visibility = View.GONE

            loadActiveCarData()
        } else {
            // Вкладка "Список"
            tabList.setTextColor(Color.WHITE)
            // Сначала устанавливаем ресурс
            tabList.setBackgroundResource(R.drawable.bg_round_button)
            tabList.background.setTint(Color.parseColor("#333333"))

            // Сбрасываем первую вкладку
            tabActive.setTextColor(Color.parseColor("#888888"))
            tabActive.background = null

            scrollViewDetails.visibility = View.GONE
            recyclerCars.visibility = View.VISIBLE
            btnAddCar.visibility = View.VISIBLE

            loadCarList()
        }
    }

    // --- ЗАГРУЗКА АКТИВНОГО АВТО ---
    private fun loadActiveCarData() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@CarActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val car = profile.car // CarDto
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

    // Изменение внутри метода bindCarDetails в файле CarActivity.kt
    private fun bindCarDetails(car: CarDto) {
        findViewById<TextView>(R.id.tv_car_model).text = "${car.make} ${car.model}"
        findViewById<TextView>(R.id.tv_plate_number).text = car.plateNumber
        findViewById<TextView>(R.id.tv_car_year).text = car.year.toString()
        findViewById<TextView>(R.id.tv_car_type).text = car.carType ?: "Седан"

        val colorView = findViewById<View>(R.id.view_car_color)
        try {
            if (car.color.startsWith("#")) {
                colorView.setBackgroundColor(Color.parseColor(car.color))
            } else {
                colorView.setBackgroundColor(parseColorName(car.color))
            }
        } catch (e: Exception) {
            colorView.setBackgroundColor(Color.LTGRAY)
        }

        val imgCar = findViewById<ImageView>(R.id.img_car_photo)

        // ИСПРАВЛЕНО: Умный фолбек на стороне клиента. Если сервер прислал пустой photoUrl, приложение само берет photoRight
        val finalPhotoUrl = if (!car.photoUrl.isNullOrEmpty()) car.photoUrl else car.photoRight

        if (!finalPhotoUrl.isNullOrEmpty()) {
            imgCar.clearColorFilter()
            Glide.with(this).load(finalPhotoUrl).centerCrop().into(imgCar)
        } else {
            imgCar.setImageResource(R.drawable.ic_car)
            imgCar.setColorFilter(Color.parseColor("#444444"))
        }
    }

    // --- ЗАГРУЗКА СПИСКА ---
    private fun loadCarList() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Сначала узнаем ID активного
                val profileResp = ApiClient.getInstance().getApiService(this@CarActivity).getDriverProfile()
                activeCarId = profileResp.body()?.car?.id

                // Теперь список
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

    // --- СМЕНА АКТИВНОГО АВТО ---
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

        // Убираем лишний слэш и формируем URL
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

        for (tariff in tariffs) {
            val tariffView = LayoutInflater.from(this).inflate(R.layout.item_filter, container, false)
            val tvName = tariffView.findViewById<TextView>(R.id.tv_filter_name)
            tvName.text = "${tariff.name}\nУвімкнено"
            container.addView(tariffView)
        }
    }

    private fun parseColorName(name: String): Int {
        return when (name.lowercase()) {
            "білий", "white" -> Color.WHITE
            "чорний", "black" -> Color.BLACK
            "червоний", "red" -> Color.RED
            "синій", "blue" -> Color.BLUE
            "зелений", "green" -> Color.GREEN
            "жовтий", "yellow" -> Color.YELLOW
            "сірий", "gray", "grey" -> Color.GRAY
            "сріблястий", "silver" -> Color.LTGRAY
            else -> Color.LTGRAY
        }
    }
}