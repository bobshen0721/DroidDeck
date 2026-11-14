package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestImeChannelTest {
    @Test fun shortTextIsOneChunk() {
        assertEquals(listOf("中文"), GuestImeChannel.chunks("中文"))
        assertEquals(listOf(""), GuestImeChannel.chunks(""))
    }

    @Test fun longTextIsSplitIntoPipeSizedChunks() {
        val text = "字".repeat(GuestImeChannel.CHUNK_CHARS * 2 + 7)
        val chunks = GuestImeChannel.chunks(text)
        assertEquals(listOf(GuestImeChannel.CHUNK_CHARS, GuestImeChannel.CHUNK_CHARS, 7), chunks.map { it.length })
        assertEquals(text, chunks.joinToString(""))
        // Six bytes per UTF-16 unit is JSON's worst case (\uXXXX), plus the request's own keys.
        assertTrue(chunks.all { it.length * 6 + 64 < 4096 })
    }

    @Test fun aSurrogatePairIsNeverSplit() {
        val text = "a".repeat(GuestImeChannel.CHUNK_CHARS - 1) + "😀" + "b"
        val chunks = GuestImeChannel.chunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.none { it.isNotEmpty() && Character.isHighSurrogate(it.last()) })
    }
}
