// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import com.auxo.pos.data.local.DatabaseEncryption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The parts of database locking that do not need SQLCipher's native library: telling a plain file from a locked one, and the key format. */
class DatabaseEncryptionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val plainHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Test
    fun aFileThatStartsLikeSqlite_isPlain() {
        val file = folder.newFile("plain.db").apply { writeBytes(plainHeader + ByteArray(100)) }
        assertTrue(DatabaseEncryption.isPlaintext(file))
    }

    @Test
    fun aFileOfRandomBytes_isNotPlain() {
        val file = folder.newFile("locked.db").apply { writeBytes(ByteArray(4096) { (it * 31 + 7).toByte() }) }
        assertFalse(DatabaseEncryption.isPlaintext(file))
    }

    @Test
    fun aMissingOrTinyFile_isNotPlain() {
        assertFalse(DatabaseEncryption.isPlaintext(java.io.File(folder.root, "missing.db")))
        assertFalse(DatabaseEncryption.isPlaintext(folder.newFile("tiny.db").apply { writeBytes(byteArrayOf(1, 2, 3)) }))
    }

    @Test
    fun aKeyIsSixtyFourLowercaseHexCharacters() {
        assertTrue(DatabaseEncryption.isValidKey("0123456789abcdef".repeat(4)))
        assertFalse(DatabaseEncryption.isValidKey("0123456789ABCDEF".repeat(4)))
        assertFalse(DatabaseEncryption.isValidKey("abc"))
        assertFalse(DatabaseEncryption.isValidKey("0123456789abcdef".repeat(4) + "0"))
    }
}
