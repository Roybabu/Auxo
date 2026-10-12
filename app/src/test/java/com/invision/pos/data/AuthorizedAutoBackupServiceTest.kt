package com.invision.pos.data

import com.invision.pos.core.audit.AuditAction
import com.invision.pos.core.auth.FixedAuthorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionDeniedException
import com.invision.pos.core.auth.Role
import com.invision.pos.core.backup.auto.AutoBackupRunner
import com.invision.pos.core.backup.auto.AutoBackupSettings
import com.invision.pos.core.backup.auto.BackupSchedule
import com.invision.pos.data.security.AuthorizedAutoBackupService
import com.invision.pos.testutil.FakeAutoBackupStore
import com.invision.pos.testutil.FakeBackupFolder
import com.invision.pos.testutil.RecordingAudit
import com.invision.pos.testutil.RecordingScheduler
import com.invision.pos.testutil.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthorizedAutoBackupServiceTest {

    private val store = FakeAutoBackupStore()
    private val scheduler = RecordingScheduler()
    private val audit = RecordingAudit()
    private val folder = FakeBackupFolder()
    private val clock = TestClock()
    private var backups = 0
    private val job = Job()

    private val runner = AutoBackupRunner(
        store = store,
        folderFor = { folder },
        backUpTo = { storage ->
            backups++
            storage.save("Invision-backup-2026-10-12_09${"%02d".format(backups)}.zip") { it.write(1) }
        },
        clock = clock,
    )

    private fun service(role: Role, extras: Set<Permission> = emptySet()) =
        AuthorizedAutoBackupService(store, runner, scheduler, FixedAuthorizer(role, extras), audit, CoroutineScope(Dispatchers.Unconfined + job))

    private fun denied(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected the change to be refused")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.BACKUP_AND_RESTORE, e.permission)
        }
    }

    @Test
    fun anEmployee_cannotChangeAnyChoice_orBackUpNow_whateverTheyWereGiven() {
        val staff = service(Role.EMPLOYEE, Permission.entries.toSet())
        val before = store.settings.value
        denied { staff.chooseFolder("content://tree/x", "X") }
        denied { staff.setSchedule(BackupSchedule.DAILY) }
        denied { staff.setInvoiceInterval(5) }
        denied { staff.setKeep(2) }
        denied { staff.backUpNow() }
        assertEquals(before, store.settings.value)
        assertTrue(scheduler.calls.isEmpty())
        assertTrue(audit.drafts.isEmpty())
        assertEquals(0, backups)
    }

    @Test
    fun theSuperAdmin_choosesAFolderAndSchedule_andTheChoicesAreSaved() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.chooseFolder("content://tree/sd", "SD card")
        assertEquals("content://tree/sd", store.settings.value.folderUri)
        assertEquals("SD card", store.settings.value.folderName)
        assertFalse(store.settings.value.isActive) // still off until a schedule is chosen

        admin.setInvoiceInterval(50)
        admin.setKeep(10)
        admin.setSchedule(BackupSchedule.EVERY_N_INVOICES)
        assertEquals(50, store.settings.value.invoiceInterval)
        assertEquals(10, store.settings.value.keep)
        assertEquals(BackupSchedule.EVERY_N_INVOICES, store.settings.value.schedule)
        assertTrue(store.settings.value.isActive)
    }

    @Test
    fun outOfRangeNumbers_areCorrected() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.setKeep(0)
        assertEquals(AutoBackupSettings.MIN_KEEP, store.settings.value.keep)
        admin.setInvoiceInterval(1_000_000)
        assertEquals(AutoBackupSettings.MAX_INVOICE_INTERVAL, store.settings.value.invoiceInterval)
    }

    @Test
    fun theBackgroundCheck_followsWhetherBackupIsActive() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.chooseFolder("content://tree/sd", "SD card")
        admin.setSchedule(BackupSchedule.DAILY)
        admin.setSchedule(BackupSchedule.OFF)
        assertEquals(listOf(false, true, false), scheduler.calls)
    }

    @Test
    fun switchingASchedulOn_makesTheFirstBackupStraightAway() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.chooseFolder("content://tree/sd", "SD card")
        assertEquals(0, backups)
        admin.setSchedule(BackupSchedule.DAILY)
        assertEquals(1, backups)
        assertEquals(1, folder.files.size)
        // The run finishes on another thread; wait for it to record its time instead of racing it.
        kotlinx.coroutines.withTimeoutOrNull(5_000) {
            while (store.settings.value.lastSuccessMillis == null) kotlinx.coroutines.delay(10)
        }
        assertEquals(store.settings.value.toString(), clock.now, store.settings.value.lastSuccessMillis)
    }

    @Test
    fun changingFolder_clearsAnOldFailure() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        store.update { it.copy(lastFailureMillis = 5L, lastFailure = com.invision.pos.core.backup.auto.AutoBackupFailure.FOLDER_UNAVAILABLE) }
        admin.chooseFolder("content://tree/usb", "USB drive")
        assertNull(store.settings.value.lastFailure)
        assertNull(store.settings.value.lastFailureMillis)
    }

    @Test
    fun everyChange_isWrittenToTheActivityLog() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.chooseFolder("content://tree/sd", "SD card")
        admin.setKeep(5)
        assertEquals(List(2) { AuditAction.AUTO_BACKUP_SETTINGS_CHANGED }, audit.drafts.map { it.action })
        assertTrue(audit.drafts.first().summary.contains("SD card"))
    }

    @Test
    fun backUpNow_savesToTheChosenFolder() = runBlocking {
        val admin = service(Role.SUPER_ADMIN)
        admin.chooseFolder("content://tree/sd", "SD card")
        admin.backUpNow()
        assertEquals(1, folder.files.size)
    }
}
