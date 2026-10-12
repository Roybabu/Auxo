package com.auxo.pos.testutil

import android.net.Uri
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.SavedBackup
import com.auxo.pos.core.backup.auto.BackupFolder
import com.auxo.pos.core.backup.auto.FolderFile
import com.auxo.pos.core.backup.auto.FolderUnavailableException
import java.io.OutputStream

/** A pretend card: files in memory, with switches for the ways a real one goes wrong. */
class FakeBackupFolder(override val label: String = "SD card") : BackupFolder {
    val files = linkedMapOf<String, ByteArray>()
    var writable = true
    var failWhileWriting = false
    var failListing = false
    var refuseDelete = emptySet<String>()
    var deleteThrows = false

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
        if (deleteThrows) throw java.io.IOException("cannot delete")
        if (file.name in refuseDelete) return false
        return files.remove(file.name) != null
    }
}

