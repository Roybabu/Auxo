// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedBackupFileTest {

    private fun fileBytes(passphrase: String): ByteArray {
        val tables = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }
        val json = SharedFile.toJson(SharedFile.assemble(tables, shopName = "Shop", createdAt = 5L, appVersion = "1.0"))
        return SharedBackupContainer.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)
    }

    @Test
    fun aSharedFile_startsWithTheSignature() {
        assertTrue(SharedBackupFile.startsWithSignature(fileBytes("a long passphrase")))
    }

    @Test
    fun aZipFile_isNotShared() {
        assertFalse(SharedBackupFile.startsWithSignature("PK\u0003\u0004 rest of a zip".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun aWrongPassphrase_isReportedAsOne() {
        val problem = assertThrows(BackupException::class.java) {
            SharedBackupFile.open(fileBytes("the right passphrase"), "the wrong passphrase")
        }
        assertEquals(BackupProblem.WRONG_PASSPHRASE, problem.problem)
    }

    @Test
    fun theManifest_showsTheShopAndItsCounts() {
        val document = SharedBackupFile.open(fileBytes("the right passphrase"), "the right passphrase")
        val manifest = SharedBackupFile.manifestOf(document)
        assertEquals("Shop", manifest.shopName)
        assertEquals(0, manifest.products)
        assertEquals(0, manifest.customers)
    }
}
