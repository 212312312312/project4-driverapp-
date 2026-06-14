package com.taxiapp.driver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue // ДОБАВЛЕНО для правильной установки шрифтов
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
                // Підключаємо наш новий кастомний преміум-макет елемента списку
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_driver_card, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val card = cardsList[position]

                val tvCardNumber = holder.itemView.findViewById<TextView>(R.id.tv_card_number)
                val tvCardStatus = holder.itemView.findViewById<TextView>(R.id.tv_card_status)
                val ivCardCheck = holder.itemView.findViewById<ImageView>(R.id.iv_card_check)
                val ivCardIcon = holder.itemView.findViewById<ImageView>(R.id.iv_card_icon)

                // Встановлюємо номер карти
                tvCardNumber.text = card.cardNumber

                // Логіка підсвічування та відображення елементів залежно від статусу карти
                if (card.isMain) {
                    tvCardStatus.text = "Основна картка для виплат"
                    tvCardStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@CardsActivity, R.color.driver_neon_teal))

                    // Включаем ЖИРНЫЙ стиль шрифта для главной карты
                    tvCardStatus.setTypeface(tvCardStatus.typeface, android.graphics.Typeface.BOLD)

                    ivCardCheck.visibility = View.VISIBLE
                    ivCardIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(this@CardsActivity, R.color.driver_neon_teal))
                } else {
                    tvCardStatus.text = "Утримуйте для видалення"
                    tvCardStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@CardsActivity, R.color.driver_text_secondary))

                    // Возвращаем ОБЫЧНЫЙ стиль шрифта для остальных карт
                    tvCardStatus.setTypeface(tvCardStatus.typeface, android.graphics.Typeface.NORMAL)

                    ivCardCheck.visibility = View.GONE
                    ivCardIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(this@CardsActivity, R.color.driver_text_primary))
                }

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
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_card, null)
        builder.setView(dialogView)

        val dialog = builder.create()
        // Делаем стандартный фон AlertDialog прозрачным, чтобы углы нашего bg_bottom_nav_floating не имели белых краев
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Находим кнопки из нашего шаблона
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btn_cancel_delete)
        val btnConfirm = dialogView.findViewById<android.view.View>(R.id.btn_confirm_delete)

        // Нажатие на "Скасувати"
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Нажатие на "Видалити"
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.getInstance().getApiService(this@CardsActivity).deleteCard(id)
                    if (resp.isSuccessful) loadCards()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        dialog.show()
    }
}