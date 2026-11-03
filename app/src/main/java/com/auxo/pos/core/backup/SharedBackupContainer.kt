// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The shared "auxo-backup" version 1 container (see docs/BACKUP_FORMAT.md in the web repository).
 *
 * Layout: a fixed 74-byte header (magic, version, KDF name, cipher name, iterations, salt, nonce), then one
 * AES-256-GCM ciphertext with its 16-byte tag. The whole header is the authenticated data, so a changed header byte
 * fails authentication. This layer only handles the encrypted container; the JSON payload is handled separately.
 */
object SharedBackupContainer {
    const val MAGIC = "AUXOBKUP"
    const val VERSION = 1
    const val ITERATIONS = 600_000
    const val HEADER_BYTES = 74
    const val MIN_BYTES = 90
    const val MAX_PLAINTEXT_BYTES = 256L * 1024 * 1024

    private const val KDF_NAME = "PBKDF2-HMAC-SHA256"
    private const val CIPHER_NAME = "AES-256-GCM"
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** Parsed header fields. */
    class Header(val iterations: Int, val salt: ByteArray, val nonce: ByteArray, val bytes: ByteArray)

    /** Builds the 74-byte header from [salt] and [nonce]. */
    fun buildHeader(salt: ByteArray, nonce: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(salt.size == SALT_BYTES && nonce.size == NONCE_BYTES) { "Backup salt or nonce has the wrong length." }
        val buffer = ByteBuffer.allocate(HEADER_BYTES)
        buffer.put(ascii(MAGIC, 8))
        buffer.putShort(VERSION.toShort())
        buffer.put(ascii(KDF_NAME, 20))
        buffer.put(ascii(CIPHER_NAME, 12))
        buffer.putInt(iterations)
        buffer.put(salt)
        buffer.put(nonce)
        return buffer.array()
    }

    /** Checks and reads the header. Throws before any key derivation if anything is unexpected. */
    fun parseHeader(file: ByteArray): Header {
        if (file.size < MIN_BYTES) throw IllegalArgumentException("This file is too small to be an AUXO backup.")
        if (readAscii(file, 0, 8) != MAGIC) throw IllegalArgumentException("This is not an AUXO backup file.")
        val buffer = ByteBuffer.wrap(file)
        if (buffer.getShort(8).toInt() != VERSION) throw IllegalArgumentException("This backup needs a newer version of AUXO.")
        if (readAscii(file, 10, 20) != KDF_NAME || readAscii(file, 30, 12) != CIPHER_NAME) {
            throw IllegalArgumentException("This backup uses an unsupported encryption method.")
        }
        val iterations = buffer.getInt(42)
        if (iterations != ITERATIONS) throw IllegalArgumentException("This backup uses unsupported key settings.")
        return Header(
            iterations = iterations,
            salt = file.copyOfRange(46, 62),
            nonce = file.copyOfRange(62, 74),
            bytes = file.copyOfRange(0, HEADER_BYTES),
        )
    }

    /** Encrypts UTF-8 JSON [json] into a complete backup file. Random salt and nonce unless given (tests only). */
    fun encrypt(
        json: ByteArray,
        passphrase: String,
        salt: ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes),
        nonce: ByteArray = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes),
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "Enter a backup passphrase." }
        require(json.size <= MAX_PLAINTEXT_BYTES) { "This backup is too large to save." }
        val header = buildHeader(salt, nonce)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return header + cipher.doFinal(json)
    }

    /** Authenticates and decrypts a backup file, returning the UTF-8 JSON bytes. Nothing is returned unless the tag checks out. */
    fun decrypt(file: ByteArray, passphrase: String): ByteArray {
        val header = parseHeader(file)
        val key = deriveKey(passphrase, header.salt, header.iterations)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, header.nonce))
            updateAAD(header.bytes)
        }
        return try {
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw IllegalArgumentException("Wrong passphrase, or the backup file is damaged.", e)
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** PBKDF2-HMAC-SHA256 with the passphrase in NFC-normalised UTF-8, giving the 256-bit AES key. */
    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val normalized = Normalizer.normalize(passphrase, Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(normalized, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun ascii(text: String, width: Int): ByteArray {
        val out = ByteArray(width)
        text.toByteArray(Charsets.US_ASCII).copyInto(out, endIndex = minOf(text.length, width))
        return out
    }

    private fun readAscii(file: ByteArray, offset: Int, width: Int): String {
        var end = offset
        while (end < offset + width && file[end] != 0.toByte()) end++
        return String(file, offset, end - offset, Charsets.US_ASCII)
    }
}
