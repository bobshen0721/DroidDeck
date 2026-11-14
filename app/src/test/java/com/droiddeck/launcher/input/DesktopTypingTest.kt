package com.droiddeck.launcher.input

import com.droiddeck.launcher.input.DesktopTyping.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTypingTest {
    private var clock = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val log = mutableListOf<String>()
    private val typing = DesktopTyping({ clock }, { delay, run -> timers += (clock + delay) to run })

    private fun advance(ms: Long) {
        clock += ms
        while (true) {
            val due = timers.filter { it.first <= clock }.minByOrNull { it.first } ?: return
            timers.remove(due)
            due.second()
        }
    }

    @Test fun stepsOnOneChannelRunAtOnce() {
        typing.post(Channel.KEYS) { log += "a" }
        typing.post(Channel.KEYS) { log += "b" }
        assertEquals(listOf("a", "b"), log)
        assertTrue(typing.idle)
    }

    @Test fun aChannelSwitchWaitsForThePreviousRouteToSettle() {
        typing.post(Channel.IME) { log += "text" }
        typing.post(Channel.KEYS) { log += "enter" }
        assertEquals(listOf("text"), log)
        assertFalse(typing.idle)
        advance(Channel.IME.settleMs - 1)
        assertEquals(listOf("text"), log)
        advance(1)
        assertEquals(listOf("text", "enter"), log)
        assertTrue(typing.idle)
    }

    @Test fun laterStepsQueueBehindAWaitingSwitch() {
        typing.post(Channel.KEYS) { log += "a" }
        typing.post(Channel.IME) { log += "中" }
        typing.post(Channel.IME) { log += "文" }
        typing.post(Channel.KEYS) { log += "b" }
        advance(Channel.KEYS.settleMs)
        assertEquals(listOf("a", "中", "文"), log)
        advance(Channel.IME.settleMs)
        assertEquals(listOf("a", "中", "文", "b"), log)
    }

    @Test fun aSwitchAfterTheRouteSettledRunsAtOnce() {
        typing.post(Channel.IME) { log += "text" }
        advance(500)
        typing.post(Channel.KEYS) { log += "enter" }
        assertEquals(listOf("text", "enter"), log)
    }

    @Test fun stepsPostedWhileRunningKeepTheirPlace() {
        typing.post(Channel.KEYS) {
            log += "outer"
            typing.post(Channel.KEYS) { log += "inner" }
        }
        typing.post(Channel.KEYS) { log += "next" }
        assertEquals(listOf("outer", "inner", "next"), log)
    }

    @Test fun aStepCanQueueItsReplacementAheadOfLaterSteps() {
        typing.post(Channel.KEYS) { log += "a" }
        typing.post(Channel.IME) {
            log += "failed"
            typing.next(Channel.KEYS) { log += "fallback 1" }
            typing.next(Channel.KEYS) { log += "fallback 2" }
        }
        typing.post(Channel.KEYS) { log += "later" }
        advance(1_000)
        advance(1_000)
        assertEquals(listOf("a", "failed", "fallback 1", "fallback 2", "later"), log)
    }

    @Test fun nextOutsideAStepIsAPost() {
        typing.next(Channel.KEYS) { log += "a" }
        assertEquals(listOf("a"), log)
    }

    @Test fun clearDropsQueuedSteps() {
        typing.post(Channel.IME) { log += "text" }
        typing.post(Channel.KEYS) { log += "enter" }
        typing.clear()
        advance(1_000)
        assertEquals(listOf("text"), log)
        assertTrue(typing.idle)
    }
}
