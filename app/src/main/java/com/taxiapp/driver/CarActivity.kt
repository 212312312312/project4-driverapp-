package com.taxiapp.driver

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
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.CarTariffDto
import kotlinx.coroutines.launch

class CarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Заглушки для кнопок меню
        findViewById<View>(R.id.btn_branding).setOnClickListener {
            Toast.makeText(this, "Брендування: в розробці", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_services).setOnClickListener {
            Toast.makeText(this, "Послуги: в розробці", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_documents).setOnClickListener {
            // Переход на новый экран
            val intent = android.content.Intent(this, CarDocumentsActivity::class.java)
            startActivity(intent)
        }

        loadCarData()
    }

    private fun loadCarData() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@CarActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val car = profile.car

                    // ЛОГ ССЫЛКИ (Если ссылка есть тут - сервер молодец)
                    Log.d("CarDebug", "CAR URL: ${car?.photoUrl}")

                    if (car != null) {
                        findViewById<TextView>(R.id.tv_car_model).text = "${car.make} ${car.model}"
                        findViewById<TextView>(R.id.tv_plate_number).text = car.plateNumber
                        findViewById<TextView>(R.id.tv_car_year).text = car.year.toString()
                        findViewById<TextView>(R.id.tv_car_type).text = car.carType ?: "Седан"

                        // Цвет авто
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

                        // --- ЗАГРУЗКА ФОТО (КАК АВАТАРКА) ---
                        val imgCar = findViewById<ImageView>(R.id.img_car_photo)

                        if (!car.photoUrl.isNullOrEmpty()) {
                            // 1. Убираем фильтр (хотя мы убрали его и в XML, тут для надежности)
                            imgCar.clearColorFilter()

                            // 2. Glide как в MainActivity
                            Glide.with(this@CarActivity)
                                .load(car.photoUrl)
                                .centerCrop()
                                .placeholder(R.drawable.ic_car) // Пока грузим - машинка
                                .error(R.drawable.ic_car)       // Если ошибка - машинка
                                .into(imgCar)
                        } else {
                            // Если фото нет вообще - ставим заглушку и серый цвет
                            imgCar.setImageResource(R.drawable.ic_car)
                            imgCar.setColorFilter(Color.parseColor("#444444"))
                        }
                        // ------------------------------------

                    } else {
                        findViewById<TextView>(R.id.tv_car_model).text = "Авто не призначено"
                    }
                    setupTariffs(profile.allowedTariffs)

                } else {
                    Toast.makeText(this@CarActivity, "Не вдалося завантажити дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CarActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }   

    private fun setupTariffs(tariffs: List<CarTariffDto>?) {
        val container = findViewById<LinearLayout>(R.id.layout_tariffs_container)
        container.removeAllViews()

        if (tariffs.isNullOrEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "Немає доступних тарифів"
            emptyTv.setTextColor(Color.GRAY)
            container.addView(emptyTv)
            return
        }

        for (tariff in tariffs) {
            val tariffView = LayoutInflater.from(this).inflate(R.layout.item_filter, container, false)
            val tvName = tariffView.findViewById<TextView>(R.id.tv_filter_name)
            if (tvName != null) {
                tvName.text = tariff.name
                tvName.append("\nУвімкнено")
            }
            tariffView.setOnClickListener {
                Toast.makeText(this, "Зміна статусу тарифу ${tariff.name}", Toast.LENGTH_SHORT).show()
            }
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