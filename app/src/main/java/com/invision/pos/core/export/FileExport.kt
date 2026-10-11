package com.invision.pos.core.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A file to hand to another app: its name, contents and type. */
data class ExportFile(
    val fileName: String,
    val content: String,
    val mimeType: String = CSV_MIME_TYPE,
) {
    companion object {
        const val CSV_MIME_TYPE = "text/csv"
    }
}

/**
 * Writes exported reports and statements into the app's cache and opens the
 * share sheet, so they can be saved to Drive, mailed or opened in a spreadsheet.
 */
object FileExport {
    private const val EXPORT_DIR = "exports"
    private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    /** Keeps letters, digits, dot, dash and underscore, so the name is safe everywhere. */
    fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "export" }

    /**
     * Writes [export] as UTF-8. CSV files start with a byte order mark so Excel
     * reads the rupee sign and other non-ASCII text correctly.
     */
    fun write(context: Context, export: ExportFile): File {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(dir, safeFileName(export.fileName))
        val bom = if (export.mimeType == ExportFile.CSV_MIME_TYPE) "\uFEFF" else ""
        file.writeText(bom + export.content, Charsets.UTF_8)
        return file
    }

    fun shareIntent(context: Context, file: File, mimeType: String, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle)
    }

    /** Writes the file off the main thread and opens the share sheet. Returns false if that failed. */
    suspend fun share(context: Context, export: ExportFile, chooserTitle: String): Boolean {
        val file = runCatching { withContext(Dispatchers.IO) { write(context, export) } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
        return try {
            context.startActivity(shareIntent(context, file, export.mimeType, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Opens the share sheet with plain text, such as a purchase order for WhatsApp or SMS. */
    fun shareText(context: Context, subject: String, text: String, chooserTitle: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(send, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
