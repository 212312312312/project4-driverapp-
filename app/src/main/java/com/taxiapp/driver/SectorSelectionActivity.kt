package com.taxiapp.driver

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Sector
import kotlinx.coroutines.launch

class SectorSelectionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var rvSectors: RecyclerView
    private lateinit var rgToggle: RadioGroup
    private lateinit var btnSearch: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var tvTitle: TextView

    private var allSectors = listOf<Sector>()
    private val selectedIds = mutableSetOf<Long>()
    private val polygons = mutableMapOf<Long, Polygon>()
    private var sectorsAdapter: SectorsListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sector_selection)

        // Получаем переданные ID (из "Звідки" или "Куди")
        val preSelected = intent.getLongArrayExtra("SELECTED_IDS")
        preSelected?.forEach { selectedIds.add(it) }

        initUI()
        loadSectors()
    }

    private fun initUI() {
        rvSectors = findViewById(R.id.rv_sectors_list)
        rvSectors.layoutManager = LinearLayoutManager(this)

        rgToggle = findViewById(R.id.rg_view_toggle)
        btnSearch = findViewById(R.id.btn_search_sectors)
        etSearch = findViewById(R.id.et_search_query)
        tvTitle = findViewById(R.id.tv_title_selection)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_selection) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Переключатель режимов: Карта / Список
        rgToggle.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_view_map) {
                findViewById<View>(R.id.map_selection).visibility = View.VISIBLE
                rvSectors.visibility = View.GONE
                btnSearch.visibility = View.GONE
                etSearch.visibility = View.GONE
                tvTitle.text = "Вибір на карті"
            } else {
                findViewById<View>(R.id.map_selection).visibility = View.GONE
                rvSectors.visibility = View.VISIBLE
                btnSearch.visibility = View.VISIBLE
                tvTitle.text = "Список секторів"
                updateList()
            }
        }

        btnSearch.setOnClickListener {
            etSearch.visibility = if (etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        etSearch.addTextChangedListener { updateList() }

        // Кнопка сохранения (Галочка)
        findViewById<View>(R.id.btn_save_selection).setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("SELECTED_IDS", selectedIds.toLongArray())
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        findViewById<View>(R.id.btn_back_selection).setOnClickListener { finish() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Пытаемся применить ночной стиль карты
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (e: Exception) { e.printStackTrace() }

        map.setOnPolygonClickListener { polygon ->
            val id = polygon.tag as? Long ?: return@setOnPolygonClickListener
            toggleSector(id)
        }

        drawSectorsOnMap()
    }

    private fun loadSectors() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@SectorSelectionActivity).getSectors()
                if (res.isSuccessful) {
                    allSectors = res.body() ?: emptyList()
                    drawSectorsOnMap()
                    updateList()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SectorSelectionActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSector(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }

        // Обновляем визуальное состояние полигона на карте
        polygons[id]?.let { poly ->
            val isSelected = selectedIds.contains(id)
            poly.fillColor = if (isSelected) Color.argb(120, 0, 255, 170) else Color.argb(40, 128, 128, 128)
            poly.strokeColor = if (isSelected) Color.parseColor("#00ffaa") else Color.GRAY
        }

        // Обновляем список, если он открыт
        if (rvSectors.visibility == View.VISIBLE) {
            sectorsAdapter?.notifyDataSetChanged()
        }
    }

    private fun drawSectorsOnMap() {
        if (!::map.isInitialized || allSectors.isEmpty()) return

        val builder = LatLngBounds.Builder()
        var hasVisibleSectors = false

        for (sector in allSectors) {
            if (sector.points.isEmpty()) continue

            val options = PolygonOptions()
                .addAll(sector.points.map { LatLng(it.lat, it.lng) })
                .clickable(true)
                .strokeWidth(4f)

            val isSelected = selectedIds.contains(sector.id)
            options.fillColor(if (isSelected) Color.argb(120, 0, 255, 170) else Color.argb(40, 128, 128, 128))
            options.strokeColor(if (isSelected) Color.parseColor("#00ffaa") else Color.GRAY)

            val poly = map.addPolygon(options)
            poly.tag = sector.id
            polygons[sector.id] = poly

            sector.points.forEach { builder.include(LatLng(it.lat, it.lng)) }
            hasVisibleSectors = true
        }

        // Центрируем камеру на всех секторах
        if (hasVisibleSectors) {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
            } catch (e: Exception) {}
        }
    }

    private fun updateList() {
        val query = etSearch.text.toString().lowercase()
        val filtered = allSectors.filter { it.name.lowercase().contains(query) }

        // Создаем адаптер (теперь типы указаны явно)
        sectorsAdapter = SectorsListAdapter(filtered, selectedIds) { sectorId: Long ->
            toggleSector(sectorId)
        }
        rvSectors.adapter = sectorsAdapter
    }

    // --- ВНУТРЕННИЙ КЛАСС АДАПТЕРА ---
    private inner class SectorsListAdapter(
        private val sectors: List<Sector>,
        private val selected: Set<Long>,
        private val onClick: (Long) -> Unit
    ) : RecyclerView.Adapter<SectorsListAdapter.SectorVH>() {

        inner class SectorVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_sector_name)
            val ivCheck: ImageView = view.findViewById(R.id.iv_selected_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectorVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sector_selectable, parent, false)
            return SectorVH(view)
        }

        override fun onBindViewHolder(holder: SectorVH, position: Int) {
            val sector = sectors[position]
            holder.tvName.text = sector.name

            val isSelected = selected.contains(sector.id)
            holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Красим текст в бирюзовый, если выбран
            holder.tvName.setTextColor(if (isSelected) Color.parseColor("#00ffaa") else Color.WHITE)

            holder.itemView.setOnClickListener { onClick(sector.id) }
        }

        override fun getItemCount() = sectors.size
    }
}