package com.taxiapp.driver.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.taxiapp.driver.R

/**
 * Єдина фабрика генерації та кешування Bitmap-маркерів для Google Maps.
 * Усуває дублювання Canvas-рендерингу між MainActivity, OrderDetailsActivity,
 * HistoryDetailsActivity, OrderOfferActivity та OrderProgressActivity.
 */
object MapMarkerBitmapFactory {

    private val bitmapCache = object : LruCache<String, Bitmap>(48) {}
    private val glowDescriptorCache = HashMap<Int, BitmapDescriptor>()

    fun getNumberedMarkerBitmap(context: Context, number: Int, @ColorInt colorInt: Int): Bitmap {
        val densityKey = context.resources.displayMetrics.densityDpi
        val key = "num_${number}_${colorInt}_$densityKey"
        bitmapCache.get(key)?.let { if (!it.isRecycled) return it }

        val view = LayoutInflater.from(context).inflate(R.layout.layout_custom_marker, null)
        val tvNumber = view.findViewById<TextView>(R.id.tv_marker_number)
        val ivBg = view.findViewById<ImageView>(R.id.iv_marker_bg)

        tvNumber.text = number.toString()
        ivBg.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    fun getNumberedMarkerBitmapRes(context: Context, number: Int, @ColorRes colorResId: Int): Bitmap {
        val colorInt = ContextCompat.getColor(context, colorResId)
        return getNumberedMarkerBitmap(context, number, colorInt)
    }

    fun getYellowDriverDotBitmap(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (14 * density).toInt().coerceAtLeast(16)
        val key = "yellow_driver_dot_$size"
        bitmapCache.get(key)?.let { if (!it.isRecycled) return it }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.driver_scheduled_yellow)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    fun getWhiteRingLocationDot(context: Context, @ColorInt color: Int): BitmapDescriptor {
        val density = context.resources.displayMetrics.density
        val totalSize = (24 * density).toInt()
        val key = "ring_dot_${color}_$totalSize"
        bitmapCache.get(key)?.let {
            if (!it.isRecycled) return BitmapDescriptorFactory.fromBitmap(it)
        }

        val baseRadius = 7f * density
        val strokeRadius = 9.5f * density
        val bitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = totalSize / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, strokeRadius, paint)

        paint.color = color
        canvas.drawCircle(center, center, baseRadius, paint)

        bitmapCache.put(key, bitmap)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun getVectorDescriptor(
        context: Context,
        @DrawableRes vectorResId: Int,
        @ColorInt tintColor: Int? = null,
        widthDp: Int? = null,
        heightDp: Int? = null
    ): BitmapDescriptor? {
        val density = context.resources.displayMetrics.density
        val key = "vec_${vectorResId}_${tintColor}_${widthDp}_${heightDp}_${context.resources.displayMetrics.densityDpi}"
        bitmapCache.get(key)?.let {
            if (!it.isRecycled) return BitmapDescriptorFactory.fromBitmap(it)
        }

        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)?.mutate() ?: return null
        tintColor?.let { vectorDrawable.setTint(it) }

        val width = widthDp?.let { (it * density).toInt() } ?: vectorDrawable.intrinsicWidth
        val height = heightDp?.let { (it * density).toInt() } ?: vectorDrawable.intrinsicHeight

        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)

        bitmapCache.put(key, bitmap)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun getDriverLocationDotDescriptor(context: Context): BitmapDescriptor? {
        return getVectorDescriptor(context, R.drawable.bg_driver_location_dot, widthDp = 12, heightDp = 12)
    }

    fun getRadialGlowDescriptor(@ColorInt color: Int): BitmapDescriptor {
        glowDescriptorCache[color]?.let { return it }
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                radius, radius, radius,
                intArrayOf(color, color, Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(radius, radius, radius, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { glowDescriptorCache[color] = it }
    }

    fun getSectorTextDescriptor(context: Context, text: String): BitmapDescriptor {
        val key = "sector_txt_${text}_${context.resources.displayMetrics.densityDpi}"
        bitmapCache.get(key)?.let {
            if (!it.isRecycled) return BitmapDescriptorFactory.fromBitmap(it)
        }

        val textView = TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#00ffaa"))
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            layout(0, 0, measuredWidth, measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(
            textView.measuredWidth.coerceAtLeast(1),
            textView.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        textView.draw(Canvas(bitmap))
        bitmapCache.put(key, bitmap)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
