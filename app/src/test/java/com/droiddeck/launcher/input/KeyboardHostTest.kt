package com.droiddeck.launcher.input

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
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

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        host = KeyboardHost(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        idle()
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

    private class RecordingTypist(override var suggestions: Boolean = false) : KeyboardHost.Typist {
        val events = mutableListOf<String>()
        override fun composing(text: String, cursor: Int) { events += "compose:$text@$cursor" }
        override fun commit(text: String) { events += "commit:$text" }
        override fun key(event: KeyEvent) {
            events += (if (event.action == KeyEvent.ACTION_DOWN) "down:" else "up:") + event.keyCode
        }
    }

    private fun typingHost(typist: RecordingTypist): Pair<KeyboardHost, EditorInfo> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val typing = KeyboardHost(activity).apply { typistFor = { typist } }
        activity.setContentView(FrameLayout(activity).apply { addView(typing) })
        idle()
        typing.show()
        idle()
        return typing to EditorInfo()
    }

    @Config(sdk = [33])
    @Test fun aTypistGetsAKeyboardThatCanWriteChinese() {
        val typist = RecordingTypist()
        val (typing, info) = typingHost(typist)
        assertTrue(typing.onCreateInputConnection(info) != null)
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, info.inputType)
        typist.suggestions = true
        val again = EditorInfo()
        typing.onCreateInputConnection(again)
        assertEquals(InputType.TYPE_CLASS_TEXT, again.inputType)
    }

    @Config(sdk = [33])
    @Test fun aTypistReceivesTheCompositionCommitsAndKeys() {
        val typist = RecordingTypist()
        val (typing, info) = typingHost(typist)
        val connection = typing.onCreateInputConnection(info)!!
        connection.setComposingText("ㄓㄨ", 1)
        assertEquals("ㄓㄨ", connection.getTextBeforeCursor(10, 0).toString())
        connection.commitText("注", 1)
        connection.setComposingText("ab", 1)
        connection.finishComposingText()
        connection.deleteSurroundingText(1, 0)
        connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
        assertEquals(listOf(
            "compose:ㄓㄨ@2", "commit:注", "compose:ab@2", "commit:ab",
            "down:${KeyEvent.KEYCODE_DEL}", "up:${KeyEvent.KEYCODE_DEL}",
            "down:${KeyEvent.KEYCODE_ENTER}", "up:${KeyEvent.KEYCODE_ENTER}",
        ), typist.events)
    }

    @Config(sdk = [33])
    @Test fun hidingCommitsAnUnfinishedComposition() {
        val typist = RecordingTypist()
        val (typing, info) = typingHost(typist)
        typing.onCreateInputConnection(info)!!.setComposingText("ni", 1)
        typing.hide()
        assertEquals(listOf("compose:ni@2", "commit:ni"), typist.events)
    }

    @Config(sdk = [33])
    @Test fun withoutATypistTheKeyboardStaysLatin() {
        host.show()
        idle()
        val info = EditorInfo()
        host.onCreateInputConnection(info)
        assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, info.inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
