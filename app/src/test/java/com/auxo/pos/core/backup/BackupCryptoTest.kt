// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random

/** Locking a backup with a passphrase. A low iteration count keeps these quick; the real one is the default. */
class BackupCryptoTest {

    private val fast = 1_000

    private fun lock(data: ByteArray, passphrase: String = "correct horse"): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, passphrase, iterations = fast).use { it.write(data) }
        return out.toByteArray()
    }

    private fun unlock(locked: ByteArray, passphrase: String): ByteArray =
        BackupCrypto.decrypt(ByteArrayInputStream(locked), passphrase).use { it.readBytes() }

    private fun problemOf(block: () -> Unit): BackupProblem? = try {
        block()
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun roundTrip_returnsTheSameBytes() {
        val data = "customers, bills and stock".toByteArray()
        assertArrayEquals(data, unlock(lock(data), "correct horse"))
    }

    @Test
    fun emptyAndLargeData_roundTrip_acrossSeveralPieces() {
        assertArrayEquals(ByteArray(0), unlock(lock(ByteArray(0)), "correct horse"))
        val big = ByteArray(3 * 1024 * 1024 + 123).also { Random(7).nextBytes(it) }
        assertArrayEquals(big, unlock(lock(big), "correct horse"))
    }

    @Test
    fun theLockedFile_hasTheSignature_andHidesTheContent() {
        val data = "PK plain text that must not appear".toByteArray()
        val locked = lock(data)
        assertTrue(BackupCrypto.looksEncrypted(locked))
        assertFalse(BackupCrypto.looksEncrypted(data))
        assertFalse(String(locked, Charsets.ISO_8859_1).contains("must not appear"))
    }

    @Test
    fun aWrongPassphrase_isRefused_beforeAnyDataIsReturned() {
        val locked = lock("secret".toByteArray())
        assertEquals(BackupProblem.WRONG_PASSPHRASE, problemOf { unlock(locked, "wrong passphrase") })
    }

    @Test
    fun twoBackupsOfTheSameData_differ_becauseTheSaltIsNew() {
        val data = "same".toByteArray()
        assertFalse(lock(data).contentEquals(lock(data)))
    }

    @Test
    fun aChangedHeader_isNotAccepted() {
        val locked = lock("secret".toByteArray())
        // Change a salt byte: the key differs and the header no longer matches what was protected.
        locked[BackupCrypto.SIGNATURE_BYTES + 4] = (locked[BackupCrypto.SIGNATURE_BYTES + 4] + 1).toByte()
        assertEquals(BackupProblem.WRONG_PASSPHRASE, problemOf { unlock(locked, "correct horse") })
    }

    @Test
    fun aDamagedPiece_isDetected() {
        val big = ByteArray(2 * 1024 * 1024).also { Random(3).nextBytes(it) }
        val locked = lock(big)
        locked[locked.size - 10] = (locked[locked.size - 10] + 1).toByte()
        val failed = try {
            unlock(locked, "correct horse")
            false
        } catch (e: java.io.IOException) {
            true
        } catch (e: BackupException) {
            true
        }
        assertTrue("A changed byte must not be read back as good data", failed)
    }

    @Test
    fun aFileThatIsNotLocked_isNotAccepted() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf { unlock("just some text, not locked at all".toByteArray(), "x") })
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf { unlock(ByteArray(5), "x") })
    }

    @Test
    fun aLockedBackupArchive_roundTripsThroughTheRealArchiveCode() {
        val dir = java.nio.file.Files.createTempDirectory("crypto").toFile()
        try {
            val database = File(dir, "auxo.db").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
            val manifest = BackupManifest(createdAtMillis = 5L, databaseVersion = 17, appVersion = "0.4.0")
            val locked = ByteArrayOutputStream()
            BackupArchive.write(BackupCrypto.encrypt(locked, "correct horse", iterations = fast), manifest, database, mapOf("accent" to "violet"))

            val work = File(dir, "work")
            val stream = BackupCrypto.decrypt(ByteArrayInputStream(locked.toByteArray()), "correct horse")
            val read = BackupArchive.read(stream, work)
            assertEquals(17, read.manifest.databaseVersion)
            assertEquals("violet", read.preferences["accent"])
            assertArrayEquals(database.readBytes(), read.database.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theFileExtension_isNotZip() {
        assertFalse(BackupCrypto.FILE_EXTENSION.endsWith("zip"))
        assertTrue(com.auxo.pos.core.backup.auto.BackupRetention.isOwnBackup("Auxo-backup-2026-10-12_0106${BackupCrypto.FILE_EXTENSION}"))
        assertEquals(
            "Auxo-backup-2026-10-12_0106 (1).auxobak",
            com.auxo.pos.core.backup.auto.FolderBackupStorage.uniqueName(
                "Auxo-backup-2026-10-12_0106.auxobak",
                setOf("Auxo-backup-2026-10-12_0106.auxobak"),
            ),
        )
    }
}
