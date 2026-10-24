// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.auto

import java.time.Duration

/** The rules for when a backup is due and how the backups are doing. Pure, so every case can be tested. */
object AutoBackupPolicy {

    private val DAY: Long = Duration.ofDays(1).toMillis()
    private val SIX_HOURS: Long = Duration.ofHours(6).toMillis()
    private val EARLY_ALLOWANCE: Long = Duration.ofMinutes(2).toMillis()

    /** Whether an automatic backup should be made now. A first backup is due as soon as it is switched on. */
    fun isDue(settings: AutoBackupSettings, nowMillis: Long): Boolean {
        if (!settings.isActive) return false
        val last = settings.lastSuccessMillis
        if (settings.schedule == BackupSchedule.EVERY_N_INVOICES) return last == null || settings.invoicesSinceBackup >= settings.invoiceInterval
        val interval = settings.schedule.intervalMillis ?: return false
        if (last == null) return true
        // The phone wakes the app a little off schedule, so a short wait is not missed by a minute or two.
        val early = if (interval < DAY) EARLY_ALLOWANCE else 0L
        return nowMillis - last >= interval - early
    }

    /** How often the phone is asked to wake the app: as often as the schedule needs, but never under 15 minutes nor over 6 hours. */
    fun checkEveryMinutes(schedule: BackupSchedule): Long =
        ((schedule.intervalMillis ?: SIX_HOURS) / 60_000L).coerceIn(15L, SIX_HOURS / 60_000L)

    /** A failed attempt is not retried more than once an hour, so a missing card does not keep the app busy. */
    fun mayRetry(settings: AutoBackupSettings, nowMillis: Long): Boolean {
        val failedAt = settings.lastFailureMillis ?: return true
        return nowMillis - failedAt >= RETRY_AFTER_FAILURE
    }

    fun status(settings: AutoBackupSettings, nowMillis: Long): AutoBackupStatus {
        if (!settings.isActive) return AutoBackupStatus.OFF
        val failedAt = settings.lastFailureMillis
        val succeededAt = settings.lastSuccessMillis
        if (failedAt != null && (succeededAt == null || failedAt > succeededAt)) return AutoBackupStatus.FAILED
        if (succeededAt != null && overdue(settings, succeededAt, nowMillis)) return AutoBackupStatus.OVERDUE
        return AutoBackupStatus.OK
    }

    private fun overdue(settings: AutoBackupSettings, succeededAt: Long, nowMillis: Long): Boolean {
        if (settings.schedule == BackupSchedule.EVERY_N_INVOICES) return settings.invoicesSinceBackup >= 2 * settings.invoiceInterval
        val interval = settings.schedule.intervalMillis ?: return false
        // A phone that is asleep may skip several short rounds, so it is only late after at least six hours.
        return nowMillis - succeededAt > maxOf(2 * interval, SIX_HOURS)
    }

    private val RETRY_AFTER_FAILURE: Long = Duration.ofHours(1).toMillis()
}

/** Which backup files to delete so that only the newest few remain. */
object BackupRetention {

    /** Only files the app named itself are ever considered; anything else in the folder is left alone. */
    private val OWN_FILE = Regex("""Auxo-backup-\d{4}-\d{2}-\d{2}_\d{4}(?: \(\d+\))?\.(?:zip|auxobak)""")

    fun isOwnBackup(fileName: String): Boolean = OWN_FILE.matches(fileName)

    /** The names to delete when [keep] backups should remain. The newest are kept (the date is in the name). */
    fun toDelete(fileNames: List<String>, keep: Int): List<String> {
        val own = fileNames.filter(::isOwnBackup).sortedDescending()
        return own.drop(keep.coerceAtLeast(1))
    }
}
