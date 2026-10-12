package com.invision.pos.core.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.backup.auto.BackupFolder
import com.invision.pos.core.backup.auto.FolderBackupStorage
import com.invision.pos.core.backup.auto.FolderFile
import com.invision.pos.core.backup.auto.FolderUnavailableException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** A pretend card: files in memory, with switches for the ways a real one goes wrong. */
private class FakeFolder(override val label: String = "SD card") : BackupFolder {
    val files = linkedMapOf<String, ByteArray>()
    var writable = true
    var failWhileWriting = false
    var failListing = false
    var refuseDelete = emptySet<String>()

    override fun canWrite() = writable

    override fun list(): List<FolderFile> {
        if (failListing) throw FolderUnavailableException()
        return files.keys.map { FolderFile("id-$it", it) }
    }

    override fun create(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val bytes = java.io.ByteArrayOutputStream()
        try {
            write(bytes)
            if (failWhileWriting) throw java.io.IOException("card full")
        } catch (e: Exception) {
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        files[fileName] = bytes.toByteArray()
        return SavedBackup(fileName, label, Uri.parse("content://test/$fileName"))
    }

    override fun delete(file: FolderFile): Boolean {
        if (file.name in refuseDelete) return false
        return files.remove(file.name) != null
    }
}

@RunWith(AndroidJUnit4::class)
class FolderBackupStorageTest {

    private fun backup(stamp: String) = "Invision-backup-$stamp.zip"

    @Test
    fun savesTheBackupInTheFolder_withTheContentsWritten() {
        val folder = FakeFolder()
        val saved = FolderBackupStorage(folder).save(backup("2026-10-12_0900")) { it.write("zip-bytes".toByteArray()) }
        assertEquals(backup("2026-10-12_0900"), saved.fileName)
        assertEquals("SD card", saved.folder)
        assertEquals("zip-bytes", String(folder.files.getValue(backup("2026-10-12_0900"))))
    }

    @Test
    fun aSecondBackupInTheSameMinute_doesNotOverwriteTheFirst() {
        val folder = FakeFolder()
        val storage = FolderBackupStorage(folder)
        storage.save(backup("2026-10-12_0900")) { it.write(1) }
        val second = storage.save(backup("2026-10-12_0900")) { it.write(2) }
        assertEquals("Invision-backup-2026-10-12_0900 (1).zip", second.fileName)
        assertEquals(2, folder.files.size)
        assertEquals(1, folder.files.getValue(backup("2026-10-12_0900"))[0].toInt())
    }

    @Test
    fun uniqueName_countsUpPastExistingCopies() {
        val taken = setOf("a.zip", "a (1).zip", "a (2).zip")
        assertEquals("a (3).zip", FolderBackupStorage.uniqueName("a.zip", taken))
        assertEquals("b.zip", FolderBackupStorage.uniqueName("b.zip", taken))
    }

    @Test
    fun aFolderThatCannotBeWrittenTo_isReportedAsUnavailable_andNothingIsWritten() {
        val folder = FakeFolder().apply { writable = false }
        try {
            FolderBackupStorage(folder).save(backup("2026-10-12_0900")) { it.write(1) }
            fail("expected the folder to be reported unavailable")
        } catch (e: FolderUnavailableException) {
            assertTrue(folder.files.isEmpty())
        }
    }

    @Test
    fun aFolderThatCannotBeListed_isReportedAsUnavailable() {
        val folder = FakeFolder().apply { failListing = true }
        try {
            FolderBackupStorage(folder).save(backup("2026-10-12_0900")) { it.write(1) }
            fail("expected the folder to be reported unavailable")
        } catch (e: FolderUnavailableException) {
            assertTrue(folder.files.isEmpty())
        }
    }

    @Test
    fun aWriteThatFails_leavesNoHalfFileAndIsReportedAsASaveProblem() {
        val folder = FakeFolder().apply { failWhileWriting = true }
        try {
            FolderBackupStorage(folder).save(backup("2026-10-12_0900")) { it.write(1) }
            fail("expected a save problem")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.SAVE_FAILED, e.problem)
            assertTrue(folder.files.isEmpty())
        }
    }

    @Test
    fun prune_removesTheOldestBackups_keepingTheNewestN() {
        val folder = FakeFolder()
        val storage = FolderBackupStorage(folder)
        listOf("2026-10-01_0900", "2026-10-02_0900", "2026-10-03_0900", "2026-10-04_0900", "2026-10-05_0900")
            .forEach { storage.save(backup(it)) { out -> out.write(1) } }
        assertEquals(3, storage.prune(keep = 2))
        assertEquals(listOf(backup("2026-10-04_0900"), backup("2026-10-05_0900")), folder.files.keys.sorted())
    }

    @Test
    fun prune_leavesOtherFilesInTheFolderAlone() {
        val folder = FakeFolder()
        folder.files["holiday-photos.zip"] = ByteArray(1)
        folder.files["notes.txt"] = ByteArray(1)
        folder.files["Invision-backup-final.zip"] = ByteArray(1)
        val storage = FolderBackupStorage(folder)
        storage.save(backup("2026-10-01_0900")) { it.write(1) }
        storage.save(backup("2026-10-02_0900")) { it.write(1) }
        storage.prune(keep = 1)
        assertEquals(
            setOf("holiday-photos.zip", "notes.txt", "Invision-backup-final.zip", backup("2026-10-02_0900")),
            folder.files.keys,
        )
    }

    @Test
    fun prune_neverRemovesTheBackupJustSaved_evenWhenItsNameSortsOlder() {
        val folder = FakeFolder()
        val storage = FolderBackupStorage(folder)
        storage.save(backup("2026-10-12_0900")) { it.write(1) }
        // Same minute again: saved as "... (1).zip", which sorts before the original name.
        val second = storage.save(backup("2026-10-12_0900")) { it.write(2) }
        storage.prune(keep = 1)
        assertTrue(second.fileName in folder.files)
    }

    @Test
    fun prune_countsOnlyFilesActuallyDeleted() {
        val folder = FakeFolder()
        val storage = FolderBackupStorage(folder)
        listOf("2026-10-01_0900", "2026-10-02_0900", "2026-10-03_0900").forEach { storage.save(backup(it)) { out -> out.write(1) } }
        folder.refuseDelete = setOf(backup("2026-10-01_0900"))
        assertEquals(1, storage.prune(keep = 1))
        assertTrue(backup("2026-10-01_0900") in folder.files)
        assertFalse(backup("2026-10-02_0900") in folder.files)
    }

    @Test
    fun prune_doesNothing_whenFewerThanNExist() {
        val folder = FakeFolder()
        val storage = FolderBackupStorage(folder)
        storage.save(backup("2026-10-01_0900")) { it.write(1) }
        assertEquals(0, storage.prune(keep = 7))
        assertEquals(1, folder.files.size)
    }
}
