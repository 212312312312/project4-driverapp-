package com.taxiapp.driver

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverFilter
import com.taxiapp.driver.network.UpdateFilterModeRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class FiltersActivity : AppCompatActivity() {

    companion object {
        // Кеш назв секторів у пам'яті, щоб виводити реальні назви ("НОВОПЕЧЕРСЬКІ ЛИПКИ +4") як на скріншоті
        @Volatile private var cachedSectorNames: Map<Long, String> = emptyMap()
    }

    private lateinit var rvFilters: RecyclerView
    private lateinit var btnDisableContainer: View
    private lateinit var btnDisableAll: TextView
    private lateinit var adapter: FiltersAdapter
    private val filterList = mutableListOf<DriverFilter>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        val rootView = findViewById<View>(R.id.filters_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_filters_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        rvFilters = findViewById(R.id.rv_filters)
        btnDisableContainer = findViewById(R.id.btn_disable_container)
        btnDisableAll = findViewById(R.id.btn_disable_all)
        rvFilters.layoutManager = LinearLayoutManager(this)

        adapter = FiltersAdapter(
            list = filterList,
            onUpdateMode = { f, req -> updateFilterMode(f, req) },
            onEdit = { f -> openEditFilter(f) },
            onLongClick = { f -> showDeleteDialog(f) }
        )
        rvFilters.adapter = adapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_filter).setOnClickListener {
            startActivity(Intent(this, CreateFilterActivity::class.java))
        }
        btnDisableAll.setOnClickListener { disableAll() }
        updateDisableAllButtonState()
    }

    override fun onResume() {
        super.onResume()
        loadFiltersAndSectors()
    }

    private fun loadFiltersAndSectors() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getInstance().getApiService(this@FiltersActivity)
                coroutineScope {
                    val filtersDeferred = async { api.getFilters() }
                    val sectorsDeferred = if (cachedSectorNames.isEmpty()) {
                        async { api.getSectors() }
                    } else {
                        null
                    }

                    val sectorsRes = sectorsDeferred?.await()
                    val sectorsBody = sectorsRes?.body()
                    if (sectorsRes != null && sectorsRes.isSuccessful && sectorsBody != null) {
                        cachedSectorNames = sectorsBody.associate { it.id to it.name }
                    }

                    val res = filtersDeferred.await()
                    val filtersBody = res.body()
                    if (res.isSuccessful && filtersBody != null) {
                        filterList.clear()
                        filterList.addAll(filtersBody)
                        adapter.notifyDataSetChanged()
                        updateDisableAllButtonState()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateDisableAllButtonState() {
        val hasAnyActive = filterList.any { it.isActive }
        val activeColor = ContextCompat.getColor(this, R.color.driver_neon_teal)
        val inactiveColor = Color.parseColor("#A7AAB3")

        btnDisableContainer.backgroundTintList = ColorStateList.valueOf(
            if (hasAnyActive) activeColor else inactiveColor
        )
        btnDisableAll.isEnabled = hasAnyActive
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
            typeface = Typeface.DEFAULT_BOLD
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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 20
            }
        }

        val btnNo = MaterialButton(this).apply {
            text = "Скасувати"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            strokeWidth = 2
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@FiltersActivity, R.color.driver_neon_teal))
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

                val updatedFilter = res.body()
                if (res.isSuccessful && updatedFilter != null) {
                    val index = filterList.indexOfFirst { it.id == filter.id }
                    if (index != -1) {
                        filterList[index] = updatedFilter
                        adapter.notifyItemChanged(index)
                        updateDisableAllButtonState()
                    }
                } else {
                    val errorMsg = try {
                        val errStr = res.errorBody()?.string()
                        if (!errStr.isNullOrEmpty()) JSONObject(errStr).getString("message") else "Помилка оновлення"
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
                filterList.removeAll { it.id == filter.id }
                adapter.notifyDataSetChanged()
                updateDisableAllButtonState()
                Toast.makeText(this@FiltersActivity, "Фільтр видалено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableAll() {
        lifecycleScope.launch {
            if (ApiClient.getInstance().getApiService(this@FiltersActivity).disableAllFilters().isSuccessful) {
                val disabledList = filterList.map {
                    it.copy(isActive = false, isEther = false, isAuto = false, isCycle = false)
                }
                filterList.clear()
                filterList.addAll(disabledList)
                adapter.notifyDataSetChanged()
                updateDisableAllButtonState()
                Toast.makeText(this@FiltersActivity, "Всі фільтри вимкнено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkOnlineAndShowToast(): Boolean {
        val sessionManager = com.taxiapp.driver.utils.SessionManager(this)
        if (!sessionManager.isDriverOnline()) {
            Toast.makeText(this, "Переключіть режим на Онлайн", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

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
            val rowTariff: View? = v.findViewById(R.id.row_filter_tariff)
            val switchMain: MaterialCheckBox = v.findViewById(R.id.switch_main_toggle)

            val btnAuto: MaterialButton = v.findViewById(R.id.btn_mode_auto)
            val btnEther: MaterialButton = v.findViewById(R.id.btn_mode_ether)
            val btnCycle: MaterialButton = v.findViewById(R.id.btn_mode_cycle)

            init {
                v.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos in list.indices) {
                        onEdit(list[pos])
                    }
                }
                v.setOnLongClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos in list.indices) {
                        onLongClick(list[pos])
                    }
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
            val context = holder.itemView.context

            holder.name.text = f.name

            // 1. Звідки ("радіус 1.5 км" або назва сектора + кількість)
            holder.from.text = if (f.fromType == "DISTANCE") {
                val dist = f.fromDistance ?: 0.0
                "радіус ${formatCleanDecimal(dist)} км"
            } else {
                formatSectorsSummary(f.fromSectors, fallbackEmpty = "будь-звідки")
            }

            // 2. Куди ("НОВОПЕЧЕРСЬКІ ЛИПКИ +4" або "Будь-куди")
            holder.to.text = formatSectorsSummary(f.toSectors, fallbackEmpty = "Будь-куди")

            // 3. Тариф (показуємо рядок "Тариф:" як на референсі, або ховаємо якщо тариф не обмежений)
            val tariffSummary = formatTariffSummary(f)
            if (tariffSummary == null) {
                holder.rowTariff?.visibility = View.GONE
            } else {
                holder.rowTariff?.visibility = View.VISIBLE
                holder.desc.text = tariffSummary
            }

            // 4. Векторні іконки режимів Авто / Цикл / Ефір (як на скріншоті image_f076bf.jpg)
            val isAutoActive = f.isActive && f.isAuto
            val isCycleActive = f.isActive && f.isCycle
            val isEtherActive = f.isActive && f.isEther

            updateModeSegmentStyle(holder.btnAuto, FilterModeIconDrawable.Mode.AUTO, isAutoActive)
            updateModeSegmentStyle(holder.btnCycle, FilterModeIconDrawable.Mode.CYCLE, isCycleActive)
            updateModeSegmentStyle(holder.btnEther, FilterModeIconDrawable.Mode.ETHER, isEtherActive)

            // 5. Кастомний Pill-тумблер у правому верхньому куті картки (як на скріншоті)
            val accentColor = ContextCompat.getColor(context, R.color.driver_neon_teal)
            holder.switchMain.buttonDrawable = null
            holder.switchMain.background = PillSwitchDrawable(isChecked = f.isActive, activeTrackColor = accentColor)

            holder.switchMain.setOnCheckedChangeListener(null)
            holder.switchMain.isChecked = f.isActive
            holder.switchMain.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !checkOnlineAndShowToast()) {
                    holder.switchMain.isChecked = false
                    holder.switchMain.background = PillSwitchDrawable(isChecked = false, activeTrackColor = accentColor)
                    return@setOnCheckedChangeListener
                }
                holder.switchMain.background = PillSwitchDrawable(isChecked = isChecked, activeTrackColor = accentColor)

                if (!isChecked) {
                    sendUpdate(f, ether = false, auto = false, cycle = false)
                } else {
                    sendUpdate(f, ether = true, auto = false, cycle = false)
                }
            }

            holder.btnEther.setOnClickListener {
                if (!f.isActive && !checkOnlineAndShowToast()) return@setOnClickListener
                val nextEther = !f.isEther
                sendUpdate(f, ether = nextEther, auto = f.isAuto, cycle = f.isCycle)
            }

            holder.btnAuto.setOnClickListener {
                if (!f.isActive && !checkOnlineAndShowToast()) return@setOnClickListener
                val newAuto = !f.isAuto
                val newCycle = if (newAuto) false else f.isCycle
                sendUpdate(f, ether = f.isEther, auto = newAuto, cycle = newCycle)
            }

            holder.btnCycle.setOnClickListener {
                if (!f.isActive && !checkOnlineAndShowToast()) return@setOnClickListener
                val newCycle = !f.isCycle
                val newAuto = if (newCycle) false else f.isAuto
                sendUpdate(f, ether = f.isEther, auto = newAuto, cycle = newCycle)
            }
        }

        private fun formatSectorsSummary(sectorIds: List<Long>, fallbackEmpty: String): String {
            if (sectorIds.isEmpty()) return fallbackEmpty
            val firstId = sectorIds.first()
            val firstName = cachedSectorNames[firstId]?.uppercase(Locale.getDefault())
            val extraCount = sectorIds.size - 1

            return if (!firstName.isNullOrBlank()) {
                if (extraCount > 0) "$firstName +$extraCount" else firstName
            } else {
                if (extraCount > 0) "СЕКТОР #$firstId +$extraCount" else "СЕКТОР #$firstId"
            }
        }

        private fun formatTariffSummary(f: DriverFilter): String? {
            return if (f.tariffType == "SIMPLE") {
                val minP = f.minPrice ?: 0.0
                val minKm = f.minPricePerKm ?: 0.0
                if (minP <= 0.0 && minKm <= 0.0) {
                    null
                } else if (minKm > 0.0) {
                    "${formatCleanDecimal(minP)} ₴; ${formatCleanDecimal(minKm)} ₴/км"
                } else {
                    "${formatCleanDecimal(minP)} ₴"
                }
            } else {
                val cMin = f.complexMinPrice ?: 0.0
                val cKmInMin = f.complexKmInMin ?: 0.0
                val cCity = f.complexPriceKmCity ?: 0.0
                val cSuburb = f.complexPriceKmSuburbs ?: 0.0
                if (cMin <= 0.0 && cCity <= 0.0 && cSuburb <= 0.0) {
                    null
                } else {
                    val parts = mutableListOf<String>()
                    if (cMin > 0.0) {
                        if (cKmInMin > 0.0) {
                            parts.add("${formatCleanDecimal(cMin)} ₴ за ${formatCleanDecimal(cKmInMin)} км")
                        } else {
                            parts.add("${formatCleanDecimal(cMin)} ₴")
                        }
                    }
                    if (cCity > 0.0) parts.add("місто ${formatCleanDecimal(cCity)} ₴/км")
                    if (cSuburb > 0.0) parts.add("за містом ${formatCleanDecimal(cSuburb)} ₴/км")
                    parts.joinToString("; ")
                }
            }
        }

        private fun formatCleanDecimal(value: Double): String {
            val asLong = value.toLong()
            return if (value == asLong.toDouble()) {
                asLong.toString()
            } else {
                String.format(Locale.US, "%.1f", value)
            }
        }

        private fun sendUpdate(f: DriverFilter, ether: Boolean, auto: Boolean, cycle: Boolean) {
            val isActive = ether || auto || cycle
            onUpdateMode(f, UpdateFilterModeRequest(isActive, ether, auto, cycle))
        }

        private fun updateModeSegmentStyle(
            btn: MaterialButton,
            mode: FilterModeIconDrawable.Mode,
            isActive: Boolean
        ) {
            val context = btn.context
            val density = context.resources.displayMetrics.density
            val activeTextColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val activeAccent = ContextCompat.getColor(context, R.color.driver_neon_teal)
            val inactiveColor = Color.parseColor("#C2C5CE")

            val iconColor = if (isActive) activeAccent else inactiveColor
            btn.icon = FilterModeIconDrawable(mode, iconColor, (18 * density).toInt())
            btn.iconTint = null

            if (isActive) {
                val activeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f * density
                    setColor(Color.parseColor("#5A5E6D"))
                    setStroke((1.2f * density).toInt().coerceAtLeast(1), activeAccent)
                }
                btn.background = activeBg
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(inactiveColor)
            }
        }

        override fun getItemCount() = list.size
    }

    /**
     * Малює кастомний овальний Pill-тумблер (52x30dp) у правому верхньому куті картки фільтра
     * точнісінько як на скріншоті image_f076bf.jpg.
     */
    private class PillSwitchDrawable(
        private val isChecked: Boolean,
        private val activeTrackColor: Int
    ) : Drawable() {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val radius = h / 2f

            trackPaint.color = if (isChecked) activeTrackColor else Color.parseColor("#5C606D")
            canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, trackPaint)

            val thumbRadius = h * 0.34f
            val margin = (h - thumbRadius * 2f) / 2f
            val thumbCx = if (isChecked) (w - margin - thumbRadius) else (margin + thumbRadius)
            val thumbCy = h / 2f

            thumbPaint.color = if (isChecked) Color.parseColor("#1F2229") else Color.parseColor("#C9CCD4")
            canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint)
        }

        override fun setAlpha(alpha: Int) {
            trackPaint.alpha = alpha
            thumbPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            trackPaint.colorFilter = colorFilter
            thumbPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * Малює 3 фірмові іконки режимів фільтра як на скріншоті image_f076bf.jpg:
     * - AUTO: приціл (кільце з точкою в центрі та 4 насічками по сторонах)
     * - CYCLE: дві горизонтальні зворотні стрілки циклу
     * - ETHER: три горизонтальні лінії фільтра різної ширини (пірамідкою вниз)
     */
    private class FilterModeIconDrawable(
        private val mode: Mode,
        private val strokeColor: Int,
        private val sizePx: Int
    ) : Drawable() {

        enum class Mode { AUTO, CYCLE, ETHER }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.FILL
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = minOf(w, h) / 24f

            canvas.save()
            canvas.translate(b.left + (w - 24f * s) / 2f, b.top + (h - 24f * s) / 2f)
            canvas.scale(s, s)
            strokePaint.strokeWidth = 2.0f

            when (mode) {
                Mode.AUTO -> {
                    canvas.drawCircle(12f, 12f, 6.2f, strokePaint)
                    canvas.drawCircle(12f, 12f, 1.8f, fillPaint)
                    canvas.drawLine(12f, 2.5f, 12f, 5.8f, strokePaint)
                    canvas.drawLine(12f, 18.2f, 12f, 21.5f, strokePaint)
                    canvas.drawLine(2.5f, 12f, 5.8f, 12f, strokePaint)
                    canvas.drawLine(18.2f, 12f, 21.5f, 12f, strokePaint)
                }

                Mode.CYCLE -> {
                    val topPath = Path().apply {
                        moveTo(5f, 10.5f)
                        quadTo(5f, 7f, 8.5f, 7f)
                        lineTo(18.5f, 7f)
                    }
                    canvas.drawPath(topPath, strokePaint)
                    canvas.drawLine(15.5f, 4.3f, 18.8f, 7f, strokePaint)
                    canvas.drawLine(18.8f, 7f, 15.5f, 9.7f, strokePaint)

                    val bottomPath = Path().apply {
                        moveTo(19f, 13.5f)
                        quadTo(19f, 17f, 15.5f, 17f)
                        lineTo(5.5f, 17f)
                    }
                    canvas.drawPath(bottomPath, strokePaint)
                    canvas.drawLine(8.5f, 14.3f, 5.2f, 17f, strokePaint)
                    canvas.drawLine(5.2f, 17f, 8.5f, 19.7f, strokePaint)
                }

                Mode.ETHER -> {
                    canvas.drawLine(3.5f, 7.0f, 20.5f, 7.0f, strokePaint)
                    canvas.drawLine(6.8f, 12.0f, 17.2f, 12.0f, strokePaint)
                    canvas.drawLine(10.0f, 17.0f, 14.0f, 17.0f, strokePaint)
                }
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    override fun finish() {
        super.finish()
    }
}