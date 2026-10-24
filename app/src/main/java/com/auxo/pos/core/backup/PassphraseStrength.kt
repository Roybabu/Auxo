// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import java.util.Locale

/**
 * Whether a new backup passphrase is good enough, and how strong it looks (0 to 4 segments for the meter).
 *
 * A stolen backup can be guessed at offline, as fast as the thief's computers allow, so the passphrase is the only
 * thing between the file and the shop's data. Short, common, repeated and counting-up passphrases are refused; the
 * slow key derivation in [BackupCrypto] helps, but it cannot save "12345678". Only new passphrases are checked: an
 * older, shorter one still opens the backups it made.
 */
object PassphraseStrength {
    const val SEGMENTS = 4

    enum class Problem {
        /** Shorter than [BackupCrypto.MIN_PASSPHRASE_LENGTH]. */
        TOO_SHORT,

        /** One of the first things anyone would guess. */
        TOO_COMMON,

        /** Mostly one character, only digits, or a run like abcdefghij or 9876543210. */
        TOO_SIMPLE,
    }

    fun problem(passphrase: String): Problem? {
        if (passphrase.length < BackupCrypto.MIN_PASSPHRASE_LENGTH) return Problem.TOO_SHORT
        val lower = passphrase.lowercase(Locale.ROOT)
        val squeezed = lower.filter { it.isLetterOrDigit() }
        if (lower in COMMON || squeezed in COMMON || COMMON.any { squeezed == it + it }) return Problem.TOO_COMMON
        if (passphrase.toSet().size < MIN_DISTINCT || passphrase.all { it.isDigit() } || isRun(squeezed)) return Problem.TOO_SIMPLE
        return null
    }

    fun segments(passphrase: String): Int {
        if (passphrase.isEmpty()) return 0
        if (problem(passphrase) != null) return 1
        var points = 1
        if (passphrase.length >= 14) points++
        val words = passphrase.trim().split(Regex("\\s+")).count { it.length >= 3 }
        val mixed = passphrase.any { it.isUpperCase() } && passphrase.any { it.isLowerCase() } &&
            passphrase.any { !it.isLetter() }
        if (words >= 3 || mixed) points++
        if (passphrase.length >= 20) points++
        return points.coerceIn(1, SEGMENTS)
    }

    private fun isRun(text: String): Boolean {
        if (text.length < 4) return false
        val step = text[1] - text[0]
        return (step == 1 || step == -1) && text.zipWithNext().all { (a, b) -> b - a == step }
    }

    private const val MIN_DISTINCT = 5

    private val COMMON = setOf(
        "password", "passphrase", "password1", "password12", "password123", "passw0rd", "letmein123", "iloveyou12",
        "qwertyuiop", "qwerty123", "asdfghjkl", "1234567890", "12345678910", "0123456789", "1q2w3e4r5t", "welcome123",
        "admin12345", "administrator", "auxopassword", "auxobackup", "backuppassword", "mybackup", "shopbackup",
        "mystorebackup", "changeme123", "abcd1234", "abcd12345", "abc1234567", "india12345", "india123456",
    )
}
