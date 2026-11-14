package com.droiddeck.launcher.input

import com.droiddeck.launcher.input.DesktopTyping.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTextRouterTest {
    private var clock = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val log = mutableListOf<String>()
    private var focused = true
    private var guestAccepts = true
    private var guestTakes = Int.MAX_VALUE
    private var clipboardAccepts = true
    private val typing = DesktopTyping({ clock }, { delay, run -> timers += (clock + delay) to run })

    private val guest = object : DesktopTextRouter.Guest {
        override val focused get() = this@DesktopTextRouterTest.focused
        override fun commit(text: String): String {
            if (!guestAccepts) return text
            val taken = text.take(guestTakes)
            if (taken.isNotEmpty()) log += "ime:$taken"
            return text.drop(taken.length)
        }
        override fun preedit(text: String, cursor: Int) = guestAccepts.also { if (it) log += "preedit:$text@$cursor" }
    }
    private val keys = object : DesktopTextRouter.Keys<String> {
        override fun type(text: String) { log += "keys:" + text.replace("\n", "⏎").replace("\t", "⇥") }
        override fun send(event: String) { log += "key:$event" }
        override fun modifiers(codes: Set<Int>, down: Boolean) { log += (if (down) "mods+" else "mods-") + codes.sorted() }
        override fun share(text: String) = clipboardAccepts.also { if (it) log += "clip:$text" }
        override fun paste() { log += "paste" }
        override fun preview(text: String) { log += "preview:$text" }
        override fun dropped(text: String) { log += "dropped:$text" }
    }
    private val router = DesktopTextRouter(typing, guest, keys)

    private fun settle() {
        repeat(20) {
            clock += 1_000
            timers.toList().also { timers.clear() }.sortedBy { it.first }.forEach { it.second() }
        }
    }

    private fun typed(): List<String> = log.filterNot { it == "preview:" }

    @Test fun aFocusedFieldGetsChineseAndLatinTextThroughTheInputMethod() {
        router.commit("你好 abc")
        settle()
        assertEquals(listOf("ime:你好 abc"), typed())
    }

    @Test fun enterAndTabStayKeysBetweenTextParts() {
        router.commit("第一行\n第二行\t!")
        settle()
        assertEquals(listOf("ime:第一行", "keys:⏎", "ime:第二行", "keys:⇥", "ime:!"), typed())
    }

    @Test fun anImeKeyAfterCommittedTextWaitsForTheText() {
        router.commit("送出")
        router.key("ENTER", true)
        assertEquals(listOf("ime:送出"), typed())
        settle()
        assertEquals(listOf("ime:送出", "key:ENTER"), typed())
    }

    @Test fun theCompositionIsShownInTheFocusedField() {
        router.composing("ㄋㄧ", 2)
        router.commit("你")
        settle()
        assertEquals(listOf("preedit:ㄋㄧ@2", "ime:你"), typed())
    }

    @Test fun withoutAFocusedFieldTheCompositionIsPreviewedOnAndroid() {
        focused = false
        router.composing("ㄋㄧ", 2)
        router.commit("你")
        settle()
        assertEquals(listOf("preview:ㄋㄧ", "clip:你", "paste"), typed())
    }

    @Test fun withoutAFocusedFieldLatinIsTypedAndTheRestIsPasted() {
        focused = false
        router.commit("ls 中文 -l\n")
        settle()
        assertEquals(listOf("keys:ls ", "clip:中文", "paste", "keys: -l⏎"), typed())
    }

    @Test fun aRefusedClipboardDropsTheTextWithoutPasting() {
        focused = false
        clipboardAccepts = false
        router.commit("中")
        settle()
        assertEquals(listOf("dropped:中"), typed())
    }

    @Test fun pasteFallbackCanBeTurnedOff() {
        focused = false
        router.pasteFallback = false
        router.commit("a中")
        settle()
        assertEquals(listOf("keys:a", "dropped:中"), typed())
    }

    @Test fun aGuestThatStoppedListeningFallsBackToKeysAndPaste() {
        guestAccepts = false
        router.commit("ok")
        settle()
        assertEquals(listOf("keys:ok"), typed())
    }

    @Test fun onlyWhatTheGuestDidNotTakeFallsBack() {
        guestTakes = 2
        router.commit("ab中")
        settle()
        assertEquals(listOf("ime:ab", "clip:中", "paste"), typed())
    }

    @Test fun aFailedInputMethodStepFallsBackAheadOfLaterKeys() {
        guestAccepts = false
        router.key("A", true)
        router.commit("中文\n")
        assertEquals(listOf("key:A"), typed())
        settle()
        assertEquals(listOf("key:A", "clip:中文", "paste", "keys:⏎"), typed())
    }

    @Test fun losingTheFieldClearsAPreeditLeftInIt() {
        router.composing("ㄅ", 1)
        settle()
        focused = false
        router.commit("b")
        settle()
        focused = true
        assertEquals(listOf("preedit:ㄅ@1", "preedit:@0", "keys:b"), typed().filterNot { it.startsWith("preview") })
    }

    @Test fun aOneShotModifierAppliesToTheNextCharacterOnly() {
        val changes = mutableListOf<Set<Int>>()
        router.onOneShotChanged = { changes += it }
        router.oneShot = setOf(29)
        router.commit("c")
        router.commit("c")
        settle()
        assertEquals(listOf("mods+[29]", "keys:c", "mods-[29]", "ime:c"), typed())
        assertEquals(listOf(setOf(29), emptySet<Int>()), changes)
    }

    @Test fun aOneShotModifierIsHeldAroundAnImeKey() {
        router.oneShot = setOf(29, 42)
        router.key("DEL", true)
        router.key("DEL", false)
        settle()
        assertEquals(listOf("mods+[29, 42]", "key:DEL", "key:DEL", "mods-[29, 42]"), typed())
        assertTrue(router.oneShot.isEmpty())
    }

    @Test fun releasingModifiersLetsGoOfAHeldOne() {
        router.oneShot = setOf(56)
        router.key("TAB", true)
        settle()
        router.releaseModifiers()
        assertEquals(listOf("mods+[56]", "key:TAB", "mods-[56]"), typed())
    }

    @Test fun runsAndFieldsSplitOnTheRightBoundaries() {
        assertEquals(listOf("ab ", "中文", "!"), DesktopTextRouter.runs("ab 中文!"))
        assertEquals(listOf("😀"), DesktopTextRouter.runs("😀"))
        assertEquals(emptyList<String>(), DesktopTextRouter.runs(""))
        assertEquals(listOf("a", "\n", "\n", "b"), DesktopTextRouter.fields("a\n\nb"))
        assertTrue(DesktopTextRouter.keyable("Hello, world!\n"))
        assertFalse(DesktopTextRouter.keyable("é"))
    }
}
