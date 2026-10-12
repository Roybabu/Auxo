package com.invision.pos.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.appearance.AppAccent
import com.invision.pos.core.appearance.SharedPrefsAppearanceRepository
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.domain.model.Product
import com.invision.pos.testutil.TestData
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

/** In-memory stand-in for the Invision folder. */
private class MemoryStorage : BackupStorage {
    val files = linkedMapOf<String, ByteArray>()

    override fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val out = java.io.ByteArrayOutputStream()
        write(out)
        files[fileName] = out.toByteArray()
        return SavedBackup(fileName, "Documents/Invision", Uri.parse("content://test/$fileName"))
    }
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
    private lateinit var database: InvisionDatabase

    private fun open(): InvisionDatabase =
        InvisionDatabase.builder(context).allowMainThreadQueries().build()

    private fun manager(db: InvisionDatabase) = BackupManager(context, db, appearance, storage, TestData.clock)

    private fun saveNew(db: InvisionDatabase, product: Product) = runBlocking {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        RoomInventoryRepository(db, TestData.clock).saveProduct(fresh)
    }

    private fun names(db: InvisionDatabase) = runBlocking { RoomInventoryRepository(db, TestData.clock).observeProducts().first().map { it.name }.sorted() }

    @Before
    fun setUp() {
        context.deleteDatabase(InvisionDatabase.NAME)
        database = open()
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(InvisionDatabase.NAME)
    }

    @Test
    fun theDatabaseVersionConstant_matchesTheRealOne() {
        assertEquals(InvisionDatabase.VERSION, database.openHelper.readableDatabase.version)
    }

    @Test
    fun backup_isSavedInTheInvisionFolder_withATimestampedName() = runBlocking {
        val saved = manager(database).backUp()

        assertEquals("Documents/Invision", saved.folder)
        assertEquals("Invision-backup-2026-10-10_1000.zip", saved.fileName)
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

        manager(database).restoreFrom(ByteArrayInputStream(backup))

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
    fun photos_goIntoTheBackup_andComeBackWithTheirProducts() = runBlocking {
        val photos = com.invision.pos.core.photos.FileProductPhotoStore(context)
        val dir = java.io.File(context.filesDir, com.invision.pos.core.photos.PhotoFiles.DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val kept = "0123456789abcdef0123456789abcdef.jpg"
        val later = "ffffffffffffffffffffffffffffffff.jpg"
        java.io.File(dir, kept).writeBytes(byteArrayOf(1, 2, 3, 4))
        saveNew(database, TestData.tShirt().copy(photoFile = kept))
        val manager = BackupManager(context, database, appearance, storage, TestData.clock, photos = photos)
        val backup = storage.files.getValue(manager.backUp().fileName)

        // The photo is lost, and another one is added after the backup.
        java.io.File(dir, kept).delete()
        java.io.File(dir, later).writeBytes(byteArrayOf(9))

        manager.restoreFrom(ByteArrayInputStream(backup))

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
        context.deleteDatabase(InvisionDatabase.NAME) // a new phone has no data yet

        val fresh = open()
        fresh.openHelper.writableDatabase // creates the empty database a new install would have
        manager(fresh).restoreFrom(ByteArrayInputStream(backup))

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
}
