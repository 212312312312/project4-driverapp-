package com.taxiapp.driver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.utils.toFullImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class CarDocumentsActivity : AppCompatActivity() {

    private var currentImageId: Int? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val targetImageId = currentImageId
        if (uri != null && targetImageId != null) {
            val imageView = findViewById<ImageView>(targetImageId)
            imageView.clearColorFilter()
            imageView.setPadding(0, 0, 0, 0)
            imageView.setImageURI(uri)

            val fieldName = mapImageViewIdToField(targetImageId)
            if (fieldName != null) {
                uploadDocumentToServer(uri, fieldName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_documents)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        loadDocuments()
    }

    private fun mapImageViewIdToField(imageViewId: Int): String? {
        return when (imageViewId) {
            R.id.img_tech_front -> "tech_passport_front"
            R.id.img_tech_back -> "tech_passport_back"
            R.id.img_insurance -> "insurance_photo"
            R.id.img_photo_front -> "photo_front"
            R.id.img_photo_back -> "photo_back"
            R.id.img_photo_left -> "photo_left"
            R.id.img_photo_right -> "photo_right"
            R.id.img_seats_front -> "photo_seats_front"
            R.id.img_seats_back -> "photo_seats_back"
            else -> null
        }
    }

    private fun loadDocuments() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@CarDocumentsActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val car = profile.car

                    if (car != null) {
                        loadImage(car.techPassportFront, R.id.img_tech_front)
                        loadImage(car.techPassportBack, R.id.img_tech_back)
                        loadImage(car.insurancePhoto, R.id.img_insurance)
                        loadImage(car.photoFront, R.id.img_photo_front)
                        loadImage(car.photoBack, R.id.img_photo_back)
                        loadImage(car.photoLeft, R.id.img_photo_left)
                        loadImage(car.photoRight, R.id.img_photo_right)
                        loadImage(car.photoSeatsFront, R.id.img_seats_front)
                        loadImage(car.photoSeatsBack, R.id.img_seats_back)
                    } else {
                        Toast.makeText(this@CarDocumentsActivity, "Спочатку додайте авто в розділі 'Автомобіль'", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@CarDocumentsActivity, "Не вдалося завантажити дані", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CarDocumentsActivity, "Помилка з'єднання", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImage(url: String?, imageViewId: Int) {
        val imageView = findViewById<ImageView>(imageViewId)
        val fullUrl = toFullImageUrl(url)

        imageView.clearColorFilter()
        imageView.setPadding(0, 0, 0, 0)

        if (!fullUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(fullUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_driver_placeholder)
                .error(R.drawable.ic_driver_placeholder)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_driver_placeholder)
            imageView.setColorFilter(Color.parseColor("#444444"))
            imageView.setPadding(50, 50, 50, 50)
        }

        // Разрешаем клик для загрузки или замены фото документа
        imageView.setOnClickListener {
            currentImageId = imageViewId
            pickMedia.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }
    }

    private fun uploadDocumentToServer(uri: Uri, fieldName: String) {
        Toast.makeText(this, "Завантаження фото...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            var tempFile: File? = null
            try {
                tempFile = withContext(Dispatchers.IO) {
                    compressUriToTempFile(uri, fieldName)
                }
                if (tempFile == null) {
                    Toast.makeText(this@CarDocumentsActivity, "Не вдалося обробити зображення", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fieldBody = fieldName.toRequestBody("text/plain".toMediaTypeOrNull())
                val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val multipartPart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

                val response = ApiClient.getInstance().getApiService(this@CarDocumentsActivity)
                    .uploadCarDocument(fieldBody, multipartPart)

                if (response.isSuccessful) {
                    Toast.makeText(this@CarDocumentsActivity, "Фото успішно збережено!", Toast.LENGTH_SHORT).show()
                    loadDocuments()
                } else {
                    Toast.makeText(this@CarDocumentsActivity, "Помилка збереження: ${response.code()}", Toast.LENGTH_SHORT).show()
                    loadDocuments()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CarDocumentsActivity, "Помилка мережі при відправці фото", Toast.LENGTH_SHORT).show()
                loadDocuments()
            } finally {
                tempFile?.delete()
            }
        }
    }

    private fun compressUriToTempFile(sourceUri: Uri, prefix: String): File? {
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
            } ?: return null

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

            val tempFile = File.createTempFile("doc_${prefix}_", ".jpg", cacheDir)
            FileOutputStream(tempFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            finalBitmap.recycle()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}