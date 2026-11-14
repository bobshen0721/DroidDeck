package com.droiddeck.launcher

import com.droiddeck.launcher.input.SecondScreenMode
import com.droiddeck.launcher.session.SessionDisplay
import com.droiddeck.launcher.session.SessionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopQuickControlsTest {
    @Test fun phoneDesktopShowsKeyboardWithoutWaitingForGuestFocusDetection() {
        assertTrue(DesktopQuickControls.showKeyboardButton(true, true, true, false, false))
        assertFalse(DesktopQuickControls.showKeyboardButton(false, true, true, false, false))
        assertFalse(DesktopQuickControls.showKeyboardButton(true, false, true, false, false))
    }

    @Test fun keyboardButtonHidesForOverlaysAndIme() {
        assertFalse(DesktopQuickControls.showKeyboardButton(true, true, false, false, false))
        assertFalse(DesktopQuickControls.showKeyboardButton(true, true, true, true, false))
        assertFalse(DesktopQuickControls.showKeyboardButton(true, true, true, false, true))
    }

    @Test fun zoomStepsUseFollowScreenResolutionWithoutChangingWindowsPackages() {
        assertEquals(SessionDisplay.FOLLOW_SCREEN, DesktopQuickControls.resolutionChoice(1f))
        assertEquals(SessionDisplay.followScaled(1.5f), DesktopQuickControls.resolutionChoice(1.5f))
        assertEquals(SessionDisplay.followScaled(2f), DesktopQuickControls.resolutionChoice(2f))
        assertEquals(1.9f, DesktopQuickControls.normalizeScale(1.9f), 0.0001f)
        assertEquals(2.5f, DesktopQuickControls.normalizeScale(2.5f), 0.0001f)
        assertEquals(8f, DesktopQuickControls.normalizeScale(99f), 0.0001f)
    }

    @Test fun pcKeyRowNeedsTheAndroidKeyboardInset() {
        assertTrue(DesktopQuickControls.showExtraKeys(true, true, false, true))
        assertFalse(DesktopQuickControls.showExtraKeys(true, true, false, false))
        assertFalse(DesktopQuickControls.showExtraKeys(true, true, true, true))
        assertFalse(DesktopQuickControls.showExtraKeys(false, true, false, true))
    }

    @Test fun automaticTouchIsATrackpadOnTheDesktopOnly() {
        assertTrue(DesktopQuickControls.usesTouchpad(SessionPrefs.TOUCH_AUTO, false, false))
        assertFalse(DesktopQuickControls.usesTouchpad(SessionPrefs.TOUCH_AUTO, true, false))
        assertFalse(DesktopQuickControls.usesTouchpad(SessionPrefs.TOUCH_DIRECT, false, false))
        assertTrue(DesktopQuickControls.usesTouchpad(SessionPrefs.TOUCH_PAD, true, false))
    }

    @Test fun externalModesHideUnavailablePanel() {
        assertEquals(listOf(SecondScreenMode.NONE, SecondScreenMode.KEYBOARD_TRACKPAD),
            DesktopQuickControls.externalModes(false))
        assertEquals(listOf(SecondScreenMode.NONE, SecondScreenMode.KEYBOARD_TRACKPAD, SecondScreenMode.DESKTOP),
            DesktopQuickControls.externalModes(true))
    }
}
