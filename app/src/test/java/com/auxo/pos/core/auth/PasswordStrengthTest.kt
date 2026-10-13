package com.auxo.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthTest {

    @Test
    fun anEmptyPassword_showsNothing() = assertEquals(0, PasswordStrength.segments(""))

    @Test
    fun aPasswordTheFormWouldRefuse_neverShowsMoreThanOneSegment() {
        assertEquals(1, PasswordStrength.segments("abc12"))
        assertEquals(1, PasswordStrength.segments("onlyletters"))
        assertEquals(1, PasswordStrength.segments("password123"))
        assertEquals(1, PasswordStrength.segments("roy.babu-2024", identifier = "roy.babu"))
    }

    @Test
    fun anAcceptedPassword_growsWithLengthMixAndSymbols() {
        assertEquals(1, PasswordStrength.segments("greenleaf42"))
        assertEquals(2, PasswordStrength.segments("greenleafpress42"))
        assertEquals(3, PasswordStrength.segments("GreenLeafPress42"))
        assertEquals(4, PasswordStrength.segments("Green-Leaf-Press-42"))
    }

    @Test
    fun theMeterNeverLeavesItsRange() {
        for (pw in listOf("a", "A1", "Zz9!Zz9!Zz9!Zz9!", "x".repeat(200) + "1")) {
            assertTrue(PasswordStrength.segments(pw) in 0..PasswordStrength.SEGMENTS)
        }
    }
}
