package com.taxiapp.driver

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.databinding.ActivityAccountSelectionBinding
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.service.LocationService
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class AccountSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSelectionBinding
    private lateinit var sessionManager: SessionManager

    private var permissionDialog: AlertDialog? = null
    private var isWaitingForFullScreenSettings = false
    private var isProceeding = false

    private val fullScreenIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isWaitingForFullScreenSettings = false
        if (checkFullScreenIntentPermission()) {
            dismissPermissionDialog()
            Toast.makeText(this, "Повноекранні сповіщення дозволено!", Toast.LENGTH_SHORT).show()
            validateTokenAndProceed()
        } else {
            Toast.makeText(this, "Для отримання замовлень увімкніть повноекранні сповіщення", Toast.LENGTH_LONG).show()
        }
    }

    private val requestRuntimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: checkLocationPermission()
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: checkLocationPermission()

        if (fineGranted || coarseGranted) {
            handlePermissionsAndProceed()
        } else {
            Toast.makeText(
                this,
                "Для роботи програми обов'язково потрібен доступ до геолокації!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // Если водитель включил полноэкранный доступ в настройках и вернулся в приложение —
        // мгновенно закрываем диалог (если он был открыт) и сразу пускаем в приложение!
        if (checkFullScreenIntentPermission()) {
            val wasDialogShowing = permissionDialog?.isShowing == true
            dismissPermissionDialog()
            if ((wasDialogShowing || isWaitingForFullScreenSettings) && !isProceeding) {
                isWaitingForFullScreenSettings = false
                validateTokenAndProceed()
            }
        }
    }

    override fun onDestroy() {
        dismissPermissionDialog()
        super.onDestroy()
    }

    private fun dismissPermissionDialog() {
        try {
            if (permissionDialog?.isShowing == true) {
                permissionDialog?.dismiss()
            }
        } catch (_: Exception) {}
        permissionDialog = null
    }

    private fun setupUI() {
        val phone = sessionManager.getDriverPhone() ?: "Невідомий номер"
        binding.tvPhoneNumber.text = phone
    }

    private fun setupListeners() {
        binding.btnContinue.setOnClickListener {
            handlePermissionsAndProceed()
        }

        binding.btnSwitchAccount.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun handlePermissionsAndProceed() {
        if (!checkLocationPermission() || !checkNotificationPermission()) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestRuntimePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            return
        }

        if (!checkFullScreenIntentPermission()) {
            showFullScreenIntentPermissionDialog()
            return
        }

        dismissPermissionDialog()
        validateTokenAndProceed()
    }

    private fun checkLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED ||
                coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    private fun showFullScreenIntentPermissionDialog() {
        if (checkFullScreenIntentPermission()) {
            dismissPermissionDialog()
            validateTokenAndProceed()
            return
        }

        if (permissionDialog?.isShowing == true) return

        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_permission_overlay, null)
        builder.setView(dialogView)

        val alertDialog = builder.create()
        alertDialog.setCancelable(false)
        permissionDialog = alertDialog

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelPermission)
        val btnAllow = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnAllowPermission)
        val tvDesc = (dialogView as LinearLayout).getChildAt(1) as TextView

        tvDesc.setText(R.string.permission_fsi_desc)

        btnCancel.visibility = View.VISIBLE
        btnCancel.setOnClickListener {
            dismissPermissionDialog()
            validateTokenAndProceed()
        }

        btnAllow.text = "Увімкнути"
        btnAllow.setOnClickListener {
            dismissPermissionDialog()
            openFullScreenIntentSettings()
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun openFullScreenIntentSettings() {
        isWaitingForFullScreenSettings = true
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
                fullScreenIntentLauncher.launch(intent)
                return
            } catch (_: Exception) {}
        }

        try {
            val fallbackIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            fullScreenIntentLauncher.launch(fallbackIntent)
        } catch (e: Exception) {
            isWaitingForFullScreenSettings = false
            Toast.makeText(this, "Не вдалося відкрити налаштування системи", Toast.LENGTH_SHORT).show()
            validateTokenAndProceed()
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
        if (isProceeding) return
        isProceeding = true
        dismissPermissionDialog()
        binding.btnContinue.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@AccountSelectionActivity).getDriverProfile()

                if (response.isSuccessful) {
                    goToMainActivity()
                } else {
                    isProceeding = false
                    Toast.makeText(
                        this@AccountSelectionActivity,
                        "Не вдалося оновити сесію. Спробуйте увійти знову або змінити акаунт.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnContinue.isEnabled = true
                }
            } catch (e: Exception) {
                isProceeding = false
                Toast.makeText(
                    this@AccountSelectionActivity,
                    "Помилка з'єднання з сервером, спробуйте ще раз.",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnContinue.isEnabled = true
            }
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}