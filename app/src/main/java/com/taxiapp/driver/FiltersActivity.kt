package com.taxiapp.driver

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverFilter
import com.taxiapp.driver.network.UpdateFilterModeRequest
import kotlinx.coroutines.launch
import org.json.JSONObject

class FiltersActivity : AppCompatActivity() {

    private lateinit var rvFilters: RecyclerView
    private lateinit var adapter: FiltersAdapter
    private var filterList = mutableListOf<DriverFilter>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        rvFilters = findViewById(R.id.rv_filters)
        rvFilters.layoutManager = LinearLayoutManager(this)

        adapter = FiltersAdapter(filterList,
            onUpdateMode = { f, req -> updateFilterMode(f, req) },
            onEdit = { f -> openEditFilter(f) },
            onLongClick = { f -> showDeleteDialog(f) }
        )
        rvFilters.adapter = adapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_filter).setOnClickListener {
            startActivity(Intent(this, CreateFilterActivity::class.java))
        }
        findViewById<View>(R.id.btn_disable_all).setOnClickListener { disableAll() }
    }

    override fun onResume() {
        super.onResume()
        loadFilters()
    }

    private fun loadFilters() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@FiltersActivity).getFilters()
                if (res.isSuccessful && res.body() != null) {
                    filterList.clear()
                    filterList.addAll(res.body()!!)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun openEditFilter(filter: DriverFilter) {
        val intent = Intent(this, CreateFilterActivity::class.java)
        intent.putExtra("FILTER_DATA", filter)
        startActivity(intent)
    }

    private fun showDeleteDialog(filter: DriverFilter) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundResource(R.drawable.bg_bottom_nav_floating)
            layoutParams = ViewGroup.LayoutParams(900, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = "Видалити фільтр?"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "'${filter.name}'"
            textSize = 16f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnYes = MaterialButton(this).apply {
            text = "Видалити"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F44336"))
            cornerRadius = 20
            setOnClickListener {
                deleteFilter(filter)
                dialog.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 20
            }
        }

        val btnNo = MaterialButton(this).apply {
            text = "Скасувати"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            strokeWidth = 2
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            cornerRadius = 20
            setOnClickListener { dialog.dismiss() }
        }

        btnContainer.addView(btnYes)
        btnContainer.addView(btnNo)
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(btnContainer)

        dialog.setContentView(layout)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun updateFilterMode(filter: DriverFilter, req: UpdateFilterModeRequest) {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@FiltersActivity)
                    .updateFilterMode(filter.id, req)

                if (res.isSuccessful && res.body() != null) {
                    val index = filterList.indexOfFirst { it.id == filter.id }
                    if (index != -1) {
                        filterList[index] = res.body()!!
                        adapter.notifyItemChanged(index)
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(res.errorBody()?.string()).getString("message")
                    } catch (e: Exception) {
                        "Помилка оновлення"
                    }
                    Toast.makeText(this@FiltersActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FiltersActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun deleteFilter(filter: DriverFilter) {
        lifecycleScope.launch {
            if (ApiClient.getInstance().getApiService(this@FiltersActivity).deleteFilter(filter.id).isSuccessful) {
                loadFilters()
                Toast.makeText(this@FiltersActivity, "Фільтр видалено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableAll() {
        lifecycleScope.launch {
            if (ApiClient.getInstance().getApiService(this@FiltersActivity).disableAllFilters().isSuccessful) {
                loadFilters()
                Toast.makeText(this@FiltersActivity, "Всі фільтри вимкнено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- АДАПТЕР ---
    inner class FiltersAdapter(
        private val list: List<DriverFilter>,
        private val onUpdateMode: (DriverFilter, UpdateFilterModeRequest) -> Unit,
        private val onEdit: (DriverFilter) -> Unit,
        private val onLongClick: (DriverFilter) -> Unit
    ) : RecyclerView.Adapter<FiltersAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tv_filter_name)
            val from: TextView = v.findViewById(R.id.tv_filter_from)
            val to: TextView = v.findViewById(R.id.tv_filter_to)
            val desc: TextView = v.findViewById(R.id.tv_filter_desc)
            val switchMain: SwitchMaterial = v.findViewById(R.id.switch_main_toggle)

            val btnAuto: MaterialButton = v.findViewById(R.id.btn_mode_auto)
            val btnEther: MaterialButton = v.findViewById(R.id.btn_mode_ether)
            val btnCycle: MaterialButton = v.findViewById(R.id.btn_mode_cycle)

            init {
                v.setOnClickListener { onEdit(list[adapterPosition]) }
                v.setOnLongClickListener {
                    onLongClick(list[adapterPosition])
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = list[position]
            holder.name.text = f.name

            // 1. ЗВІДКИ
            holder.from.text = if (f.fromType == "DISTANCE") {
                "Радіус ${f.fromDistance ?: 0.0} км"
            } else {
                "Сектори подачі (${f.fromSectors.size})"
            }

            // 2. КУДИ
            holder.to.text = if (f.toSectors.isEmpty()) {
                "Будь-куди"
            } else {
                "Сектори (${f.toSectors.size})"
            }

            // 3. ТАРИФ
            val tariffText = if (f.tariffType == "SIMPLE") {
                val base = "від ${f.minPrice ?: 0.0} ₴"
                if (f.minPricePerKm != null && f.minPricePerKm > 0) {
                    "$base + ${f.minPricePerKm} ₴/км"
                } else {
                    base
                }
            } else {
                "Мін: ${f.complexMinPrice ?: 0.0} ₴ • Місто: ${f.complexPriceKmCity ?: 0.0} ₴ • Передмістя: ${f.complexPriceKmSuburbs ?: 0.0} ₴"
            }

            val payType = when (f.paymentType) {
                "CASH" -> "Готівка"
                "CARD" -> "Картка"
                else -> "Будь-яка"
            }
            holder.desc.text = "$tariffText • $payType"

            // Первоначальная отрисовка подложек из базы данных
            updateBtnStyle(holder.btnEther, f.isEther)
            updateBtnStyle(holder.btnAuto, f.isAuto)
            updateBtnStyle(holder.btnCycle, f.isCycle)

            // --- ГОЛОВНИЙ СВІТЧ ---
            holder.switchMain.setOnCheckedChangeListener(null)
            holder.switchMain.isChecked = f.isActive
            holder.switchMain.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) {
                    sendUpdate(f, ether = false, auto = false, cycle = false)
                } else {
                    sendUpdate(f, ether = true, auto = false, cycle = false)
                }
            }

            // --- КЛИКИ С ИГНОРИРОВАНИЕМ СЕРОГО МЕЛЬКАНИЯ ---
            holder.btnEther.setOnClickListener {
                val nextEther = !f.isEther
                updateBtnStyle(holder.btnEther, nextEther) // Гасим/включаем мгновенно!
                sendUpdate(f, ether = nextEther, auto = f.isAuto, cycle = f.isCycle)
            }

            holder.btnAuto.setOnClickListener {
                val newAuto = !f.isAuto
                val newCycle = if (newAuto) false else f.isCycle

                updateBtnStyle(holder.btnAuto, newAuto)
                updateBtnStyle(holder.btnCycle, newCycle)

                sendUpdate(f, ether = f.isEther, auto = newAuto, cycle = newCycle)
            }

            holder.btnCycle.setOnClickListener {
                val newCycle = !f.isCycle
                val newAuto = if (newCycle) false else f.isAuto

                updateBtnStyle(holder.btnCycle, newCycle)
                updateBtnStyle(holder.btnAuto, newAuto)

                sendUpdate(f, ether = f.isEther, auto = newAuto, cycle = newCycle)
            }
        }

        private fun sendUpdate(f: DriverFilter, ether: Boolean, auto: Boolean, cycle: Boolean) {
            val isActive = ether || auto || cycle
            onUpdateMode(f, UpdateFilterModeRequest(isActive, ether, auto, cycle))
        }

        private fun updateBtnStyle(btn: MaterialButton, isActive: Boolean) {
            val isDarkTheme = (btn.context.resources.configuration.uiMode
                    and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

            btn.isSelected = isActive // Переключаем стейт селектора для иконок

            if (isActive) {
                // Если режим горит -> накатываем бирюзовую подложку, красим контент в черный
                btn.setBackgroundResource(R.drawable.bg_status_pill)
                btn.setTextColor(Color.BLACK)
                btn.iconTint = android.content.res.ColorStateList.valueOf(Color.BLACK)
            } else {
                // Если режим выключен -> полностью затираем фон в прозрачный, без единого блика
                btn.setBackgroundColor(Color.TRANSPARENT)
                val inactiveColor = if (isDarkTheme) Color.parseColor("#B0B0B0") else Color.parseColor("#757575")
                btn.setTextColor(inactiveColor)
                btn.iconTint = android.content.res.ColorStateList.valueOf(inactiveColor)
            }
        }

        override fun getItemCount() = list.size
    }
}