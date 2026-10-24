// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.auto

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.auxo.pos.core.backup.BackupCrypto
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.BackupStorage
import com.auxo.pos.core.backup.DocumentsBackupStorage
import com.auxo.pos.core.backup.SavedBackup
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** The chosen folder cannot be used right now: the card or drive is out, or access to it was withdrawn. */
class FolderUnavailableException(message: String? = null, cause: Throwable? = null) : IOException(message ?: "The backup folder is not available.", cause)

/** A file already in the backup folder. */
data class FolderFile(val id: String, val name: String)

/** A folder the Super Admin picked. Kept small so the rules around it can be tested without a real card. */
interface BackupFolder {
    /** The folder's name, for showing. */
    val label: String

    /** Whether new files can be created in the folder right now. */
    fun canWrite(): Boolean

    /** The files directly inside the folder. Throws [FolderUnavailableException] if it cannot be read. */
    fun list(): List<FolderFile>

    /** Creates [fileName] and lets [write] fill it. Removes the file again if writing fails. */
    fun create(fileName: String, write: (OutputStream) -> Unit): SavedBackup

    fun delete(file: FolderFile): Boolean
}

/** Puts backups in a picked folder and trims old ones. */
class FolderBackupStorage(private val folder: BackupFolder) : BackupStorage {

    /** The name the last backup was saved under, so trimming never removes it. */
    @Volatile
    var lastSavedName: String? = null
        private set

    override fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        if (!folder.canWrite()) throw FolderUnavailableException()
        val existing = try {
            folder.list().map { it.name }.toSet()
        } catch (e: FolderUnavailableException) {
            throw e
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("BackupFolder", "Failed and showed an error", e)
            throw FolderUnavailableException(cause = e)
        }
        val name = uniqueName(fileName, existing)
        val saved = try {
            folder.create(name, write)
        } catch (e: FolderUnavailableException) {
            throw e
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("BackupFolder", "Failed and showed an error", e)
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        lastSavedName = saved.fileName
        return saved
    }

    /**
     * Deletes the oldest of the app's own backups so that [keep] remain. Files the app did not name are left
     * alone, and so is the backup just saved. Returns how many were removed.
     */
    fun prune(keep: Int): Int {
        val files = folder.list()
        val doomed = BackupRetention.toDelete(files.map { it.name }, keep) - setOfNotNull(lastSavedName)
        return files.filter { it.name in doomed }.count { folder.delete(it) }
    }

    companion object {
        /** [wanted], or "name (1).zip", "name (2).zip" … (or .auxobak for a locked backup) when a file by that name is already there. */
        fun uniqueName(wanted: String, existing: Set<String>): String {
            if (wanted !in existing) return wanted
            val extension = if (wanted.endsWith(BackupCrypto.FILE_EXTENSION)) BackupCrypto.FILE_EXTENSION else ".zip"
            val base = wanted.removeSuffix(extension)
            var n = 1
            while ("$base ($n)$extension" in existing) n++
            return "$base ($n)$extension"
        }
    }
}


/**
 * The Auxo folder inside Documents on this phone, which is created the first time a backup is saved. Saving goes
 * through [DocumentsBackupStorage], so it behaves like a backup made by hand on every Android version. The app can
 * list and delete only the backups it made itself, which is all the clean-up needs.
 */
class DocumentsAutoFolder(
    private val context: Context,
    private val storage: DocumentsBackupStorage = DocumentsBackupStorage(context),
) : BackupFolder {

    override val label: String get() = "Documents/${DocumentsBackupStorage.FOLDER}"

    // Saving falls back to the app's own folder when shared storage is not available, so there is always somewhere.
    override fun canWrite(): Boolean = true

    override fun list(): List<FolderFile> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listInMediaStore() else listInFolders()
    } catch (e: Exception) {
        com.auxo.pos.core.debuglog.DebugLog.error("BackupFolder", "Failed and showed an error", e)
        throw FolderUnavailableException(cause = e)
    }

    override fun create(fileName: String, write: (OutputStream) -> Unit): SavedBackup = storage.save(fileName, write)

    override fun delete(file: FolderFile): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(ContentUris.withAppendedId(filesUri(), file.id.toLong()), null, null) > 0
        } else {
            File(file.id).delete()
        }
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun filesUri(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listInMediaStore(): List<FolderFile> {
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Documents/${DocumentsBackupStorage.FOLDER}%")
        return context.contentResolver.query(filesUri(), columns, selection, args, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(FolderFile(cursor.getLong(0).toString(), cursor.getString(1).orEmpty()))
            }
        } ?: throw FolderUnavailableException()
    }

    /** Android 9 and older: the plain folders, both the shared one and the app's own fallback. */
    private fun listInFolders(): List<FolderFile> =
        storage.legacyFolders().flatMap { dir -> dir.listFiles()?.filter { it.isFile }.orEmpty() }.map { FolderFile(it.path, it.name) }
}
