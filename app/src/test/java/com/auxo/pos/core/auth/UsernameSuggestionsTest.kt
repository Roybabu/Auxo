package com.auxo.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameSuggestionsTest {

    @Test
    fun aFullName_givesIdeasBuiltFromIt() {
        val ideas = UsernameSuggestions.from("Roy Babu")
        assertEquals(listOf("roy.babu", "roybabu", "roy_babu"), ideas)
    }

    @Test
    fun noName_givesNoIdeas() {
        assertTrue(UsernameSuggestions.from("").isEmpty())
        assertTrue(UsernameSuggestions.from("   ").isEmpty())
        assertTrue(UsernameSuggestions.from("!!! ???").isEmpty())
    }

    @Test
    fun everyIdea_isAUsernameTheFormAccepts() {
        for (name in listOf("Roy", "Ro", "Åsa Öberg", "Mary Ann O'Neil", "A B")) {
            for (idea in UsernameSuggestions.from(name)) {
                assertTrue("$name -> $idea", AdminSetupValidator.isUsername(idea))
            }
        }
    }

    @Test
    fun ideasNeverRepeat_andAreAtMostThree() {
        val ideas = UsernameSuggestions.from("Roy Roy")
        assertEquals(ideas.distinct(), ideas)
        assertTrue(ideas.size <= 3)
    }
}
