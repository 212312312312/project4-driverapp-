package com.taxiapp.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.taxiapp.driver.utils.SessionManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class PhotoControlActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var photoURI: Uri? = null
    private var currentPhotoFile: File? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null

        val results: Array<Uri>? = when (result.resultCode) {
            Activity.RESULT_OK -> {
                val selectedUri = result.data?.data ?: photoURI
                if (selectedUri != null) {
                    val compressedUri = compressImageUriIfNeeded(selectedUri)
                    arrayOf(compressedUri)
                } else {
                    null
                }
            }
            else -> null
        }

        callback.onReceiveValue(results)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted && filePathCallback != null) {
            openFileChooserProcess()
        } else if (filePathCallback != null) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(this, "Для фотоконтролю потрібен доступ до камери", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (this@PhotoControlActivity::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        val sessionManager = SessionManager(this)
        val photoControlId = intent.getLongExtra("PHOTO_CONTROL_ID", 0L)
        val driverId = intent.getLongExtra("DRIVER_ID", sessionManager.getDriverId()).coerceAtLeast(0L)
        val token = sessionManager.fetchAuthToken() ?: ""

        setupWebView()

        val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
        val url = if (token.isNotEmpty()) {
            "$baseUrl/driver/photo-upload?id=$photoControlId&driverId=$driverId&token=$token"
        } else {
            "$baseUrl/driver/photo-upload?id=$photoControlId&driverId=$driverId"
        }
        val headers = if (token.isNotEmpty()) mapOf("Authorization" to "Bearer $token") else emptyMap()
        webView.loadUrl(url, headers)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.contains("success")) {
                    Toast.makeText(this@PhotoControlActivity, "Фотоконтроль успішно відправлено!", Toast.LENGTH_LONG).show()
                    finish()
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@PhotoControlActivity.filePathCallback?.onReceiveValue(null)
                this@PhotoControlActivity.filePathCallback = filePathCallback

                if (!hasPermissions()) {
                    requestPermissions()
                    return true
                }

                openFileChooserProcess()
                return true
            }
        }
    }

    private fun openFileChooserProcess() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
                currentPhotoFile = photoFile
            } catch (_: IOException) {}

            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    photoFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }
        }

        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            putExtra(Intent.EXTRA_TITLE, "Оберіть або зробіть фото")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
        }

        try {
            fileChooserLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    /**
     * Сжимает фото с камеры/галереи до 1600px по длинной стороне (JPEG 82%),
     * чтобы отправка 6 фотографий авто не вызывала 413 Payload Too Large или OutOfMemoryError.
     */
    private fun compressImageUriIfNeeded(sourceUri: Uri): Uri {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, options) }

            val maxDim = 1600
            var sampleSize = 1
            while (max(options.outWidth, options.outHeight) / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return sourceUri

            val rotation = contentResolver.openInputStream(sourceUri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val finalBitmap = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            val compressedFile = createImageFile()
            FileOutputStream(compressedFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            finalBitmap.recycle()

            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                compressedFile
            )
        } catch (e: Exception) {
            sourceUri
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (this::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}