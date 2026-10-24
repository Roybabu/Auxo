// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Locks a backup with a passphrase. The key comes from the passphrase with salted PBKDF2-HMAC-SHA256 (the same
 * slow method the app uses for passwords), and the data is encrypted with Google Tink's streaming AES-256-GCM,
 * which works in 1 MB pieces so a large database never has to fit in memory. Every piece is authenticated, so a
 * wrong passphrase or a changed file is detected before anything is restored.
 *
 * File layout: 8 bytes "AUXOENC1", the PBKDF2 iteration count (4 bytes), the salt (16 bytes), then the Tink
 * stream holding the ordinary backup zip. The first 28 bytes are also bound into the encryption as associated
 * data, so they cannot be altered without the file failing to open.
 */
object BackupCrypto {
    /** Ends the name of an encrypted backup, so nobody mistakes it for a plain zip. */
    const val FILE_EXTENSION = ".auxobak"

    const val MIN_PASSPHRASE_LENGTH = 8

    const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private const val SEGMENT_BYTES = 1 shl 20
    private val MAGIC = "AUXOENC1".toByteArray(Charsets.US_ASCII)
    private val HEADER_BYTES = MAGIC.size + 4 + SALT_BYTES

    /** How many bytes at the start of a file are needed to tell whether it is encrypted. */
    val SIGNATURE_BYTES: Int get() = MAGIC.size

    /** True when [head] (the first bytes of a file) starts with the encrypted-backup signature. */
    fun looksEncrypted(head: ByteArray, length: Int = head.size): Boolean =
        length >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /**
     * Wraps [out] so that everything written to it is encrypted. Closing the returned stream finishes the file, so
     * it must be closed.
     */
    fun encrypt(
        out: OutputStream,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(iterations).put(salt).array()
        out.write(header)
        try {
            return streaming(passphrase, salt, iterations).newEncryptingStream(out, header)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
    }

    /**
     * Opens an encrypted backup. Throws [BackupProblem.WRONG_PASSPHRASE] when the passphrase does not match (or the
     * start of the file has been damaged), before any of the content is returned.
     */
    fun decrypt(input: InputStream, passphrase: String): InputStream {
        val header = ByteArray(HEADER_BYTES)
        var filled = 0
        while (filled < header.size) {
            val read = input.read(header, filled, header.size - filled)
            if (read < 0) throw BackupException(BackupProblem.NOT_A_BACKUP)
            filled += read
        }
        if (!looksEncrypted(header)) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val buffer = ByteBuffer.wrap(header, MAGIC.size, header.size - MAGIC.size)
        val iterations = buffer.int
        if (iterations !in 1..MAX_ITERATIONS) throw BackupException(BackupProblem.CORRUPT)
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }

        val stream = try {
            PushbackInputStream(streaming(passphrase, salt, iterations).newDecryptingStream(input, header), 1)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupProblem.WRONG_PASSPHRASE, cause = e)
        }
        // Reading the first byte checks the first piece of the file, which is what tells a wrong passphrase apart.
        val first = try {
            stream.read()
        } catch (e: IOException) {
            throw BackupException(BackupProblem.WRONG_PASSPHRASE, cause = e)
        }
        if (first >= 0) stream.unread(first)
        return stream
    }

    private fun streaming(passphrase: String, salt: ByteArray, iterations: Int): AesGcmHkdfStreaming {
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BYTES * 8)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
        return AesGcmHkdfStreaming(key, "HmacSha256", KEY_BYTES, SEGMENT_BYTES, 0)
    }
}
