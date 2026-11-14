package com.droiddeck.launcher.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.droiddeck.launcher.R
import kotlin.math.abs

/** Collapsible shortcuts for driving the Linux desktop by touch; draggable, and kept clear of cutouts. */
@SuppressLint("ViewConstructor")
class DesktopToolbar(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun keyboard()
        fun pcKeys()
        fun touchMode()
        fun rightClick()
        fun scale()
        fun external()
        fun menu()
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handle = button("⋮") {}
    private val keyboard = button("⌨") { listener.keyboard() }
    private val extras = ArrayList<View>()
    private val touchButton: TextView
    private val rightButton: TextView
    private val scaleButton: TextView
    private val safe = Rect()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var expanded = prefs.getBoolean(KEY_EXPANDED, false)
        private set

    init {
        orientation = HORIZONTAL
        visibility = GONE
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(Color.argb(215, 24, 25, 28))
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
        handle.contentDescription = context.getString(R.string.desktop_toolbar_handle)
        keyboard.contentDescription = context.getString(R.string.second_show_keyboard)
        addView(handle)
        addView(keyboard)
        extras += button(context.getString(R.string.desktop_toolbar_pc_keys)) { listener.pcKeys() }
        touchButton = button(context.getString(R.string.desktop_toolbar_touchpad)) { listener.touchMode() }
        rightButton = button(context.getString(R.string.desktop_toolbar_right_click)) { listener.rightClick() }
        scaleButton = button("100%") { listener.scale() }
        extras += touchButton
        extras += rightButton
        extras += scaleButton
        extras += button(context.getString(R.string.desktop_toolbar_external)) { listener.external() }
        extras += button(context.getString(R.string.desktop_toolbar_menu)) { listener.menu() }
        extras.forEach(::addView)
        scaleButton.contentDescription = context.getString(R.string.desktop_toolbar_scale)
        applyExpanded()
        handle.setOnTouchListener(DragToggle())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        minWidth = dp(DesktopExtraKeys.MIN_TARGET_DP)
        minHeight = dp(DesktopExtraKeys.MIN_TARGET_DP)
        setPadding(dp(8), 0, dp(8), 0)
        background = chip(false)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(DesktopExtraKeys.MIN_TARGET_DP)).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }

    private fun chip(on: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(if (on) Color.rgb(61, 126, 255) else Color.argb(255, 52, 54, 60))
    }

    fun setTouchpad(touchpad: Boolean) {
        touchButton.setText(if (touchpad) R.string.desktop_toolbar_touchpad else R.string.desktop_toolbar_direct)
    }

    fun setRightClickArmed(armed: Boolean) {
        rightButton.background = chip(armed)
    }

    fun setScalePercent(percent: Int) {
        scaleButton.text = context.getString(R.string.mobile_desktop_scale_factor, percent)
    }

    private fun applyExpanded() {
        extras.forEach { it.visibility = if (expanded) VISIBLE else GONE }
        handle.text = if (expanded) "‹" else "⋮"
        requestLayout()
        post { place() }
    }

    private fun toggle() {
        expanded = !expanded
        prefs.edit().putBoolean(KEY_EXPANDED, expanded).apply()
        applyExpanded()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
        safe.set(cutout?.safeInsetLeft ?: 0, cutout?.safeInsetTop ?: 0,
            cutout?.safeInsetRight ?: 0, cutout?.safeInsetBottom ?: 0)
        post { place() }
        return super.onApplyWindowInsets(insets)
    }

    private val parentLayout = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
        if (r - l != or_ - ol || b - t != ob - ot) post { place() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? View)?.addOnLayoutChangeListener(parentLayout)
        post { place() }
    }

    override fun onDetachedFromWindow() {
        (parent as? View)?.removeOnLayoutChangeListener(parentLayout)
        super.onDetachedFromWindow()
    }

    private fun bounds(): Rect? {
        val host = parent as? ViewGroup ?: return null
        if (host.width <= 0 || host.height <= 0) return null
        val margin = dp(EDGE_DP)
        val w = if (width > 0) width else measuredWidth
        val h = if (height > 0) height else measuredHeight
        return Rect(safe.left + margin, safe.top + margin,
            maxOf(safe.left + margin, host.width - safe.right - margin - w),
            maxOf(safe.top + margin, host.height - safe.bottom - margin - h))
    }

    /** Places the bar from the stored fractions of the free area, so it survives rotation. */
    fun place() {
        val host = parent as? ViewGroup ?: return
        val wanted = if (host.height > host.width) VERTICAL else HORIZONTAL
        if (host.width > 0 && orientation != wanted) {
            orientation = wanted
            requestLayout()
            post { place() }
            return
        }
        val area = bounds() ?: return
        val fx = prefs.getFloat(KEY_X, DEFAULT_X)
        val fy = prefs.getFloat(KEY_Y, DEFAULT_Y)
        moveTo(ToolbarPlacement.position(area.left, area.right, fx), ToolbarPlacement.position(area.top, area.bottom, fy))
    }

    private fun moveTo(x: Int, y: Int) {
        val params = layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.leftMargin == x && params.topMargin == y) return
        params.leftMargin = x
        params.topMargin = y
        layoutParams = params
    }

    private fun remember() {
        val area = bounds() ?: return
        val params = layoutParams as FrameLayout.LayoutParams
        prefs.edit()
            .putFloat(KEY_X, ToolbarPlacement.fraction(area.left, area.right, params.leftMargin))
            .putFloat(KEY_Y, ToolbarPlacement.fraction(area.top, area.bottom, params.topMargin))
            .apply()
    }

    private inner class DragToggle : OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams as? FrameLayout.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.leftMargin; startY = params.topMargin
                    dragging = false
                    view.background = chip(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && abs(dx) + abs(dy) > touchSlop) dragging = true
                    if (dragging) {
                        val area = bounds() ?: return true
                        moveTo((startX + dx).toInt().coerceIn(area.left, area.right),
                            (startY + dy).toInt().coerceIn(area.top, area.bottom))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.background = chip(false)
                    if (dragging) remember() else {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        toggle()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.background = chip(false)
                    if (dragging) remember()
                }
            }
            return true
        }
    }

    companion object {
        private const val PREFS = "desktop_toolbar"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_EXPANDED = "expanded"
        private const val EDGE_DP = 8
        // Middle of the right edge: clear of Plasma's bottom panel and of maximized windows' title buttons.
        private const val DEFAULT_X = 1f
        private const val DEFAULT_Y = 0.45f
    }
}

object ToolbarPlacement {
    fun fraction(min: Int, max: Int, value: Int): Float =
        if (max <= min) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    fun position(min: Int, max: Int, fraction: Float): Int =
        if (max <= min) min else (min + (max - min) * fraction.coerceIn(0f, 1f)).toInt()
}
