// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The Super Admin's recovery code: 20 characters from a 32-letter alphabet (100 bits), written in four groups
 * of five. The alphabet leaves out 0, 1, I and O, so a code copied by hand cannot be misread. The code itself
 * is never saved; only a salted hash of its normal form is (see [RecoveryRecord]).
 */
object RecoveryCodes {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 20
    const val GROUP = 5

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Upper-case, without spaces, dashes or any other separator. This is what gets hashed and compared. */
    fun normalize(text: String): String = text.uppercase().filter { it.isLetterOrDigit() }

    /** True if [text] is a code this app could have made, however it was spaced or capitalised. */
    fun isWellFormed(text: String): Boolean {
        val n = normalize(text)
        return n.length == LENGTH && n.all { it in ALPHABET }
    }

    /** `ABCDE-FGHJK-LMNPQ-RSTUV`, for showing and printing. */
    fun format(code: String): String = normalize(code).chunked(GROUP).joinToString("-")

    /** Compares two codes in constant time, ignoring spacing and case. */
    fun same(a: String, b: String): Boolean =
        MessageDigest.isEqual(normalize(a).toByteArray(Charsets.UTF_8), normalize(b).toByteArray(Charsets.UTF_8))
}

/** The saved proof that a recovery code exists: a salted hash, never the code. Only the Super Admin has one. */
class RecoveryRecord(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
    val createdAtMillis: Long,
) {
    fun matches(code: String, hasher: PasswordHasher): Boolean {
        if (!RecoveryCodes.isWellFormed(code)) return false
        return MessageDigest.isEqual(hasher.hash(RecoveryCodes.normalize(code), salt, iterations), hash)
    }

    companion object {
        fun create(code: String, hasher: PasswordHasher, nowMillis: Long): RecoveryRecord {
            val salt = hasher.newSalt()
            return RecoveryRecord(salt, hasher.hash(RecoveryCodes.normalize(code), salt), hasher.iterations, nowMillis)
        }
    }
}
