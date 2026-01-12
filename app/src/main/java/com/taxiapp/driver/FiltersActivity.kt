package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverFilter
import kotlinx.coroutines.launch

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
            onToggle = { f -> toggleFilter(f) },
            onDelete = { f -> deleteFilter(f) }
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

    private fun toggleFilter(filter: DriverFilter) {
        lifecycleScope.launch {
            ApiClient.getInstance().getApiService(this@FiltersActivity).toggleFilter(filter.id)
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

    private fun deleteFilter(filter: DriverFilter) {
        lifecycleScope.launch {
            if (ApiClient.getInstance().getApiService(this@FiltersActivity).deleteFilter(filter.id).isSuccessful) {
                loadFilters()
            }
        }
    }

    // --- АДАПТЕР ---
    inner class FiltersAdapter(
        private val list: List<DriverFilter>,
        private val onToggle: (DriverFilter) -> Unit,
        private val onDelete: (DriverFilter) -> Unit
    ) : RecyclerView.Adapter<FiltersAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name = v.findViewById<TextView>(R.id.tv_filter_name)
            val desc = v.findViewById<TextView>(R.id.tv_filter_desc)
            val switch = v.findViewById<SwitchMaterial>(R.id.switch_active)

            init {
                v.setOnLongClickListener {
                    onDelete(list[adapterPosition])
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
            holder.desc.text = f.description

            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = f.isActive
            holder.switch.setOnCheckedChangeListener { _, _ -> onToggle(f) }
        }

        override fun getItemCount() = list.size
    }
}