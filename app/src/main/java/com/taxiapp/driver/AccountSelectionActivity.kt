package com.taxiapp.driver

import android.app.AlertDialog
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.taxiapp.driver.databinding.ActivityAccountSelectionBinding
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager

class AccountSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSelectionBinding
    private lateinit var sessionManager: SessionManager
    private val FSI_PERMISSION_REQ_CODE = 5678

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
        // Кнопка ВОЙТИ (ПРОДОВЖИТИ) - теперь проверяет доступ к ПОЛНОЭКРАННЫМ УВЕДОМЛЕНИЯМ
        binding.btnContinue.setOnClickListener {
            if (checkFullScreenIntentPermission()) {
                validateTokenAndProceed() // Используем безопасный вход с проверкой токена
            } else {
                showFullScreenPermissionDialog()
            }
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

    // Проверка разрешения на Полноэкранные уведомления (Full-Screen Intent)
    private fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) { // Android 14+ (API 34)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent() // Официальный чек от Google
        } else {
            true // До Android 14 разрешение выдавалось автоматически при установке
        }
    }

    // Показываем диалог принудительного включения на базе твоего dialog_permission_overlay.xml
    private fun showFullScreenPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_permission_overlay, null)
        builder.setView(dialogView)

        val alertDialog = builder.create()
        alertDialog.setCancelable(false) // Водитель не сможет закрыть диалог тапом мимо экрана

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelPermission)
        val btnAllow = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnAllowPermission)

        // 1. Полностью скрываем кнопку "Скасувати" из разметки
        btnCancel.visibility = View.GONE

        // 2. Растягиваем кнопку "Увімкнути" на всю ширину (weight = 2f)
        val params = btnAllow.layoutParams as LinearLayout.LayoutParams
        params.weight = 2f
        params.marginStart = 0
        btnAllow.layoutParams = params
        btnAllow.text = "Увімкнути"

        btnAllow.setOnClickListener {
            alertDialog.dismiss()
            openFullScreenIntentSettings() // Открываем системный тумблер
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // Переход к системным настройкам конкретно "Полноэкранных уведомлений"
    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // Прямой интент на страницу управления полноэкранными уведомлениями нашего приложения
                val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, FSI_PERMISSION_REQ_CODE)
            } catch (e: Exception) {
                // Если прямой интент на тумблер не сработал — открываем общую страницу уведомлений приложения
                try {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                } catch (anfe: Exception) {
                    Toast.makeText(this, "Не вдалося відкрити налаштування системи", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Автоматическая обработка возвращения водителя из настроек телефона
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FSI_PERMISSION_REQ_CODE) {
            if (checkFullScreenIntentPermission()) {
                Toast.makeText(this, "Доступ дозволено!", Toast.LENGTH_SHORT).show()
                validateTokenAndProceed() // Используем безопасный вход с проверкой токена
            } else {
                Toast.makeText(this, "Для роботи обов'язково увімкніть повноекранні сповіщення!", Toast.LENGTH_LONG).show()
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
                // Выполняем легкий проверочный запрос профиля.
                // Если токен протух, наш обновленный сетевой слой сделает чистый рефреш в бэкграунде!
                val response = ApiClient.getInstance().getApiService(this@AccountSelectionActivity).getDriverProfile()

                if (response.isSuccessful) {
                    // Токен валиден или успешно обновлен — заходим!
                    goToMainActivity()
                } else {
                    // Сервер вернул ошибку, и рефреш не удался (например, сессия удалена на сервере)
                    // Архитектурное правило: НЕ пускаем в MainActivity, но и не выкидываем, давая водителю выбор
                    Toast.makeText(this@AccountSelectionActivity, "Не вдалося оновити сесію. Спробуйте увійти знову або змінити акаунт.", Toast.LENGTH_LONG).show()
                    binding.btnContinue.isEnabled = true
                }
            } catch (e: Exception) {
                // Ошибка сети (например, пропал интернет) — пускаем в MainActivity.
                // Там сработает локальный кэш и логика оффлайна, водитель не должен застревать на входе.
                goToMainActivity()
            }
        }
    }
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}