package com.taxiapp.driver

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.DriverCardDto
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class CardsActivity : AppCompatActivity() {

    companion object {
        // HIGH-LOAD КЕШ КАРТОК: не смикаємо сервер щоразу при вході на екран "Ваші картки"
        private const val CARDS_CACHE_TTL_MS = 120_000L

        @Volatile private var cachedCards: List<DriverCardDto>? = null
        @Volatile private var lastCardsFetchTimeMs: Long = 0L

        fun invalidateCache() {
            lastCardsFetchTimeMs = 0L
        }
    }

    private lateinit var rvCards: RecyclerView
    private lateinit var card3dView: MaterialCardView
    private lateinit var card3dSurface: View
    private lateinit var iv3dCardChip: ImageView
    private lateinit var iv3dCardEmblem: ImageView
    private lateinit var tv3dCardNumber: TextView
    private lateinit var tv3dCardHolder: TextView
    private lateinit var iv3dCardNetwork: ImageView

    private val cardsList = mutableListOf<DriverCardDto>()
    private var driverRealName: String = ""
    private val card3dSurfaceDrawable = BankCard3dSurfaceDrawable()

    private val webViewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            invalidateCache()
            loadCards(forceNetwork = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)

        val rootView = findViewById<View>(R.id.cards_root) ?: findViewById(android.R.id.content)
        val headerInner = findViewById<View>(R.id.header_cards_inner)
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

        driverRealName = SessionManager(this).getDriverName()?.trim().orEmpty()

        initViews()
        setup3dBankCard()
        setupAdapter()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back_cards).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_card).setOnClickListener { initCardBinding() }

        card3dView = findViewById(R.id.card_3d_view)
        card3dSurface = findViewById(R.id.card_3d_surface)
        iv3dCardChip = findViewById(R.id.iv_3d_card_chip)
        iv3dCardEmblem = findViewById(R.id.iv_3d_card_emblem)
        tv3dCardNumber = findViewById(R.id.tv_3d_card_number)
        tv3dCardHolder = findViewById(R.id.tv_3d_card_holder)
        iv3dCardNetwork = findViewById(R.id.iv_3d_card_network)

        rvCards = findViewById(R.id.rv_cards)
        rvCards.layoutManager = LinearLayoutManager(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup3dBankCard() {
        val density = resources.displayMetrics.density
        card3dView.cameraDistance = 14000f * density
        card3dSurface.background = card3dSurfaceDrawable

        iv3dCardChip.imageTintList = null
        iv3dCardChip.setImageDrawable(EmvChipDrawable())

        iv3dCardEmblem.imageTintList = null
        iv3dCardEmblem.alpha = 1f
        iv3dCardEmblem.setImageDrawable(EmbossedAppLogoDrawable(this))

        render3dCardData(null, animateFlip = false)

        card3dView.setOnTouchListener { v, event ->
            val w = v.width.toFloat().coerceAtLeast(1f)
            val h = v.height.toFloat().coerceAtLeast(1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.animate().cancel()
                    val normX = ((event.x / w) - 0.5f).coerceIn(-0.6f, 0.6f)
                    val normY = ((event.y / h) - 0.5f).coerceIn(-0.6f, 0.6f)

                    v.rotationY = normX * 22f
                    v.rotationX = -normY * 18f
                    v.scaleX = 1.02f
                    v.scaleY = 1.02f

                    card3dSurfaceDrawable.setSpotlight(
                        event.x.coerceIn(0f, w) / w,
                        event.y.coerceIn(0f, h) / h,
                        active = true
                    )
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    card3dSurfaceDrawable.setSpotlight(0.3f, 0.2f, active = false)
                    v.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(420)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .start()
                }
            }
            true
        }
    }

    private fun render3dCardData(card: DriverCardDto?, animateFlip: Boolean) {
        val updateContent = {
            val rawNumber = card?.cardNumber?.trim().orEmpty()
            tv3dCardNumber.text = formatCardNumberFor3dCard(rawNumber)

            val holderRaw = when {
                driverRealName.isNotBlank() -> driverRealName
                !card?.cardHolder.isNullOrBlank() && card?.cardHolder != "Картка Водія" -> card!!.cardHolder!!
                !card?.cardHolder.isNullOrBlank() -> card!!.cardHolder!!
                else -> "CARD HOLDER"
            }
            tv3dCardHolder.text = holderRaw.uppercase()

            val brand = detectCardBrand(rawNumber)
            iv3dCardNetwork.imageTintList = null
            iv3dCardNetwork.setImageDrawable(CardNetworkLogoDrawable(brand))
        }

        if (animateFlip) {
            card3dView.animate()
                .rotationY(12f)
                .rotationX(-5f)
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(130)
                .withEndAction {
                    updateContent()
                    card3dView.animate()
                        .rotationY(0f)
                        .rotationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(280)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                }
                .start()
        } else {
            updateContent()
        }
    }

    private fun formatCardNumberFor3dCard(raw: String): String {
        if (raw.isBlank()) return "••••   ••••   ••••   ••••"
        val cleaned = raw.replace(" ", "")
        return if (cleaned.length >= 10) {
            val first4 = cleaned.take(4).replace('*', '•')
            val last4 = cleaned.takeLast(4).replace('*', '•')
            "$first4   ••••   ••••   $last4"
        } else {
            raw.replace('*', '•')
        }
    }

    private fun formatCardNumberForListItem(raw: String): String {
        val cleaned = raw.replace(" ", "")
        return if (cleaned.length >= 6) {
            val prefix = cleaned.take(2).filter { it.isDigit() }.ifEmpty { "44" }
            val suffix = cleaned.takeLast(4)
            "${prefix}****$suffix"
        } else {
            raw
        }
    }

    private fun detectCardBrand(cardNumber: String): CardBrand {
        val firstDigit = cardNumber.firstOrNull { it.isDigit() }
        return when {
            cardNumber.contains("visa", ignoreCase = true) || firstDigit == '4' -> CardBrand.VISA
            cardNumber.contains("master", ignoreCase = true) || firstDigit == '5' || firstDigit == '2' -> CardBrand.MASTERCARD
            else -> CardBrand.MASTERCARD
        }
    }

    override fun onResume() {
        super.onResume()
        loadDriverProfileAndCards()
    }

    private fun setupAdapter() {
        rvCards.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_driver_card, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val card = cardsList[position]
                val density = resources.displayMetrics.density

                val cardContainer = holder.itemView.findViewById<MaterialCardView>(R.id.card_item_container)
                val badgeActive = holder.itemView.findViewById<View>(R.id.badge_active_card)
                val tvCardNumber = holder.itemView.findViewById<TextView>(R.id.tv_card_number)
                val tvCardStatus = holder.itemView.findViewById<TextView>(R.id.tv_card_status)
                val ivCardCheck = holder.itemView.findViewById<ImageView>(R.id.iv_card_check)
                val ivCardIcon = holder.itemView.findViewById<ImageView>(R.id.iv_card_icon)

                tvCardNumber.text = formatCardNumberForListItem(card.cardNumber)

                val accentColor = ContextCompat.getColor(this@CardsActivity, R.color.driver_neon_teal)
                val dividerColor = ContextCompat.getColor(this@CardsActivity, R.color.driver_divider)
                val secondaryTextColor = ContextCompat.getColor(this@CardsActivity, R.color.driver_text_secondary)

                ivCardCheck.imageTintList = null
                ivCardCheck.colorFilter = null
                ivCardIcon.imageTintList = null
                ivCardIcon.colorFilter = null
                ivCardIcon.setImageDrawable(MiniCardOutlineDrawable(secondaryTextColor))

                if (card.isMain) {
                    cardContainer?.strokeWidth = (2f * density).toInt()
                    cardContainer?.strokeColor = accentColor
                    badgeActive?.visibility = View.VISIBLE

                    ivCardCheck.visibility = View.VISIBLE
                    ivCardCheck.setImageDrawable(RadioRingDrawable(isSelected = true, activeColor = accentColor, inactiveColor = secondaryTextColor))

                    tvCardStatus.visibility = View.GONE
                } else {
                    cardContainer?.strokeWidth = (1f * density).toInt()
                    cardContainer?.strokeColor = dividerColor
                    badgeActive?.visibility = View.GONE

                    ivCardCheck.visibility = View.VISIBLE
                    ivCardCheck.setImageDrawable(RadioRingDrawable(isSelected = false, activeColor = accentColor, inactiveColor = secondaryTextColor))

                    tvCardStatus.text = "Утримуйте для видалення"
                    tvCardStatus.setTextColor(secondaryTextColor)
                    tvCardStatus.setTypeface(tvCardStatus.typeface, Typeface.NORMAL)
                    tvCardStatus.visibility = View.VISIBLE
                }

                val clickTarget = cardContainer ?: holder.itemView
                clickTarget.setOnClickListener {
                    render3dCardData(card, animateFlip = true)
                    if (!card.isMain) {
                        selectMainCard(card.id)
                    }
                }

                clickTarget.setOnLongClickListener {
                    showDeleteConfirmDialog(card.id)
                    true
                }
            }

            override fun getItemCount() = cardsList.size
        }
    }

    /**
     * Завантажує ім'я водія ТІЛЬКИ якщо його ще немає в SessionManager,
     * а список карток бере з кешу пам'яті (TTL 2 хв), щоб не навантажувати сервер.
     */
    private fun loadDriverProfileAndCards() {
        lifecycleScope.launch {
            if (driverRealName.isBlank()) {
                try {
                    val profileResp = ApiClient.getInstance().getApiService(this@CardsActivity).getDriverProfile()
                    if (profileResp.isSuccessful && profileResp.body() != null) {
                        val fullName = profileResp.body()!!.fullName?.trim().orEmpty()
                        if (fullName.isNotEmpty()) {
                            driverRealName = fullName
                            SessionManager(this@CardsActivity).saveDriverName(fullName)
                        }
                    }
                } catch (_: Exception) {}
            }
            loadCards(forceNetwork = false)
        }
    }

    private fun loadCards(forceNetwork: Boolean = false) {
        val now = System.currentTimeMillis()
        val cached = cachedCards
        if (!forceNetwork && cached != null && (now - lastCardsFetchTimeMs) < CARDS_CACHE_TTL_MS) {
            cardsList.clear()
            cardsList.addAll(cached)
            rvCards.adapter?.notifyDataSetChanged()
            val activeCard = cardsList.firstOrNull { it.isMain } ?: cardsList.firstOrNull()
            render3dCardData(activeCard, animateFlip = false)
            return
        }

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).getCards()
                if (resp.isSuccessful && resp.body() != null) {
                    val fetched = resp.body()!!
                    cachedCards = fetched
                    lastCardsFetchTimeMs = System.currentTimeMillis()

                    cardsList.clear()
                    cardsList.addAll(fetched)
                    rvCards.adapter?.notifyDataSetChanged()

                    val activeCard = cardsList.firstOrNull { it.isMain } ?: cardsList.firstOrNull()
                    render3dCardData(activeCard, animateFlip = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initCardBinding() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).initBindCard()
                if (resp.isSuccessful && resp.body() != null) {
                    val url = resp.body()!!["url"]
                    if (!url.isNullOrEmpty()) {
                        val intent = Intent(this@CardsActivity, WebViewActivity::class.java)
                        intent.putExtra("URL", url as String?)
                        webViewLauncher.launch(intent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CardsActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Оптимістичне оновлення основної картки в пам'яті без повторного GET /api/v1/driver/cards.
     */
    private fun selectMainCard(id: Long) {
        val previousState = cardsList.toList()
        val updatedState = cardsList.map { it.copy(isMain = it.id == id) }
        cardsList.clear()
        cardsList.addAll(updatedState)
        cachedCards = updatedState
        rvCards.adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getInstance().getApiService(this@CardsActivity).selectMainCard(id)
                if (!resp.isSuccessful) {
                    cardsList.clear()
                    cardsList.addAll(previousState)
                    cachedCards = previousState
                    rvCards.adapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                cardsList.clear()
                cardsList.addAll(previousState)
                cachedCards = previousState
                rvCards.adapter?.notifyDataSetChanged()
                e.printStackTrace()
            }
        }
    }

    private fun showDeleteConfirmDialog(id: Long) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_card, null)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel_delete)
        val btnConfirm = dialogView.findViewById<View>(R.id.btn_confirm_delete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.getInstance().getApiService(this@CardsActivity).deleteCard(id)
                    if (resp.isSuccessful) {
                        // Оновлюємо список локально в пам'яті без зайвого GET-запиту на сервер
                        val remaining = cardsList.filterNot { it.id == id }.toMutableList()
                        if (remaining.isNotEmpty() && remaining.none { it.isMain }) {
                            remaining[0] = remaining[0].copy(isMain = true)
                        }
                        cardsList.clear()
                        cardsList.addAll(remaining)
                        cachedCards = remaining.toList()
                        rvCards.adapter?.notifyDataSetChanged()

                        val activeCard = cardsList.firstOrNull { it.isMain } ?: cardsList.firstOrNull()
                        render3dCardData(activeCard, animateFlip = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        dialog.show()
    }

    private enum class CardBrand { VISA, MASTERCARD }

    private class BankCard3dSurfaceDrawable : Drawable() {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        private var spotX = 0.3f
        private var spotY = 0.25f
        private var spotActive = false

        fun setSpotlight(normX: Float, normY: Float, active: Boolean) {
            spotX = normX
            spotY = normY
            spotActive = active
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val rect = RectF(0f, 0f, w, h)
            val corner = 20f * (w / 320f).coerceIn(0.8f, 1.5f)

            basePaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.parseColor("#3B3E47"),
                    Color.parseColor("#2A2C33"),
                    Color.parseColor("#1D1E23")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, corner, corner, basePaint)

            sheenPaint.shader = LinearGradient(
                w * 0.15f, 0f, w * 0.85f, h,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#18FFFFFF"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.2f, 0.52f, 0.85f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, corner, corner, sheenPaint)

            val spotRadius = maxOf(w, h) * 0.65f
            val alphaHex = if (spotActive) "#2EFFFFFF" else "#12FFFFFF"
            spotlightPaint.shader = RadialGradient(
                w * spotX,
                h * spotY,
                spotRadius,
                intArrayOf(Color.parseColor(alphaHex), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, corner, corner, spotlightPaint)

            rimPaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.parseColor("#45FFFFFF"),
                    Color.parseColor("#14FFFFFF"),
                    Color.parseColor("#28000000")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            val inset = 1.2f
            canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), corner, corner, rimPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class EmvChipDrawable : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A7E85")
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            val r = minOf(w, h) * 0.18f

            fillPaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.parseColor("#E6E8EC"),
                    Color.parseColor("#C8CCD4"),
                    Color.parseColor("#DCE0E6")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, r, r, fillPaint)
            canvas.drawRoundRect(rect, r, r, linePaint)

            canvas.drawLine(1f, h * 0.34f, w * 0.36f, h * 0.34f, linePaint)
            canvas.drawLine(1f, h * 0.66f, w * 0.36f, h * 0.66f, linePaint)
            canvas.drawLine(w * 0.64f, h * 0.34f, w - 1f, h * 0.34f, linePaint)
            canvas.drawLine(w * 0.64f, h * 0.66f, w - 1f, h * 0.66f, linePaint)

            val centerRect = RectF(w * 0.36f, h * 0.20f, w * 0.64f, h * 0.80f)
            canvas.drawRoundRect(centerRect, r * 0.7f, r * 0.7f, linePaint)
            canvas.drawLine(w * 0.5f, 1f, w * 0.5f, h * 0.20f, linePaint)
            canvas.drawLine(w * 0.5f, h * 0.80f, w * 0.5f, h - 1f, linePaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class EmbossedAppLogoDrawable(context: Context) : Drawable() {
        private val baseLogo: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_unit_logo)?.mutate()
        private val highlightFilter = PorterDuffColorFilter(Color.parseColor("#26FFFFFF"), PorterDuff.Mode.SRC_IN)
        private val darkRimFilter = PorterDuffColorFilter(Color.parseColor("#121418"), PorterDuff.Mode.SRC_IN)
        private val innerMetalFilter = PorterDuffColorFilter(Color.parseColor("#353842"), PorterDuff.Mode.SRC_IN)

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
            val rimOffsets = arrayOf(
                Pair(-2, -2),
                Pair(2, -2),
                Pair(-2, 2),
                Pair(2, 2),
                Pair(0, -2),
                Pair(-2, 0)
            )
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

    private class CardNetworkLogoDrawable(private val brand: CardBrand) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2F4F8")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            when (brand) {
                CardBrand.MASTERCARD -> {
                    val r = minOf(w * 0.25f, h * 0.34f)
                    val cy = h * 0.42f
                    val leftCx = w * 0.5f - r * 0.62f
                    val rightCx = w * 0.5f + r * 0.62f

                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#EB001B")
                    canvas.drawCircle(leftCx, cy, r, paint)

                    paint.color = Color.parseColor("#F79E1B")
                    canvas.drawCircle(rightCx, cy, r, paint)

                    val leftPath = Path().apply { addCircle(leftCx, cy, r, Path.Direction.CW) }
                    val rightPath = Path().apply { addCircle(rightCx, cy, r, Path.Direction.CW) }
                    leftPath.op(rightPath, Path.Op.INTERSECT)
                    paint.color = Color.parseColor("#FF5F00")
                    canvas.drawPath(leftPath, paint)

                    labelPaint.textSize = h * 0.22f
                    canvas.drawText("mastercard.", w * 0.5f, h * 0.94f, labelPaint)
                }

                CardBrand.VISA -> {
                    val visaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                        textSize = h * 0.52f
                        textAlign = Paint.Align.CENTER
                        letterSpacing = 0.04f
                    }
                    canvas.drawText("VISA", w * 0.5f, h * 0.68f, visaPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class RadioRingDrawable(
        private val isSelected: Boolean,
        private val activeColor: Int,
        private val inactiveColor: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = minOf(b.width(), b.height()) / 2f

            if (isSelected) {
                paint.style = Paint.Style.FILL
                paint.color = activeColor
                canvas.drawCircle(cx, cy, r * 0.92f, paint)

                paint.color = Color.parseColor("#2B2E36")
                canvas.drawCircle(cx, cy, r * 0.36f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.18f
                paint.color = inactiveColor
                canvas.drawCircle(cx, cy, r * 0.82f, paint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class MiniCardOutlineDrawable(private val tintColor: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val rect = RectF(2f, 3f, w - 2f, h - 3f)
            val corner = minOf(w, h) * 0.18f

            paint.strokeWidth = (minOf(w, h) * 0.10f).coerceAtLeast(2f)
            canvas.drawRoundRect(rect, corner, corner, paint)
            canvas.drawLine(2f, h * 0.40f, w - 2f, h * 0.40f, paint)
            canvas.drawRoundRect(
                RectF(w * 0.62f, h * 0.56f, w * 0.82f, h * 0.72f),
                2f, 2f, fillPaint
            )
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}