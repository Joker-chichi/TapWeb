package com.tapweb.ui.webview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.tapweb.R

class FloatingCapsuleMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val capsuleView: View
    private val menuHandle: ImageView
    private val expandedPanel: LinearLayout
    private val btnBrowser: ImageView
    private val btnShare: ImageView

    private val handler = Handler(Looper.getMainLooper())

    private var isExpanded = false
    private var isCollapsed = false
    private var isDragging = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTranslationX = 0f
    private var downTranslationY = 0f
    private var hasMoved = false

    private val autoCollapseDelay = 4000L

    var onOpenBrowser: (() -> Unit)? = null
    var onShare: (() -> Unit)? = null

    private val autoCollapseRunnable = Runnable {
        if (!isDragging && !isExpanded) {
            collapse()
        }
    }

    private val autoCollapseExpandedRunnable = Runnable {
        if (!isDragging && isExpanded) {
            collapseExpanded()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_floating_capsule, this, true)
        capsuleView = findViewById(R.id.capsule_container)
        menuHandle = findViewById(R.id.menu_handle)
        expandedPanel = findViewById(R.id.expanded_panel)
        btnBrowser = findViewById(R.id.btn_browser)
        btnShare = findViewById(R.id.btn_share)

        setupTouch()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnBrowser.setOnClickListener {
            collapseExpanded()
            onOpenBrowser?.invoke()
        }
        btnShare.setOnClickListener {
            collapseExpanded()
            onShare?.invoke()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        menuHandle.setOnTouchListener { _, event ->
            handleTouchEvent(event)
            true
        }
    }

    private fun handleTouchEvent(event: MotionEvent) {
        val rawX = event.rawX
        val rawY = event.rawY
        val parent = parent as? View ?: return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = rawX
                downRawY = rawY
                downTranslationX = translationX
                downTranslationY = translationY
                hasMoved = false
                isDragging = false

                cancelAutoTimers()
                if (isCollapsed) {
                    expand()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downRawX
                val dy = rawY - downRawY

                if (!hasMoved && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    hasMoved = true
                    isDragging = true
                    if (isExpanded) {
                        collapseExpanded()
                    }
                }

                if (hasMoved) {
                    val newTx = downTranslationX + dx
                    val maxTx = (parent.width - width) / 2f
                    val minTx = -(parent.width - width) / 2f
                    val newTy = downTranslationY + dy
                    val maxTy = (parent.height - height) / 2f - getStatusBarHeight()
                    val minTy = -(parent.height - height) / 2f + getStatusBarHeight()

                    translationX = newTx.coerceIn(minTx, maxTx)
                    translationY = newTy.coerceIn(minTy, maxTy)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!hasMoved) {
                    toggleExpanded()
                } else {
                    snapToEdge(parent)
                }
                isDragging = false
                // Only schedule collapse timer if menu is NOT expanded
                // (expandMenu handles its own timer for expanded panel)
                if (!isExpanded) {
                    scheduleAutoCollapse()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                snapToEdge(parent)
                scheduleAutoCollapse()
            }
        }
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            collapseExpanded()
        } else {
            expandMenu()
        }
    }

    private fun expandMenu() {
        isExpanded = true
        expandedPanel.animate().cancel()
        expandedPanel.visibility = View.VISIBLE
        expandedPanel.alpha = 0f
        expandedPanel.animate()
            .setListener(null)
            .alpha(1f)
            .setDuration(200)
            .start()

        menuHandle.animate()
            .rotation(90f)
            .setDuration(200)
            .start()

        capsuleView.setBackgroundResource(R.drawable.bg_capsule_expanded)
        cancelAutoTimers()
        handler.postDelayed(autoCollapseExpandedRunnable, autoCollapseDelay)
    }

    private fun collapseExpanded() {
        isExpanded = false
        expandedPanel.animate()
            .alpha(0f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    expandedPanel.visibility = View.GONE
                }
            })
            .start()

        menuHandle.animate()
            .rotation(0f)
            .setDuration(150)
            .start()

        capsuleView.setBackgroundResource(R.drawable.bg_floating_capsule)
        cancelAutoTimers()
        scheduleAutoCollapse()
    }

    private fun snapToEdge(parent: View) {
        val screenWidth = parent.width
        val centerX = translationX + width / 2f
        val targetX = if (centerX < screenWidth / 2f) {
            -(screenWidth - width) / 2f
        } else {
            (screenWidth - width) / 2f
        }

        animate()
            .translationX(targetX)
            .setDuration(250)
            .start()
    }

    fun collapse() {
        isCollapsed = true
        isExpanded = false
        expandedPanel.visibility = View.GONE

        val parent = parent as? View ?: return
        val screenWidth = parent.width
        val centerX = translationX + width / 2f
        val isLeftSide = centerX < screenWidth / 2f

        capsuleView.setBackgroundResource(R.drawable.bg_capsule_collapsed)

        val edgeX = if (isLeftSide) {
            -(screenWidth - width) / 2f
        } else {
            (screenWidth - width) / 2f
        }

        animate()
            .translationX(edgeX)
            .setDuration(300)
            .start()
    }

    private fun expand() {
        isCollapsed = false
        val parent = parent as? View ?: return

        val screenWidth = parent.width
        val currentCenterX = translationX + width / 2f
        val isLeftSide = currentCenterX < screenWidth / 2f
        val targetX = if (isLeftSide) {
            -(screenWidth - width) / 2f
        } else {
            (screenWidth - width) / 2f
        }

        capsuleView.setBackgroundResource(R.drawable.bg_floating_capsule)
        animate()
            .translationX(targetX)
            .setDuration(250)
            .start()
    }

    fun resetVisibility() {
        cancelAutoTimers()
        if (isCollapsed) {
            expand()
        }
        if (isExpanded) {
            collapseExpanded()
        }
        scheduleAutoCollapse()
    }

    private fun scheduleAutoCollapse() {
        cancelAutoTimers()
        handler.postDelayed(autoCollapseRunnable, autoCollapseDelay)
    }

    private fun cancelAutoTimers() {
        handler.removeCallbacks(autoCollapseRunnable)
        handler.removeCallbacks(autoCollapseExpandedRunnable)
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    fun setPositionDefault() {
        post {
            val parent = parent as? View ?: return@post
            translationX = (parent.width - width) / 2f
            translationY = 0f
            scheduleAutoCollapse()
        }
    }

    override fun onDetachedFromWindow() {
        cancelAutoTimers()
        super.onDetachedFromWindow()
    }
}
