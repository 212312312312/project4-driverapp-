package com.taxiapp.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Код для результата выбора файла
    private val FILE_CHOOSER_RESULT_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем WebView программно (без XML)
        webView = WebView(this)
        setContentView(webView)

        val url = intent.getStringExtra("URL")
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        setupWebView()
        webView.loadUrl(url)
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Обработка навигации
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Все ссылки открываем внутри WebView
            }
        }

        // Обработка загрузки файлов (input type="file")
        webView.webChromeClient = object : WebChromeClient() {
            // Для Android 5.0+
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Если был предыдущий запрос, сбрасываем его
                if (fileUploadCallback != null) {
                    fileUploadCallback!!.onReceiveValue(null)
                    fileUploadCallback = null
                }
                fileUploadCallback = filePathCallback

                // --- ИСПРАВЛЕНИЕ ОШИБКИ ТУТ ---
                val intent = fileChooserParams?.createIntent()

                // Проверяем, что intent не null перед использованием
                if (intent == null) {
                    fileUploadCallback = null
                    return false
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    Toast.makeText(this@WebViewActivity, "Не вдалося відкрити вибір файлу", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }
        }
    }

    // Получаем результат выбора фото и отдаем его в WebView
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (fileUploadCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            val results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileUploadCallback!!.onReceiveValue(results)
            fileUploadCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}