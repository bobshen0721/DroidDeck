package com.droiddeck.launcher

import com.droiddeck.launcher.input.SecondScreenMode
import com.droiddeck.launcher.session.SessionDisplay
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

    @Test fun externalModesHideUnavailablePanel() {
        assertEquals(listOf(SecondScreenMode.NONE, SecondScreenMode.KEYBOARD_TRACKPAD),
            DesktopQuickControls.externalModes(false))
        assertEquals(listOf(SecondScreenMode.NONE, SecondScreenMode.KEYBOARD_TRACKPAD, SecondScreenMode.DESKTOP),
            DesktopQuickControls.externalModes(true))
    }
}
