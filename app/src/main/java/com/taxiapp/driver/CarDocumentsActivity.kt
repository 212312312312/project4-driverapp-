package com.taxiapp.driver

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView // Используем ImageView вместо ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import kotlinx.coroutines.launch

class CarDocumentsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_documents)

        // ИСПРАВЛЕНИЕ: Изменили тип на ImageView, чтобы соответствовать новой разметке хедера
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        loadDocuments()
    }

    private fun loadDocuments() {
        lifecycleScope.launch {
            try {
                // Запрашиваем профиль водителя (там лежит объект Car с новыми полями)
                val response = ApiClient.getInstance().getApiService(this@CarDocumentsActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val car = profile.car

                    if (car != null) {
                        // Техпаспорт
                        loadImage(car.techPassportFront, R.id.img_tech_front)
                        loadImage(car.techPassportBack, R.id.img_tech_back)

                        // Страховка
                        loadImage(car.insurancePhoto, R.id.img_insurance)

                        // Авто (6 сторон)
                        loadImage(car.photoFront, R.id.img_photo_front)
                        loadImage(car.photoBack, R.id.img_photo_back)
                        loadImage(car.photoLeft, R.id.img_photo_left)
                        loadImage(car.photoRight, R.id.img_photo_right)
                        loadImage(car.photoSeatsFront, R.id.img_seats_front)
                        loadImage(car.photoSeatsBack, R.id.img_seats_back)
                    }
                } else {
                    Toast.makeText(this@CarDocumentsActivity, "Не вдалося завантажити дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CarDocumentsActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImage(url: String?, imageViewId: Int) {
        val imageView = findViewById<ImageView>(imageViewId)

        // ВАЖНО: Сбрасываем фильтры, чтобы фото было оригинальным
        imageView.clearColorFilter()
        imageView.setPadding(0, 0, 0, 0)

        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.ic_driver_placeholder)
                .error(R.drawable.ic_driver_placeholder)
                .into(imageView)
        } else {
            // Если фото нет - ставим серую заглушку
            imageView.setImageResource(R.drawable.ic_driver_placeholder)
            imageView.setColorFilter(Color.parseColor("#444444"))
            imageView.setPadding(50, 50, 50, 50) // Чтобы иконка была меньше
        }
    }
}