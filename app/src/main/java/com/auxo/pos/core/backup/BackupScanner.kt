// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.InputStream

/** One backup file found in a folder the person chose. */
data class FoundBackup(
    val uri: Uri,
    val fileName: String,
    val modifiedMillis: Long,
    val sizeBytes: Long,
    /** Locked with a passphrase, so only the file itself can be described until the passphrase is typed. */
    val locked: Boolean,
    /** What the file says about itself; null for a locked file. */
    val manifest: BackupManifest?,
) {
    /** When the backup was made: the date it records, or the file's date for a locked file or an old backup. */
    val takenAtMillis: Long get() = manifest?.createdAtMillis?.takeIf { it > 0 } ?: modifiedMillis
}

/** What a look at the start of a file found. */
sealed interface Inspection {
    data object NotABackup : Inspection
    data object Locked : Inspection
    data class Plain(val manifest: BackupManifest) : Inspection
}

/** Looks through a folder (and the folders directly inside it) for Auxo backups, without unpacking any of them. */
class BackupScanner(private val context: Context) {

    fun scan(tree: Uri): List<FoundBackup> {
        val found = mutableListOf<FoundBackup>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), depth = 0, found)
        return found.sortedByDescending { it.takenAtMillis }
    }

    /** The manifest of a locked backup, read with its [passphrase]; throws [BackupProblem.WRONG_PASSPHRASE] if it does not open. */
    fun peekLocked(uri: Uri, passphrase: String): BackupManifest {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: throw BackupException(BackupProblem.UNREADABLE)
        return input.use { BackupArchive.readManifest(BackupCrypto.decrypt(BufferedInputStream(it), passphrase)) }
    }

    private fun walk(tree: Uri, parentId: String, depth: Int, into: MutableList<FoundBackup>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val rows = mutableListOf<Array<Any?>>()
        runCatching {
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) rows += arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
            }
        }
        for (row in rows) {
            if (into.size >= MAX_FOUND) return
            val id = row[0] as String
            val name = row[1] as? String ?: continue
            val mime = row[2] as? String
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (depth < MAX_DEPTH) walk(tree, id, depth + 1, into)
                continue
            }
            if (!isCandidate(name)) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            val inspection = runCatching {
                context.contentResolver.openInputStream(uri)?.use { inspect(it) }
            }.getOrNull() ?: continue
            when (inspection) {
                Inspection.NotABackup -> Unit
                Inspection.Locked -> into += FoundBackup(uri, name, row[3] as Long, row[4] as Long, locked = true, manifest = null)
                is Inspection.Plain -> into += FoundBackup(uri, name, row[3] as Long, row[4] as Long, locked = false, manifest = inspection.manifest)
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 2
        private const val MAX_FOUND = 200

        /** Only files named like a backup are opened at all. */
        fun isCandidate(fileName: String): Boolean {
            val lower = fileName.lowercase()
            return lower.endsWith(".zip") || lower.endsWith(BackupCrypto.FILE_EXTENSION)
        }

        /** Says what [input] is: a locked backup, a plain one (with its manifest), or something else. */
        fun inspect(input: InputStream): Inspection {
            val buffered = if (input.markSupported()) input else BufferedInputStream(input)
            val head = ByteArray(BackupCrypto.SIGNATURE_BYTES)
            buffered.mark(head.size)
            var filled = 0
            while (filled < head.size) {
                val read = buffered.read(head, filled, head.size - filled)
                if (read < 0) break
                filled += read
            }
            buffered.reset()
            if (BackupCrypto.looksEncrypted(head, filled)) return Inspection.Locked
            return try {
                Inspection.Plain(BackupArchive.readManifest(buffered))
            } catch (e: BackupException) {
                // A backup from a newer app is still a backup, but this version cannot restore it, so it is not listed.
                Inspection.NotABackup
            }
        }
    }
}
