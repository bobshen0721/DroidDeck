package com.droiddeck.launcher.input

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor

/** Writes to droiddeck-ime's FIFO (agent/ime-input), which turns each JSON line into input-method requests. */
class GuestImeChannel(private val fifo: () -> File) {
    private var fd: FileDescriptor? = null

    val listening: Boolean get() = fd != null || fifo().exists()

    /** Returns the part of [text] that could not be written; earlier chunks have already reached droiddeck-ime. */
    @Synchronized
    fun commit(text: String): String {
        var sent = 0
        for (part in chunks(text)) {
            if (!send(JSONObject().put("op", "commit").put("text", part))) return text.substring(sent)
            sent += part.length
        }
        return ""
    }

    @Synchronized
    fun preedit(text: String, cursor: Int): Boolean {
        val shown = chunks(text).firstOrNull().orEmpty()
        return send(JSONObject().put("op", "preedit").put("text", shown).put("cursor", cursor.coerceIn(0, shown.length)))
    }

    @Synchronized
    fun close() {
        fd?.let { runCatching { Os.close(it) } }
        fd = null
    }

    private fun send(request: JSONObject): Boolean {
        val line = (request.toString() + "\n").toByteArray(Charsets.UTF_8)
        repeat(2) {
            val out = fd ?: open() ?: return false
            try {
                var written = 0
                while (written < line.size) written += Os.write(out, line, written, line.size - written)
                return true
            } catch (e: ErrnoException) {
                Log.w(TAG, "droiddeck-ime did not take text: ${OsConstants.errnoName(e.errno)}")
                close()
            }
        }
        return false
    }

    private fun open(): FileDescriptor? = try {
        Os.open(fifo().path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0).also { fd = it }
    } catch (e: ErrnoException) {
        null
    }

    companion object {
        private const val TAG = "GuestImeChannel"

        // A FIFO write of up to PIPE_BUF (4096) bytes is atomic; a UTF-16 unit costs at most 6 bytes as JSON.
        const val CHUNK_CHARS = 600

        fun chunks(text: String): List<String> {
            if (text.length <= CHUNK_CHARS) return listOf(text)
            val out = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = minOf(text.length, start + CHUNK_CHARS)
                if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
                out += text.substring(start, end)
                start = end
            }
            return out
        }
    }
}
