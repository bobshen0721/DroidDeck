package com.droiddeck.launcher.input

import com.droiddeck.launcher.input.ImeText.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeTextTest {
    @Test fun asciiLinesBecomeKeysAndBreaksBecomeEnter() {
        assertEquals(listOf(Piece.Keys("ls -la"), Piece.Enter, Piece.Keys("cd ~")), ImeText.pieces("ls -la\ncd ~"))
        assertEquals(listOf(Piece.Enter), ImeText.pieces("\r\n"))
        assertEquals(emptyList<Piece>(), ImeText.pieces(""))
    }

    @Test fun anyCharacterWithoutAKeyCommitsTheWholeLine() {
        assertEquals(listOf(Piece.Unicode("你好 world"), Piece.Enter), ImeText.pieces("你好 world\n"))
        assertEquals(listOf(Piece.Unicode("café")), ImeText.pieces("café"))
        assertEquals(listOf(Piece.Unicode("，")), ImeText.pieces("，"))
    }

    @Test fun chunksNeverSplitASurrogatePair() {
        val text = "a😀".repeat(5)
        val chunks = ImeText.chunks(text, 4)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.isNotEmpty() && !Character.isHighSurrogate(it.last()) })
    }
}
