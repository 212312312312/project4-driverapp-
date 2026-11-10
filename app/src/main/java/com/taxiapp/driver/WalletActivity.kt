package com.taxiapp.driver

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.InitPaymentRequest
import com.taxiapp.driver.network.WalletTransactionDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class WalletActivity : AppCompatActivity() {

    companion object {
        // HIGH-LOAD КЕШ У ПАМ'ЯТІ: запобігає повторним запитам до сервера при переходах між екранами та вкладками
        private const val CACHE_TTL_MS = 60_000L
        private const val MANUAL_REFRESH_COOLDOWN_MS = 15_000L

        @Volatile private var cachedCommissionBalance: Double = 0.0
        @Volatile private var cachedOrderEarningsBalance: Double = 0.0
        @Volatile private var cachedTransactions: List<WalletTransactionDto>? = null
        @Volatile private var lastFetchTimeMs: Long = 0L

        fun invalidateCache() {
            lastFetchTimeMs = 0L
        }
    }

    private lateinit var walletTabs: TabLayout
    private lateinit var segTabBalance: MaterialCardView
    private lateinit var segTabOperations: MaterialCardView
    private lateinit var tvSegBalance: TextView
    private lateinit var tvSegOperations: TextView

    private lateinit var balanceHeroSection: View
    private lateinit var balanceActionsContainer: View
    private lateinit var tvBalanceLabel: TextView
    private lateinit var tvBalance: TextView
    private lateinit var ivBalanceLoader: ImageView
    private lateinit var btnTopUp: View
    private lateinit var layoutFundsButtons: View

    private lateinit var transactionsSheetContainer: View
    private lateinit var sheetDragHandleArea: View
    private lateinit var tvStickyDate: TextView
    private lateinit var tvEmptyTransactions: TextView
    private lateinit var adapter: WalletAdapter
    private lateinit var rvTransactions: RecyclerView

    private var commissionBalance: Double = 0.00
    private var orderEarningsBalance: Double = 0.00
    private var allTransactions: List<WalletTransactionDto> = emptyList()
    private var isCommissionTabSelected: Boolean = true

    // Параметри плавного згортання/розгортання блоку круглих кнопок при свайпі шторки
    private var actionsMaxHeight: Int = 0
    private var collapseFraction: Float = 0f // 0f = кнопки розгорнуті, 1f = кнопки згорнуті (шторка піднята)
    private var sheetAnimator: ValueAnimator? = null

    // Білий круглий спінер завантаження (тільки при першому холодному старті або ручному pull-to-refresh)
    private val whiteSpinnerDrawable = WhiteCircleSpinnerDrawable()
    private var spinnerAnimator: ValueAnimator? = null
    private var isRefreshingData: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        val rootView = findViewById<View>(R.id.wallet_root) ?: findViewById(android.R.id.content)
        val headerContent = findViewById<View>(R.id.header_content)
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (headerContent != null) {
                headerContent.setPadding(
                    (16 * density).toInt(),
                    systemBars.top + (24 * density).toInt(),
                    (16 * density).toInt(),
                    (12 * density).toInt()
                )
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        initViews()
        setupCustomActionIcons()
        setupRecyclerView()
        setupListeners()
        setupSheetCollapseAndPullRefreshGesture()

        // Якщо в кеші вже є дані — миттєво показуємо їх при відкритті без очікування мережі
        cachedTransactions?.let { cached ->
            commissionBalance = cachedCommissionBalance
            orderEarningsBalance = cachedOrderEarningsBalance
            allTransactions = cached
        }
        updateTabVisuals(isCommissionTab = true)
        filterAndSubmitTransactions()
    }

    private fun initViews() {
        walletTabs = findViewById(R.id.wallet_tabs)
        segTabBalance = findViewById(R.id.seg_tab_balance)
        segTabOperations = findViewById(R.id.seg_tab_operations)
        tvSegBalance = findViewById(R.id.tv_seg_balance)
        tvSegOperations = findViewById(R.id.tv_seg_operations)

        balanceHeroSection = findViewById(R.id.balance_hero_section)
        balanceActionsContainer = findViewById(R.id.balance_actions_container)
        tvBalanceLabel = findViewById(R.id.tv_balance_label)
        tvBalance = findViewById(R.id.tv_main_balance)
        ivBalanceLoader = findViewById(R.id.iv_balance_loader)
        btnTopUp = findViewById(R.id.btn_top_up)
        layoutFundsButtons = findViewById(R.id.layout_funds_buttons)

        transactionsSheetContainer = findViewById(R.id.transactions_sheet_container)
        sheetDragHandleArea = findViewById(R.id.sheet_drag_handle_area)
        tvStickyDate = findViewById(R.id.tv_sticky_date)
        tvEmptyTransactions = findViewById(R.id.tv_empty_transactions)
        rvTransactions = findViewById(R.id.rv_transactions)

        ivBalanceLoader.setImageDrawable(whiteSpinnerDrawable)

        balanceActionsContainer.post {
            measureActionsMaxHeight()
        }
    }

    private fun measureActionsMaxHeight() {
        val parentWidth = (balanceHeroSection.width).takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        balanceActionsContainer.measure(
            View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measured = balanceActionsContainer.measuredHeight
        if (measured > 0) {
            actionsMaxHeight = measured
        }
    }

    private fun setupCustomActionIcons() {
        val primaryColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val darkIconColor = Color.parseColor("#2B2D35")

        findViewById<ImageView>(R.id.iv_top_up_icon)?.apply {
            imageTintList = null
            setImageDrawable(WalletActionIconDrawable(WalletActionIconDrawable.Type.WALLET_PLUS, primaryColor))
        }
        findViewById<ImageView>(R.id.iv_my_cards_icon)?.apply {
            imageTintList = null
            setImageDrawable(WalletActionIconDrawable(WalletActionIconDrawable.Type.WALLET_CARDS, primaryColor))
        }
        findViewById<ImageView>(R.id.iv_my_cards_badge)?.apply {
            imageTintList = null
            setImageDrawable(WalletActionIconDrawable(WalletActionIconDrawable.Type.EDIT_PENCIL, darkIconColor))
        }
        findViewById<ImageView>(R.id.iv_my_funds_icon)?.apply {
            imageTintList = null
            setImageDrawable(WalletActionIconDrawable(WalletActionIconDrawable.Type.WALLET_PAYOUT, primaryColor))
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        segTabBalance.setOnClickListener {
            if (!isCommissionTabSelected) {
                walletTabs.getTabAt(0)?.select()
                onTabSwitched(isCommissionTab = true)
            }
        }

        segTabOperations.setOnClickListener {
            if (isCommissionTabSelected) {
                walletTabs.getTabAt(1)?.select()
                onTabSwitched(isCommissionTab = false)
            }
        }

        walletTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val isBalance = (tab?.position ?: 0) == 0
                if (isCommissionTabSelected != isBalance) {
                    onTabSwitched(isBalance)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.btn_my_cards).setOnClickListener {
            startActivity(Intent(this, CardsActivity::class.java))
        }
        findViewById<View>(R.id.btn_my_funds).setOnClickListener {
            startActivity(Intent(this, PendingFundsActivity::class.java))
        }
        findViewById<View>(R.id.btn_top_up).setOnClickListener {
            val intent = Intent(this, TopUpActivity::class.java)
            intent.putExtra("CURRENT_BALANCE", commissionBalance)
            startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSheetCollapseAndPullRefreshGesture() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val density = resources.displayMetrics.density
        val pullToRefreshThreshold = 78f * density

        var handleStartY = 0f
        var handleLastY = 0f
        var handleDragging = false
        var pullDownDistance = 0f

        sheetDragHandleArea.setOnTouchListener { _, event ->
            if (actionsMaxHeight <= 0) measureActionsMaxHeight()
            val maxH = actionsMaxHeight.coerceAtLeast((118 * density).toInt()).toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheetAnimator?.cancel()
                    handleStartY = event.rawY
                    handleLastY = event.rawY
                    handleDragging = false
                    pullDownDistance = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = handleLastY - event.rawY
                    if (!handleDragging && abs(event.rawY - handleStartY) > touchSlop) {
                        handleDragging = true
                    }
                    if (handleDragging) {
                        if (collapseFraction == 0f && dy < 0f) {
                            pullDownDistance += (-dy)
                        } else {
                            pullDownDistance = 0f
                            val newFraction = (collapseFraction + (dy / maxH)).coerceIn(0f, 1f)
                            setSheetCollapseFraction(newFraction)
                        }
                    }
                    handleLastY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!handleDragging) {
                        animateSheetTo(if (collapseFraction < 0.5f) 1f else 0f)
                    } else if (pullDownDistance >= pullToRefreshThreshold && !isRefreshingData) {
                        onManualPullToRefresh()
                    } else {
                        val target = if (collapseFraction > 0.35f) 1f else 0f
                        animateSheetTo(target)
                    }
                    pullDownDistance = 0f
                    true
                }

                else -> false
            }
        }

        var rvLastY = 0f
        var rvPullDownDistance = 0f
        var velocityTracker: VelocityTracker? = null

        rvTransactions.setOnTouchListener { _, event ->
            if (actionsMaxHeight <= 0) measureActionsMaxHeight()
            val maxH = actionsMaxHeight.coerceAtLeast((118 * density).toInt()).toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheetAnimator?.cancel()
                    rvLastY = event.rawY
                    rvPullDownDistance = 0f
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val rawY = event.rawY
                    val dy = rvLastY - rawY
                    rvLastY = rawY

                    if (dy > 0f && collapseFraction < 1f) {
                        val newFraction = (collapseFraction + (dy / maxH)).coerceIn(0f, 1f)
                        setSheetCollapseFraction(newFraction)
                        return@setOnTouchListener true
                    }

                    val isAtTop = !rvTransactions.canScrollVertically(-1)
                    if (dy < 0f && isAtTop) {
                        if (collapseFraction > 0f) {
                            val newFraction = (collapseFraction + (dy / maxH)).coerceIn(0f, 1f)
                            setSheetCollapseFraction(newFraction)
                            return@setOnTouchListener true
                        } else {
                            rvPullDownDistance += (-dy)
                        }
                    } else {
                        rvPullDownDistance = 0f
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    if (rvPullDownDistance >= pullToRefreshThreshold && !isRefreshingData) {
                        rvPullDownDistance = 0f
                        onManualPullToRefresh()
                    } else if (collapseFraction > 0f && collapseFraction < 1f) {
                        val target = when {
                            vy < -500f -> 1f
                            vy > 500f -> 0f
                            collapseFraction >= 0.35f -> 1f
                            else -> 0f
                        }
                        animateSheetTo(target)
                    }
                    rvPullDownDistance = 0f
                    false
                }

                else -> false
            }
        }
    }

    /**
     * Ручний pull-to-refresh (коли водій сам потягнув шторку вниз для оновлення):
     * захищений cooldown-таймером (15 сек), щоб свайпи туди-сюди не спамили сервер.
     */
    private fun onManualPullToRefresh() {
        val now = System.currentTimeMillis()
        if (cachedTransactions != null && (now - lastFetchTimeMs) < MANUAL_REFRESH_COOLDOWN_MS) {
            // Дані вже свіжі — показуємо короткий візуальний відгук без зайвого HTTP-запиту
            lifecycleScope.launch {
                startWhiteLoaderAnimation()
                delay(320L)
                stopWhiteLoaderAnimation()
                filterAndSubmitTransactions()
            }
        } else {
            loadData(forceNetwork = true, showWhiteLoader = true)
        }
    }

    private fun setSheetCollapseFraction(fraction: Float) {
        collapseFraction = fraction.coerceIn(0f, 1f)
        if (actionsMaxHeight <= 0) measureActionsMaxHeight()
        val maxH = actionsMaxHeight.coerceAtLeast((118 * resources.displayMetrics.density).toInt())
        val currentH = ((1f - collapseFraction) * maxH).toInt()

        val lp = balanceActionsContainer.layoutParams
        lp.height = currentH
        balanceActionsContainer.layoutParams = lp
        balanceActionsContainer.alpha = (1f - collapseFraction * 1.25f).coerceIn(0f, 1f)
        balanceActionsContainer.visibility = if (currentH <= 1) View.INVISIBLE else View.VISIBLE
    }

    private fun animateSheetTo(targetFraction: Float) {
        sheetAnimator?.cancel()
        val start = collapseFraction
        val end = targetFraction.coerceIn(0f, 1f)
        if (abs(start - end) < 0.005f) {
            setSheetCollapseFraction(end)
            if (end == 0f) {
                val lp = balanceActionsContainer.layoutParams
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                balanceActionsContainer.layoutParams = lp
            }
            return
        }

        sheetAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 230
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                setSheetCollapseFraction(anim.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (end == 0f) {
                        val lp = balanceActionsContainer.layoutParams
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        balanceActionsContainer.layoutParams = lp
                        balanceActionsContainer.alpha = 1f
                        balanceActionsContainer.visibility = View.VISIBLE
                        balanceActionsContainer.post { measureActionsMaxHeight() }
                    }
                }
            })
            start()
        }
    }

    private fun setupRecyclerView() {
        adapter = WalletAdapter { orderId ->
            loadOrderDetailsAndOpen(orderId)
        }
        val layoutManager = LinearLayoutManager(this)
        rvTransactions.layoutManager = layoutManager
        rvTransactions.adapter = adapter

        rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isRefreshingData) return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val headerText = adapter.getDateHeaderForPosition(firstVisible)
                if (!headerText.isNullOrBlank()) {
                    tvStickyDate.text = headerText
                    tvStickyDate.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun loadOrderDetailsAndOpen(orderId: Long) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Завантаження деталей замовлення...", Toast.LENGTH_SHORT).show()

                val response = ApiClient.getInstance()
                    .getApiService(this@WalletActivity)
                    .getOrderByInternalId(orderId)

                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    val intent = Intent(this@WalletActivity, HistoryDetailsActivity::class.java).apply {
                        putExtra("EXTRA_ORDER", order)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@WalletActivity, "Не вдалося завантажити деталі замовлення", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Помилка мережі при завантаженні замовлення", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        verifyPendingPaymentAndLoadData()
    }

    private fun verifyPendingPaymentAndLoadData() {
        lifecycleScope.launch {
            val session = com.taxiapp.driver.utils.SessionManager(this@WalletActivity)
            val pendingPaymentId = session.getPendingPaymentId()

            // Якщо водій щойно повернувся з оплати LiqPay — перевіряємо платіж та примусово оновлюємо баланс
            if (pendingPaymentId > 0L) {
                try {
                    val checkResp = ApiClient.getInstance().getApiService(this@WalletActivity).checkPaymentStatus(pendingPaymentId)
                    if (checkResp.isSuccessful) {
                        val status = checkResp.body()?.get("status")
                        if (status == "SUCCESS") {
                            session.clearPendingPaymentId()
                            invalidateCache()
                            Toast.makeText(this@WalletActivity, "Баланс успішно поповнено!", Toast.LENGTH_LONG).show()
                        } else if (status == "FAILED") {
                            session.clearPendingPaymentId()
                        }
                    }
                } catch (_: Exception) {}
                loadData(forceNetwork = true, showWhiteLoader = true)
                return@launch
            }

            // Якщо кеш свіжий (до 60 сек) — НЕ смикаємо сервер взагалі, беремо з пам'яті!
            val now = System.currentTimeMillis()
            val cached = cachedTransactions
            if (cached != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
                commissionBalance = cachedCommissionBalance
                orderEarningsBalance = cachedOrderEarningsBalance
                allTransactions = cached
                renderBalance(
                    if (isCommissionTabSelected) commissionBalance else orderEarningsBalance,
                    isOperationsTab = !isCommissionTabSelected
                )
                filterAndSubmitTransactions()
                return@launch
            }

            // Холодний старт або кеш застарів (> 60 сек)
            loadData(forceNetwork = true, showWhiteLoader = cached == null)
        }
    }

    /**
     * Перемикання між вкладками "Баланс" та "Операції" працює МИТТЄВО в пам'яті (0 запитів до сервера!).
     */
    private fun onTabSwitched(isCommissionTab: Boolean) {
        updateTabVisuals(isCommissionTab)
        if (collapseFraction > 0f) {
            animateSheetTo(0f)
        } else {
            balanceActionsContainer.post { measureActionsMaxHeight() }
        }
        filterAndSubmitTransactions()
    }

    private fun updateTabVisuals(isCommissionTab: Boolean) {
        isCommissionTabSelected = isCommissionTab
        val density = resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)

        val primaryTextColor = ContextCompat.getColor(this, R.color.driver_text_primary)
        val secondaryTextColor = ContextCompat.getColor(this, R.color.driver_text_secondary)
        val activeSegmentBg = Color.parseColor("#5A5E6D")

        if (isCommissionTab) {
            segTabBalance.setCardBackgroundColor(activeSegmentBg)
            segTabBalance.strokeWidth = strokePx
            segTabBalance.strokeColor = primaryTextColor
            tvSegBalance.setTextColor(primaryTextColor)

            segTabOperations.setCardBackgroundColor(Color.TRANSPARENT)
            segTabOperations.strokeWidth = 0
            tvSegOperations.setTextColor(secondaryTextColor)

            tvBalanceLabel.text = "Баланс показує стан ваших розрахунків. Підтримуйте позитивний баланс, щоб приймати замовлення"
            btnTopUp.visibility = View.VISIBLE
            layoutFundsButtons.visibility = View.GONE
            if (!isRefreshingData) {
                renderBalance(commissionBalance, isOperationsTab = false)
            }
        } else {
            segTabOperations.setCardBackgroundColor(activeSegmentBg)
            segTabOperations.strokeWidth = strokePx
            segTabOperations.strokeColor = primaryTextColor
            tvSegOperations.setTextColor(primaryTextColor)

            segTabBalance.setCardBackgroundColor(Color.TRANSPARENT)
            segTabBalance.strokeWidth = 0
            tvSegBalance.setTextColor(secondaryTextColor)

            tvBalanceLabel.text = "Тут відображаються кошти за безготівкові замовлення та чайові"
            btnTopUp.visibility = View.GONE
            layoutFundsButtons.visibility = View.VISIBLE
            if (!isRefreshingData) {
                renderBalance(orderEarningsBalance, isOperationsTab = true)
            }
        }
    }

    private fun startWhiteLoaderAnimation() {
        isRefreshingData = true
        tvBalance.visibility = View.INVISIBLE
        ivBalanceLoader.visibility = View.VISIBLE

        tvStickyDate.visibility = View.GONE
        tvEmptyTransactions.visibility = View.GONE
        rvTransactions.visibility = View.INVISIBLE

        spinnerAnimator?.cancel()
        spinnerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                whiteSpinnerDrawable.setRotation(anim.animatedValue as Float)
            }
            start()
        }
    }

    private fun stopWhiteLoaderAnimation() {
        isRefreshingData = false
        spinnerAnimator?.cancel()
        ivBalanceLoader.visibility = View.GONE
        tvBalance.visibility = View.VISIBLE
        rvTransactions.visibility = View.VISIBLE
    }

    private fun filterAndSubmitTransactions() {
        if (allTransactions.isEmpty()) {
            adapter.submitList(emptyList())
            tvStickyDate.visibility = View.GONE
            tvEmptyTransactions.visibility = if (isRefreshingData) View.GONE else View.VISIBLE
            return
        }

        val filtered = if (!isCommissionTabSelected) {
            val opsOnly = allTransactions.filter { tx ->
                tx.operationType == "WITHDRAWAL" ||
                        (tx.operationType == "DEPOSIT" && tx.orderId != null) ||
                        (tx.description?.contains("карт", ignoreCase = true) == true && tx.operationType != "DEPOSIT") ||
                        (tx.description?.contains("безготівк", ignoreCase = true) == true) ||
                        (tx.description?.contains("перерахування", ignoreCase = true) == true)
            }
            if (opsOnly.isNotEmpty()) opsOnly else allTransactions
        } else {
            allTransactions
        }

        adapter.submitList(filtered) {
            if (!isRefreshingData) {
                val topDate = adapter.getDateHeaderForPosition(0)
                if (!topDate.isNullOrBlank()) {
                    tvStickyDate.text = topDate
                    tvStickyDate.visibility = View.VISIBLE
                } else {
                    tvStickyDate.visibility = View.GONE
                }
                tvEmptyTransactions.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun renderBalance(amount: Double, isOperationsTab: Boolean) {
        val primaryColor = if (amount < 0) {
            ContextCompat.getColor(this, R.color.driver_error_red)
        } else {
            ContextCompat.getColor(this, R.color.driver_text_primary)
        }
        val secondaryColor = ContextCompat.getColor(this, R.color.driver_text_secondary)

        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }

        val cents = ((abs(amount) * 100.0) + 0.5).toLong() % 100L
        if (isOperationsTab && abs(amount) < 0.005) {
            tvBalance.text = "0 ₴"
            tvBalance.setTextColor(primaryColor)
            return
        }

        val intPartFormat = DecimalFormat("#,##0", symbols)
        val signPrefix = if (amount < -0.004) "-" else ""
        val intPartStr = signPrefix + intPartFormat.format(abs(amount).toLong())
        val fracStr = String.format(Locale.US, "%02d", cents)
        val currencySuffix = " ₴"

        val fullText = "$intPartStr.$fracStr$currencySuffix"
        val spannable = SpannableString(fullText)

        val dotIndex = intPartStr.length
        val fracStart = dotIndex + 1
        val fracEnd = fracStart + fracStr.length

        spannable.setSpan(
            RelativeSizeSpan(0.66f),
            fracStart,
            fracEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(secondaryColor),
            fracStart,
            fracEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvBalance.setTextColor(primaryColor)
        tvBalance.text = spannable
    }

    private fun showTopUpDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "100"
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 50; params.rightMargin = 50
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.wallet_btn_top_up))
            .setMessage("Введіть суму (мін 1 грн):")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount >= 1.0) initiatePayment(amount)
                else Toast.makeText(this, "Некоректна сума", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun initiatePayment(amount: Double) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@WalletActivity, "Створення платежу...", Toast.LENGTH_SHORT).show()
                val response = ApiClient.getInstance().getApiService(this@WalletActivity).initPayment(InitPaymentRequest(amount))
                if (response.isSuccessful && response.body() != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(response.body()!!.paymentUrl))
                    startActivity(browserIntent)
                } else {
                    Toast.makeText(this@WalletActivity, "Помилка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Завантажує профіль і транзакції ПАРАЛЕЛЬНО (async) лише тоді, коли кеш застарів або примусово оновлено,
     * та зберігає результат у пам'яті на 60 секунд.
     */
    private fun loadData(forceNetwork: Boolean = false, showWhiteLoader: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceNetwork && cachedTransactions != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            commissionBalance = cachedCommissionBalance
            orderEarningsBalance = cachedOrderEarningsBalance
            allTransactions = cachedTransactions!!
            renderBalance(
                if (isCommissionTabSelected) commissionBalance else orderEarningsBalance,
                isOperationsTab = !isCommissionTabSelected
            )
            filterAndSubmitTransactions()
            return
        }

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            if (showWhiteLoader) {
                startWhiteLoaderAnimation()
            }
            try {
                val api = ApiClient.getInstance().getApiService(this@WalletActivity)
                coroutineScope {
                    val profileDeferred = async { api.getDriverProfile() }
                    val txDeferred = async { api.getWalletTransactions() }

                    val profileResp = profileDeferred.await()
                    if (profileResp.isSuccessful && profileResp.body() != null) {
                        val profile = profileResp.body()!!
                        commissionBalance = profile.balance
                        orderEarningsBalance = profile.payoutBalance
                        cachedCommissionBalance = commissionBalance
                        cachedOrderEarningsBalance = orderEarningsBalance

                        if (!profile.fullName.isNullOrBlank()) {
                            com.taxiapp.driver.utils.SessionManager(this@WalletActivity).saveDriverName(profile.fullName)
                        }
                    }

                    val txResp = txDeferred.await()
                    if (txResp.isSuccessful && txResp.body() != null) {
                        allTransactions = txResp.body()!!
                        cachedTransactions = allTransactions
                        lastFetchTimeMs = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (showWhiteLoader) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 420L) {
                        delay(420L - elapsed)
                    }
                    stopWhiteLoaderAnimation()
                }
                renderBalance(
                    if (isCommissionTabSelected) commissionBalance else orderEarningsBalance,
                    isOperationsTab = !isCommissionTabSelected
                )
                filterAndSubmitTransactions()
            }
        }
    }

    override fun onDestroy() {
        spinnerAnimator?.cancel()
        sheetAnimator?.cancel()
        super.onDestroy()
    }

    private class WhiteCircleSpinnerDrawable : Drawable() {
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#232426")
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
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
            val rect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
            canvas.drawArc(rect, rotationDeg, 250f, false, arcPaint)
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

    private class WalletActionIconDrawable(
        private val type: Type,
        private val strokeColor: Int
    ) : Drawable() {

        enum class Type { WALLET_PLUS, WALLET_CARDS, EDIT_PENCIL, WALLET_PAYOUT }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val scale = minOf(w, h) / 24f

            canvas.save()
            canvas.translate(b.left + (w - 24f * scale) / 2f, b.top + (h - 24f * scale) / 2f)
            canvas.scale(scale, scale)

            paint.strokeWidth = 2.0f

            when (type) {
                Type.WALLET_PLUS -> {
                    val bodyPath = Path().apply {
                        moveTo(14.5f, 19.5f)
                        lineTo(5.5f, 19.5f)
                        quadTo(3.5f, 19.5f, 3.5f, 17.5f)
                        lineTo(3.5f, 6.5f)
                        quadTo(3.5f, 4.5f, 5.5f, 4.5f)
                        lineTo(18.5f, 4.5f)
                        quadTo(20.5f, 4.5f, 20.5f, 6.5f)
                        lineTo(20.5f, 13.5f)
                    }
                    canvas.drawPath(bodyPath, paint)

                    val claspRect = RectF(15.5f, 9.5f, 20.5f, 13.5f)
                    canvas.drawRoundRect(claspRect, 1.8f, 1.8f, paint)

                    canvas.drawLine(18.0f, 15.8f, 18.0f, 20.6f, paint)
                    canvas.drawLine(15.6f, 18.2f, 20.4f, 18.2f, paint)
                }

                Type.WALLET_CARDS -> {
                    val rect = RectF(3.5f, 5.0f, 20.5f, 19.0f)
                    canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                    val clasp = RectF(15.2f, 10.0f, 20.5f, 14.0f)
                    canvas.drawRoundRect(clasp, 1.8f, 1.8f, paint)
                }

                Type.EDIT_PENCIL -> {
                    paint.strokeWidth = 2.2f
                    val path = Path().apply {
                        moveTo(14.5f, 5.5f)
                        lineTo(18.5f, 9.5f)
                        lineTo(9.0f, 19.0f)
                        lineTo(4.5f, 19.5f)
                        lineTo(5.0f, 15.0f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }

                Type.WALLET_PAYOUT -> {
                    val trayRect = RectF(3.5f, 9.5f, 20.5f, 19.5f)
                    canvas.drawRoundRect(trayRect, 2.5f, 2.5f, paint)
                    canvas.drawLine(3.5f, 13.5f, 20.5f, 13.5f, paint)

                    val arcRect = RectF(7.5f, 3.8f, 15.5f, 11.5f)
                    canvas.drawArc(arcRect, 190f, 160f, false, paint)
                    canvas.drawLine(13.2f, 5.2f, 15.8f, 6.8f, paint)
                    canvas.drawLine(15.8f, 6.8f, 13.8f, 9.0f, paint)
                }
            }

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}