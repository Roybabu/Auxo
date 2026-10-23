package com.auxo.pos.core.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** A backup file that has been saved: where it is, for showing and for sharing. */
data class SavedBackup(
    val fileName: String,
    val folder: String,
    val uri: Uri,
    /** True when the file is only in the app's own storage, which is erased if the app is uninstalled. */
    val appFolderOnly: Boolean = false,
)

/** Somewhere to put backup files. */
interface BackupStorage {
    /** Creates [fileName] in the Auxo folder and lets [write] fill it. */
    fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup
}

/**
 * Saves backups in a folder called "Auxo" inside Documents, which is created the first
 * time. The folder is visible in any file manager and survives uninstalling the app, so the
 * file can be copied to another phone. On Android 9 and older, adding to Documents needs the storage
 * permission; with it the same folder is used. Without it the backup goes into the app's own storage area,
 * which is erased when the app is uninstalled, and [SavedBackup.appFolderOnly] says so.
 */
class DocumentsBackupStorage(private val context: Context) : BackupStorage {

    override fun save(fileName: String, write: (OutputStream) -> Unit): SavedBackup = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveInDocuments(fileName, write)
        canWritePublicDocuments() -> try {
            saveInPublicDocuments(fileName, write)
        } catch (e: BackupException) {
            // The shared storage would not take it (for example the card was removed). A backup in the app's
            // folder is better than none, and the person is told it is only there.
            saveInAppFolder(fileName, write)
        }
        else -> saveInAppFolder(fileName, write)
    }

    /** Android 9 and older: true once the person has allowed storage access and shared storage is mounted. */
    private fun canWritePublicDocuments(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    @Suppress("DEPRECATION")
    private fun saveInPublicDocuments(fileName: String, write: (OutputStream) -> Unit): SavedBackup {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw BackupException(BackupProblem.SAVE_FAILED, "Could not create the Auxo folder.")
        val file = File(dir, fileName)
        try {
            file.outputStream().use(write)
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("BackupStorage", "Failed and showed an error", e)
            runCatching { file.delete() }
            if (e is BackupException) throw e
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        // So the file shows up in file managers and over USB straight away.
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return SavedBackup(fileName, "$DOCUMENTS/$FOLDER", uri)
    }

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
            com.auxo.pos.core.debuglog.DebugLog.error("BackupStorage", "Failed and showed an error", e)
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
            com.auxo.pos.core.debuglog.DebugLog.error("BackupStorage", "Failed and showed an error", e)
            runCatching { file.delete() }
            if (e is BackupException) throw e
            throw BackupException(BackupProblem.SAVE_FAILED, cause = e)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return SavedBackup(fileName, "Android/data/${context.packageName}/files/$FOLDER", uri, appFolderOnly = true)
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
