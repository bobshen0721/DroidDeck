package com.droiddeck.launcher.input

import android.content.Context

/** Trackpad tuning for desktop sessions; game sessions keep their per-layout trackpad settings. */
object DesktopTouchPrefs {
    val POINTER_SPEEDS = listOf(0.6f, 0.8f, 1f, 1.3f, 1.7f, 2.2f)
    val SCROLL_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    const val LONG_PRESS_RIGHT_CLICK = "right_click"
    const val LONG_PRESS_DRAG = "drag"

    private fun prefs(context: Context) = context.getSharedPreferences("desktop_touch", Context.MODE_PRIVATE)

    fun pointerSpeed(context: Context): Float = nearest(prefs(context).getFloat("pointer_speed", 1f), POINTER_SPEEDS)
    fun setPointerSpeed(context: Context, value: Float) = prefs(context).edit().putFloat("pointer_speed", value).apply()

    fun scrollSpeed(context: Context): Float = nearest(prefs(context).getFloat("scroll_speed", 1f), SCROLL_SPEEDS)
    fun setScrollSpeed(context: Context, value: Float) = prefs(context).edit().putFloat("scroll_speed", value).apply()

    fun longPress(context: Context): String =
        prefs(context).getString("long_press", LONG_PRESS_RIGHT_CLICK)?.takeIf { it == LONG_PRESS_DRAG } ?: LONG_PRESS_RIGHT_CLICK
    fun setLongPress(context: Context, value: String) = prefs(context).edit().putString("long_press", value).apply()

    /** The Plasma scale last asked for from the phone; Plasma itself keeps the applied value. */
    fun kdeScale(context: Context): Float = nearest(prefs(context).getFloat("kde_scale", 1f), KDE_SCALES)
    fun setKdeScale(context: Context, value: Float) = prefs(context).edit().putFloat("kde_scale", value).apply()

    val KDE_SCALES = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    fun nextKdeScale(current: Float): Float {
        val index = KDE_SCALES.indexOf(nearest(current, KDE_SCALES))
        return KDE_SCALES[(index + 1) % KDE_SCALES.size]
    }

    fun nearest(value: Float, choices: List<Float>): Float =
        if (!value.isFinite()) choices[choices.size / 2] else choices.minBy { kotlin.math.abs(it - value) }
}
