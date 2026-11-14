package com.droiddeck.launcher.input

import com.droiddeck.launcher.input.DesktopTyping.Channel

/**
 * Decides how text the Android keyboard produces reaches the KDE desktop.
 *
 * A field that took KWin's input method gets everything as text through droiddeck-ime, except
 * Enter and Tab, which stay keys so chat boxes still send and forms still advance. Without such a
 * field, US-keyboard characters are typed as keys and the rest is pasted, since X11 programs have
 * no other way to receive them.
 */
class DesktopTextRouter<K>(
    private val typing: DesktopTyping,
    private val guest: Guest,
    private val keys: Keys<K>,
) : ImeComposition.Sink {
    interface Guest {
        /** A desktop field has the input method and droiddeck-ime is listening. */
        val focused: Boolean
        /** Returns the part of [text] that droiddeck-ime did not take; empty when it took all of it. */
        fun commit(text: String): String
        fun preedit(text: String, cursor: Int): Boolean
    }

    interface Keys<K> {
        fun type(text: String)
        fun send(event: K)
        fun modifiers(codes: Set<Int>, down: Boolean)
        fun share(text: String): Boolean
        fun paste()
        fun preview(text: String)
        fun dropped(text: String)
    }

    var pasteFallback = true
    var oneShot: Set<Int> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            onOneShotChanged?.invoke(value)
        }
    var onOneShotChanged: ((Set<Int>) -> Unit)? = null

    private var guestPreedit = false
    private var heldForKey: Set<Int> = emptySet()

    override fun composing(text: String, cursor: Int) {
        if (guest.focused) {
            keys.preview("")
            typing.post(Channel.IME) {
                if (guest.preedit(text, cursor)) guestPreedit = text.isNotEmpty() else keys.preview(text)
            }
        } else {
            clearGuestPreedit()
            keys.preview(text)
        }
    }

    override fun commit(text: String) {
        keys.preview("")
        if (text.isEmpty()) {
            clearGuestPreedit()
            return
        }
        val mods = oneShot
        if (mods.isNotEmpty() && text.length == 1 && keyable(text)) {
            oneShot = emptySet()
            clearGuestPreedit()
            typing.post(Channel.KEYS) {
                keys.modifiers(mods, true)
                keys.type(text)
                keys.modifiers(mods, false)
            }
            return
        }
        if (!guest.focused) {
            clearGuestPreedit()
            deliver(text)
            return
        }
        for (part in fields(text)) {
            if (part == "\n" || part == "\t") {
                typing.post(Channel.KEYS) { keys.type(part) }
            } else {
                typing.post(Channel.IME) {
                    val rest = guest.commit(part)
                    if (rest.length < part.length) guestPreedit = false
                    if (rest.isNotEmpty()) deliver(rest, front = true)
                }
            }
        }
    }

    fun key(event: K, down: Boolean) {
        if (down) {
            val mods = oneShot
            if (mods.isNotEmpty()) {
                oneShot = emptySet()
                heldForKey = mods
            }
            typing.post(Channel.KEYS) {
                if (mods.isNotEmpty()) keys.modifiers(mods, true)
                keys.send(event)
            }
        } else {
            val mods = heldForKey
            heldForKey = emptySet()
            typing.post(Channel.KEYS) {
                keys.send(event)
                if (mods.isNotEmpty()) keys.modifiers(mods, false)
            }
        }
    }

    fun releaseModifiers() {
        val mods = heldForKey
        heldForKey = emptySet()
        oneShot = emptySet()
        if (mods.isNotEmpty()) keys.modifiers(mods, false)
    }

    private fun clearGuestPreedit() {
        if (!guestPreedit) return
        guestPreedit = false
        typing.post(Channel.IME) { guest.preedit("", 0) }
    }

    /** With [front], the steps go ahead of anything already queued: a failed input-method step is being replaced. */
    private fun deliver(text: String, front: Boolean = false) {
        val queue: (Channel, () -> Unit) -> Unit = if (front) typing::next else typing::post
        for (run in runs(text)) {
            if (keyable(run)) {
                queue(Channel.KEYS) { keys.type(run) }
            } else if (pasteFallback) {
                var shared = false
                queue(Channel.CLIPBOARD) {
                    shared = keys.share(run)
                    if (!shared) keys.dropped(run)
                }
                queue(Channel.KEYS) { if (shared) keys.paste() }
            } else {
                keys.dropped(run)
            }
        }
    }

    companion object {
        fun keyable(c: Char): Boolean = c == '\n' || c == '\t' || c in ' '..'~'

        fun keyable(text: String): Boolean = text.all(::keyable)

        /** Splits [text] into runs that are all keyable or all not. */
        fun runs(text: String): List<String> {
            val out = mutableListOf<String>()
            var start = 0
            for (i in 1..text.length) {
                if (i == text.length || keyable(text[i]) != keyable(text[start])) {
                    if (i > start) out += text.substring(start, i)
                    start = i
                }
            }
            return out
        }

        /** Splits [text] around each newline and tab, keeping them as their own parts. */
        fun fields(text: String): List<String> {
            val out = mutableListOf<String>()
            val run = StringBuilder()
            for (c in text) {
                if (c == '\n' || c == '\t') {
                    if (run.isNotEmpty()) out += run.toString().also { run.clear() }
                    out += c.toString()
                } else {
                    run.append(c)
                }
            }
            if (run.isNotEmpty()) out += run.toString()
            return out
        }
    }
}
