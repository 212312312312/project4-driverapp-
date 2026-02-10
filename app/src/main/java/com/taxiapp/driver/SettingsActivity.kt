package com.taxiapp.driver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    // ВАЖЛИВО: Це дозволяє екрану зрозуміти поточну мову до створення UI
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        sessionManager = SessionManager(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Оновлюємо тексти кнопок (вони підтягнуться з потрібного strings.xml)
        setupSimpleButton(R.id.btn_language, R.string.settings_language)
        setupSimpleButton(R.id.btn_theme, R.string.settings_theme)
        setupSimpleButton(R.id.btn_ether_settings, R.string.settings_ether_orders)
        setupSimpleButton(R.id.btn_sounds, R.string.settings_sounds)
        setupSimpleButton(R.id.btn_navigator, R.string.settings_navigator)

        findViewById<View>(R.id.btn_language).setOnClickListener {
            showLanguageBottomSheet()
        }

        val clickListener = View.OnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_theme).setOnClickListener(clickListener)
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

    private fun updateAppLocale(languageCode: String) {
        val currentLang = sessionManager.getLanguage()
        if (currentLang == languageCode) return // Не оновлюємо, якщо мова та ж сама

        // 1. Зберігаємо нову мову
        LocaleHelper.setLocale(this, languageCode)
        sessionManager.saveLanguage(languageCode)

        // 2. Перезавантажуємо додаток, щоб зміни вступили в силу всюди
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}