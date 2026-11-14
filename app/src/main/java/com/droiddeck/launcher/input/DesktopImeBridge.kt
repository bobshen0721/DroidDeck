package com.droiddeck.launcher.input

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Writes Android keyboard text to droiddeck-ime, KWin's input method in the guest, which commits it to the
 * focused text field through zwp_input_method_context_v1. Key presses cannot carry Chinese or other text
 * that has no key on the guest's layout.
 */
class DesktopImeBridge(private val directory: () -> File?) {

    /** True when the text reached a running droiddeck-ime; false when no reader holds the FIFO. */
    fun commit(text: String): Boolean {
        if (text.isEmpty()) return true
        for (chunk in ImeText.chunks(text, MAX_CHARS)) {
            if (!write(JSONObject().put("op", "commit").put("text", chunk))) return false
        }
        return true
    }

    fun preedit(text: String, cursor: Int): Boolean {
        if (text.length > MAX_CHARS) return false
        return write(JSONObject().put("op", "preedit").put("text", text).put("cursor", cursor.coerceIn(0, text.length)))
    }

    /** Asks Plasma to scale its interface; the output keeps its pixel size. */
    fun scale(value: Float): Boolean = write(JSONObject().put("op", "scale").put("value", value.toDouble()))

    fun available(): Boolean = fifo()?.let { file ->
        try {
            val fd = Os.open(file.path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
            Os.close(fd)
            true
        } catch (_: ErrnoException) {
            false
        }
    } ?: false

    private fun fifo(): File? {
        val file = File(directory() ?: return null, FIFO)
        return try {
            if (OsConstants.S_ISFIFO(Os.stat(file.path).st_mode)) file else null
        } catch (_: ErrnoException) {
            null
        }
    }

    private fun write(command: JSONObject): Boolean {
        val file = fifo() ?: return false
        val bytes = (command.toString() + "\n").toByteArray(Charsets.UTF_8)
        // A write of up to PIPE_BUF bytes is atomic, so concurrent lines never interleave.
        if (bytes.size > PIPE_BUF) return false
        val fd = try {
            Os.open(file.path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.ENXIO) Log.w(TAG, "cannot open the desktop text channel", e)
            return false
        }
        return try {
            Os.write(fd, bytes, 0, bytes.size) == bytes.size
        } catch (e: ErrnoException) {
            Log.w(TAG, "desktop text channel write failed", e)
            false
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    companion object {
        private const val TAG = "DesktopImeBridge"
        const val FIFO = "ime-input"
        private const val PIPE_BUF = 4096
        /** Four UTF-8 bytes per character at most, leaving room for the JSON around it. */
        internal const val MAX_CHARS = 900
    }
}
