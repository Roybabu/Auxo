package com.invision.pos.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherEmptyPasswordTest {

    private val hasher = PasswordHasher(iterations = 1_000)

    private fun account(password: String): UserAccount {
        val salt = hasher.newSalt()
        return UserAccount(1L, "Owner", "owner", Role.SUPER_ADMIN, true, salt, hasher.hash(password, salt), 1_000, 0L)
    }

    @Test
    fun anEmptyPassword_neverMatches_andDoesNotThrow() {
        assertFalse(hasher.matches("", account("secret-1")))
    }

    @Test
    fun theRightPassword_stillMatches_andAWrongOneDoesNot() {
        val owner = account("secret-1")
        assertTrue(hasher.matches("secret-1", owner))
        assertFalse(hasher.matches("secret-2", owner))
    }
}
