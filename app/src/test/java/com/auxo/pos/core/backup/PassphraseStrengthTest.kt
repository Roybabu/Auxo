// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import com.auxo.pos.core.backup.PassphraseStrength.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseStrengthTest {

    @Test
    fun shortOnes_areRefused() {
        assertEquals(Problem.TOO_SHORT, PassphraseStrength.problem("Ab1!xyz"))
        assertEquals(Problem.TOO_SHORT, PassphraseStrength.problem("123456789"))
    }

    @Test
    fun commonOnes_areRefused_evenWithSpacesOrCapitals() {
        assertEquals(Problem.TOO_COMMON, PassphraseStrength.problem("password123"))
        assertEquals(Problem.TOO_COMMON, PassphraseStrength.problem("Password 123"))
        assertEquals(Problem.TOO_COMMON, PassphraseStrength.problem("1234567890"))
        assertEquals(Problem.TOO_COMMON, PassphraseStrength.problem("qwertyuiop"))
    }

    @Test
    fun repeatedOnlyNumberAndRunPassphrases_areRefused() {
        assertEquals(Problem.TOO_SIMPLE, PassphraseStrength.problem("aaaaaaaaaaaa"))
        assertEquals(Problem.TOO_SIMPLE, PassphraseStrength.problem("1357924680"))
        assertEquals(Problem.TOO_SIMPLE, PassphraseStrength.problem("abcdefghijkl"))
        assertEquals(Problem.TOO_SIMPLE, PassphraseStrength.problem("9876543210987"))
    }

    @Test
    fun aFewRandomWords_areAccepted() {
        assertNull(PassphraseStrength.problem("copper river lamp window"))
        assertNull(PassphraseStrength.problem("Shop#Mumbai2026!"))
    }

    @Test
    fun theMeter_growsWithLengthAndVariety_andStaysLowForRefusedOnes() {
        assertEquals(0, PassphraseStrength.segments(""))
        assertEquals(1, PassphraseStrength.segments("password123"))
        assertTrue(PassphraseStrength.segments("copper river lamp window") > PassphraseStrength.segments("blue rose cats"))
        assertEquals(PassphraseStrength.SEGMENTS, PassphraseStrength.segments("Copper River Lamp Window 2026!"))
    }
}
