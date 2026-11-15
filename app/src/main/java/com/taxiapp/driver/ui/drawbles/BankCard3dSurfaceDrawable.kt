package com.taxiapp.driver.ui.drawables

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.taxiapp.driver.R

/**
 * 3D-поверхня банківської картки з металевим градієнтом, діагональним відблиском
 * та інтерактивним світловим променем (spotlight) при свайпі/дотику.
 */
class BankCard3dSurfaceDrawable : Drawable() {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val cardRect = RectF()
    private val rimRect = RectF()

    private val baseColors = intArrayOf(
        Color.parseColor("#3B3E47"),
        Color.parseColor("#2A2C33"),
        Color.parseColor("#1D1E23")
    )
    private val sheenColors = intArrayOf(
        Color.TRANSPARENT,
        Color.parseColor("#18FFFFFF"),
        Color.TRANSPARENT
    )
    private val rimColors = intArrayOf(
        Color.parseColor("#45FFFFFF"),
        Color.parseColor("#14FFFFFF"),
        Color.parseColor("#28000000")
    )
    private val activeSpotColor = Color.parseColor("#2EFFFFFF")
    private val idleSpotColor = Color.parseColor("#12FFFFFF")

    private var spotX = 0.3f
    private var spotY = 0.25f
    private var spotActive = false

    fun setSpotlight(normX: Float, normY: Float, active: Boolean) {
        spotX = normX
        spotY = normY
        spotActive = active
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        cardRect.set(0f, 0f, w, h)
        val inset = 1.2f
        rimRect.set(inset, inset, w - inset, h - inset)

        basePaint.shader = LinearGradient(
            0f, 0f, w, h,
            baseColors,
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        sheenPaint.shader = LinearGradient(
            w * 0.15f, 0f, w * 0.85f, h,
            sheenColors,
            floatArrayOf(0.2f, 0.52f, 0.85f),
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            0f, 0f, w, h,
            rimColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val corner = 20f * (w / 320f).coerceIn(0.8f, 1.5f)

        canvas.drawRoundRect(cardRect, corner, corner, basePaint)
        canvas.drawRoundRect(cardRect, corner, corner, sheenPaint)

        val spotRadius = maxOf(w, h) * 0.65f
        val centerColor = if (spotActive) activeSpotColor else idleSpotColor
        spotlightPaint.shader = RadialGradient(
            w * spotX,
            h * spotY,
            spotRadius,
            intArrayOf(centerColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(cardRect, corner, corner, spotlightPaint)
        canvas.drawRoundRect(rimRect, corner, corner, rimPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Тиснений металевий логотип додатку на 3D-картці.
 */
class EmbossedAppLogoDrawable(context: Context) : Drawable() {
    private val baseLogo: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_unit_logo)?.mutate()
    private val highlightFilter = PorterDuffColorFilter(Color.parseColor("#26FFFFFF"), PorterDuff.Mode.SRC_IN)
    private val darkRimFilter = PorterDuffColorFilter(Color.parseColor("#121418"), PorterDuff.Mode.SRC_IN)
    private val innerMetalFilter = PorterDuffColorFilter(Color.parseColor("#353842"), PorterDuff.Mode.SRC_IN)

    private val rimOffsets = arrayOf(
        Pair(-2, -2),
        Pair(2, -2),
        Pair(-2, 2),
        Pair(2, 2),
        Pair(0, -2),
        Pair(-2, 0)
    )

    override fun draw(canvas: Canvas) {
        val logo = baseLogo ?: return
        val b = bounds
        if (b.isEmpty) return

        val w = b.width()
        val h = b.height()
        val pad = (minOf(w, h) * 0.06f).toInt().coerceAtLeast(2)

        logo.colorFilter = highlightFilter
        logo.setBounds(b.left + pad + 1, b.top + pad + 2, b.right - pad + 1, b.bottom - pad + 2)
        logo.draw(canvas)

        logo.colorFilter = darkRimFilter
        for ((dx, dy) in rimOffsets) {
            logo.setBounds(b.left + pad + dx, b.top + pad + dy, b.right - pad + dx, b.bottom - pad + dy)
            logo.draw(canvas)
        }

        logo.colorFilter = innerMetalFilter
        logo.setBounds(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
        logo.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        baseLogo?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
