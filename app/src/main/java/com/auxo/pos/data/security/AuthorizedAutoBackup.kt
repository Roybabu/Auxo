// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.security

import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditEntity
import com.auxo.pos.core.audit.AuditTrail
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.backup.auto.AutoBackupResult
import com.auxo.pos.core.backup.auto.AutoBackupRunner
import com.auxo.pos.core.backup.auto.AutoBackupScheduler
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupSettingsStore
import com.auxo.pos.core.backup.auto.BackupSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Choosing where and when backups happen. Everything that changes a choice is Super Admin only. */
interface AutoBackupService {
    val settings: StateFlow<AutoBackupSettings>

    /** Remembers the folder the person picked. The caller has already kept access to it. */
    suspend fun chooseFolder(folderUri: String, folderName: String)

    suspend fun setSchedule(schedule: BackupSchedule)

    suspend fun setInvoiceInterval(invoices: Int)

    suspend fun setKeep(backups: Int)

    /** Makes a backup in the chosen folder now. */
    suspend fun backUpNow(): AutoBackupResult
}

class AuthorizedAutoBackupService(
    private val store: AutoBackupSettingsStore,
    private val runner: AutoBackupRunner,
    private val scheduler: AutoBackupScheduler,
    private val auth: Authorizer,
    private val audit: AuditTrail,
    private val scope: CoroutineScope,
) : AutoBackupService {

    override val settings: StateFlow<AutoBackupSettings> get() = store.settings

    override suspend fun chooseFolder(folderUri: String, folderName: String) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        store.update {
            // A new folder starts clean: an old failure belongs to the old folder.
            it.copy(folderUri = folderUri, folderName = folderName, lastFailureMillis = null, lastFailure = null)
        }
        changed("Automatic backup folder set to $folderName")
    }

    override suspend fun setSchedule(schedule: BackupSchedule) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        store.update { it.copy(schedule = schedule, lastFailureMillis = null, lastFailure = null) }
        changed("Automatic backup schedule set to ${schedule.name.lowercase().replace('_', ' ')}")
    }

    override suspend fun setInvoiceInterval(invoices: Int) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        store.update { it.copy(invoiceInterval = invoices) }
        changed("Automatic backup after every ${store.settings.value.invoiceInterval} invoices")
    }

    override suspend fun setKeep(backups: Int) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        store.update { it.copy(keep = backups) }
        changed("Automatic backup keeps the last ${store.settings.value.keep} backups")
    }

    override suspend fun backUpNow(): AutoBackupResult {
        auth.require(Permission.BACKUP_AND_RESTORE)
        return runner.runNow()
    }

    private suspend fun changed(summary: String) {
        scheduler.sync(store.settings.value.isActive)
        audit.record(AuditDraft(AuditAction.AUTO_BACKUP_SETTINGS_CHANGED, AuditEntity.BACKUP, null, summary))
        // A newly switched-on schedule makes its first backup straight away, without making the screen wait.
        if (store.settings.value.isActive) scope.launch { runner.runIfDue() }
    }
}
