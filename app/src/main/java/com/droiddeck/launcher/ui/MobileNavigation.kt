package com.droiddeck.launcher.ui

internal data class MobileNavPlan(val bar: List<String>, val more: List<String>)

/** Keep the core phone destinations visible and put the rest behind one touch target. */
internal object MobileNavigation {
    private val primaryKeys = setOf("steam", "games", "desktop", "setup")
    private const val maxPrimary = 4
    private const val moreKey = "more"

    fun plan(bar: List<String>, shelved: List<String>, order: List<String>, activeSession: Boolean = false): MobileNavPlan {
        val visible = bar.filterNot { it == moreKey }.distinct()
        val primary = (visible.filter { it in primaryKeys } + visible.filterNot { it in primaryKeys }).take(maxPrimary)
        val all = (visible + shelved).toSet()
        val overflow = (order + visible + shelved).distinct().filter { it in all && it !in primary && it != moreKey }
        val tabs = if (overflow.isNotEmpty() || activeSession) primary + moreKey else primary
        return MobileNavPlan(tabs, overflow)
    }
}
