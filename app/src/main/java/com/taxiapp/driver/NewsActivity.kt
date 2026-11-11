package com.taxiapp.driver

import android.content.Context
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.NewsDto
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.toFullImageUrl
import kotlinx.coroutines.launch

class NewsActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var rvNews: RecyclerView
    private lateinit var tvEmpty: TextView
    private var emptyStateContainer: View? = null
    private val adapter = NewsAdapter()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

        val rootView = findViewById<View>(R.id.news_root) ?: findViewById(android.R.id.content)
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

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        rvNews = findViewById(R.id.rv_news)
        tvEmpty = findViewById(R.id.tv_empty)
        emptyStateContainer = findViewById(R.id.empty_state_container)

        rvNews.layoutManager = LinearLayoutManager(this)
        rvNews.adapter = adapter

        loadNews()
    }

    private fun setEmptyStateVisible(visible: Boolean) {
        if (emptyStateContainer != null) {
            emptyStateContainer?.visibility = if (visible) View.VISIBLE else View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun loadNews() {
        progressBar.visibility = View.VISIBLE
        setEmptyStateVisible(false)

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@NewsActivity).getNews()
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val newsList = response.body()!!
                    if (newsList.isEmpty()) {
                        setEmptyStateVisible(true)
                    } else {
                        setEmptyStateVisible(false)
                        adapter.setItems(newsList)
                    }
                } else {
                    setEmptyStateVisible(adapter.itemCount == 0)
                    Toast.makeText(this@NewsActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                setEmptyStateVisible(adapter.itemCount == 0)
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