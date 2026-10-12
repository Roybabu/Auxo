package com.auxo.pos.core.backup.auto

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.BackupStorage
import com.auxo.pos.core.backup.DocumentsBackupStorage
import com.auxo.pos.core.backup.SavedBackup
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
        /** [wanted], or "name (1).zip", "name (2).zip" … when a file by that name is already there. */
        fun uniqueName(wanted: String, existing: Set<String>): String {
            if (wanted !in existing) return wanted
            val base = wanted.removeSuffix(".zip")
            var n = 1
            while ("$base ($n).zip" in existing) n++
            return "$base ($n).zip"
        }
    }
}


/**
 * A folder picked with the phone's own folder picker (SD card, USB drive, Google Drive). The app only ever
 * receives access to that one folder, and nothing else on the phone.
 */
class DocumentTreeFolder(private val context: Context, private val tree: Uri) : BackupFolder {

    private val resolver get() = context.contentResolver

    private val rootDocument: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    override val label: String
        get() = runCatching {
            resolver.query(rootDocument, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull().orEmpty()

    override fun canWrite(): Boolean {
        val holdsAccess = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!holdsAccess) return false
        return runCatching {
            resolver.query(rootDocument, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                it.moveToFirst() && (it.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
            } ?: false
        }.getOrDefault(false)
    }

    override fun list(): List<FolderFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(FolderFile(cursor.getString(0), cursor.getString(1).orEmpty()))
                }
            } ?: throw FolderUnavailableException()
        } catch (e: FolderUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw FolderUnavailableException(cause = e)
        }
    }

    override fun create(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val made: Uri? = try {
            DocumentsContract.createDocument(resolver, rootDocument, DocumentsBackupStorage.MIME_TYPE, fileName)
        } catch (e: Exception) {
            throw FolderUnavailableException(cause = e)
        }
        val created = made ?: throw FolderUnavailableException("The folder would not create the backup file.")
        try {
            val out = resolver.openOutputStream(created, "w") ?: throw IOException("Could not open the backup file for writing.")
            out.use(write)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            if (e is BackupException) throw e
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        return SavedBackup(fileName, label.ifEmpty { fileName }, created)
    }

    override fun delete(file: FolderFile): Boolean = runCatching {
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, file.id))
    }.getOrDefault(false)
}
