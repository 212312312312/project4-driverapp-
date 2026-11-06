package com.taxiapp.driver.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.taxiapp.driver.R
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addFloatingWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (floatingView == null) {
            addFloatingWidget()
        }
        return START_STICKY
    }

    private fun addFloatingWidget() {
        if (floatingView != null) return

        val themeContext = ContextThemeWrapper(this, R.style.Theme_TaxiAppDriver)
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            floatingView = null
            stopSelf()
            return
        }

        setupTouchListener()
    }

    private fun setupTouchListener() {
        val rootContainer = floatingView?.findViewById<View>(R.id.root_container)

        rootContainer?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var startClickTime: Long = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startClickTime = System.currentTimeMillis()
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - startClickTime
                        if (clickDuration < 200 && abs(event.rawX - initialTouchX) < 15 && abs(event.rawY - initialTouchY) < 15) {
                            openApp()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val displayMetrics = resources.displayMetrics
                        val maxX = (displayMetrics.widthPixels - (floatingView?.width ?: 120)).coerceAtLeast(0)
                        val maxY = (displayMetrics.heightPixels - (floatingView?.height ?: 120)).coerceAtLeast(0)

                        params?.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, maxX)
                        params?.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, maxY)
                        try {
                            if (floatingView?.isAttachedToWindow == true) {
                                windowManager?.updateViewLayout(floatingView, params)
                            }
                        } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun openApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(it)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }
}