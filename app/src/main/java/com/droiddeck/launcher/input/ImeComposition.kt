package com.droiddeck.launcher.input

/** The Android keyboard's current composition (Zhuyin, Pinyin, a predicted word) for an editor whose text lives in the guest. */
class ImeComposition(private val sink: Sink) {
    interface Sink {
        /** Shows [text] as the composition, replacing the previous one; empty clears it. */
        fun composing(text: String, cursor: Int)

        /** Replaces the composition, if any, with [text]. */
        fun commit(text: String)
    }

    var text = ""
        private set

    fun compose(value: CharSequence?, newCursorPosition: Int) {
        val next = value?.toString().orEmpty()
        if (next == text) return
        text = next
        sink.composing(next, if (newCursorPosition > 0) next.length else 0)
    }

    fun commit(value: CharSequence?) {
        val committed = value?.toString().orEmpty()
        val composed = text.isNotEmpty()
        text = ""
        when {
            committed.isNotEmpty() -> sink.commit(committed)
            composed -> sink.composing("", 0)
        }
    }

    fun finish() {
        if (text.isEmpty()) return
        val committed = text
        text = ""
        sink.commit(committed)
    }

    fun drop() {
        if (text.isEmpty()) return
        text = ""
        sink.composing("", 0)
    }
}
