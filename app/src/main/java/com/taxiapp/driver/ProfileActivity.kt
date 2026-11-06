package com.taxiapp.driver

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.utils.SessionManager
import com.taxiapp.driver.utils.toFullImageUrl
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var currentRnokpp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val rootView = findViewById<View>(R.id.profile_root) ?: findViewById(android.R.id.content)
        val headerContent = findViewById<View>(R.id.header_content)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerContent != null) {
                headerContent.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (28 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        sessionManager = SessionManager(this)

        setupUI()
        applyUklonOutlineIcons()
        loadProfileData()
    }

    override fun onResume() {
        super.onResume()
        loadProfileData()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_delete_account).setOnClickListener {
            startActivity(Intent(this, DeleteAccountActivity::class.java))
        }

        findViewById<View>(R.id.btn_edit_phone).setOnClickListener {
            startActivity(Intent(this, ChangePhoneActivity::class.java))
        }

        findViewById<View>(R.id.btn_edit_ipn).setOnClickListener {
            val intent = Intent(this, ChangeRnokppActivity::class.java)
            intent.putExtra("CURRENT_RNOKPP", currentRnokpp)
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_disability_status).setOnClickListener {
            startActivity(Intent(this, DisabilityStatusActivity::class.java))
        }
    }

    /**
     * Отрисовывает фирменные контурные иконки в точном стиле Uklon Drive:
     * Конверт (Email), Пин локации (Регион), Бейдж ИПН, Мед-планшет и Удаление.
     */
    private fun applyUklonOutlineIcons() {
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val errorColor = ContextCompat.getColor(this, R.color.driver_error_red)

        // 1. Конверт (Email)
        findViewById<ImageView>(R.id.iv_icon_email)?.apply {
            clearColorFilter()
            setImageDrawable(createOutlineIcon(primaryColor) { canvas, paint, s ->
                val rect = RectF(s * 0.12f, s * 0.22f, s * 0.88f, s * 0.78f)
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, paint)
                canvas.drawLine(s * 0.14f, s * 0.26f, s * 0.50f, s * 0.54f, paint)
                canvas.drawLine(s * 0.86f, s * 0.26f, s * 0.50f, s * 0.54f, paint)
            })
        }

        // 2. Пин локации (Регіон)
        findViewById<ImageView>(R.id.iv_icon_region)?.apply {
            clearColorFilter()
            setImageDrawable(createOutlineIcon(primaryColor) { canvas, paint, s ->
                val path = PathParser.createPathFromPathData(
                    "M12,21 C12,21 18,14.8 18,9.5 C18,6.19 15.31,3.5 12,3.5 C8.69,3.5 6,6.19 6,9.5 C6,14.8 12,21 12,21 Z"
                )
                val matrix = android.graphics.Matrix().apply { setScale(s / 24f, s / 24f) }
                path.transform(matrix)
                canvas.drawPath(path, paint)
                canvas.drawCircle(s * 0.5f, s * (9.5f / 24f), s * (2.3f / 24f), paint)
            })
        }

        // 3. Карточка документа РНОКПП (ІПН)
        findViewById<ImageView>(R.id.iv_icon_ipn)?.apply {
            clearColorFilter()
            setImageDrawable(createOutlineIcon(primaryColor) { canvas, paint, s ->
                val rect = RectF(s * 0.12f, s * 0.20f, s * 0.88f, s * 0.80f)
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, paint)
                canvas.drawLine(s * 0.24f, s * 0.38f, s * 0.48f, s * 0.38f, paint)
                canvas.drawLine(s * 0.24f, s * 0.52f, s * 0.48f, s * 0.52f, paint)
                canvas.drawLine(s * 0.24f, s * 0.66f, s * 0.42f, s * 0.66f, paint)
                canvas.drawCircle(s * 0.68f, s * 0.44f, s * 0.08f, paint)
                val arcRect = RectF(s * 0.56f, s * 0.54f, s * 0.80f, s * 0.74f)
                canvas.drawArc(arcRect, 180f, 180f, false, paint)
            })
        }

        // 4. Медицинский планшет с плюсом (Порушення функцій / інвалідність, как в Uklon)
        findViewById<ImageView>(R.id.icon_accessibility)?.apply {
            clearColorFilter()
            setImageDrawable(createOutlineIcon(primaryColor) { canvas, paint, s ->
                val board = RectF(s * 0.20f, s * 0.22f, s * 0.80f, s * 0.86f)
                canvas.drawRoundRect(board, s * 0.08f, s * 0.08f, paint)
                val clip = RectF(s * 0.36f, s * 0.14f, s * 0.64f, s * 0.28f)
                canvas.drawRoundRect(clip, s * 0.04f, s * 0.04f, paint)
                canvas.drawLine(s * 0.50f, s * 0.44f, s * 0.50f, s * 0.66f, paint)
                canvas.drawLine(s * 0.39f, s * 0.55f, s * 0.61f, s * 0.55f, paint)
            })
        }

        // 5. Иконка удаления аккаунта (Корзина)
        findViewById<ImageView>(R.id.iv_icon_delete)?.apply {
            clearColorFilter()
            setImageDrawable(createOutlineIcon(errorColor) { canvas, paint, s ->
                canvas.drawLine(s * 0.20f, s * 0.28f, s * 0.80f, s * 0.28f, paint)
                canvas.drawLine(s * 0.38f, s * 0.20f, s * 0.62f, s * 0.20f, paint)
                val body = RectF(s * 0.26f, s * 0.28f, s * 0.74f, s * 0.84f)
                canvas.drawRoundRect(body, s * 0.06f, s * 0.06f, paint)
                canvas.drawLine(s * 0.42f, s * 0.42f, s * 0.42f, s * 0.70f, paint)
                canvas.drawLine(s * 0.58f, s * 0.42f, s * 0.58f, s * 0.70f, paint)
            })
        }
    }

    private fun createOutlineIcon(color: Int, drawBlock: (Canvas, Paint, Float) -> Unit): BitmapDrawable {
        val sizePx = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawBlock(canvas, paint, sizePx.toFloat())
        return BitmapDrawable(resources, bitmap)
    }

    private fun loadProfileData() {
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvDriverId = findViewById<TextView>(R.id.tv_profile_driver_id)
        val tvRating = findViewById<TextView>(R.id.tv_profile_rating)
        val tvPhone = findViewById<TextView>(R.id.tv_profile_phone)
        val imgAvatar = findViewById<ImageView>(R.id.img_profile_avatar)
        val tvEmail = findViewById<TextView>(R.id.tv_profile_email)
        val tvIpn = findViewById<TextView>(R.id.tv_profile_ipn)
        val tvLicense = findViewById<TextView>(R.id.tv_profile_license)

        val savedName = sessionManager.getDriverName()
        if (savedName != null) {
            tvName.text = extractFirstName(savedName)
        }
        val savedId = sessionManager.getDriverId()
        if (savedId > 0) {
            tvDriverId?.text = savedId.toString()
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@ProfileActivity).getDriverProfile()

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    val fullName = profile.fullName ?: "Водій"
                    sessionManager.saveDriverName(fullName)
                    tvName.text = extractFirstName(fullName)

                    tvDriverId?.text = profile.id.toString()
                    tvRating?.text = String.format(Locale.US, "%.2f", profile.rating)

                    tvPhone.text = formatPhoneDisplay(profile.phoneNumber)
                    tvEmail.text = if (!profile.email.isNullOrBlank()) profile.email else "Не вказано"

                    currentRnokpp = profile.rnokpp

                    if (!profile.rnokpp.isNullOrEmpty() && profile.rnokpp.length >= 6) {
                        val first2 = profile.rnokpp.take(2)
                        val last2 = profile.rnokpp.takeLast(2)
                        tvIpn.text = "${first2}****${last2}"
                        tvIpn.setTextColor(ContextCompat.getColor(this@ProfileActivity, R.color.driver_text_secondary))
                    } else {
                        tvIpn.text = "Додати"
                        tvIpn.setTextColor(ContextCompat.getColor(this@ProfileActivity, R.color.driver_neon_teal))
                    }

                    tvLicense.text = formatLicenseDisplay(profile.driverLicense)

                    val fullAvatarUrl = toFullImageUrl(profile.photoUrl)
                    if (!fullAvatarUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(fullAvatarUrl)
                            .placeholder(R.drawable.ic_driver_avatar_placeholder)
                            .error(R.drawable.ic_driver_avatar_placeholder)
                            .circleCrop()
                            .into(imgAvatar)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun formatPhoneDisplay(phone: String?): String {
        if (phone.isNullOrBlank()) return "Не вказано"
        val clean = phone.trim()
        return if (clean.startsWith("0") && clean.length == 10) {
            "+38$clean"
        } else {
            clean
        }
    }

    private fun formatLicenseDisplay(license: String?): String {
        if (license.isNullOrBlank()) return "Не вказано"
        val clean = license.trim().replace(" ", "")
        return if (clean.length >= 6) {
            "${clean.take(2)}****${clean.takeLast(2)}"
        } else {
            license
        }
    }

    private fun extractFirstName(fullName: String): String {
        if (fullName.isBlank()) return "Водій"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> parts[1]
            parts.isNotEmpty() -> parts[0]
            else -> "Водій"
        }
    }
}