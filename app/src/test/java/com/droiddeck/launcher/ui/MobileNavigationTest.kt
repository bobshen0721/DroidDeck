package com.droiddeck.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNavigationTest {
    @Test fun prioritizesSteamLibraryDesktopAndSettings() {
        val plan = MobileNavigation.plan(
            listOf("steam", "games", "stores", "desktop", "components", "setup", "updates", "more"),
            listOf("soundtracks"),
            listOf("steam", "games", "soundtracks", "stores", "desktop", "components", "setup", "updates"),
        )
        assertEquals(listOf("steam", "games", "desktop", "setup", "more"), plan.bar)
        assertEquals(listOf("soundtracks", "stores", "components", "updates"), plan.more)
    }

    @Test fun respectsUserOrderingAndShelvedDestinations() {
        val plan = MobileNavigation.plan(
            listOf("setup", "desktop", "games", "steam", "stores"),
            listOf("components"),
            listOf("setup", "desktop", "games", "steam", "stores", "components"),
        )
        assertEquals(listOf("setup", "desktop", "games", "steam", "more"), plan.bar)
        assertEquals(listOf("stores", "components"), plan.more)
    }

    @Test fun hiddenDestinationsDoNotReturnInOverflow() {
        val plan = MobileNavigation.plan(
            listOf("games", "desktop", "more"),
            listOf("setup"),
            listOf("steam", "games", "desktop", "setup", "updates"),
        )
        assertEquals(listOf("games", "desktop", "more"), plan.bar)
        assertEquals(listOf("setup"), plan.more)
    }

    @Test fun sparseNavigationDoesNotInventMore() {
        val plan = MobileNavigation.plan(listOf("steam", "desktop"), emptyList(), listOf("steam", "desktop"))
        assertEquals(listOf("steam", "desktop"), plan.bar)
        assertEquals(emptyList<String>(), plan.more)
    }

    @Test fun runningSessionRemainsReachableThroughMore() {
        val plan = MobileNavigation.plan(listOf("steam", "desktop"), emptyList(), listOf("steam", "desktop"), true)
        assertEquals(listOf("steam", "desktop", "more"), plan.bar)
        assertEquals(emptyList<String>(), plan.more)
    }

    @Test fun duplicateKeysAndExistingMoreAreNotRepeated() {
        val plan = MobileNavigation.plan(
            listOf("steam", "steam", "games", "desktop", "setup", "stores", "more"),
            listOf("stores", "soundtracks"),
            listOf("steam", "games", "desktop", "setup", "stores", "soundtracks"),
        )
        assertEquals(listOf("steam", "games", "desktop", "setup", "more"), plan.bar)
        assertEquals(listOf("stores", "soundtracks"), plan.more)
    }
}
