// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameSuggestionsTest {

    @Test
    fun aFullName_givesIdeasBuiltFromIt() {
        val ideas = UsernameSuggestions.from("Sample User")
        assertEquals(listOf("sample.user", "sampleuser", "sample_user"), ideas)
    }

    @Test
    fun noName_givesNoIdeas() {
        assertTrue(UsernameSuggestions.from("").isEmpty())
        assertTrue(UsernameSuggestions.from("   ").isEmpty())
        assertTrue(UsernameSuggestions.from("!!! ???").isEmpty())
    }

    @Test
    fun everyIdea_isAUsernameTheFormAccepts() {
        for (name in listOf("Sample", "Ro", "Åsa Öberg", "Mary Ann O'Neil", "A B")) {
            for (idea in UsernameSuggestions.from(name)) {
                assertTrue("$name -> $idea", AdminSetupValidator.isUsername(idea))
            }
        }
    }

    @Test
    fun ideasNeverRepeat_andAreAtMostThree() {
        val ideas = UsernameSuggestions.from("Sample Sample")
        assertEquals(ideas.distinct(), ideas)
        assertTrue(ideas.size <= 3)
    }
}
