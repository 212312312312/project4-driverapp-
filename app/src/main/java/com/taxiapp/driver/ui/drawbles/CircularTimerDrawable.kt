package com.taxiapp.driver.ui.drawables

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Кастомний Drawable для кругового таймера зворотного відліку навколо цифри на кнопці "Прийняти".
 * Малює фонову підкладку-кільце та дугу прогресу, яка зменшується від 360° до 0°.
 */
class CircularTimerDrawable(
    ringColor: Int,
    trackColor: Int,
    strokeWidthPx: Float
) : Drawable() {

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val inset = arcPaint.strokeWidth / 2f + 2f
        arcRect.set(
            b.left + inset,
            b.top + inset,
            b.right - inset,
            b.bottom - inset
        )

        canvas.drawOval(arcRect, trackPaint)

        val sweepAngle = 360f * progress
        if (sweepAngle > 0.5f) {
            canvas.drawArc(arcRect, -90f, sweepAngle, false, arcPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        trackPaint.alpha = alpha
        arcPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        trackPaint.colorFilter = colorFilter
        arcPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
