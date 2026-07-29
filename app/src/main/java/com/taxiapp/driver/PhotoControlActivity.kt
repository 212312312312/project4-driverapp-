package com.taxiapp.driver

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class PhotoControlActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        val photoControlId = intent.getLongExtra("PHOTO_CONTROL_ID", 0L)
        val driverId = intent.getLongExtra("DRIVER_ID", 0L)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // WebView загружает страницу отправки 6 фото с вашего сервера
        val url = "http://192.168.0.107:8080/driver/photo-upload?id=$photoControlId&driverId=$driverId"
        webView.loadUrl(url)
    }
}