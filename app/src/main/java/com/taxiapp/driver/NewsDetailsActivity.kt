package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class NewsDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_details)

        findViewById<View>(R.id.btn_back_details).setOnClickListener { finish() }

        val title = intent.getStringExtra("title")
        val content = intent.getStringExtra("content")
        val date = intent.getStringExtra("date")
        val imageUrl = intent.getStringExtra("imageUrl")
        // Получаем Base URL из предыдущего экрана или используем дефолтный
        val baseUrl = intent.getStringExtra("baseUrl") ?: "http://192.168.0.100:8080"

        findViewById<TextView>(R.id.tv_full_title).text = title
        findViewById<TextView>(R.id.tv_full_content).text = content
        findViewById<TextView>(R.id.tv_full_date).text = date

        val imgBanner = findViewById<ImageView>(R.id.img_full_banner)

        if (!imageUrl.isNullOrEmpty()) {
            imgBanner.visibility = View.VISIBLE

            val fullUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "$baseUrl$imageUrl" // Исправлено: просто склеиваем
            }

            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(imgBanner)
        } else {
            imgBanner.visibility = View.GONE
        }
    }
}