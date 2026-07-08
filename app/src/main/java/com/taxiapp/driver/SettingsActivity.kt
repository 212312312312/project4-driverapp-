package com.taxiapp.driver

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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

        setContentView(R.layout.activity_settings)

        // 🛠️ ДОБАВЛЕНО: Безопасный отступ контента от системных панелей для Android 15 во избежание Type mismatch
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        // --- ДИНАМИЧЕСКАЯ НАСТРОЙКА ЦВЕТОВ СВИТЧЕЙ ИЗ КОДА ---
        val thumbStates = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                ContextCompat.getColor(this, R.color.driver_neon_teal),
                ContextCompat.getColor(this, R.color.driver_black_bg)
            )
        )

        val trackStates = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                Color.parseColor("#5500BFA5"),
                Color.parseColor("#22FFFFFF")
            )
        )

        // --- ШВИДКИЙ ДОСТУП ---
        switchQuickAccess = findViewById(R.id.switch_quick_access)
        switchQuickAccess.isChecked = sessionManager.isQuickAccessEnabled()
        switchQuickAccess.thumbTintList = thumbStates
        switchQuickAccess.trackTintList = trackStates

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
        switchStatusReminder.thumbTintList = thumbStates
        switchStatusReminder.trackTintList = trackStates

        switchStatusReminder.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setStatusReminderEnabled(isChecked)
        }

        val clickListener = View.OnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_sounds).setOnClickListener(clickListener)
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // --- ОБНОВЛЕННЫЙ КАСТОМНЫЙ ДИАЛОГ С УМНЫМ ПЕРЕХОДОМ ---
    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_permission_overlay, null)
        builder.setView(dialogView)

        val alertDialog = builder.create()

        dialogView.findViewById<View>(R.id.btnCancelPermission).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnAllowPermission).setOnClickListener {
            alertDialog.dismiss()
            openOverlayPermissionSettings() // Вызов умного метода перехода
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // --- МЕТОД ДЛЯ МГНОВЕННОГО ПЕРЕХОДА К НАСТРОЙКЕ НА РАЗНЫХ УСТРОЙСТВАХ ---
    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // 1. Проверяем, не Xiaomi ли это (они чаще всего ломают переход)
            if (isXiaomiDevice()) {
                try {
                    // Специальный прямой интент для открытия окна разрешений конкретного приложения в MIUI/HyperOS
                    val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        putExtra("extra_pkgname", packageName)
                    }
                    startActivityForResult(miuiIntent, OVERLAY_PERMISSION_REQ_CODE)
                    return // Если успешно запустилось — выходим
                } catch (e: Exception) {
                    // Если прошивка старая/новая и интент упал — идем дальше к стандартным методам
                }
            }

            // 2. Стандартный и самый точный способ для чистого Android, Samsung, Pixel
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            } catch (e: Exception) {
                // 3. Тотальный фолбек (если устройство заблокировало прямой переход по пакету, открываем общий список)
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivityForResult(fallbackIntent, OVERLAY_PERMISSION_REQ_CODE)
                } catch (anfe: Exception) {
                    Toast.makeText(this, "Не удалось открыть настройки системы", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Вспомогательная функция определения китайских прошивок Xiaomi / Poco / RedMi
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("xiaomi") || manufacturer.contains("poco") || manufacturer.contains("redmi")) {
            return true
        }
        return try {
            val buildClass = Class.forName("android.os.SystemProperties")
            val getMethod = buildClass.getMethod("get", String::class.java)
            val property = getMethod.invoke(buildClass, "ro.miui.ui.version.name") as String
            property.isNotEmpty()
        } catch (e: Exception) {
            false
        }
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
        // Передаем кастомный стиль в конструктор
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_navigator, null)
        bottomSheetDialog.setContentView(view)

        val btnGoogle = view.findViewById<androidx.cardview.widget.CardView>(R.id.btn_nav_google)
        val btnWaze = view.findViewById<androidx.cardview.widget.CardView>(R.id.btn_nav_waze)

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
        // Передаем кастомный стиль в конструктор
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_language, null)
        bottomSheetDialog.setContentView(view)

        val currentLang = sessionManager.getLanguage()
        if (currentLang == "uk") {
            view.findViewById<View>(R.id.iv_check_ua)?.visibility = View.VISIBLE
        } else if (currentLang == "en") {
            view.findViewById<View>(R.id.iv_check_en)?.visibility = View.VISIBLE
        }

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
        // Передаем кастомный стиль в конструктор
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_theme, null)
        bottomSheetDialog.setContentView(view)

        val currentTheme = sessionManager.getTheme()
        when (currentTheme) {
            "DARK" -> view.findViewById<View>(R.id.iv_check_dark)?.visibility = View.VISIBLE
            "LIGHT" -> view.findViewById<View>(R.id.iv_check_light)?.visibility = View.VISIBLE
            else -> view.findViewById<View>(R.id.iv_check_system)?.visibility = View.VISIBLE
        }

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