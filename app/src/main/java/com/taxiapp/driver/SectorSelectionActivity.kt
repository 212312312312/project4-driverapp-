package com.taxiapp.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Sector
import com.taxiapp.driver.network.SectorPointDto
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

    // Зберігаємо посилання на об'єкти карти для очищення
    private val polygons = mutableMapOf<Long, Polygon>()
    private val sectorMarkers = mutableListOf<Marker>() // <--- ДОДАНО ДЛЯ ТЕКСТУ

    private var sectorsAdapter: SectorsListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sector_selection)

        // Отримуємо передані ID
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

        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (e: Exception) { e.printStackTrace() }

        enableMyLocation()

        map.setOnPolygonClickListener { polygon ->
            val id = polygon.tag as? Long ?: return@setOnPolygonClickListener
            toggleSector(id)
        }

        if (allSectors.isNotEmpty()) {
            drawSectorsOnMap()
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                }
            }
        }
    }

    private fun loadSectors() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getInstance().getApiService(this@SectorSelectionActivity).getSectors()
                if (res.isSuccessful) {
                    allSectors = res.body() ?: emptyList()
                    if (::map.isInitialized) {
                        drawSectorsOnMap()
                    }
                    updateList()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SectorSelectionActivity, "Помилка завантаження секторів", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSector(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }

        polygons[id]?.let { poly ->
            val isSelected = selectedIds.contains(id)
            poly.fillColor = if (isSelected) Color.argb(120, 0, 255, 170) else Color.argb(40, 128, 128, 128)
            poly.strokeColor = if (isSelected) Color.parseColor("#00ffaa") else Color.GRAY
        }

        if (rvSectors.visibility == View.VISIBLE) {
            sectorsAdapter?.notifyDataSetChanged()
        }
    }

    private fun drawSectorsOnMap() {
        if (!::map.isInitialized || allSectors.isEmpty()) return

        // 1. Очищення старих полігонів та маркерів
        polygons.values.forEach { it.remove() }
        polygons.clear()

        sectorMarkers.forEach { it.remove() }
        sectorMarkers.clear()

        // 2. Малювання нових
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

            // --- ДОДАНО: ТЕКСТОВИЙ МАРКЕР ПО ЦЕНТРУ ---
            val center = getPolygonCenter(sector.points)
            val textIcon = createTextIcon(sector.name)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(center)
                    .icon(textIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true) // Щоб текст "лежав" на карті, або false, щоб завжди дивився на користувача
            )
            marker?.let { sectorMarkers.add(it) }
            // ------------------------------------------
        }
    }

    // --- ДОПОМІЖНІ ФУНКЦІЇ ДЛЯ ТЕКСТУ (ПОВЕРНУТО З MAIN ACTIVITY) ---

    private fun getPolygonCenter(points: List<SectorPointDto>): LatLng {
        val builder = LatLngBounds.Builder()
        for (p in points) builder.include(LatLng(p.lat, p.lng))
        return builder.build().center
    }

    private fun createTextIcon(text: String): BitmapDescriptor {
        val textView = TextView(this)
        textView.text = text
        textView.setTextColor(Color.parseColor("#00ffaa")) // Яскравий колір тексту
        textView.textSize = 14f
        textView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textView.setShadowLayer(3f, 1f, 1f, Color.BLACK) // Тінь для читабельності

        textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)

        val bitmap = Bitmap.createBitmap(textView.measuredWidth, textView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        textView.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // -------------------------------------------------------------

    private fun updateList() {
        val query = etSearch.text.toString().lowercase()
        val filtered = allSectors.filter { it.name.lowercase().contains(query) }

        sectorsAdapter = SectorsListAdapter(filtered, selectedIds) { sectorId: Long ->
            toggleSector(sectorId)
        }
        rvSectors.adapter = sectorsAdapter
    }

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
            holder.tvName.setTextColor(if (isSelected) Color.parseColor("#00ffaa") else Color.WHITE)

            holder.itemView.setOnClickListener { onClick(sector.id) }
        }

        override fun getItemCount() = sectors.size
    }
}