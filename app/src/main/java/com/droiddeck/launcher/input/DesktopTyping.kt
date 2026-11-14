package com.droiddeck.launcher.input

/**
 * Runs typing steps in the order they were typed although they reach the guest by different routes:
 * key presses through the compositor, text through droiddeck-ime, paste text through the clipboard.
 * The routes are not ordered against each other, so a step on another route waits until the previous
 * route has had time to deliver.
 */
class DesktopTyping(
    private val now: () -> Long,
    private val later: (delayMs: Long, run: () -> Unit) -> Unit,
) {
    enum class Channel(val settleMs: Long) { KEYS(40L), IME(80L), CLIPBOARD(300L) }

    private class Step(val channel: Channel, val run: () -> Unit)

    private val queue = ArrayDeque<Step>()
    private var last: Channel? = null
    private var lastAt = 0L
    private var waiting = false
    private var running = false
    private var insertAt = -1

    val idle: Boolean get() = queue.isEmpty() && !waiting

    fun post(channel: Channel, run: () -> Unit) {
        queue.addLast(Step(channel, run))
        pump()
    }

    /** Queues a step right after the one now running, ahead of steps queued before; outside a step it is [post]. */
    fun next(channel: Channel, run: () -> Unit) {
        if (insertAt < 0) post(channel, run) else queue.add(insertAt++, Step(channel, run))
    }

    fun clear() {
        queue.clear()
    }

    private fun pump() {
        if (waiting || running) return
        running = true
        try {
            while (queue.isNotEmpty()) {
                val step = queue.first()
                val previous = last
                if (previous != null && previous != step.channel) {
                    val wait = lastAt + previous.settleMs - now()
                    if (wait > 0) {
                        waiting = true
                        later(wait) {
                            waiting = false
                            pump()
                        }
                        return
                    }
                }
                queue.removeFirst()
                last = step.channel
                insertAt = 0
                try {
                    step.run()
                } finally {
                    insertAt = -1
                }
                lastAt = now()
            }
        } finally {
            running = false
        }
    }
}
