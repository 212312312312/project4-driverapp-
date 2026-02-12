package com.taxiapp.driver.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.taxiapp.driver.R

class StatusWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    // Тип действия: 0 = ничего, 1 = На месте (Arrived), 2 = Завершить (Complete)
    private var currentActionType = 0

    companion object {
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val ACTION_ARRIVED = 1
        const val ACTION_COMPLETE = 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionType = intent?.getIntExtra(EXTRA_ACTION_TYPE, 0) ?: 0
        if (actionType != 0) {
            currentActionType = actionType
            showWidget(actionType)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showWidget(actionType: Int) {
        if (floatingView != null) {
            updateWidgetUI(actionType)
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Обертаємо контекст у тему для коректної роботи MaterialCardView
        val themeContext = ContextThemeWrapper(this, R.style.Theme_TaxiAppDriver)
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_status_widget, null)

        updateWidgetUI(actionType)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Розміщуємо по центру знизу (зручно натискати пальцем)
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 200 // Відступ від низу

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        floatingView?.setOnClickListener {
            openApp()
        }
    }

    private fun updateWidgetUI(actionType: Int) {
        val tvAction = floatingView?.findViewById<TextView>(R.id.tv_action)
        val ivIcon = floatingView?.findViewById<ImageView>(R.id.iv_icon)

        if (actionType == ACTION_ARRIVED) {
            tvAction?.text = getString(R.string.status_widget_arrived) // "На місці"
            ivIcon?.setImageResource(R.drawable.ic_place_small)
            ivIcon?.setColorFilter(ContextCompat.getColor(this, R.color.driver_neon_teal))
        } else if (actionType == ACTION_COMPLETE) {
            tvAction?.text = getString(R.string.status_widget_complete) // "Завершити"
            ivIcon?.setImageResource(R.drawable.ic_check_mark)
            ivIcon?.setColorFilter(ContextCompat.getColor(this, R.color.taxi_yellow)) // Або інший колір
        }
    }

    private fun openApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(it)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) { e.printStackTrace() }
            floatingView = null
        }
    }
}