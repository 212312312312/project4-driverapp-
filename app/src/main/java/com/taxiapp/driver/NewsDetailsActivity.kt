package com.taxiapp.driver

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.toFullImageUrl

class NewsDetailsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_details)

        val rootView = findViewById<View>(R.id.news_details_root) ?: findViewById(android.R.id.content)
        val headerContent = findViewById<View>(R.id.header_content)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerContent != null) {
                headerContent.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (28 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        findViewById<View>(R.id.btn_back_details).setOnClickListener { finish() }

        val title = intent.getStringExtra("title")
        val content = intent.getStringExtra("content")
        val date = intent.getStringExtra("date")
        val imageUrl = intent.getStringExtra("imageUrl")

        findViewById<TextView>(R.id.tv_full_title).text = title
        findViewById<TextView>(R.id.tv_full_content).text = content
        findViewById<TextView>(R.id.tv_full_date).text = date

        val imgBanner = findViewById<ImageView>(R.id.img_full_banner)

        val fullUrl = toFullImageUrl(imageUrl)
        if (!fullUrl.isNullOrEmpty()) {
            imgBanner.visibility = View.VISIBLE
            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(imgBanner)
        } else {
            imgBanner.visibility = View.GONE
        }
    }
}