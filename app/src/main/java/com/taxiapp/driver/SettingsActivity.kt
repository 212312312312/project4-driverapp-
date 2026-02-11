package com.taxiapp.driver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Важно: Применяем тему перед setContentView (хотя AppCompatDelegate работает и динамически)
        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode())

        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        setupSimpleButton(R.id.btn_language, R.string.settings_language)
        setupSimpleButton(R.id.btn_theme, R.string.settings_theme) // Текст кнопки обновится из strings
        setupSimpleButton(R.id.btn_ether_settings, R.string.settings_ether_orders)
        setupSimpleButton(R.id.btn_sounds, R.string.settings_sounds)
        setupSimpleButton(R.id.btn_navigator, R.string.settings_navigator)

        // Язык
        findViewById<View>(R.id.btn_language).setOnClickListener {
            showLanguageBottomSheet()
        }

        // --- ТЕМА (Подключили слушатель) ---
        findViewById<View>(R.id.btn_theme).setOnClickListener {
            showThemeBottomSheet()
        }

        val clickListener = View.OnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_ether_settings).setOnClickListener(clickListener)
        findViewById<View>(R.id.btn_sounds).setOnClickListener(clickListener)
        findViewById<View>(R.id.btn_navigator).setOnClickListener(clickListener)
        findViewById<View>(R.id.btn_quick_access).setOnClickListener(clickListener)
        findViewById<View>(R.id.btn_notifications).setOnClickListener(clickListener)
    }

    private fun setupSimpleButton(includeId: Int, stringId: Int) {
        val view = findViewById<View>(includeId)
        val tvTitle = view.findViewById<TextView>(R.id.item_title)
        tvTitle.text = getString(stringId)
    }

    // --- BottomSheet выбора языка ---
    private fun showLanguageBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_language, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<View>(R.id.btn_lang_ua)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            updateAppLocale("uk")
        }

        view.findViewById<View>(R.id.btn_lang_en)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            updateAppLocale("en")
        }

        bottomSheetDialog.show()
    }

    // --- BottomSheet выбора ТЕМЫ (Новый метод) ---
    private fun showThemeBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_theme, null)
        bottomSheetDialog.setContentView(view)

        // Слушатель на "Системная"
        view.findViewById<View>(R.id.btn_theme_system)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("SYSTEM")
        }

        // Слушатель на "Темная"
        view.findViewById<View>(R.id.btn_theme_dark)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("DARK")
        }

        // Слушатель на "Светлая"
        view.findViewById<View>(R.id.btn_theme_light)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("LIGHT")
        }

        bottomSheetDialog.show()
    }

    // Метод применения темы
    private fun applyAppTheme(themeCode: String) {
        val currentTheme = sessionManager.getTheme()
        if (currentTheme == themeCode) return

        // 1. Сохраняем
        sessionManager.saveTheme(themeCode)

        // 2. Применяем режим
        val mode = when (themeCode) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        // 3. Пересоздаем активити (опционально, но надежнее для обновления ресурсов)
        // recreate()
        // Или так же, как с языком — перезагрузить через Main, чтобы везде применилось:
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateAppLocale(languageCode: String) {
        val currentLang = sessionManager.getLanguage()
        if (currentLang == languageCode) return

        LocaleHelper.setLocale(this, languageCode)
        sessionManager.saveLanguage(languageCode)

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}