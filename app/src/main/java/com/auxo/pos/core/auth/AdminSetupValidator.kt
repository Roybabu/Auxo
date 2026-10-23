// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import java.util.Locale

enum class SetupField { BUSINESS, NAME, IDENTIFIER, PASSWORD, CONFIRM }

enum class SetupError {
    BUSINESS_REQUIRED,
    BUSINESS_TOO_LONG,
    NAME_REQUIRED,
    NAME_TOO_LONG,
    IDENTIFIER_REQUIRED,
    IDENTIFIER_INVALID,
    IDENTIFIER_TAKEN,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_NEEDS_LETTER_AND_DIGIT,
    PASSWORD_TOO_COMMON,
    PASSWORD_CONTAINS_IDENTIFIER,
    CONFIRM_REQUIRED,
    CONFIRM_MISMATCH,
}

/** Rules for the first administrator account. Pure Kotlin, so every rule can be tested without Android. */
object AdminSetupValidator {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128
    const val MAX_NAME_LENGTH = 60
    const val MAX_BUSINESS_NAME_LENGTH = 70
    const val MAX_IDENTIFIER_LENGTH = 254

    private val USERNAME = Regex("^[a-z0-9._-]{3,32}$")
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

    /** Passwords that meet the length and character rules but are among the first ones guessed. */
    private val COMMON = setOf(
        "password1", "password12", "password123", "passw0rd", "p@ssw0rd", "12345678", "123456789", "1234567890",
        "qwerty123", "qwertyuiop1", "abc12345", "admin123", "admin1234", "welcome1", "welcome123", "letmein123",
        "iloveyou1", "auxo1234", "auxo12345", "changeme1", "test1234", "india123", "asdf1234",
    )

    internal fun isCommon(lowerCasePassword: String): Boolean = lowerCasePassword in COMMON

    internal fun isUsername(candidate: String): Boolean = USERNAME.matches(candidate)

    fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /** Usernames and email addresses are matched without regard to case. */
    fun normalizeIdentifier(identifier: String): String = identifier.trim().lowercase(Locale.ROOT)

    /** The business name shown on bills and the Home screen. Checked on the setup form, apart from the account rules. */
    fun businessNameError(businessName: String): SetupError? {
        val clean = normalizeName(businessName)
        return when {
            clean.isEmpty() -> SetupError.BUSINESS_REQUIRED
            clean.length > MAX_BUSINESS_NAME_LENGTH -> SetupError.BUSINESS_TOO_LONG
            else -> null
        }
    }

    fun validate(name: String, identifier: String, password: String, confirm: String): Map<SetupField, SetupError> {
        val errors = linkedMapOf<SetupField, SetupError>()

        val cleanName = normalizeName(name)
        when {
            cleanName.isEmpty() -> errors[SetupField.NAME] = SetupError.NAME_REQUIRED
            cleanName.length > MAX_NAME_LENGTH -> errors[SetupField.NAME] = SetupError.NAME_TOO_LONG
        }

        val id = normalizeIdentifier(identifier)
        when {
            id.isEmpty() -> errors[SetupField.IDENTIFIER] = SetupError.IDENTIFIER_REQUIRED
            id.length > MAX_IDENTIFIER_LENGTH -> errors[SetupField.IDENTIFIER] = SetupError.IDENTIFIER_INVALID
            '@' in id -> if (!EMAIL.matches(id)) errors[SetupField.IDENTIFIER] = SetupError.IDENTIFIER_INVALID
            !USERNAME.matches(id) -> errors[SetupField.IDENTIFIER] = SetupError.IDENTIFIER_INVALID
        }

        passwordError(password, id)?.let { errors[SetupField.PASSWORD] = it }

        when {
            confirm.isEmpty() -> errors[SetupField.CONFIRM] = SetupError.CONFIRM_REQUIRED
            confirm != password -> errors[SetupField.CONFIRM] = SetupError.CONFIRM_MISMATCH
        }
        return errors
    }

    private fun passwordError(password: String, identifier: String): SetupError? {
        if (password.isEmpty()) return SetupError.PASSWORD_REQUIRED
        if (password.length < MIN_PASSWORD_LENGTH) return SetupError.PASSWORD_TOO_SHORT
        if (password.length > MAX_PASSWORD_LENGTH) return SetupError.PASSWORD_TOO_LONG
        if (password.none { it.isLetter() } || password.none { it.isDigit() }) return SetupError.PASSWORD_NEEDS_LETTER_AND_DIGIT
        val lower = password.lowercase(Locale.ROOT)
        if (lower in COMMON) return SetupError.PASSWORD_TOO_COMMON
        val localPart = identifier.substringBefore('@')
        if (localPart.length >= 3 && lower.contains(localPart)) return SetupError.PASSWORD_CONTAINS_IDENTIFIER
        return null
    }
}
