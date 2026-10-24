// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import android.net.Uri
import com.auxo.pos.ui.screens.auth.groupByShop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BackupScannerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun backupBytes(manifest: BackupManifest, passphrase: String? = null): ByteArray {
        val database = folder.newFile().apply { writeBytes(ByteArray(100)) }
        val out = ByteArrayOutputStream()
        val target = passphrase?.let { BackupCrypto.encrypt(out, it) } ?: out
        BackupArchive.write(target, manifest, database, emptyMap())
        return out.toByteArray()
    }

    private val manifest = BackupManifest(
        createdAtMillis = 1_700_000_000_000,
        databaseVersion = 1,
        appVersion = "1.0.0",
        shopName = "Roy Stores",
        products = 12,
        customers = 3,
        invoices = 40,
    )

    @Test
    fun manifestKeepsTheShopNameAndCounts() {
        val read = BackupArchive.readManifest(ByteArrayInputStream(backupBytes(manifest)))
        assertEquals(manifest, read)
    }

    @Test
    fun anOldManifestWithoutTheShopStillReads() {
        val old = manifest.copy(shopName = "", products = null, customers = null, invoices = null)
        val read = BackupArchive.readManifest(ByteArrayInputStream(backupBytes(old)))
        assertEquals("", read.shopName)
        assertEquals(null, read.products)
    }

    @Test
    fun plainBackup_isDescribed() {
        val result = BackupScanner.inspect(ByteArrayInputStream(backupBytes(manifest)))
        assertEquals(Inspection.Plain(manifest), result)
    }

    @Test
    fun lockedBackup_isReportedAsLockedWithoutReadingIt() {
        val result = BackupScanner.inspect(ByteArrayInputStream(backupBytes(manifest, passphrase = "a long passphrase")))
        assertEquals(Inspection.Locked, result)
    }

    @Test
    fun otherFiles_areNotBackups() {
        assertEquals(Inspection.NotABackup, BackupScanner.inspect(ByteArrayInputStream("hello, not a zip".toByteArray())))
        assertEquals(Inspection.NotABackup, BackupScanner.inspect(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun onlyBackupLookingNamesAreOpened() {
        assertFalse(BackupScanner.isCandidate("Auxo-backup-2026-10-24_1200.zip"))
        assertTrue(BackupScanner.isCandidate("Auxo-backup-2026-10-24_1200.auxobak"))
        assertFalse(BackupScanner.isCandidate("COPY.ZIP"))
        assertTrue(BackupScanner.isCandidate("COPY.AUXOBAK"))
        assertFalse(BackupScanner.isCandidate("photo.jpg"))
    }

    private fun found(name: String, shop: String?, taken: Long, locked: Boolean = false) = FoundBackup(
        uri = Uri.parse("content://test/$name"),
        fileName = name,
        modifiedMillis = taken,
        sizeBytes = 10,
        locked = locked,
        manifest = if (locked) null else manifest.copy(shopName = shop.orEmpty(), createdAtMillis = taken),
    )

    @Test
    fun backups_areGroupedByShop_newestFirst() {
        val groups = groupByShop(
            listOf(
                found("a", "Roy Stores", 100),
                found("b", "Green Mart", 300),
                found("c", "roy stores", 200),
                found("d", null, 50, locked = true),
            ),
        )
        // The title uses the spelling in the newest backup of that shop.
        assertEquals(listOf("Green Mart", "roy stores", ""), groups.map { it.title })
        assertEquals(listOf("c", "a"), groups[1].backups.map { it.fileName })
        assertEquals(listOf("d"), groups[2].backups.map { it.fileName })
    }
}
