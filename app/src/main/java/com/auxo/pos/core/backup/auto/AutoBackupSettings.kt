// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.auto

/** When a backup is made without anyone asking. */
enum class BackupSchedule { OFF, DAILY, WEEKLY, EVERY_N_INVOICES }

/** Why the last automatic backup did not happen, for the warning shown to the Super Admin. */
enum class AutoBackupFailure {
    /** The chosen folder can no longer be written to (card removed, access withdrawn, account signed out). */
    FOLDER_UNAVAILABLE,

    /** The backup could not be made or written. */
    SAVE_FAILED,
}

/**
 * Everything chosen for automatic backup, plus what happened last time. The folder is one the Super Admin
 * picked with the phone's own folder picker: an SD card, a USB drive, or a Google Drive folder.
 */
data class AutoBackupSettings(
    /** The picked folder, as a document-tree address. Null until one is chosen. */
    val folderUri: String? = null,
    /** The folder's name, for showing. */
    val folderName: String = "",
    val schedule: BackupSchedule = BackupSchedule.OFF,
    /** For [BackupSchedule.EVERY_N_INVOICES]: how many new invoices trigger a backup. */
    val invoiceInterval: Int = DEFAULT_INVOICE_INTERVAL,
    /** How many automatic backups to keep in the folder; older ones are deleted. */
    val keep: Int = DEFAULT_KEEP,
    val lastSuccessMillis: Long? = null,
    val lastFailureMillis: Long? = null,
    val lastFailure: AutoBackupFailure? = null,
    /** Invoices made since the last successful automatic backup. */
    val invoicesSinceBackup: Int = 0,
) {
    /** Automatic backup will run: a schedule is chosen and there is a folder to put it in. */
    val isActive: Boolean get() = schedule != BackupSchedule.OFF && !folderUri.isNullOrEmpty()

    /** Keeps every number inside the range the screen allows, whatever was saved or typed. */
    fun sanitized(): AutoBackupSettings = copy(
        invoiceInterval = invoiceInterval.coerceIn(MIN_INVOICE_INTERVAL, MAX_INVOICE_INTERVAL),
        keep = keep.coerceIn(MIN_KEEP, MAX_KEEP),
        invoicesSinceBackup = invoicesSinceBackup.coerceAtLeast(0),
    )

    companion object {
        const val DEFAULT_INVOICE_INTERVAL = 20
        const val MIN_INVOICE_INTERVAL = 1
        const val MAX_INVOICE_INTERVAL = 1000
        const val DEFAULT_KEEP = 7
        const val MIN_KEEP = 1
        const val MAX_KEEP = 60
    }
}

/** How the backups are doing, as the Super Admin is told on Home. */
enum class AutoBackupStatus {
    /** Not switched on, so nothing to report. */
    OFF,
    OK,

    /** The last attempt failed and nothing has succeeded since. */
    FAILED,

    /** It has worked before, but a backup is long overdue. */
    OVERDUE,
}
