// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupPolicy
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupStatus
import com.auxo.pos.core.backup.auto.BackupRetention
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.core.backup.auto.SharedPrefsAutoBackupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupPolicyTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000 * day

    private fun active(schedule: BackupSchedule, lastSuccess: Long? = null, since: Int = 0, interval: Int = 20) = AutoBackupSettings(
        folderUri = "content://tree/backups",
        schedule = schedule,
        invoiceInterval = interval,
        lastSuccessMillis = lastSuccess,
        invoicesSinceBackup = since,
    )

    // When a backup is due

    @Test
    fun nothingIsDue_whenOff_orWithoutAFolder() {
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.OFF), now))
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.DAILY).copy(folderUri = null), now))
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.DAILY).copy(folderUri = ""), now))
    }

    @Test
    fun theFirstBackup_isDueAtOnce_forEveryScheduleThatIsOn() {
        for (schedule in listOf(BackupSchedule.DAILY, BackupSchedule.WEEKLY, BackupSchedule.EVERY_N_INVOICES)) {
            assertTrue(schedule.name, AutoBackupPolicy.isDue(active(schedule), now))
        }
    }

    @Test
    fun daily_isDueAfter24Hours_notBefore() {
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.DAILY, lastSuccess = now - 23 * hour), now))
        assertTrue(AutoBackupPolicy.isDue(active(BackupSchedule.DAILY, lastSuccess = now - 24 * hour), now))
    }

    @Test
    fun weekly_isDueAfterSevenDays_notBefore() {
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.WEEKLY, lastSuccess = now - 6 * day), now))
        assertTrue(AutoBackupPolicy.isDue(active(BackupSchedule.WEEKLY, lastSuccess = now - 7 * day), now))
    }

    @Test
    fun everyNInvoices_isDueOnceNInvoicesHaveBeenMade_whateverTheTime() {
        assertFalse(AutoBackupPolicy.isDue(active(BackupSchedule.EVERY_N_INVOICES, lastSuccess = now - 30 * day, since = 19, interval = 20), now))
        assertTrue(AutoBackupPolicy.isDue(active(BackupSchedule.EVERY_N_INVOICES, lastSuccess = now - hour, since = 20, interval = 20), now))
    }

    @Test
    fun aFailedAttempt_isNotRetriedForAnHour() {
        val failed = active(BackupSchedule.DAILY).copy(lastFailureMillis = now - 30 * 60_000L, lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE)
        assertFalse(AutoBackupPolicy.mayRetry(failed, now))
        assertTrue(AutoBackupPolicy.mayRetry(failed, now + hour))
        assertTrue(AutoBackupPolicy.mayRetry(active(BackupSchedule.DAILY), now))
    }

    // How it is doing

    @Test
    fun status_isOff_untilAScheduleAndAFolderAreChosen() {
        assertEquals(AutoBackupStatus.OFF, AutoBackupPolicy.status(AutoBackupSettings(), now))
        assertEquals(AutoBackupStatus.OFF, AutoBackupPolicy.status(active(BackupSchedule.OFF), now))
    }

    @Test
    fun status_isFailed_whenTheLastAttemptFailedAndNothingWorkedSince() {
        val failed = active(BackupSchedule.DAILY, lastSuccess = now - 2 * hour).copy(lastFailureMillis = now - hour, lastFailure = AutoBackupFailure.SAVE_FAILED)
        assertEquals(AutoBackupStatus.FAILED, AutoBackupPolicy.status(failed, now))
        val neverWorked = active(BackupSchedule.DAILY).copy(lastFailureMillis = now - hour, lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE)
        assertEquals(AutoBackupStatus.FAILED, AutoBackupPolicy.status(neverWorked, now))
        val recovered = failed.copy(lastSuccessMillis = now - 10 * 60_000L)
        assertEquals(AutoBackupStatus.OK, AutoBackupPolicy.status(recovered, now))
    }

    @Test
    fun status_isOverdue_afterTwiceTheInterval() {
        assertEquals(AutoBackupStatus.OK, AutoBackupPolicy.status(active(BackupSchedule.DAILY, lastSuccess = now - 2 * day), now))
        assertEquals(AutoBackupStatus.OVERDUE, AutoBackupPolicy.status(active(BackupSchedule.DAILY, lastSuccess = now - 3 * day), now))
        assertEquals(AutoBackupStatus.OK, AutoBackupPolicy.status(active(BackupSchedule.WEEKLY, lastSuccess = now - 13 * day), now))
        assertEquals(AutoBackupStatus.OVERDUE, AutoBackupPolicy.status(active(BackupSchedule.WEEKLY, lastSuccess = now - 15 * day), now))
        assertEquals(AutoBackupStatus.OVERDUE, AutoBackupPolicy.status(active(BackupSchedule.EVERY_N_INVOICES, lastSuccess = now, since = 40, interval = 20), now))
        assertEquals(AutoBackupStatus.OK, AutoBackupPolicy.status(active(BackupSchedule.EVERY_N_INVOICES, lastSuccess = now, since = 39, interval = 20), now))
    }

    // Keeping the last N

    private fun name(stamp: String) = "Auxo-backup-$stamp.zip"

    @Test
    fun retention_keepsTheNewestN_andDeletesTheRest() {
        val files = listOf("2026-10-01_0900", "2026-10-03_0900", "2026-10-02_0900", "2026-10-04_0900").map(::name)
        assertEquals(listOf(name("2026-10-02_0900"), name("2026-10-01_0900")), BackupRetention.toDelete(files, keep = 2))
        assertTrue(BackupRetention.toDelete(files, keep = 4).isEmpty())
        assertTrue(BackupRetention.toDelete(files, keep = 10).isEmpty())
    }

    @Test
    fun retention_alwaysKeepsAtLeastOne() {
        val files = listOf("2026-10-01_0900", "2026-10-02_0900").map(::name)
        assertEquals(listOf(name("2026-10-01_0900")), BackupRetention.toDelete(files, keep = 0))
    }

    @Test
    fun retention_neverTouchesFilesItDidNotName() {
        val own = listOf("2026-10-01_0900", "2026-10-02_0900", "2026-10-03_0900").map(::name)
        val others = listOf("notes.txt", "Auxo-backup-final.zip", "Auxo-backup-2026-10-01_0900.zip.bak", "photo.jpg", "Auxo-backup-2026-10-01_0900 (1).zip.tmp")
        val doomed = BackupRetention.toDelete(own + others, keep = 1)
        assertEquals(listOf(name("2026-10-02_0900"), name("2026-10-01_0900")), doomed)
        assertTrue(others.none { it in doomed })
    }

    @Test
    fun retention_recognisesTheCopyNumberTheStorageAddsWhenTwoBackupsShareAMinute() {
        assertTrue(BackupRetention.isOwnBackup("Auxo-backup-2026-10-01_0900 (1).zip"))
        assertFalse(BackupRetention.isOwnBackup("Auxo-backup-2026-10-01_0900 (x).zip"))
    }

    // Settings

    @Test
    fun settings_stayWithinTheAllowedRange() {
        val wild = AutoBackupSettings(invoiceInterval = 0, keep = 500, invoicesSinceBackup = -3).sanitized()
        assertEquals(AutoBackupSettings.MIN_INVOICE_INTERVAL, wild.invoiceInterval)
        assertEquals(AutoBackupSettings.MAX_KEEP, wild.keep)
        assertEquals(0, wild.invoicesSinceBackup)
        assertEquals(AutoBackupSettings.MAX_INVOICE_INTERVAL, AutoBackupSettings(invoiceInterval = 99_999).sanitized().invoiceInterval)
        assertEquals(AutoBackupSettings.MIN_KEEP, AutoBackupSettings(keep = -1).sanitized().keep)
    }

    @Test
    fun settings_surviveARestart_andStartSwitchedOff() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("auto_backup", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val first = SharedPrefsAutoBackupStore(context)
        assertEquals(BackupSchedule.OFF, first.settings.value.schedule)
        assertNull(first.settings.value.folderUri)
        assertFalse(first.settings.value.isActive)

        first.update {
            it.copy(
                folderUri = "content://tree/sd",
                folderName = "Backups",
                schedule = BackupSchedule.EVERY_N_INVOICES,
                invoiceInterval = 50,
                keep = 10,
                lastSuccessMillis = 123L,
                lastFailureMillis = 456L,
                lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE,
                invoicesSinceBackup = 7,
            )
        }
        val restarted = SharedPrefsAutoBackupStore(context).settings.value
        assertEquals("content://tree/sd", restarted.folderUri)
        assertEquals("Backups", restarted.folderName)
        assertEquals(BackupSchedule.EVERY_N_INVOICES, restarted.schedule)
        assertEquals(50, restarted.invoiceInterval)
        assertEquals(10, restarted.keep)
        assertEquals(123L, restarted.lastSuccessMillis)
        assertEquals(456L, restarted.lastFailureMillis)
        assertEquals(AutoBackupFailure.FOLDER_UNAVAILABLE, restarted.lastFailure)
        assertEquals(7, restarted.invoicesSinceBackup)
        assertTrue(restarted.isActive)
    }

    @Test
    fun settings_outOfRangeValuesAreCorrectedWhenSaved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("auto_backup", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val store = SharedPrefsAutoBackupStore(context)
        store.update { it.copy(keep = 0, invoiceInterval = 5_000) }
        assertEquals(AutoBackupSettings.MIN_KEEP, SharedPrefsAutoBackupStore(context).settings.value.keep)
        assertEquals(AutoBackupSettings.MAX_INVOICE_INTERVAL, SharedPrefsAutoBackupStore(context).settings.value.invoiceInterval)
    }
}
