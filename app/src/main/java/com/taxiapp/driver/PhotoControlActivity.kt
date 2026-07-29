package com.taxiapp.driver

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class PhotoControlActivity : AppCompatActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Лончер для вызова системного выбора файлов / галереи
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val intentData = result.data
            val results: Array<Uri>? = if (result.resultCode == RESULT_OK) {
                if (intentData?.dataString != null) {
                    arrayOf(Uri.parse(intentData.dataString))
                } else if (intentData?.clipData != null) {
                    val clipData = intentData.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else null
            } else null

            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        val photoControlId = intent.getLongExtra("PHOTO_CONTROL_ID", 0L)
        val driverId = intent.getLongExtra("DRIVER_ID", 0L)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.webViewClient = WebViewClient()

        // ВАЖНО: WebChromeClient обрабатывает клики на <input type="file">
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@PhotoControlActivity.filePathCallback?.onReceiveValue(null)
                this@PhotoControlActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    this@PhotoControlActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        val baseUrl = "http://192.168.0.107:8080" // Замените на ваш актуальный BASE_URL если используется другой
        val url = "$baseUrl/driver/photo-upload?id=$photoControlId&driverId=$driverId"
        webView.loadUrl(url)
    }
}