package com.taxiapp.driver

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.WalletTransactionDto
import kotlinx.coroutines.launch

class PendingFundsActivity : AppCompatActivity() {

    companion object {
        // HIGH-LOAD КЕШ ОПЕРАЦІЙ В ЧЕРЗІ (TTL 60 сек)
        private const val PENDING_CACHE_TTL_MS = 60_000L

        @Volatile private var cachedPendingList: List<WalletTransactionDto>? = null
        @Volatile private var lastPendingFetchTimeMs: Long = 0L

        fun invalidateCache() {
            lastPendingFetchTimeMs = 0L
        }
    }

    private lateinit var rvPending: RecyclerView
    private lateinit var layoutEmptyPending: View
    private lateinit var adapter: WalletAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_funds)

        val rootView = findViewById<View>(R.id.pending_funds_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_pending_inner)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerInner != null) {
                headerInner.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (16 * density).toInt(),
                    (16 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        findViewById<View>(R.id.btn_back_pending).setOnClickListener { finish() }

        layoutEmptyPending = findViewById(R.id.layout_empty_pending)
        findViewById<ImageView>(R.id.iv_empty_pending_illustration)?.apply {
            imageTintList = null
            setImageDrawable(EmptyFundsIllustrationDrawable())
        }

        rvPending = findViewById(R.id.rv_pending_transactions)
        rvPending.layoutManager = LinearLayoutManager(this)
        adapter = WalletAdapter { orderId ->
            loadOrderDetailsAndOpen(orderId)
        }
        rvPending.adapter = adapter

        loadPendingTransactions()
    }

    private fun loadOrderDetailsAndOpen(orderId: Long) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@PendingFundsActivity, "Завантаження деталей замовлення...", Toast.LENGTH_SHORT).show()

                val response = ApiClient.getInstance()
                    .getApiService(this@PendingFundsActivity)
                    .getOrderByInternalId(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    val intent = Intent(this@PendingFundsActivity, HistoryDetailsActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", order)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@PendingFundsActivity, "Не вдалося завантажити деталі замовлення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFundsActivity, "Помилка мережі при завантаженні замовлення", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPendingList(pendingList: List<WalletTransactionDto>) {
        adapter.submitList(pendingList)
        if (pendingList.isEmpty()) {
            layoutEmptyPending.visibility = View.VISIBLE
            rvPending.visibility = View.GONE
        } else {
            layoutEmptyPending.visibility = View.GONE
            rvPending.visibility = View.VISIBLE
        }
    }

    private fun loadPendingTransactions(forceNetwork: Boolean = false) {
        val now = System.currentTimeMillis()
        val cached = cachedPendingList
        if (!forceNetwork && cached != null && (now - lastPendingFetchTimeMs) < PENDING_CACHE_TTL_MS) {
            renderPendingList(cached)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@PendingFundsActivity).getPendingWalletTransactions()
                if (response.isSuccessful && response.body() != null) {
                    val pendingList = response.body()!!
                    cachedPendingList = pendingList
                    lastPendingFetchTimeMs = System.currentTimeMillis()
                    renderPendingList(pendingList)
                } else {
                    renderPendingList(cached ?: emptyList())
                }
            } catch (e: Exception) {
                renderPendingList(cached ?: emptyList())
                Toast.makeText(this@PendingFundsActivity, "Помилка завантаження", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class EmptyFundsIllustrationDrawable : Drawable() {

        private val mainStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A8E9B")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val questionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A8E9B")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A8E9B")
            style = Paint.Style.FILL
        }

        private val haloOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#32343A")
            style = Paint.Style.FILL
        }

        private val haloInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60636E")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = minOf(w / 220f, h / 150f)

            canvas.save()
            canvas.translate(b.left + (w - 220f * s) / 2f, b.top + (h - 150f * s) / 2f)
            canvas.scale(s, s)

            mainStrokePaint.strokeWidth = 3.2f
            questionStrokePaint.strokeWidth = 2.4f

            drawHaloDot(canvas, 168f, 18f, 9f, 3.2f)
            drawHaloDot(canvas, 50f, 118f, 9f, 3.2f)
            drawHaloDot(canvas, 160f, 128f, 7f, 2.5f)

            val faceCx = 108f
            val faceCy = 86f
            val faceRadius = 33f
            canvas.drawCircle(faceCx, faceCy, faceRadius, mainStrokePaint)

            canvas.drawCircle(faceCx - 7f, faceCy + 3f, 2.3f, dotPaint)
            canvas.drawCircle(faceCx + 7f, faceCy + 3f, 2.3f, dotPaint)

            val mouthRect = RectF(faceCx - 9.5f, faceCy + 13f, faceCx + 9.5f, faceCy + 28f)
            canvas.drawArc(mouthRect, 195f, 150f, false, mainStrokePaint)

            val leftWave = Path().apply {
                moveTo(64f, 108f)
                cubicTo(70f, 113f, 73f, 113f, 77f, 106f)
                cubicTo(81f, 99f, 84f, 100f, 88f, 107f)
            }
            canvas.drawPath(leftWave, mainStrokePaint)

            val rightWave = Path().apply {
                moveTo(128f, 107f)
                cubicTo(132f, 100f, 135f, 99f, 139f, 106f)
                cubicTo(143f, 113f, 146f, 113f, 152f, 108f)
            }
            canvas.drawPath(rightWave, mainStrokePaint)

            canvas.save()
            canvas.rotate(-12f, 62f, 52f)
            val qLeft = Path().apply {
                moveTo(52f, 38f)
                cubicTo(52f, 25f, 72f, 25f, 72f, 37f)
                cubicTo(72f, 46f, 61f, 47f, 61f, 58f)
            }
            canvas.drawPath(qLeft, questionStrokePaint)
            canvas.drawCircle(61f, 67f, 2.0f, dotPaint)
            canvas.restore()

            canvas.save()
            canvas.rotate(14f, 156f, 58f)
            val qRight = Path().apply {
                moveTo(148f, 46f)
                cubicTo(148f, 35f, 165f, 35f, 165f, 45f)
                cubicTo(165f, 53f, 156f, 54f, 156f, 63f)
            }
            canvas.drawPath(qRight, questionStrokePaint)
            canvas.drawCircle(156f, 71f, 1.9f, dotPaint)
            canvas.restore()

            canvas.restore()
        }

        private fun drawHaloDot(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float) {
            canvas.drawCircle(cx, cy, outerR, haloOuterPaint)
            canvas.drawCircle(cx, cy, innerR, haloInnerPaint)
        }

        override fun setAlpha(alpha: Int) {
            mainStrokePaint.alpha = alpha
            questionStrokePaint.alpha = alpha
            dotPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            mainStrokePaint.colorFilter = colorFilter
            questionStrokePaint.colorFilter = colorFilter
            dotPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}