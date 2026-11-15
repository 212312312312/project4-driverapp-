package com.taxiapp.driver.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Анімований білий круговий лоадер оновлення балансу для екрану "Гаманець".
 */
class WhiteCircleSpinnerDrawable : Drawable() {
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#232426")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()
    private var rotationDeg = 0f

    fun setRotation(deg: Float) {
        rotationDeg = deg
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = minOf(b.width(), b.height()) / 2f

        canvas.drawCircle(cx, cy, radius, circlePaint)

        val arcRadius = radius * 0.52f
        arcPaint.strokeWidth = radius * 0.16f
        arcRect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        canvas.drawArc(arcRect, rotationDeg, 250f, false, arcPaint)
    }

    override fun setAlpha(alpha: Int) {
        circlePaint.alpha = alpha
        arcPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        circlePaint.colorFilter = colorFilter
        arcPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
