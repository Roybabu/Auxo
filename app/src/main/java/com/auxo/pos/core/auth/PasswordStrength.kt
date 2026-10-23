// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import java.util.Locale

/**
 * How strong a new password looks, as 0 to 4 filled segments for the meter on the setup form. It is only a guide:
 * the rules in [AdminSetupValidator] still decide whether the password is accepted. Pure Kotlin, so it is tested without Android.
 */
object PasswordStrength {
    const val SEGMENTS = 4

    fun segments(password: String, identifier: String = ""): Int {
        if (password.isEmpty()) return 0
        val lower = password.lowercase(Locale.ROOT)
        val localPart = AdminSetupValidator.normalizeIdentifier(identifier).substringBefore('@')
        val breaksARule = password.length < AdminSetupValidator.MIN_PASSWORD_LENGTH ||
            password.none { it.isLetter() } || password.none { it.isDigit() } ||
            AdminSetupValidator.isCommon(lower) || (localPart.length >= 3 && lower.contains(localPart))
        // A password the form would refuse never shows more than one segment.
        if (breaksARule) return 1
        var points = 1
        if (password.length >= 12) points++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) points++
        if (password.any { !it.isLetterOrDigit() }) points++
        return points.coerceIn(1, SEGMENTS)
    }
}
