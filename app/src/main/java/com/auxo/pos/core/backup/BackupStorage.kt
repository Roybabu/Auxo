package com.auxo.pos.core.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** A backup file that has been saved: where it is, for showing and for sharing. */
data class SavedBackup(val fileName: String, val folder: String, val uri: Uri)

/** Somewhere to put backup files. */
interface BackupStorage {
    /** Creates [fileName] in the Auxo folder and lets [write] fill it. */
    fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup
}

/**
 * Saves backups in a folder called "Auxo" inside Documents, which is created the first
 * time. The folder is visible in any file manager and survives uninstalling the app, so the
 * file can be copied to another phone. On Android 9 and older, where apps cannot add to
 * Documents without a permission, the same folder name is used inside the app's own
 * storage area instead.
 */
class DocumentsBackupStorage(private val context: Context) : BackupStorage {

    override fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveInDocuments(fileName, write) else saveInAppFolder(fileName, write)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveInDocuments(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$DOCUMENTS/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw BackupException(BackupProblem.SAVE_FAILED, "The phone would not create the backup file.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the backup file for writing.")
            out.use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is BackupException) throw e
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        return SavedBackup(fileName, "$DOCUMENTS/$FOLDER", uri)
    }

    private fun saveInAppFolder(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val base = context.getExternalFilesDir(null) ?: throw BackupException(BackupProblem.SAVE_FAILED, "Storage is not available.")
        val dir = File(base, FOLDER).apply { mkdirs() }
        val file = File(dir, fileName)
        try {
            file.outputStream().use(write)
        } catch (e: Exception) {
            runCatching { file.delete() }
            if (e is BackupException) throw e
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return SavedBackup(fileName, "Android/data/${context.packageName}/files/$FOLDER", uri)
    }

    companion object {
        /** The folder named after the app. */
        const val FOLDER = "Auxo"
        const val MIME_TYPE = "application/zip"

        /** A locked backup is not a zip file, so it is not labelled as one. */
        fun mimeTypeFor(fileName: String): String =
            if (fileName.endsWith(BackupCrypto.FILE_EXTENSION)) "application/octet-stream" else MIME_TYPE
        private const val DOCUMENTS = "Documents"
    }
}
