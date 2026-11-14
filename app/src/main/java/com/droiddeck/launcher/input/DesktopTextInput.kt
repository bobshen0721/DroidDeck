package com.droiddeck.launcher.input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.droiddeck.launcher.R
import java.io.File

/** The Android keyboard's typist for a KDE desktop session. */
class DesktopTextInput(
    private val context: Context,
    fifo: () -> File,
    private val fieldFocused: () -> Boolean,
    private val dispatchKey: (KeyEvent) -> Unit,
    private val typeText: (String) -> Unit,
    private val guestKey: (code: Int, down: Boolean) -> Unit,
    private val share: (String) -> Boolean,
    private val onPreview: (String) -> Unit,
) : KeyboardHost.Typist {
    private val handler = Handler(Looper.getMainLooper())
    private val channel = GuestImeChannel(fifo)
    private val typing = DesktopTyping({ SystemClock.uptimeMillis() }) { delay, run -> handler.postDelayed(run, delay) }
    private var pasteNoticeShown = false
    private var dropNoticeAt = 0L

    var settings = DesktopInputPrefs.Settings()
        set(value) {
            field = value
            router.pasteFallback = value.pasteFallback
        }

    private val guest = object : DesktopTextRouter.Guest {
        override val focused: Boolean get() = fieldFocused() && channel.listening
        override fun commit(text: String): String = channel.commit(text)
        override fun preedit(text: String, cursor: Int) = channel.preedit(text, cursor)
    }

    private val keys = object : DesktopTextRouter.Keys<KeyEvent> {
        override fun type(text: String) = typeText(text)
        override fun send(event: KeyEvent) = dispatchKey(event)
        override fun modifiers(codes: Set<Int>, down: Boolean) {
            for (code in if (down) codes.sorted() else codes.sortedDescending()) guestKey(code, down)
        }
        override fun share(text: String): Boolean {
            val shared = this@DesktopTextInput.share(text)
            if (shared && !pasteNoticeShown) {
                pasteNoticeShown = true
                Toast.makeText(context, R.string.desktop_ime_paste_notice, Toast.LENGTH_LONG).show()
            }
            return shared
        }
        override fun paste() {
            guestKey(KEY_LEFTCTRL, true)
            guestKey(KEY_V, true)
            guestKey(KEY_V, false)
            guestKey(KEY_LEFTCTRL, false)
        }
        override fun preview(text: String) = onPreview(text)
        override fun dropped(text: String) {
            val now = SystemClock.uptimeMillis()
            if (now - dropNoticeAt < DROP_NOTICE_MS) return
            dropNoticeAt = now
            Toast.makeText(context, R.string.desktop_ime_dropped, Toast.LENGTH_SHORT).show()
        }
    }

    private val router = DesktopTextRouter(typing, guest, keys)

    var oneShot: Set<Int>
        get() = router.oneShot
        set(value) { router.oneShot = value }

    var onOneShotChanged: ((Set<Int>) -> Unit)?
        get() = router.onOneShotChanged
        set(value) { router.onOneShotChanged = value }

    override val suggestions: Boolean get() = settings.suggestions

    override fun composing(text: String, cursor: Int) = router.composing(text, cursor)

    override fun commit(text: String) = router.commit(text)

    override fun key(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> router.key(event, true)
            KeyEvent.ACTION_UP -> router.key(event, false)
            KeyEvent.ACTION_MULTIPLE -> event.characters?.let(router::commit)
        }
    }

    /** A key from the app's own key row, in order with what the keyboard typed before it. */
    fun press(code: Int, down: Boolean) {
        val mods = if (down) oneShot.also { if (it.isNotEmpty()) oneShot = emptySet() } else emptySet()
        typing.post(DesktopTyping.Channel.KEYS) {
            if (down) {
                keys.modifiers(mods, true)
                guestKey(code, true)
                if (mods.isNotEmpty()) {
                    guestKey(code, false)
                    keys.modifiers(mods, false)
                }
            } else {
                guestKey(code, false)
            }
        }
    }

    fun chord(mods: Set<Int>, code: Int) {
        oneShot = emptySet()
        typing.post(DesktopTyping.Channel.KEYS) {
            keys.modifiers(mods, true)
            guestKey(code, true)
            guestKey(code, false)
            keys.modifiers(mods, false)
        }
    }

    fun release() {
        router.releaseModifiers()
    }

    fun close() {
        release()
        typing.clear()
        channel.close()
        onPreview("")
    }

    companion object {
        const val KEY_ESC = 1
        const val KEY_TAB = 15
        const val KEY_LEFTCTRL = 29
        const val KEY_LEFTSHIFT = 42
        const val KEY_LEFTALT = 56
        const val KEY_LEFTMETA = 125
        const val KEY_HOME = 102
        const val KEY_UP = 103
        const val KEY_PAGEUP = 104
        const val KEY_LEFT = 105
        const val KEY_RIGHT = 106
        const val KEY_END = 107
        const val KEY_DOWN = 108
        const val KEY_PAGEDOWN = 109
        const val KEY_DELETE = 111
        const val KEY_A = 30
        const val KEY_C = 46
        const val KEY_V = 47
        const val KEY_X = 45
        const val KEY_Z = 44
        const val KEY_S = 31
        const val KEY_F = 33
        val MODIFIERS = setOf(KEY_LEFTCTRL, KEY_LEFTSHIFT, KEY_LEFTALT, KEY_LEFTMETA)
        private const val DROP_NOTICE_MS = 4_000L
    }
}
