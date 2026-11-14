package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopTouchPrefsTest {
    @Test fun kdeScaleCyclesThroughTheOfferedSteps() {
        assertEquals(1.25f, DesktopTouchPrefs.nextKdeScale(1f), 0f)
        assertEquals(1.75f, DesktopTouchPrefs.nextKdeScale(1.5f), 0f)
        assertEquals(1f, DesktopTouchPrefs.nextKdeScale(2f), 0f)
        assertEquals(1.5f, DesktopTouchPrefs.nextKdeScale(1.3f), 0f)
    }

    @Test fun storedValuesSnapToTheNearestChoice() {
        assertEquals(1.3f, DesktopTouchPrefs.nearest(1.25f, DesktopTouchPrefs.POINTER_SPEEDS), 0f)
        assertEquals(3f, DesktopTouchPrefs.nearest(9f, DesktopTouchPrefs.SCROLL_SPEEDS), 0f)
        assertEquals(1.5f, DesktopTouchPrefs.nearest(Float.NaN, DesktopTouchPrefs.KDE_SCALES), 0f)
    }

    @Test fun toolbarPlacementSurvivesASizeChange() {
        val fraction = ToolbarPlacement.fraction(10, 410, 210)
        assertEquals(0.5f, fraction, 0f)
        assertEquals(510, ToolbarPlacement.position(10, 1010, fraction))
        assertEquals(10, ToolbarPlacement.position(10, 5, 1f))
        assertEquals(1f, ToolbarPlacement.fraction(0, 100, 400), 0f)
    }
}
