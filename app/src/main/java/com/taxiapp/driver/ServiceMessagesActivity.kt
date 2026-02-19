package com.taxiapp.driver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverNotificationDto
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ServiceMessagesActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_messages)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.rvMessages)

        adapter = MessagesAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadMessages() }

        loadMessages()
    }

    private fun loadMessages() {
        swipeRefresh.isRefreshing = true

        // ✅ ИСПРАВЛЕНИЕ: Используем правильный вызов для твоего ApiClient
        ApiClient.getInstance().getApiService(this).getNotifications()
            .enqueue(object : Callback<List<DriverNotificationDto>> {
                override fun onResponse(
                    call: Call<List<DriverNotificationDto>>,
                    response: Response<List<DriverNotificationDto>>
                ) {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        val list = response.body() ?: emptyList()
                        adapter.submitList(list)
                    } else {
                        Toast.makeText(
                            this@ServiceMessagesActivity,
                            "Помилка завантаження",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<DriverNotificationDto>>, t: Throwable) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this@ServiceMessagesActivity,
                        "Немає зв'язку",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        private var items: List<DriverNotificationDto> = emptyList()

        fun submitList(newItems: List<DriverNotificationDto>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_service_message, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvBody.text = item.body
            holder.tvDate.text = item.date

            val context = holder.itemView.context

            // Логика иконок и цветов
            when (item.type) {
                "PAYMENT" -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_wallet)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.marker_green))
                }
                "ORDER_CANCEL" -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_circle_red)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.driver_error_red))
                }
                else -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_stat_star)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.taxi_yellow))
                }
            }
        }

        override fun getItemCount() = items.size

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvBody: TextView = view.findViewById(R.id.tvBody)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        }
    }
}