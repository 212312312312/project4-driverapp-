package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverSearchMode
import com.taxiapp.driver.network.DriverSearchSettingsDto
import com.taxiapp.driver.network.DriverSearchStateDto
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class SearchSettingsBottomSheet(
    private val onSettingsChanged: () -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var radioHome: RadioButton
    private lateinit var radioChain: RadioButton
    private lateinit var tvHomeCounter: TextView
    private lateinit var tvHomeSector: TextView
    private lateinit var tvRadius: TextView
    private lateinit var seekBar: SeekBar

    private var currentMode = DriverSearchMode.CHAIN
    private var currentRadius = 3.0
    private var selectedHomeSectorIds: List<Long>? = null // Змінено на List
    private var isRadiusChanged = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_search_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioHome = view.findViewById(R.id.radioHome)
        radioChain = view.findViewById(R.id.radioChain)
        tvHomeCounter = view.findViewById(R.id.tvHomeCounter)
        tvHomeSector = view.findViewById(R.id.tvHomeSectorName)
        tvRadius = view.findViewById(R.id.tvRadiusValue)
        seekBar = view.findViewById(R.id.seekBarRadius)

        val btnModeHome = view.findViewById<View>(R.id.btnModeHome)
        val btnModeChain = view.findViewById<View>(R.id.btnModeChain)
        val btnEditHomeSector = view.findViewById<View>(R.id.btnEditHomeSector)
        val btnMinus = view.findViewById<View>(R.id.btnRadiusMinus)
        val btnPlus = view.findViewById<View>(R.id.btnRadiusPlus)

        loadSettings()

        btnModeHome.setOnClickListener { selectMode(DriverSearchMode.HOME) }
        btnModeChain.setOnClickListener { selectMode(DriverSearchMode.CHAIN) }

        btnEditHomeSector.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), SectorSelectionActivity::class.java)
            // Передаємо список вже обраних ID
            if (selectedHomeSectorIds != null && selectedHomeSectorIds!!.isNotEmpty()) {
                intent.putExtra("SELECTED_IDS", selectedHomeSectorIds!!.toLongArray())
            }
            requireActivity().startActivityForResult(intent, 1001)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val km = 0.5 + (progress * 0.5)
                currentRadius = km
                updateRadiusText()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isRadiusChanged = true
                saveSettings()
            }
        })

        btnMinus.setOnClickListener {
            if (seekBar.progress > 0) {
                seekBar.progress -= 1
                isRadiusChanged = true
                saveSettings()
            }
        }

        btnPlus.setOnClickListener {
            if (seekBar.progress < seekBar.max) {
                seekBar.progress += 1
                isRadiusChanged = true
                saveSettings()
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(requireContext()).getSearchSettings()
                if (response.isSuccessful && response.body() != null) {
                    val state = response.body()!!

                    currentMode = if (state.mode == DriverSearchMode.MANUAL) DriverSearchMode.CHAIN else state.mode
                    currentRadius = state.radius
                    selectedHomeSectorIds = state.homeSectorIds // Зберігаємо список

                    updateUI(state)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Помилка завантаження налаштувань", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(state: DriverSearchStateDto) {
        radioHome.isChecked = currentMode == DriverSearchMode.HOME
        radioChain.isChecked = currentMode == DriverSearchMode.CHAIN

        tvHomeCounter.text = "(${state.homeRidesLeft}/2)"

        // Відображаємо назви секторів
        tvHomeSector.text = state.homeSectorNames ?: "Сектори не обрано"
        if (state.homeSectorIds.isNullOrEmpty()) {
            tvHomeSector.text = "-"
        }

        val progress = ((state.radius - 0.5) / 0.5).toInt()
        seekBar.progress = progress
        updateRadiusText()
    }

    private fun updateRadiusText() {
        val fmt = DecimalFormat("0.0")
        tvRadius.text = "${fmt.format(currentRadius)} км"
    }

    private fun selectMode(newMode: DriverSearchMode) {
        if (currentMode == newMode) return

        currentMode = newMode
        radioHome.isChecked = currentMode == DriverSearchMode.HOME
        radioChain.isChecked = currentMode == DriverSearchMode.CHAIN

        saveSettings()
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                val req = DriverSearchSettingsDto(
                    mode = currentMode,
                    radius = currentRadius,
                    homeSectorIds = selectedHomeSectorIds // Передаємо список
                )
                val response = ApiClient.getInstance().getApiService(requireContext()).updateSearchSettings(req)

                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                    onSettingsChanged()
                } else {
                    Toast.makeText(context, "Помилка: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                    loadSettings()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Помилка збереження", Toast.LENGTH_SHORT).show()
            }
        }
    }
}