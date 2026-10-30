// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.appearance.AppAccent
import com.auxo.pos.core.appearance.SharedPrefsAppearanceRepository
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.core.shop.SharedPrefsShopPreferences
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.domain.jewellery.JewelleryMetal
import com.auxo.pos.domain.jewellery.MetalRates
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.OutputStream

/** In-memory stand-in for the Auxo folder. */
private class MemoryStorage : BackupStorage {
    val files = linkedMapOf<String, ByteArray>()

    override fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val out = java.io.ByteArrayOutputStream()
        write(out)
        files[fileName] = out.toByteArray()
        return SavedBackup(fileName, "Documents/Auxo", Uri.parse("content://test/$fileName"))
    }
}

/** A passphrase kept in memory, standing in for the Keystore-backed store. */
private class MemoryPassphrase(private var value: String? = null, var unreadable: Boolean = false) : BackupPassphraseStore {
    override fun isSet() = value != null
    override fun get() = if (unreadable) null else value
    override fun set(passphrase: String) { value = passphrase }
    override fun clear() { value = null }
}

/**
 * The whole backup and restore, against a real database file: back up, change things, restore,
 * and the original data is back. A backup of one database restores into a fresh one too, which
 * is the new-phone case.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = MemoryStorage()
    private val appearance = SharedPrefsAppearanceRepository(context)
    private lateinit var database: AuxoDatabase

    private fun open(): AuxoDatabase =
        AuxoDatabase.builder(context).allowMainThreadQueries().build()

    private val pass = "test backup passphrase"

    private fun manager(db: AuxoDatabase) = BackupManager(context, db, appearance, storage, TestData.clock, passphrases = MemoryPassphrase(pass))

    private fun saveNew(db: AuxoDatabase, product: Product) = runBlocking {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        RoomInventoryRepository(db, TestData.clock).saveProduct(fresh)
    }

    private fun names(db: AuxoDatabase) = runBlocking { RoomInventoryRepository(db, TestData.clock).observeProducts().first().map { it.name }.sorted() }

    @Before
    fun setUp() {
        context.deleteDatabase(AuxoDatabase.NAME)
        database = open()
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(AuxoDatabase.NAME)
    }

    @Test
    fun theDatabaseVersionConstant_matchesTheRealOne() {
        assertEquals(AuxoDatabase.VERSION, database.openHelper.readableDatabase.version)
    }

    @Test
    fun backup_isSavedInTheAuxoFolder_withATimestampedName() = runBlocking {
        val saved = manager(database).backUp()

        assertEquals("Documents/Auxo", saved.folder)
        assertEquals("Auxo-backup-2026-10-10_1000.auxobak", saved.fileName)
        assertTrue(storage.files.containsKey(saved.fileName))
        assertEquals(TestData.clock.millis(), manager(database).lastBackupMillis())
    }

    @Test
    fun restore_bringsBackWhatWasBackedUp_andDropsLaterChanges() = runBlocking {
        saveNew(database, TestData.tShirt())
        appearance.setAccent(AppAccent.VIOLET)
        appearance.setDarkMode(true)
        val saved = manager(database).backUp()
        val backup = storage.files.getValue(saved.fileName)
        assertEquals(listOf("Cotton T-Shirt"), names(database))

        // Later changes that the restore should undo.
        saveNew(database, TestData.biscuits())
        appearance.setAccent(AppAccent.ROSE)
        appearance.setDarkMode(false)
        assertEquals(2, names(database).size)

        manager(database).restoreFrom(ByteArrayInputStream(backup), pass)

        val reopened = open()
        try {
            assertEquals(listOf("Cotton T-Shirt"), names(reopened))
        } finally {
            reopened.close()
        }
        assertEquals(AppAccent.VIOLET, appearance.settings.value.accent)
        assertTrue(appearance.settings.value.darkMode)
    }

    @Test
    fun metalRatesAndTableCount_goIntoTheBackup_andComeBack() = runBlocking {
        val shop = SharedPrefsShopPreferences(context.getSharedPreferences("backup_test_shop", Context.MODE_PRIVATE))
        val withShop = BackupManager(context, database, appearance, storage, TestData.clock, shop = shop, passphrases = MemoryPassphrase(pass))
        val rates = MetalRates().with(JewelleryMetal.GOLD_22K, Money(612_300)).with(JewelleryMetal.SILVER, Money(9_500))
        shop.setMetalRates(rates)
        shop.setTableCount(14)
        val saved = withShop.backUp()
        val backup = storage.files.getValue(saved.fileName)

        // Later changes that the restore should undo.
        shop.setMetalRates(MetalRates())
        shop.setTableCount(3)

        withShop.restoreFrom(ByteArrayInputStream(backup), pass)

        assertEquals(rates.encode(), shop.metalRates.value.encode())
        assertEquals(14, shop.tableCount.value)
    }

    @Test
    fun photos_goIntoTheBackup_andComeBackWithTheirProducts() = runBlocking {
        val photos = com.auxo.pos.core.photos.FileProductPhotoStore(context)
        val dir = java.io.File(context.filesDir, com.auxo.pos.core.photos.PhotoFiles.DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val kept = "0123456789abcdef0123456789abcdef.jpg"
        val later = "ffffffffffffffffffffffffffffffff.jpg"
        java.io.File(dir, kept).writeBytes(byteArrayOf(1, 2, 3, 4))
        saveNew(database, TestData.tShirt().copy(photoFile = kept))
        val manager = BackupManager(context, database, appearance, storage, TestData.clock, photos = photos, passphrases = MemoryPassphrase(pass))
        val backup = storage.files.getValue(manager.backUp().fileName)

        // The photo is lost, and another one is added after the backup.
        java.io.File(dir, kept).delete()
        java.io.File(dir, later).writeBytes(byteArrayOf(9))

        manager.restoreFrom(ByteArrayInputStream(backup), pass)

        assertEquals(listOf<Byte>(1, 2, 3, 4), java.io.File(dir, kept).readBytes().toList())
        assertTrue(!java.io.File(dir, later).exists())
        val reopened = open()
        try {
            val product = RoomInventoryRepository(reopened, TestData.clock).observeProducts().first().single()
            assertEquals(kept, product.photoFile)
        } finally {
            reopened.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun restore_intoAFreshPhone_recreatesTheData() = runBlocking {
        saveNew(database, TestData.tShirt())
        val saved = manager(database).backUp()
        val backup = storage.files.getValue(saved.fileName)
        database.close()
        context.deleteDatabase(AuxoDatabase.NAME) // a new phone has no data yet

        val fresh = open()
        fresh.openHelper.writableDatabase // creates the empty database a new install would have
        manager(fresh).restoreFrom(ByteArrayInputStream(backup), pass)

        val reopened = open()
        try {
            assertEquals(listOf("Cotton T-Shirt"), names(reopened))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun aBadFile_changesNothing() = runBlocking {
        saveNew(database, TestData.tShirt())
        val problem = try {
            manager(database).restoreFrom(ByteArrayInputStream("not a backup".toByteArray()))
            null
        } catch (e: BackupException) {
            e.problem
        }

        assertEquals(BackupProblem.NOT_A_BACKUP, problem)
        // The database was not closed, and still has its data.
        assertEquals(listOf("Cotton T-Shirt"), names(database))
        assertEquals(AppAccent.DEFAULT, appearance.settings.value.accent)
    }

    private fun lockedManager(db: AuxoDatabase, store: BackupPassphraseStore) =
        BackupManager(context, db, appearance, storage, TestData.clock, passphrases = store)

    private fun problemOf(block: () -> Unit): BackupProblem? = try {
        block()
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun withAPassphrase_theBackupIsLocked_andRestoresOnlyWithIt() = runBlocking {
        saveNew(database, TestData.tShirt())
        val saved = lockedManager(database, MemoryPassphrase("correct horse")).backUp()
        val backup = storage.files.getValue(saved.fileName)

        assertEquals("Auxo-backup-2026-10-10_1000.auxobak", saved.fileName)
        assertTrue(BackupCrypto.looksEncrypted(backup))

        // No passphrase, and a wrong one, change nothing.
        assertEquals(BackupProblem.PASSPHRASE_NEEDED, problemOf { manager(database).restoreFrom(ByteArrayInputStream(backup)) })
        assertEquals(BackupProblem.WRONG_PASSPHRASE, problemOf { manager(database).restoreFrom(ByteArrayInputStream(backup), "wrong one") })
        assertEquals(listOf("Cotton T-Shirt"), names(database))

        saveNew(database, TestData.biscuits())
        manager(database).restoreFrom(ByteArrayInputStream(backup), "correct horse")
        val reopened = open()
        try {
            assertEquals(listOf("Cotton T-Shirt"), names(reopened))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun aLockedBackup_restoresOnAnotherPhone_thatHasNoPassphraseStored() = runBlocking {
        saveNew(database, TestData.tShirt())
        val backup = storage.files.getValue(lockedManager(database, MemoryPassphrase("correct horse")).backUp().fileName)
        database.close()
        context.deleteDatabase(AuxoDatabase.NAME)

        val fresh = open()
        fresh.openHelper.writableDatabase
        lockedManager(fresh, MemoryPassphrase()).restoreFrom(ByteArrayInputStream(backup), "correct horse")
        val reopened = open()
        try {
            assertEquals(listOf("Cotton T-Shirt"), names(reopened))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun anUnlockedZipBackup_isNeverRestored_andChangesNothing() = runBlocking {
        saveNew(database, TestData.tShirt())
        val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(64)

        assertEquals(BackupProblem.NOT_LOCKED, problemOf { manager(database).restoreFrom(ByteArrayInputStream(zip)) })
        assertEquals(BackupProblem.NOT_LOCKED, problemOf { manager(database).restoreFrom(ByteArrayInputStream(zip), "not needed") })
        assertEquals(listOf("Cotton T-Shirt"), names(database))
    }

    @Test
    fun ifLockingIsOnButThePassphraseCannotBeRead_noUnlockedBackupIsMade() = runBlocking {
        val store = MemoryPassphrase("correct horse", unreadable = true)
        val problem = try {
            lockedManager(database, store).backUp()
            null
        } catch (e: BackupException) {
            e.problem
        }

        assertEquals(BackupProblem.PASSPHRASE_UNAVAILABLE, problem)
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun withNoPassphrase_noBackupIsMade_soNoUnlockedFileExists() = runBlocking {
        assertEquals(BackupProblem.PASSPHRASE_UNAVAILABLE, problemOf { runBlocking { BackupManager(context, database, appearance, storage, TestData.clock).backUp() } })
        val manager = lockedManager(database, MemoryPassphrase("correct horse"))
        manager.clearPassphrase()
        assertTrue(!manager.isProtected())
        assertEquals(BackupProblem.PASSPHRASE_UNAVAILABLE, problemOf { runBlocking { manager.backUp() } })
        assertTrue(storage.files.isEmpty())
    }
}
