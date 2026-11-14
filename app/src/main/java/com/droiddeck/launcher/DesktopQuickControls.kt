package com.droiddeck.launcher

import com.droiddeck.launcher.input.SecondScreenMode
import com.droiddeck.launcher.input.TouchRouter
import com.droiddeck.launcher.session.SessionDisplay

/** Compatibility policy for desktop input controls; unrelated to the Chromium sandbox. */
internal object DesktopQuickControls {
    val SCALES = listOf(1f, 1.5f, 2f)

    fun normalizeScale(scale: Float): Float =
        if (!scale.isFinite()) 1f
        else (kotlin.math.round(scale.coerceIn(1f, SessionDisplay.FOLLOW_SCALE_MAX) * 10f) / 10f)

    fun resolutionChoice(scale: Float): String {
        val value = normalizeScale(scale)
        return if (value == 1f) SessionDisplay.FOLLOW_SCREEN else SessionDisplay.followScaled(value)
    }

    fun externalModes(desktopAvailable: Boolean): List<SecondScreenMode> =
        listOf(SecondScreenMode.NONE, SecondScreenMode.KEYBOARD_TRACKPAD) +
            if (desktopAvailable) listOf(SecondScreenMode.DESKTOP) else emptyList()

    fun showKeyboardButton(
        isDesktop: Boolean,
        isPhone: Boolean,
        sessionReady: Boolean,
        overlayOpen: Boolean,
        androidKeyboardVisible: Boolean,
    ): Boolean = isDesktop && isPhone && sessionReady && !overlayOpen && !androidKeyboardVisible

    /** The PC-key row sits on the Android keyboard, so it needs the keyboard's inset to be placed. */
    fun showExtraKeys(isDesktop: Boolean, sessionReady: Boolean, overlayOpen: Boolean, imeInsetsKnown: Boolean): Boolean =
        isDesktop && sessionReady && !overlayOpen && imeInsetsKnown

    fun usesTouchpad(mode: String, steamSession: Boolean, steamDesktopUi: Boolean): Boolean =
        TouchRouter.decide(TouchRouter.Snapshot(false, mode, false, steamSession, steamDesktopUi)) == TouchRouter.Route.TOUCHPAD
}
