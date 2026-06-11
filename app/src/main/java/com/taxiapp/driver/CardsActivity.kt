package com.taxiapp.driver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue // ДОБАВЛЕНО для правильной установки шрифтов
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverCardDto
import kotlinx.coroutines.launch

class CardsActivity : AppCompatActivity() {

    private lateinit var rvCards: RecyclerView
    private val cardsList = mutableListOf<DriverCardDto>()

    private val webViewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadCards()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)

        findViewById<View>(R.id.btn_back_cards).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_card).setOnClickListener { initCardBinding() }

        rvCards = findViewById(R.id.rv_cards)
        rvCards.layoutManager = LinearLayoutManager(this)

        setupAdapter()
        loadCards()
    }

    private fun setupAdapter() {
        rvCards.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val card = cardsList[position]
                val text1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
                val text2 = holder.itemView.findViewById<TextView>(android.R.id.text2)

                text1.text = card.cardNumber
                text1.setTextColor(Color.WHITE)

                // СТАЛО ТАК (Исправление ошибки 16sp):
                text1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)

                text2.text = if (card.isMain) "Основна картка для виплат" else "Утримуйте для видалення"
                text2.setTextColor(if (card.isMain) Color.GREEN else Color.GRAY)

                holder.itemView.setOnClickListener {
                    if (!card.isMain) selectMainCard(card.id)
                }

                holder.itemView.setOnLongClickListener {
                    showDeleteConfirmDialog(card.id)
                    true
                }
            }

            override fun getItemCount() = cardsList.size
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).getCards()
                if (resp.isSuccessful && resp.body() != null) {
                    cardsList.clear()
                    cardsList.addAll(resp.body()!!)
                    rvCards.adapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun initCardBinding() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).initBindCard()
                if (resp.isSuccessful && resp.body() != null) {
                    val url = resp.body()!!["url"]
                    if (!url.isNullOrEmpty()) {
                        val intent = Intent(this@CardsActivity, WebViewActivity::class.java)

                        // СТАЛО ТАК (Исправление ошибки Overload resolution ambiguity):
                        intent.putExtra("URL", url as String?)

                        webViewLauncher.launch(intent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CardsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectMainCard(id: Long) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).selectMainCard(id)
                if (resp.isSuccessful) loadCards()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showDeleteConfirmDialog(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Видалити картку?")
            .setMessage("Ви впевнені, що хочете видалити цю картку з профілю?")
            .setPositiveButton("Видалити") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val resp = ApiClient.getInstance().getApiService(this@CardsActivity).deleteCard(id)
                        if (resp.isSuccessful) loadCards()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }
}