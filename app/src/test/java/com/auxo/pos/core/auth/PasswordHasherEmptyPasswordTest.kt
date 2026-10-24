// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

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

/** Whatever a person types into a password, PIN or recovery-code box, checking it must give true or false and never throw. */
class AuthInputNeverThrowsTest {

    private val hasher = PasswordHasher(iterations = 1_000)

    private val odd = listOf(
        "",
        " ",
        "   \t\n",
        "\u0000",
        "😀 emoji",
        "\uD83D", // a lone surrogate
        "पासवर्ड",
        "a".repeat(10_000),
    )

    @Test
    fun passwordCheck_givesFalseForOddInput() {
        val salt = hasher.newSalt()
        val account = UserAccount(1L, "Owner", "owner", Role.SUPER_ADMIN, true, salt, hasher.hash("secret-1", salt), 1_000, 0L)
        odd.forEach { assertFalse("password '$it'", hasher.matches(it, account)) }
    }

    @Test
    fun pinCheck_givesFalseForOddInput() {
        val record = PinRecord.create("4829", hasher)
        odd.forEach { assertFalse("pin '$it'", record.matches(it, hasher)) }
    }

    @Test
    fun recoveryCodeCheck_givesFalseForOddInput() {
        val record = RecoveryRecord.create(RecoveryCodes.generate(), hasher, 0L)
        odd.forEach { assertFalse("code '$it'", record.matches(it, hasher)) }
    }
}
