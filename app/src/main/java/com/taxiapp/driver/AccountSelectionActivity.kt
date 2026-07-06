package com.taxiapp.driver

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.taxiapp.driver.network.ApiClient
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.taxiapp.driver.databinding.ActivityAccountSelectionBinding
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager

class AccountSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSelectionBinding
    private lateinit var sessionManager: SessionManager

    // Уникальные коды запросов для каждого типа разрешения
    private val REQ_CODE_FULL_SCREEN = 5678
    private val REQ_CODE_OVERLAY = 1234
    private val REQ_CODE_XIAOMI_BG = 9012

    // Перечисление для отслеживания текущего проверяемого разрешения
    private enum class PermissionType {
        LOCATION, FULL_SCREEN, OVERLAY, XIAOMI_BG
    }
    private val requestLocationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            // Если водитель разрешил GPS — перезапускаем каскад проверок дальше
            handlePermissionsAndProceed()
        } else {
            Toast.makeText(this, "Для роботи програми обов'язково потрібен доступ до геолокації!", Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val phone = sessionManager.getDriverPhone() ?: "Невідомий номер"
        binding.tvPhoneNumber.text = phone
    }

    private fun setupListeners() {
        // Кнопка ВОЙТИ (ПРОДОВЖИТИ) - запускает каскадную проверку всех разрешений
        binding.btnContinue.setOnClickListener {
            handlePermissionsAndProceed()
        }

        // ЗМІНИТИ АКАУНТ - полный разлогин и переход на Welcome
        binding.btnSwitchAccount.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // Каскадный менеджер проверки: если что-то выключено, прерываем цепочку и требуем включения
    private fun handlePermissionsAndProceed() {
        // Чек №0: Проверка стандартных Runtime-разрешений на геолокацию
        if (!checkLocationPermission()) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // 1. Проверка Полноэкранных уведомлений (Android 14+)
        if (!checkFullScreenIntentPermission()) {
            showRequiredPermissionDialog(PermissionType.FULL_SCREEN)
            return
        }

        // 2. Проверка отображения поверх других окон (Overlay)
        if (!checkOverlayPermission()) {
            showRequiredPermissionDialog(PermissionType.OVERLAY)
            return
        }

        // 3. Специфическая проверка фонового режима для китайских устройств Xiaomi/Poco/Redmi
        if (!checkXiaomiBackgroundPermission()) {
            showRequiredPermissionDialog(PermissionType.XIAOMI_BG)
            return
        }

        // Если все системные барьеры пройдены — выполняем безопасный вход
        validateTokenAndProceed()
    }

    // Чек №1: Полноэкранные уведомления
    private fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    // Чек №2: Отображение поверх других окон
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // Чек №3: Всплывающие окна в фоновом режиме (Draw/Show pop-up windows from background)
    private fun checkXiaomiBackgroundPermission(): Boolean {
        if (!isXiaomiDevice()) return true // Если устройство не Xiaomi/Poco, пропускаем чек

        return try {
            val mgr = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val checkOpMethod = mgr.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            // 10021 — это внутренний скрытый код операции OP_BACKGROUND_START_ACTIVITY в Android/MIUI
            val result = checkOpMethod.invoke(mgr, 10021, android.os.Process.myUid(), packageName) as Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            // Фолбек: если прошивка обновилась и рефлексия упала, не блокируем водителя намертво
            true
        }
    }

    // Определение китайских прошивок Xiaomi / Poco / Redmi
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

    // Кастомный диалог на базе твоего dialog_permission_overlay.xml
    private fun showRequiredPermissionDialog(type: PermissionType) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_permission_overlay, null)
        builder.setView(dialogView)

        val alertDialog = builder.create()
        alertDialog.setCancelable(false) // Водитель не закроет диалог тапом мимо экрана

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelPermission)
        val btnAllow = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnAllowPermission)

        // Находим TextView описания по индексу (второй элемент в вертикальном LinearLayout)
        val tvDesc = (dialogView as LinearLayout).getChildAt(1) as TextView

        // Настраиваем текст инструкции динамически под конкретное отсутствующее разрешение
        when (type) {
            PermissionType.LOCATION -> {} // Нативная локация не использует этот диалог
            PermissionType.FULL_SCREEN -> tvDesc.setText(R.string.permission_fsi_desc)
            PermissionType.OVERLAY -> tvDesc.setText(R.string.permission_overlay_desc_new)
            PermissionType.XIAOMI_BG -> tvDesc.setText(R.string.permission_xiaomi_bg_desc)
        }

        // Полностью скрываем кнопку "Скасувати" и растягиваем кнопку "Увімкнути" на всю ширину
        btnCancel.visibility = View.GONE
        val params = btnAllow.layoutParams as LinearLayout.LayoutParams
        params.weight = 2f
        params.marginStart = 0
        btnAllow.layoutParams = params
        btnAllow.text = "Увімкнути"

        btnAllow.setOnClickListener {
            alertDialog.dismiss()
            openSystemPermissionSettings(type) // Перенаправляем в нужный системный тумблер
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // Менеджер переходов в системные настройки телефона
    // Менеджер переходов в системные настройки телефона (Оптимизированный под UX)
    private fun openSystemPermissionSettings(type: PermissionType) {
        when (type) {
            PermissionType.LOCATION -> {} // Обрабатывается через requestLocationPermissionLauncher
            PermissionType.FULL_SCREEN -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivityForResult(intent, REQ_CODE_FULL_SCREEN)
                    } catch (e: Exception) {
                        openNotificationSettingsFallback()
                    }
                }
            }
            PermissionType.OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // ХАК ДЛЯ XIAOMI: Вместо общего списка окон поверх других, открываем
                    // личную карточку нашего приложения, где водитель сразу включит и Overlay, и Фоновые окна!
                    if (isXiaomiDevice()) {
                        if (openXiaomiPermissionEditor(REQ_CODE_OVERLAY)) return
                    }

                    try {
                        // Для чистого Android/Samsung — открываем сразу прямой тумблер приложения
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivityForResult(intent, REQ_CODE_OVERLAY)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            startActivityForResult(fallbackIntent, REQ_CODE_OVERLAY)
                        } catch (anfe: Exception) {
                            showSettingsErrorToast()
                        }
                    }
                }
            }
            PermissionType.XIAOMI_BG -> {
                // Открываем личную карточку разрешений нашего приложения в MIUI/HyperOS
                if (openXiaomiPermissionEditor(REQ_CODE_XIAOMI_BG)) return

                // Глубокий фолбек на стандартный экран "О приложении", если что-то пошло не так
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQ_CODE_XIAOMI_BG)
                } catch (anfe: Exception) {
                    showSettingsErrorToast()
                }
            }
        }
    }
    private fun checkLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                coarseLocation == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    // Вспомогательный метод для мгновенного открытия персональных разрешений конкретно нашего приложения на Xiaomi
    private fun openXiaomiPermissionEditor(requestCode: Int): Boolean {
        return try {
            val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", packageName)
            }
            startActivityForResult(miuiIntent, requestCode)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openNotificationSettingsFallback() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (anfe: Exception) {
            showSettingsErrorToast()
        }
    }

    private fun showSettingsErrorToast() {
        Toast.makeText(this, "Не вдалося відкрити налаштування системи", Toast.LENGTH_SHORT).show()
    }

    // Ловим возврат водителя из настроек телефона и запускаем цепочку заново
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_CODE_FULL_SCREEN -> {
                if (checkFullScreenIntentPermission()) {
                    Toast.makeText(this, "Повноекранні сповіщення дозволено!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Для роботи обов'язково увімкніть повноекранні сповіщення!", Toast.LENGTH_LONG).show()
                }
                handlePermissionsAndProceed() // Запуск проверки каскада заново
            }
            REQ_CODE_OVERLAY -> {
                if (checkOverlayPermission()) {
                    Toast.makeText(this, "Відображення поверх інших вікон дозволено!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Для роботи обов'язково дозвольте відображення поверх інших програм!", Toast.LENGTH_LONG).show()
                }
                handlePermissionsAndProceed()
            }
            REQ_CODE_XIAOMI_BG -> {
                if (checkXiaomiBackgroundPermission()) {
                    Toast.makeText(this, "Роботу у фоновому режимі дозволено!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Будь ласка, дозвольте відображення спливаючих вікон у фоновому режимі!", Toast.LENGTH_LONG).show()
                }
                handlePermissionsAndProceed()
            }
        }
    }

    private fun goToMainActivity() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun validateTokenAndProceed() {
        binding.btnContinue.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@AccountSelectionActivity).getDriverProfile()

                if (response.isSuccessful) {
                    goToMainActivity()
                } else {
                    Toast.makeText(this@AccountSelectionActivity, "Не вдалося оновити сесію. Спробуйте увійти знову або змінити акаунт.", Toast.LENGTH_LONG).show()
                    binding.btnContinue.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccountSelectionActivity, "Помилка з'єднання з сервером спробуйте ще раз.", Toast.LENGTH_LONG).show()
                binding.btnContinue.isEnabled = true
            }
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}