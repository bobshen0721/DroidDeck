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

    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val map: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    var shown = false
        private set
    var imeShowing = false
        private set
    var onImeChanged: ((Boolean) -> Unit)? = null

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        visibility = VISIBLE
        layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)
    }

    /** Delivers committed text that has no key on the guest's layout. */
    interface TextSink {
        fun commit(text: String): Boolean
        fun preedit(text: String, cursor: Int) {}
    }

    var textSink: TextSink? = null
    /** Presses an evdev key in the guest, for modifiers latched from the extra-keys row. */
    var sendEvdev: ((Int, Boolean) -> Unit)? = null
    var onComposing: ((String) -> Unit)? = null
    var onModifiers: ((Set<Int>) -> Unit)? = null
    /** Text no channel accepted, after the legacy key fallback. */
    var onUndelivered: ((String) -> Unit)? = null
    var imeBottom = 0
        private set
    var onImeInsets: ((Int) -> Unit)? = null
    var composing = ""
        private set
    private val latched = linkedSetOf<Int>()
    private val heldForKey = ArrayList<Int>()
    val modifiers: Set<Int> get() = latched

    override fun onCheckIsTextEditor(): Boolean = shown

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!shown) return null
        // VISIBLE_PASSWORD would lock Gboard and Samsung Keyboard to Latin layouts, hiding Zhuyin, Pinyin and Cangjie.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        if (composing.isNotEmpty()) {
            setComposing("")
            textSink?.preedit("", 0)
        }
        return TextConnection()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val visible = insets.isVisible(WindowInsets.Type.ime())
            val bottom = if (visible) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
            if (bottom != imeBottom) {
                imeBottom = bottom
                onImeInsets?.invoke(bottom)
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
        shown = true
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        imm.restartInput(this)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        shown = false
        if (composing.isNotEmpty()) commitComposing()
        clearModifiers()
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
        pressModifiers()
        sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        releaseModifiers()
    }

    /** Presses an evdev key with the latched modifiers around it; they are released with the key. */
    fun pressKey(evdev: Int, down: Boolean) {
        val send = sendEvdev ?: return
        if (down) {
            pressModifiers()
            send(evdev, true)
        } else {
            send(evdev, false)
            releaseModifiers()
        }
    }

    /** Toggles a one-shot modifier (evdev code) applied to the next key or typed text. */
    fun latch(evdev: Int) {
        if (!latched.remove(evdev)) latched.add(evdev)
        onModifiers?.invoke(latched.toSet())
    }

    fun clearModifiers() {
        releaseHeldModifiers()
        if (latched.isEmpty()) return
        latched.clear()
        onModifiers?.invoke(emptySet())
    }

    /** Commits [text] as if the input method had, e.g. for a paste from the Android clipboard. */
    fun deliver(text: String) {
        for (piece in ImeText.pieces(text)) when (piece) {
            ImeText.Piece.Enter -> tap(KeyEvent.KEYCODE_ENTER)
            is ImeText.Piece.Keys -> {
                pressModifiers()
                type(piece.text)
                releaseModifiers()
            }
            is ImeText.Piece.Unicode -> if (textSink?.commit(piece.text) != true) {
                type(piece.text)
                onUndelivered?.invoke(piece.text)
            }
        }
    }

    private fun pressModifiers() {
        val send = sendEvdev ?: return
        if (heldForKey.isNotEmpty() || latched.isEmpty()) return
        heldForKey.addAll(latched)
        heldForKey.forEach { send(it, true) }
    }

    private fun releaseModifiers() {
        if (heldForKey.isEmpty() && latched.isEmpty()) return
        releaseHeldModifiers()
        if (latched.isNotEmpty()) {
            latched.clear()
            onModifiers?.invoke(emptySet())
        }
    }

    private fun releaseHeldModifiers() {
        val send = sendEvdev
        heldForKey.asReversed().forEach { send?.invoke(it, false) }
        heldForKey.clear()
    }

    private fun setComposing(text: String) {
        if (text == composing) return
        composing = text
        onComposing?.invoke(text)
    }

    private fun commitComposing() {
        val text = composing
        commit(text)
    }

    private fun commit(text: String) {
        if (composing.isNotEmpty()) {
            setComposing("")
            textSink?.preedit("", 0)
        }
        deliver(text)
    }

    private inner class TextConnection : BaseInputConnection(this, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            commit(text?.toString().orEmpty())
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val value = text?.toString().orEmpty()
            setComposing(value)
            textSink?.preedit(value, value.length)
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean = true

        override fun finishComposingText(): Boolean {
            if (composing.isNotEmpty()) commitComposing()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceIn(0, MAX_DELETE)) { tap(KeyEvent.KEYCODE_DEL) }
            repeat(afterLength.coerceIn(0, MAX_DELETE)) { tap(KeyEvent.KEYCODE_FORWARD_DEL) }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            @Suppress("DEPRECATION")
            if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                commit(event.characters.orEmpty())
                return true
            }
            if (composing.isNotEmpty() && event.keyCode == KeyEvent.KEYCODE_DEL) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val cut = if (composing.length >= 2 && Character.isLowSurrogate(composing.last())) 2 else 1
                    val next = composing.dropLast(cut)
                    setComposing(next)
                    textSink?.preedit(next, next.length)
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (composing.isNotEmpty()) commitComposing()
                pressModifiers()
            }
            val sent = this@KeyboardHost.sendKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP && !KeyEvent.isModifierKey(event.keyCode)) releaseModifiers()
            return sent
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            if (composing.isNotEmpty()) commitComposing()
            tap(KeyEvent.KEYCODE_ENTER)
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = composing.takeLast(maxOf(0, n))
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getCursorCapsMode(reqModes: Int): Int = 0
    }

    private companion object {
        const val MAX_DELETE = 64
    }

    private class CharKeyEvent(origin: KeyEvent, private val ch: Char) : KeyEvent(origin) {
        override fun getUnicodeChar(): Int = ch.code
        override fun getUnicodeChar(metaState: Int): Int = ch.code
    }
}
