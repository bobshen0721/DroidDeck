package com.droiddeck.launcher.input

import android.app.Activity
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class KeyboardHost(
    context: Context,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
) : View(context) {
    constructor(activity: Activity) : this(activity, { activity.dispatchKeyEvent(it) })

    /** Takes the keyboard's text, composition and keys when the guest can receive whole text rather than key presses. */
    interface Typist : ImeComposition.Sink {
        val suggestions: Boolean
        fun key(event: KeyEvent)
    }

    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val map: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    var shown = false
        private set
    var imeShowing = false
        private set
    var imeHeight = 0
        private set
    var onImeChanged: ((Boolean) -> Unit)? = null
    var onImeHeight: ((Int) -> Unit)? = null
    /** Asked on every show(); null keeps the key-press connection. */
    var typistFor: () -> Typist? = { null }
    private var typist: Typist? = null
    private val composition = ImeComposition(object : ImeComposition.Sink {
        override fun composing(text: String, cursor: Int) { typist?.composing(text, cursor) }
        override fun commit(text: String) { typist?.commit(text) ?: type(text) }
    })

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        visibility = VISIBLE
        layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)
    }

    override fun onCheckIsTextEditor(): Boolean = shown

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!shown) return null
        val typing = typist
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        if (typing != null) {
            // A visible-password field locks Gboard and Samsung Keyboard to Latin layouts, so the typist asks for plain text.
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                if (typing.suggestions) 0 else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            return TypingConnection()
        }
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        return KeyConnection()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val visible = insets.isVisible(WindowInsets.Type.ime())
            val height = if (visible) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
            if (height != imeHeight) {
                imeHeight = height
                onImeHeight?.invoke(height)
            }
            if (visible != imeShowing) {
                imeShowing = visible
                onImeChanged?.invoke(visible)
            }
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || keyCode != KeyEvent.KEYCODE_BACK || !shown) {
            return super.onKeyPreIme(keyCode, event)
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            hide()
            onImeChanged?.invoke(false)
        }
        return true
    }

    fun toggle() = if (shown && imeVisible()) hide() else show()

    private fun imeVisible() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
        rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != false

    fun show() {
        val next = typistFor()
        if (next !== typist) {
            composition.drop()
            typist = next
        }
        shown = true
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        imm.restartInput(this)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        composition.finish()
        shown = false
        imm.hideSoftInputFromWindow(windowToken, 0)
        isFocusable = false
        isFocusableInTouchMode = false
        clearFocus()
    }

    fun type(text: CharSequence?) {
        if (text.isNullOrEmpty()) return
        val events = map.getEvents(text.toString().toCharArray())
        if (events != null) {
            for (event in events) sendKeyEvent(event)
        } else {
            for (ch in text) {
                val t = System.currentTimeMillis()
                val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, 0)
                sendKeyEvent(CharKeyEvent(down, ch))
                sendKeyEvent(CharKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP), ch))
            }
        }
    }

    fun tap(keyCode: Int) {
        val t = System.currentTimeMillis()
        sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** Re-reads the typist's options: the keyboard only picks them up when the input restarts. */
    fun restartInput() {
        if (shown) imm.restartInput(this)
    }

    private inner class TypingConnection : BaseInputConnection(this, false) {
        private fun press(keyCode: Int) {
            val t = android.os.SystemClock.uptimeMillis()
            val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            key(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
            key(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        }

        private fun key(event: KeyEvent) {
            typist?.key(event) ?: this@KeyboardHost.sendKeyEvent(event)
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composition.commit(text)
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composition.compose(text, newCursorPosition)
            return true
        }

        override fun finishComposingText(): Boolean {
            composition.finish()
            return true
        }

        // The text lives in the guest, so there is nothing to turn back into a composition.
        override fun setComposingRegion(start: Int, end: Int): Boolean = false

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceIn(0, MAX_DELETE)) { press(KeyEvent.KEYCODE_DEL) }
            repeat(afterLength.coerceIn(0, MAX_DELETE)) { press(KeyEvent.KEYCODE_FORWARD_DEL) }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            key(event)
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            press(KeyEvent.KEYCODE_ENTER)
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = composition.text.takeLast(n.coerceAtLeast(0))
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
    }

    private inner class KeyConnection : BaseInputConnection(this, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            type(text)
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun finishComposingText(): Boolean = true

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { tap(KeyEvent.KEYCODE_DEL) }
            repeat(afterLength) { tap(KeyEvent.KEYCODE_FORWARD_DEL) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean = this@KeyboardHost.sendKeyEvent(event)

        override fun performEditorAction(actionCode: Int): Boolean {
            tap(KeyEvent.KEYCODE_ENTER)
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
    }

    private companion object {
        const val MAX_DELETE = 64
    }

    private class CharKeyEvent(origin: KeyEvent, private val ch: Char) : KeyEvent(origin) {
        override fun getUnicodeChar(): Int = ch.code
        override fun getUnicodeChar(metaState: Int): Int = ch.code
    }
}
