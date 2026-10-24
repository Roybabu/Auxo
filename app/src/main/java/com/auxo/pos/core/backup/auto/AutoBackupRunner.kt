// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.auto

import com.auxo.pos.core.debuglog.DebugLog
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditEntity
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.BackupStorage
import com.auxo.pos.core.backup.SavedBackup
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What an attempt to back up came to. */
sealed interface AutoBackupResult {
    /** Automatic backup is off, or nothing is due yet. */
    data object NotDue : AutoBackupResult

    /** A backup failed a little while ago; the next try waits. */
    data object WaitingToRetry : AutoBackupResult

    data class Saved(val fileName: String, val removed: Int) : AutoBackupResult

    data class Failed(val reason: AutoBackupFailure) : AutoBackupResult
}

/**
 * Makes the scheduled backups. It reuses the existing backup code through [backUpTo], which writes the same
 * backup file to whatever storage it is given, and then trims old ones. It acts for the app itself, so it works
 * whoever is signed in (or nobody); changing the settings is what needs the Super Admin.
 *
 * Only one backup runs at a time. A failure never throws to the caller: it is saved in the settings, so the
 * Super Admin sees it on Home, and a line is written to the activity log when a run of failures begins.
 */
class AutoBackupRunner(
    private val store: AutoBackupSettingsStore,
    private val folderFor: (folderUri: String) -> BackupFolder,
    private val backUpTo: suspend (BackupStorage) -> SavedBackup,
    private val clock: Clock,
    private val log: suspend (AuditDraft) -> Unit = {},
    /** True while backups must be locked but no passphrase is available: no backup is made, and the failure says why. */
    private val needsPassphrase: () -> Boolean = { false },
) {
    private val running = Mutex()

    /** Makes a backup if one is due. Safe to call as often as you like. */
    suspend fun runIfDue(): AutoBackupResult = running.withLock {
        val settings = store.settings.value
        val now = clock.millis()
        if (!AutoBackupPolicy.isDue(settings, now)) return@withLock AutoBackupResult.NotDue
        if (!AutoBackupPolicy.mayRetry(settings, now)) return@withLock AutoBackupResult.WaitingToRetry
        backUp(settings)
    }

    /** Makes a backup now, whatever the schedule says, if a folder has been chosen. */
    suspend fun runNow(): AutoBackupResult = running.withLock {
        val settings = store.settings.value
        if (settings.folderUri.isNullOrEmpty()) return@withLock AutoBackupResult.NotDue
        backUp(settings)
    }

    /** Counts a finished sale towards "every N invoices". Does nothing when automatic backup is off. */
    fun onInvoiceRecorded() {
        store.update { if (it.isActive) it.copy(invoicesSinceBackup = it.invoicesSinceBackup + 1) else it }
    }

    /** True when counting that sale made a backup due. */
    fun isDueNow(): Boolean = AutoBackupPolicy.isDue(store.settings.value, clock.millis())

    private suspend fun backUp(settings: AutoBackupSettings): AutoBackupResult {
        val uri = settings.folderUri ?: return AutoBackupResult.NotDue
        val countedBefore = settings.invoicesSinceBackup
        return try {
            if (needsPassphrase()) throw BackupException(BackupProblem.PASSPHRASE_UNAVAILABLE)
            val storage = FolderBackupStorage(folderFor(uri))
            val saved = backUpTo(storage)
            // A failed clean-up must not turn a good backup into a failure.
            val removed = runCatching { storage.prune(settings.keep) }.getOrDefault(0)
            store.update {
                it.copy(
                    lastSuccessMillis = clock.millis(),
                    lastFailureMillis = null,
                    lastFailure = null,
                    // Sales made while the backup was running still count towards the next one.
                    invoicesSinceBackup = (it.invoicesSinceBackup - countedBefore).coerceAtLeast(0),
                )
            }
            log(AuditDraft(AuditAction.AUTO_BACKUP_SAVED, AuditEntity.BACKUP, null, "Automatic backup saved as ${saved.fileName}"))
            DebugLog.event("Backup", "Automatic backup saved (${saved.fileName}), $removed old removed")
            AutoBackupResult.Saved(saved.fileName, removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("AutoBackupRunner", "Failed and showed an error", e)
            val reason = when {
                e is FolderUnavailableException -> AutoBackupFailure.FOLDER_UNAVAILABLE
                (e as? BackupException)?.problem == BackupProblem.PASSPHRASE_UNAVAILABLE -> AutoBackupFailure.PASSPHRASE_REQUIRED
                else -> AutoBackupFailure.SAVE_FAILED
            }
            val alreadyFailing = AutoBackupPolicy.status(settings, clock.millis()) == AutoBackupStatus.FAILED
            store.update { it.copy(lastFailureMillis = clock.millis(), lastFailure = reason) }
            if (!alreadyFailing) {
                runCatching {
                    log(AuditDraft(AuditAction.AUTO_BACKUP_FAILED, AuditEntity.BACKUP, null, "Automatic backup failed: " + reason.describe()))
                }
            }
            DebugLog.warn("Backup", "Automatic backup failed: $reason")
            AutoBackupResult.Failed(reason)
        }
    }

    private fun AutoBackupFailure.describe(): String = when (this) {
        AutoBackupFailure.FOLDER_UNAVAILABLE -> "the backup folder is not available"
        AutoBackupFailure.SAVE_FAILED -> "the backup could not be saved"
        AutoBackupFailure.PASSPHRASE_REQUIRED -> "no backup passphrase is set"
    }
}
