// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import android.content.Context
import android.net.Uri
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.SharedBackupContainer
import com.auxo.pos.core.backup.BackupManifest
import com.auxo.pos.core.backup.BackupProblem
import java.io.InputStream

/**
 * Recognises shared backup files and opens them. The restore and the lists of backups both use this, so a shared file
 * is treated the same way wherever it is found.
 */
object SharedBackupFile {
    /** The name a shared backup is saved under. */
    const val EXTENSION = ".auxoshared"

    private val SIGNATURE: ByteArray = SharedBackupContainer.MAGIC.toByteArray(Charsets.US_ASCII)

    fun isSharedName(fileName: String): Boolean = fileName.lowercase().endsWith(EXTENSION)

    /** Whether the first [length] bytes of [head] are the shared format's signature. */
    fun startsWithSignature(head: ByteArray, length: Int = head.size): Boolean =
        length >= SIGNATURE.size && SIGNATURE.indices.all { head[it] == SIGNATURE[it] }

    /** Whether the file at [uri] is a shared backup, judged only by its first bytes. */
    fun isSharedFile(context: Context, uri: Uri): Boolean {
        val head = ByteArray(SIGNATURE.size)
        val filled = runCatching { context.contentResolver.openInputStream(uri)?.use { readHead(it, head) } }.getOrNull() ?: return false
        return startsWithSignature(head, filled)
    }

    /** The bytes of the file at [uri]; a file that cannot be opened is a [BackupException]. */
    fun readBytes(context: Context, uri: Uri): ByteArray =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: throw BackupException(BackupProblem.UNREADABLE)

    /** The checked document in [bytes], or a [BackupException] saying why it does not open. */
    fun open(bytes: ByteArray, passphrase: String): SharedBackupDocument = try {
        SharedBackupReader.read(bytes, passphrase)
    } catch (e: IllegalArgumentException) {
        val problem = if (e.message?.startsWith("Wrong passphrase") == true) BackupProblem.WRONG_PASSPHRASE else BackupProblem.CORRUPT
        throw BackupException(problem, message = e.message, cause = e)
    }

    /** The restore plan for [bytes]; a file whose contents do not check out is a [BackupException]. */
    fun plan(bytes: ByteArray, passphrase: String): RestorePlan = try {
        SharedRestorePlanner.plan(open(bytes, passphrase))
    } catch (e: RestoreProblem) {
        throw BackupException(BackupProblem.CORRUPT, message = e.message, cause = e)
    }

    /** What the backup lists show about a shared file: its shop, when it was made and how much it holds. */
    fun manifestOf(document: SharedBackupDocument): BackupManifest = BackupManifest(
        createdAtMillis = document.createdAtMillis,
        databaseVersion = 0,
        appVersion = document.appVersion,
        shopName = document.shopName,
        products = document.tables["products"]?.size,
        customers = document.tables["customers"]?.size,
        invoices = document.tables["invoices"]?.size,
    )

    private fun readHead(input: InputStream, head: ByteArray): Int {
        var filled = 0
        while (filled < head.size) {
            val read = input.read(head, filled, head.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }
}
