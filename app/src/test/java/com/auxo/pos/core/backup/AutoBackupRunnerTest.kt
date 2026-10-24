// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupResult
import com.auxo.pos.core.backup.auto.AutoBackupRunner
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.core.backup.auto.FolderUnavailableException
import com.auxo.pos.testutil.FakeAutoBackupStore
import com.auxo.pos.testutil.FakeBackupFolder
import com.auxo.pos.testutil.RecordingAudit
import com.auxo.pos.testutil.TestClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupRunnerTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val clock = TestClock()
    private val folder = FakeBackupFolder()
    private val audit = RecordingAudit()
    private var backups = 0

    private fun settings(schedule: BackupSchedule = BackupSchedule.DAILY, keep: Int = 3) =
        AutoBackupSettings(schedule = schedule, keep = keep)

    /** A runner whose "backup" writes a tiny file named by the clock, like the real one. */
    private fun newRunner(
        store: FakeAutoBackupStore,
        whileBackingUp: suspend () -> Unit = {},
        failWith: Exception? = null,
        needsPassphrase: Boolean = false,
    ) = AutoBackupRunner(
        store = store,
        folder = { folder },
        backUpTo = { storage ->
            whileBackingUp()
            failWith?.let { throw it }
            backups++
            storage.save("Auxo-backup-${stamp()}.zip") { it.write(backups) }
        },
        clock = clock,
        log = { audit.record(it) },
        needsPassphrase = { needsPassphrase },
    )

    private fun stamp(): String {
        val t = java.time.Instant.ofEpochMilli(clock.now).atZone(java.time.ZoneOffset.UTC)
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(t)
    }

    @Test
    fun whenOff_nothingIsDone() = runBlocking {
        val off = FakeAutoBackupStore(settings(BackupSchedule.OFF))
        assertEquals(AutoBackupResult.NotDue, newRunner(off).runIfDue())
        assertEquals(0, backups)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun theFirstBackup_isMadeAtOnce_andRecorded() = runBlocking {
        val store = FakeAutoBackupStore(settings().copy(invoicesSinceBackup = 4))
        val result = newRunner(store).runIfDue() as AutoBackupResult.Saved
        assertEquals(1, folder.files.size)
        assertEquals(clock.now, store.settings.value.lastSuccessMillis)
        assertNull(store.settings.value.lastFailure)
        assertEquals(0, store.settings.value.invoicesSinceBackup)
        assertEquals(listOf(AuditAction.AUTO_BACKUP_SAVED), audit.drafts.map { it.action })
        assertTrue(result.fileName in folder.files)
    }

    @Test
    fun aDailyBackup_isNotRepeatedBeforeADayHasPassed() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val runner = newRunner(store)
        runner.runIfDue()
        clock.advance(23 * hour)
        assertEquals(AutoBackupResult.NotDue, runner.runIfDue())
        clock.advance(hour)
        assertTrue(runner.runIfDue() is AutoBackupResult.Saved)
        assertEquals(2, backups)
    }

    @Test
    fun onlyTheLastNBackupsAreKept() = runBlocking {
        val store = FakeAutoBackupStore(settings(keep = 3))
        val runner = newRunner(store)
        repeat(5) {
            runner.runIfDue()
            clock.advance(day)
        }
        assertEquals(5, backups)
        assertEquals(3, folder.files.size)
        val names = folder.files.keys.sorted()
        assertTrue(names.last().contains(stampDaysAgo(1)))
    }

    private fun stampDaysAgo(days: Int): String {
        val t = java.time.Instant.ofEpochMilli(clock.now - days * day).atZone(java.time.ZoneOffset.UTC)
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(t)
    }

    @Test
    fun otherFilesInTheFolderSurviveTheTrimming() = runBlocking {
        folder.files["my-holiday.zip"] = ByteArray(1)
        val store = FakeAutoBackupStore(settings(keep = 1))
        val runner = newRunner(store)
        repeat(3) {
            runner.runIfDue()
            clock.advance(day)
        }
        assertEquals(setOf("my-holiday.zip"), folder.files.keys.filterNot { it.startsWith("Auxo-backup-") }.toSet())
        assertEquals(1, folder.files.keys.count { it.startsWith("Auxo-backup-") })
    }

    @Test
    fun aCleanUpThatFails_doesNotTurnAGoodBackupIntoAFailure() = runBlocking {
        val store = FakeAutoBackupStore(settings(keep = 1))
        val runner = newRunner(store)
        runner.runIfDue()
        clock.advance(day)
        folder.deleteThrows = true
        assertTrue(runner.runIfDue() is AutoBackupResult.Saved)
        assertNull(store.settings.value.lastFailure)
        assertEquals(clock.now, store.settings.value.lastSuccessMillis)
    }

    // Failures

    @Test
    fun aMissingFolder_isRecorded_logged_andShownAsFailed() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val result = newRunner(store, failWith = FolderUnavailableException()).runIfDue()
        assertEquals(AutoBackupResult.Failed(AutoBackupFailure.FOLDER_UNAVAILABLE), result)
        assertEquals(AutoBackupFailure.FOLDER_UNAVAILABLE, store.settings.value.lastFailure)
        assertEquals(clock.now, store.settings.value.lastFailureMillis)
        assertNull(store.settings.value.lastSuccessMillis)
        assertEquals(listOf(AuditAction.AUTO_BACKUP_FAILED), audit.drafts.map { it.action })
    }

    @Test
    fun withoutAPassphrase_noBackupIsMade_andTheFailureSaysWhy() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val result = newRunner(store, needsPassphrase = true).runIfDue()
        assertEquals(AutoBackupResult.Failed(AutoBackupFailure.PASSPHRASE_REQUIRED), result)
        assertEquals(AutoBackupFailure.PASSPHRASE_REQUIRED, store.settings.value.lastFailure)
        assertEquals(0, backups)
    }

    @Test
    fun anyOtherProblem_isRecordedAsASaveFailure() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val result = newRunner(store, failWith = BackupException(BackupProblem.SAVE_FAILED)).runIfDue()
        assertEquals(AutoBackupResult.Failed(AutoBackupFailure.SAVE_FAILED), result)
        val unexpected = FakeAutoBackupStore(settings())
        assertEquals(AutoBackupResult.Failed(AutoBackupFailure.SAVE_FAILED), newRunner(unexpected, failWith = IllegalStateException("boom")).runIfDue())
    }

    @Test
    fun aFailedBackup_isNotRetriedForAnHour_thenTriedAgain() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        var failing = true
        val runner = AutoBackupRunner(
            store = store,
            folder = { folder },
            backUpTo = { storage ->
                if (failing) throw FolderUnavailableException()
                storage.save("Auxo-backup-${stamp()}.zip") { it.write(1) }
            },
            clock = clock,
            log = { audit.record(it) },
        )
        assertTrue(runner.runIfDue() is AutoBackupResult.Failed)
        clock.advance(30 * 60_000L)
        assertEquals(AutoBackupResult.WaitingToRetry, runner.runIfDue())
        clock.advance(31 * 60_000L)
        assertTrue(runner.runIfDue() is AutoBackupResult.Failed)

        failing = false
        clock.advance(hour)
        assertTrue(runner.runIfDue() is AutoBackupResult.Saved)
        assertNull(store.settings.value.lastFailure)
        assertNull(store.settings.value.lastFailureMillis)
    }

    @Test
    fun aRunOfFailures_isLoggedOnce_notEveryTime() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val runner = newRunner(store, failWith = FolderUnavailableException())
        repeat(4) {
            runner.runIfDue()
            clock.advance(2 * hour)
        }
        assertEquals(1, audit.drafts.count { it.action == AuditAction.AUTO_BACKUP_FAILED })
    }

    // By invoice count

    @Test
    fun everyNInvoices_backsUpOnceNSalesHaveBeenCounted() = runBlocking {
        val store = FakeAutoBackupStore(settings(BackupSchedule.EVERY_N_INVOICES).copy(invoiceInterval = 3))
        val runner = newRunner(store)
        runner.runIfDue() // the first backup
        repeat(2) { runner.onInvoiceRecorded() }
        assertEquals(false, runner.isDueNow())
        assertEquals(AutoBackupResult.NotDue, runner.runIfDue())
        runner.onInvoiceRecorded()
        assertEquals(true, runner.isDueNow())
        assertTrue(runner.runIfDue() is AutoBackupResult.Saved)
        assertEquals(0, store.settings.value.invoicesSinceBackup)
    }

    @Test
    fun salesAreOnlyCounted_whileAutomaticBackupIsOn() {
        val off = FakeAutoBackupStore(settings(BackupSchedule.OFF))
        newRunner(off).onInvoiceRecorded()
        assertEquals(0, off.settings.value.invoicesSinceBackup)
        val on = FakeAutoBackupStore(settings())
        newRunner(on).onInvoiceRecorded()
        assertEquals(1, on.settings.value.invoicesSinceBackup)
    }

    @Test
    fun salesMadeWhileABackupRuns_stillCountTowardsTheNextOne() = runBlocking {
        val store = FakeAutoBackupStore(settings(BackupSchedule.EVERY_N_INVOICES).copy(invoiceInterval = 5, invoicesSinceBackup = 5))
        lateinit var subject: AutoBackupRunner
        subject = newRunner(store, whileBackingUp = { subject.onInvoiceRecorded() })
        subject.runIfDue()
        assertEquals(1, store.settings.value.invoicesSinceBackup)
    }

    // Manual and concurrent

    @Test
    fun twoCheckersAtOnce_makeOnlyOneBackup() = runBlocking {
        val store = FakeAutoBackupStore(settings())
        val runner = newRunner(store, whileBackingUp = { delay(50) })
        val results = listOf(async { runner.runIfDue() }, async { runner.runIfDue() }, async { runner.runIfDue() }).awaitAll()
        assertEquals(1, results.count { it is AutoBackupResult.Saved })
        assertEquals(1, backups)
    }
}
