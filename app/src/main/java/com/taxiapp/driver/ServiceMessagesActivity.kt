package com.taxiapp.driver

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverNotificationDto
import com.taxiapp.driver.utils.LocaleHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ServiceMessagesActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private var emptyMessagesContainer: View? = null
    private lateinit var adapter: MessagesAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_messages)

        val rootView = findViewById<View>(R.id.service_messages_root) ?: findViewById(android.R.id.content)
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

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.rvMessages)
        emptyMessagesContainer = findViewById(R.id.empty_messages_container)

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.driver_neon_teal))
        swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.driver_black_bg))

        adapter = MessagesAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadMessages() }

        loadMessages()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyMessagesContainer?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun loadMessages() {
        swipeRefresh.isRefreshing = true

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
                        updateEmptyState(list.isEmpty())
                    } else {
                        updateEmptyState(adapter.itemCount == 0)
                        Toast.makeText(
                            this@ServiceMessagesActivity,
                            "Помилка завантаження",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<DriverNotificationDto>>, t: Throwable) {
                    swipeRefresh.isRefreshing = false
                    updateEmptyState(adapter.itemCount == 0)
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
            holder.viewUnreadDot?.visibility = if (!item.isRead) View.VISIBLE else View.GONE

            val context = holder.itemView.context

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
                    holder.ivIcon.setImageResource(R.drawable.ic_messages)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.driver_neon_teal))
                }
            }
        }

        override fun getItemCount() = items.size

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvBody: TextView = view.findViewById(R.id.tvBody)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
            val viewUnreadDot: View? = view.findViewById(R.id.viewUnreadDot)
        }
    }
}