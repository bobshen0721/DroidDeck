package com.droiddeck.launcher.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.droiddeck.launcher.R

/** PC keys the Android keyboard lacks, shown just above it while a desktop session is typing. */
@SuppressLint("ViewConstructor")
class DesktopExtraKeys(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun key(evdev: Int, down: Boolean)
        fun latch(evdev: Int)
        fun paste()
        fun hideKeyboard()
    }

    data class Key(val label: String, val evdev: Int, val modifier: Boolean = false)

    private val preview = TextView(context)
    private val row = LinearLayout(context)
    private val held = LinkedHashMap<View, Int>()
    private val modifierViews = HashMap<Int, TextView>()
    private var latched: Set<Int> = emptySet()

    init {
        orientation = VERTICAL
        visibility = GONE
        isClickable = true
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        preview.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(235, 32, 33, 36))
            }
            visibility = GONE
            contentDescription = context.getString(R.string.desktop_ime_composing)
        }
        addView(preview, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        })
        row.orientation = HORIZONTAL
        row.setPadding(dp(4), dp(2), dp(4), dp(2))
        for (key in KEYS) row.addView(keyView(key))
        row.addView(action(context.getString(R.string.desktop_keys_paste)) { listener.paste() })
        row.addView(action("⌄") { listener.hideKeyboard() }.apply {
            contentDescription = context.getString(R.string.desktop_keys_hide)
        })
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.argb(235, 24, 25, 28))
            addView(row)
        }
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun base(label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        minWidth = dp(MIN_TARGET_DP)
        minHeight = dp(MIN_TARGET_DP)
        setPadding(dp(10), 0, dp(10), 0)
        background = keyBackground(false)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(MIN_TARGET_DP)).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

    private fun keyBackground(on: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(if (on) Color.rgb(61, 126, 255) else Color.argb(255, 52, 54, 60))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun keyView(key: Key): TextView = base(key.label).apply {
        if (key.modifier) {
            modifierViews[key.evdev] = this
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                listener.latch(key.evdev)
            }
            return@apply
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    view.background = keyBackground(true)
                    held[view] = key.evdev
                    listener.key(key.evdev, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(view)
            }
            true
        }
    }

    private fun action(label: String, onClick: () -> Unit) = base(label).apply {
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }

    private fun release(view: View) {
        val code = held.remove(view) ?: return
        view.background = keyBackground(false)
        listener.key(code, false)
    }

    fun releaseAll() {
        held.keys.toList().forEach(::release)
    }

    fun setLatched(codes: Set<Int>) {
        if (codes == latched) return
        latched = codes
        modifierViews.forEach { (code, view) -> view.background = keyBackground(code in codes) }
    }

    fun setComposing(text: String) {
        preview.text = text
        preview.visibility = if (text.isEmpty()) GONE else VISIBLE
    }

    fun place(imeBottom: Int) {
        val params = layoutParams as FrameLayout.LayoutParams
        if (params.bottomMargin == imeBottom) return
        params.bottomMargin = imeBottom
        layoutParams = params
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
        setPadding(cutout?.safeInsetLeft ?: 0, 0, cutout?.safeInsetRight ?: 0, 0)
        return super.onApplyWindowInsets(insets)
    }

    companion object {
        const val MIN_TARGET_DP = 48
        const val KEY_ESC = 1
        const val KEY_TAB = 15
        const val KEY_LEFTCTRL = 29
        const val KEY_LEFTSHIFT = 42
        const val KEY_LEFTALT = 56
        const val KEY_HOME = 102
        const val KEY_UP = 103
        const val KEY_PAGEUP = 104
        const val KEY_LEFT = 105
        const val KEY_RIGHT = 106
        const val KEY_END = 107
        const val KEY_DOWN = 108
        const val KEY_PAGEDOWN = 109
        const val KEY_DELETE = 111
        const val KEY_LEFTMETA = 125

        val KEYS = listOf(
            Key("Esc", KEY_ESC), Key("Tab", KEY_TAB),
            Key("Ctrl", KEY_LEFTCTRL, modifier = true), Key("Alt", KEY_LEFTALT, modifier = true),
            Key("Shift", KEY_LEFTSHIFT, modifier = true), Key("Super", KEY_LEFTMETA),
            Key("←", KEY_LEFT), Key("↑", KEY_UP), Key("↓", KEY_DOWN), Key("→", KEY_RIGHT),
            Key("Home", KEY_HOME), Key("End", KEY_END), Key("PgUp", KEY_PAGEUP), Key("PgDn", KEY_PAGEDOWN),
            Key("Del", KEY_DELETE),
        )
    }
}
