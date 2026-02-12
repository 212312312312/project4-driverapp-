package com.taxiapp.driver

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.taxiapp.driver.service.FloatingWidgetService
import com.taxiapp.driver.utils.LocaleHelper
import com.taxiapp.driver.utils.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var switchQuickAccess: SwitchMaterial
    private val OVERLAY_PERMISSION_REQ_CODE = 1234

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode())

        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        setupSimpleButton(R.id.btn_language, R.string.settings_language)
        setupSimpleButton(R.id.btn_theme, R.string.settings_theme)
        setupSimpleButton(R.id.btn_ether_settings, R.string.settings_ether_orders)
        setupSimpleButton(R.id.btn_sounds, R.string.settings_sounds)
        setupSimpleButton(R.id.btn_navigator, R.string.navigator_title)

        findViewById<View>(R.id.btn_language).setOnClickListener {
            showLanguageBottomSheet()
        }

        findViewById<View>(R.id.btn_theme).setOnClickListener {
            showThemeBottomSheet()
        }

        findViewById<View>(R.id.btn_ether_settings).setOnClickListener {
            startActivity(Intent(this, EtherSettingsActivity::class.java))
        }

        findViewById<View>(R.id.btn_navigator).setOnClickListener {
            showNavigatorBottomSheet()
        }

        // --- ШВИДКИЙ ДОСТУП ---
        switchQuickAccess = findViewById(R.id.switch_quick_access)
        switchQuickAccess.isChecked = sessionManager.isQuickAccessEnabled()

        switchQuickAccess.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checkOverlayPermission()) {
                    switchQuickAccess.isChecked = false
                    showPermissionDialog()
                } else {
                    sessionManager.setQuickAccessEnabled(true)
                    Toast.makeText(this, "Швидкий доступ увімкнено", Toast.LENGTH_SHORT).show()
                }
            } else {
                sessionManager.setQuickAccessEnabled(false)
                stopService(Intent(this, FloatingWidgetService::class.java))
            }
        }

        // --- НАГАДУВАННЯ СТАТУСУ ---
        val switchStatusReminder = findViewById<SwitchMaterial>(R.id.switch_status_reminder)
        switchStatusReminder.isChecked = sessionManager.isStatusReminderEnabled()

        switchStatusReminder.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setStatusReminderEnabled(isChecked)
        }

        // Заглушка для звуков
        val clickListener = View.OnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_sounds).setOnClickListener(clickListener)
        // Кнопка btn_notifications видалена
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_overlay_title)
            .setMessage(R.string.permission_overlay_desc)
            .setPositiveButton(R.string.permission_overlay_allow) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                sessionManager.setQuickAccessEnabled(true)
                switchQuickAccess.isChecked = true
                Toast.makeText(this, "Швидкий доступ увімкнено!", Toast.LENGTH_SHORT).show()
            } else {
                sessionManager.setQuickAccessEnabled(false)
                switchQuickAccess.isChecked = false
            }
        }
    }

    private fun setupSimpleButton(includeId: Int, stringId: Int) {
        val view = findViewById<View>(includeId)
        val tvTitle = view.findViewById<TextView>(R.id.item_title)
        tvTitle.text = getString(stringId)
    }

    private fun showNavigatorBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_navigator, null)
        bottomSheetDialog.setContentView(view)

        val btnGoogle = view.findViewById<LinearLayout>(R.id.btn_nav_google)
        val btnWaze = view.findViewById<LinearLayout>(R.id.btn_nav_waze)

        val tvStatusGoogle = view.findViewById<TextView>(R.id.tv_status_google)
        val tvStatusWaze = view.findViewById<TextView>(R.id.tv_status_waze)

        val ivCheckGoogle = view.findViewById<ImageView>(R.id.iv_check_google)
        val ivCheckWaze = view.findViewById<ImageView>(R.id.iv_check_waze)

        val isGoogleInstalled = isPackageInstalled("com.google.android.apps.maps")
        val isWazeInstalled = isPackageInstalled("com.waze")
        val currentNav = sessionManager.getNavigator()

        if (isGoogleInstalled) {
            tvStatusGoogle.visibility = View.GONE
            ivCheckGoogle.visibility = if (currentNav == "google") View.VISIBLE else View.GONE

            btnGoogle.setOnClickListener {
                sessionManager.saveNavigator("google")
                bottomSheetDialog.dismiss()
                Toast.makeText(this, "Google Maps обрано", Toast.LENGTH_SHORT).show()
            }
        } else {
            tvStatusGoogle.text = getString(R.string.navigator_status_install)
            tvStatusGoogle.visibility = View.VISIBLE
            ivCheckGoogle.visibility = View.GONE

            btnGoogle.setOnClickListener {
                openPlayStore("com.google.android.apps.maps")
                bottomSheetDialog.dismiss()
            }
        }

        if (isWazeInstalled) {
            tvStatusWaze.visibility = View.GONE
            ivCheckWaze.visibility = if (currentNav == "waze") View.VISIBLE else View.GONE

            btnWaze.setOnClickListener {
                sessionManager.saveNavigator("waze")
                bottomSheetDialog.dismiss()
                Toast.makeText(this, "Waze обрано", Toast.LENGTH_SHORT).show()
            }
        } else {
            tvStatusWaze.text = getString(R.string.navigator_status_install)
            tvStatusWaze.visibility = View.VISIBLE
            ivCheckWaze.visibility = View.GONE

            btnWaze.setOnClickListener {
                openPlayStore("com.waze")
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.show()
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openPlayStore(packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
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

    private fun showThemeBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_theme, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<View>(R.id.btn_theme_system)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("SYSTEM")
        }

        view.findViewById<View>(R.id.btn_theme_dark)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("DARK")
        }

        view.findViewById<View>(R.id.btn_theme_light)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            applyAppTheme("LIGHT")
        }

        bottomSheetDialog.show()
    }

    private fun applyAppTheme(themeCode: String) {
        val currentTheme = sessionManager.getTheme()
        if (currentTheme == themeCode) return

        sessionManager.saveTheme(themeCode)
        val mode = when (themeCode) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

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