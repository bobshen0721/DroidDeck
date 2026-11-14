package com.droiddeck.launcher.input

import android.content.Context

object DesktopInputPrefs {
    val POINTER_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f)
    val SCROLL_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    val SCALES = listOf(100, 125, 150, 175, 200)

    data class Settings(
        val toolbar: Boolean = true,
        val extraKeys: Boolean = true,
        val suggestions: Boolean = false,
        val pasteFallback: Boolean = true,
        val longPressRightClick: Boolean = true,
        val pointerSpeed: Float = 1f,
        val scrollSpeed: Float = 1f,
    )

    private fun prefs(context: Context) = context.getSharedPreferences("desktop_input", Context.MODE_PRIVATE)

    fun read(context: Context): Settings {
        val p = prefs(context)
        val defaults = Settings()
        return Settings(
            toolbar = p.getBoolean("toolbar", defaults.toolbar),
            extraKeys = p.getBoolean("extraKeys", defaults.extraKeys),
            suggestions = p.getBoolean("suggestions", defaults.suggestions),
            pasteFallback = p.getBoolean("pasteFallback", defaults.pasteFallback),
            longPressRightClick = p.getBoolean("longPressRightClick", defaults.longPressRightClick),
            pointerSpeed = nearest(p.getFloat("pointerSpeed", defaults.pointerSpeed), POINTER_SPEEDS),
            scrollSpeed = nearest(p.getFloat("scrollSpeed", defaults.scrollSpeed), SCROLL_SPEEDS),
        )
    }

    fun write(context: Context, settings: Settings) {
        prefs(context).edit()
            .putBoolean("toolbar", settings.toolbar)
            .putBoolean("extraKeys", settings.extraKeys)
            .putBoolean("suggestions", settings.suggestions)
            .putBoolean("pasteFallback", settings.pasteFallback)
            .putBoolean("longPressRightClick", settings.longPressRightClick)
            .putFloat("pointerSpeed", nearest(settings.pointerSpeed, POINTER_SPEEDS))
            .putFloat("scrollSpeed", nearest(settings.scrollSpeed, SCROLL_SPEEDS))
            .apply()
    }

    /** Where the toolbar sits, as fractions of the screen so it keeps its place across rotation. */
    fun toolbarPosition(context: Context): Pair<Float, Float> {
        val p = prefs(context)
        return p.getFloat("toolbarX", 1f).coerceIn(0f, 1f) to p.getFloat("toolbarY", 0.3f).coerceIn(0f, 1f)
    }

    fun setToolbarPosition(context: Context, x: Float, y: Float) {
        prefs(context).edit().putFloat("toolbarX", x.coerceIn(0f, 1f)).putFloat("toolbarY", y.coerceIn(0f, 1f)).apply()
    }

    fun toolbarExpanded(context: Context): Boolean = prefs(context).getBoolean("toolbarExpanded", false)

    fun setToolbarExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean("toolbarExpanded", expanded).apply()
    }

    fun scale(context: Context): Int? = prefs(context).getInt("scale", 0).takeIf { it in SCALES }

    fun setScale(context: Context, percent: Int) {
        if (percent in SCALES) prefs(context).edit().putInt("scale", percent).apply()
    }

    fun nearest(value: Float, options: List<Float>): Float = options.minBy { kotlin.math.abs(it - value) }
}
