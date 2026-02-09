package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverSearchMode
import com.taxiapp.driver.network.DriverSearchSettingsDto
import com.taxiapp.driver.network.DriverSearchStateDto
import com.taxiapp.driver.utils.SessionManager
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
    private lateinit var btnSave: MaterialButton

    private var currentMode = DriverSearchMode.CHAIN
    private var currentRadius = 3.0
    private var selectedHomeSectorIds: List<Long>? = null

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
        btnSave = view.findViewById(R.id.btnSaveSettings)

        val btnModeHome = view.findViewById<View>(R.id.btnModeHome)
        val btnModeChain = view.findViewById<View>(R.id.btnModeChain)
        val btnEditHomeSector = view.findViewById<View>(R.id.btnEditHomeSector)
        val btnMinus = view.findViewById<View>(R.id.btnRadiusMinus)
        val btnPlus = view.findViewById<View>(R.id.btnRadiusPlus)

        // 1. Сразу берем из сессии, чтобы галочка появилась МОМЕНТАЛЬНО
        val session = SessionManager(requireContext())
        currentMode = session.getSearchMode()

        // 2. Рисуем UI до запроса к серверу (чтобы юзер видел выбор сразу)
        updateLocalUI()

        // 3. Загружаем данные с сервера (радиус, лимиты, сектора)
        loadSettings()

        btnModeHome.setOnClickListener { selectMode(DriverSearchMode.HOME) }
        btnModeChain.setOnClickListener { selectMode(DriverSearchMode.CHAIN) }

        btnEditHomeSector.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), SectorSelectionActivity::class.java)
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
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnMinus.setOnClickListener {
            if (seekBar.progress > 0) {
                seekBar.progress -= 1
            }
        }

        btnPlus.setOnClickListener {
            if (seekBar.progress < seekBar.max) {
                seekBar.progress += 1
            }
        }

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun updateLocalUI() {
        // Просто ставим галочки по текущему режиму (из сессии)
        radioHome.isChecked = currentMode == DriverSearchMode.HOME
        radioChain.isChecked = currentMode == DriverSearchMode.CHAIN
        updateRadiusText()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(requireContext()).getSearchSettings()
                if (response.isSuccessful && response.body() != null) {
                    val state = response.body()!!

                    // ИСПРАВЛЕНО: Логика обработки статуса от сервера
                    // Если сервер прислал OFFLINE, MANUAL или BUSY -> мы считаем, что выбрана настройка "Ланцюг"
                    // Только если сервер явно прислал HOME, мы ставим "Додому".
                    currentMode = if (state.mode == DriverSearchMode.HOME) {
                        DriverSearchMode.HOME
                    } else {
                        DriverSearchMode.CHAIN
                    }

                    currentRadius = state.radius
                    selectedHomeSectorIds = state.homeSectorIds

                    updateUI(state)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Помилка завантаження налаштувань", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(state: DriverSearchStateDto) {
        // Обновляем галочки (теперь currentMode точно либо CHAIN, либо HOME)
        radioHome.isChecked = currentMode == DriverSearchMode.HOME
        radioChain.isChecked = currentMode == DriverSearchMode.CHAIN

        tvHomeCounter.text = "(${state.homeRidesLeft}/2)"

        tvHomeSector.text = state.homeSectorNames ?: "Сектори не обрано"
        if (state.homeSectorIds.isNullOrEmpty()) {
            tvHomeSector.text = "-"
        }

        val progress = ((currentRadius - 0.5) / 0.5).toInt()
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
    }

    private fun saveSettings() {
        btnSave.isEnabled = false
        btnSave.text = "ЗБЕРЕЖЕННЯ..."

        lifecycleScope.launch {
            try {
                val req = DriverSearchSettingsDto(
                    mode = currentMode,
                    radius = currentRadius,
                    homeSectorIds = selectedHomeSectorIds
                )
                val response = ApiClient.getInstance().getApiService(requireContext()).updateSearchSettings(req)

                if (response.isSuccessful && response.body() != null) {
                    // Зберігаємо в сесію, щоб наступного разу відкрилось миттєво правильно
                    SessionManager(requireContext()).saveSearchMode(currentMode)
                    onSettingsChanged()
                    dismiss()
                } else {
                    Toast.makeText(context, "Помилка: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                    loadSettings()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Помилка збереження", Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnSave.text = "ЗБЕРЕГТИ"
            }
        }
    }
}