package com.droiddeck.launcher.input

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KeyboardHostTest {
    private lateinit var imm: InputMethodManager
    private lateinit var host: KeyboardHost
    private val keys = mutableListOf<String>()
    private val evdev = mutableListOf<String>()
    private val committed = mutableListOf<String>()
    private val preedits = mutableListOf<String>()
    private var sinkAccepts = true

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        host = KeyboardHost(activity) { event ->
            keys += "${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} ${event.keyCode}"
            true
        }
        host.sendEvdev = { code, down -> evdev += "${if (down) "down" else "up"} $code" }
        host.textSink = object : KeyboardHost.TextSink {
            override fun commit(text: String): Boolean {
                if (sinkAccepts) committed += text
                return sinkAccepts
            }
            override fun preedit(text: String, cursor: Int) { preedits += "$text@$cursor" }
        }
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        idle()
    }

    private fun connection(): Pair<InputConnection, EditorInfo> {
        host.show()
        idle()
        val info = EditorInfo()
        return host.onCreateInputConnection(info)!! to info
    }

    @Config(sdk = [33])
    @Test fun editorAllowsChineseKeyboards() {
        val (_, info) = connection()
        assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(0, info.inputType and InputType.TYPE_MASK_VARIATION)
    }

    @Config(sdk = [33])
    @Test fun zhuyinCompositionIsPreviewedThenCommittedAsText() {
        val (ic, _) = connection()
        ic.setComposingText("ㄓ", 1)
        ic.setComposingText("ㄓㄨㄥ", 1)
        assertEquals("ㄓㄨㄥ", host.composing)
        ic.commitText("中", 1)
        assertEquals(listOf("ㄓ@1", "ㄓㄨㄥ@3", "@0"), preedits)
        assertEquals(listOf("中"), committed)
        assertEquals("", host.composing)
        assertTrue(keys.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun backspaceWhileComposingEditsTheCompositionOnly() {
        val (ic, _) = connection()
        ic.setComposingText("ㄓㄨ", 1)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        assertEquals("ㄓ", host.composing)
        assertTrue(keys.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun asciiIsTypedAsKeysAndLineBreaksPressEnter() {
        val (ic, _) = connection()
        ic.commitText("a\n", 1)
        assertEquals(pressed(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER), keys)
        assertTrue(committed.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun mixedLineIsCommittedWholeToKeepItsOrder() {
        val (ic, _) = connection()
        ic.commitText("a中b", 1)
        assertEquals(listOf("a中b"), committed)
        assertTrue(keys.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun undeliveredTextIsReported() {
        val (ic, _) = connection()
        sinkAccepts = false
        val dropped = mutableListOf<String>()
        host.onUndelivered = { dropped += it }
        ic.commitText("中", 1)
        assertEquals(listOf("中"), dropped)
    }

    @Config(sdk = [33])
    @Test fun latchedCtrlWrapsTheNextKeyOnly() {
        val (ic, _) = connection()
        host.latch(29)
        ic.commitText("c", 1)
        ic.commitText("d", 1)
        assertEquals(listOf("down 29", "up 29"), evdev)
        assertEquals(pressed(KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D), keys)
        assertTrue(host.modifiers.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun latchedModifiersWrapAHeldExtraKey() {
        host.latch(56)
        host.pressKey(15, true)
        host.pressKey(15, false)
        assertEquals(listOf("down 56", "down 15", "up 15", "up 56"), evdev)
    }

    @Config(sdk = [33])
    @Test fun hidingReleasesModifiersHeldForAKey() {
        host.show()
        idle()
        host.latch(29)
        host.pressKey(105, true)
        host.hide()
        assertEquals(listOf("down 29", "down 105", "up 29"), evdev)
        assertTrue(host.modifiers.isEmpty())
    }

    @Config(sdk = [33])
    @Test fun deleteSurroundingTextIsCapped() {
        val (ic, _) = connection()
        ic.deleteSurroundingText(500, 0)
        assertEquals(64 * 2, keys.size)
    }

    @Config(sdk = [33])
    @Test fun toggleShowsAKeyboardTheSystemDismissed() {
        host.show()
        idle()
        imm.hideSoftInputFromWindow(host.windowToken, 0)
        idle()
        host.toggle()
        idle()
        assertTrue(shadowOf(imm).isSoftInputVisible)
        assertTrue(host.shown)
    }

    @Config(sdk = [28])
    @Test fun toggleHidesAKeyboardThatIsShowing() {
        host.show()
        idle()
        host.toggle()
        idle()
        assertFalse(shadowOf(imm).isSoftInputVisible)
        assertFalse(host.shown)
    }

    private fun pressed(vararg codes: Int) = codes.flatMap { listOf("down $it", "up $it") }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
