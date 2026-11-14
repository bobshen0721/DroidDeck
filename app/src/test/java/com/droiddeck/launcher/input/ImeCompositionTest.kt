package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCompositionTest {
    private val events = mutableListOf<String>()
    private val composition = ImeComposition(object : ImeComposition.Sink {
        override fun composing(text: String, cursor: Int) { events += "compose:$text@$cursor" }
        override fun commit(text: String) { events += "commit:$text" }
    })

    @Test fun zhuyinComposesThenCommitsTheChosenCharacter() {
        composition.compose("ㄓ", 1)
        composition.compose("ㄓㄨ", 1)
        composition.commit("注")
        assertEquals(listOf("compose:ㄓ@1", "compose:ㄓㄨ@2", "commit:注"), events)
        assertEquals("", composition.text)
    }

    @Test fun anUnchangedCompositionIsNotSentAgain() {
        composition.compose("hel", 1)
        composition.compose("hel", 1)
        assertEquals(listOf("compose:hel@3"), events)
    }

    @Test fun finishingKeepsTheComposedText() {
        composition.compose("hello", 1)
        composition.finish()
        composition.finish()
        assertEquals(listOf("compose:hello@5", "commit:hello"), events)
    }

    @Test fun anEmptyCommitClearsTheComposition() {
        composition.compose("ㄅ", 1)
        composition.commit("")
        composition.commit(null)
        assertEquals(listOf("compose:ㄅ@1", "compose:@0"), events)
    }

    @Test fun droppingClearsWithoutCommitting() {
        composition.compose("ni", 1)
        composition.drop()
        composition.drop()
        assertEquals(listOf("compose:ni@2", "compose:@0"), events)
    }

    @Test fun aCursorBeforeTheTextIsKeptAtItsStart() {
        composition.compose("ab", 0)
        assertEquals(listOf("compose:ab@0"), events)
    }
}
