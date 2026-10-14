package com.auxo.pos.core.backup.auto

import java.time.Duration

/** The rules for when a backup is due and how the backups are doing. Pure, so every case can be tested. */
object AutoBackupPolicy {

    private val DAY: Long = Duration.ofDays(1).toMillis()
    private val WEEK: Long = Duration.ofDays(7).toMillis()

    /** Whether an automatic backup should be made now. A first backup is due as soon as it is switched on. */
    fun isDue(settings: AutoBackupSettings, nowMillis: Long): Boolean {
        if (!settings.isActive) return false
        val last = settings.lastSuccessMillis
        return when (settings.schedule) {
            BackupSchedule.OFF -> false
            BackupSchedule.DAILY -> last == null || nowMillis - last >= DAY
            BackupSchedule.WEEKLY -> last == null || nowMillis - last >= WEEK
            BackupSchedule.EVERY_N_INVOICES -> last == null || settings.invoicesSinceBackup >= settings.invoiceInterval
        }
    }

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

    private fun overdue(settings: AutoBackupSettings, succeededAt: Long, nowMillis: Long): Boolean = when (settings.schedule) {
        BackupSchedule.OFF -> false
        BackupSchedule.DAILY -> nowMillis - succeededAt > 2 * DAY
        BackupSchedule.WEEKLY -> nowMillis - succeededAt > 2 * WEEK
        BackupSchedule.EVERY_N_INVOICES -> settings.invoicesSinceBackup >= 2 * settings.invoiceInterval
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
