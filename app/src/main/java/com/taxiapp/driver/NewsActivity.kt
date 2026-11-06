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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.NewsDto
import com.taxiapp.driver.utils.toFullImageUrl
import kotlinx.coroutines.launch

class NewsActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var rvNews: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = NewsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

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

    // ✅ ИСПРАВЛЕНО: Используем lifecycleScope вместо утекающего CoroutineScope(Dispatchers.IO)
    // и единый toFullImageUrl() вместо захардкоженного 192.168.0.100:8080
    private fun loadNews() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@NewsActivity).getNews()
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
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@NewsActivity, "Помилка мережі: ${e.message}", Toast.LENGTH_SHORT).show()
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
            holder.bind(items[position])
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

                val fullUrl = toFullImageUrl(news.imageUrl)
                if (!fullUrl.isNullOrEmpty()) {
                    imgBanner.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(fullUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
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
                    itemView.context.startActivity(intent)
                }
            }
        }
    }
}