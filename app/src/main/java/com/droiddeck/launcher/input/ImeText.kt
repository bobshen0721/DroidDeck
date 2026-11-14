package com.droiddeck.launcher.input

/** How text an Android input method commits reaches the guest. */
object ImeText {
    sealed interface Piece {
        /** Printable ASCII typed as evdev key presses, which every Linux program understands. */
        data class Keys(val text: String) : Piece
        /** Text that has no key on a US layout (CJK, emoji, accents), committed through the desktop's input method. */
        data class Unicode(val text: String) : Piece
        data object Enter : Piece
    }

    fun typable(ch: Char): Boolean = ch == '\t' || ch in ' '..'~'

    /**
     * Splits [text] at line breaks. A line that holds any character without a key is committed whole, so its
     * characters cannot be reordered between the key and input-method channels.
     */
    fun pieces(text: String): List<Piece> {
        val out = ArrayList<Piece>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        lines.forEachIndexed { index, line ->
            if (line.isNotEmpty()) out += if (line.all(::typable)) Piece.Keys(line) else Piece.Unicode(line)
            if (index < lines.lastIndex) out += Piece.Enter
        }
        return out
    }

    /** Splits [text] into pieces of at most [maxChars] UTF-16 units without separating a surrogate pair. */
    fun chunks(text: String, maxChars: Int): List<String> {
        require(maxChars >= 2)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(text.length, start + maxChars)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            out += text.substring(start, end)
            start = end
        }
        return out
    }
}
