package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.NewsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewsActivity : AppCompatActivity() {

    // ВАЖНО: Укажи здесь IP своего компьютера, где запущен сервер!
    // Если эмулятор: "http://10.0.2.2:8080"
    // Если реальный телефон: "http://192.168.0.104:8080" (проверь свой IP через ipconfig)
    private val BASE_IMAGE_URL = "http://192.168.0.100:8080"

    private lateinit var progressBar: ProgressBar
    private lateinit var rvNews: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = NewsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ для сохранения Edge-to-Edge фона на Android 15
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        rvNews = findViewById(R.id.rv_news)
        tvEmpty = findViewById(R.id.tv_empty)

        rvNews.layoutManager = LinearLayoutManager(this)
        rvNews.adapter = adapter

        loadNews()
    }

    private fun loadNews() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@NewsActivity).getNews()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val newsList = response.body()!!
                        if (newsList.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            adapter.setItems(newsList)
                        }
                    } else {
                        Toast.makeText(this@NewsActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NewsActivity, "Помилка мережі: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class NewsAdapter : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {
        private val items = mutableListOf<NewsDto>()

        fun setItems(newItems: List<NewsDto>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
            return NewsViewHolder(view)
        }

        override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tv_news_title)
            private val tvDate: TextView = itemView.findViewById(R.id.tv_news_date)
            private val tvSnippet: TextView = itemView.findViewById(R.id.tv_news_snippet)
            private val imgBanner: ImageView = itemView.findViewById(R.id.img_news_banner)

            fun bind(news: NewsDto) {
                tvTitle.text = news.title
                tvDate.text = news.date
                tvSnippet.text = news.content

                if (!news.imageUrl.isNullOrEmpty()) {
                    imgBanner.visibility = View.VISIBLE

                    // ИСПРАВЛЕННАЯ ЛОГИКА:
                    // Если URL уже полный (начинается с http), берем его.
                    // Если нет — просто клеим BaseURL + путь с сервера (он уже содержит /uploads/...)
                    val fullUrl = if (news.imageUrl.startsWith("http")) {
                        news.imageUrl
                    } else {
                        "$BASE_IMAGE_URL${news.imageUrl}"
                    }

                    Glide.with(itemView.context)
                        .load(fullUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background) // Если ошибка, покажет заглушку
                        .into(imgBanner)
                } else {
                    imgBanner.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    val intent = Intent(itemView.context, NewsDetailsActivity::class.java)
                    intent.putExtra("title", news.title)
                    intent.putExtra("content", news.content)
                    intent.putExtra("date", news.date)
                    intent.putExtra("imageUrl", news.imageUrl)
                    intent.putExtra("baseUrl", BASE_IMAGE_URL) // Передаем базовый URL
                    itemView.context.startActivity(intent)
                }
            }
        }
    }
}