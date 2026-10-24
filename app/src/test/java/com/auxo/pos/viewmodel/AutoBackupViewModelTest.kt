// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupStatus
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.data.security.AutoBackupService
import com.auxo.pos.testutil.FakeAutoBackupStore
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AutoBackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeService(val store: FakeAutoBackupStore) : AutoBackupService {
        var refuse = false
        val calls = mutableListOf<String>()
        override val settings get() = store.settings

        private fun check() {
            if (refuse) throw PermissionDeniedException(Permission.BACKUP_AND_RESTORE)
        }

        override suspend fun setSchedule(schedule: BackupSchedule) {
            check(); calls += "schedule $schedule"
            store.update { it.copy(schedule = schedule) }
        }

        override suspend fun setInvoiceInterval(invoices: Int) {
            check(); calls += "interval $invoices"
            store.update { it.copy(invoiceInterval = invoices) }
        }

        override suspend fun setKeep(backups: Int) {
            check(); calls += "keep $backups"
            store.update { it.copy(keep = backups) }
        }
    }

    private val clock = TestClock()
    private val store = FakeAutoBackupStore()
    private val service = FakeService(store)
    private fun viewModel() = AutoBackupViewModel(service, clock)

    @Test
    fun startsFromTheSavedSettings() {
        store.update { it.copy(schedule = BackupSchedule.WEEKLY, invoiceInterval = 40, keep = 9) }
        val state = viewModel().uiState.value
        assertEquals(BackupSchedule.WEEKLY, state.settings.schedule)
        assertEquals("40", state.intervalText)
        assertEquals("9", state.keepText)
        assertEquals(AutoBackupStatus.OK, state.status)
    }

    @Test
    fun theStatus_followsTheSettingsAsTheyChange() {
        val viewModel = viewModel()
        assertEquals(AutoBackupStatus.OFF, viewModel.uiState.value.status)
        store.update { it.copy(schedule = BackupSchedule.DAILY, lastFailureMillis = clock.now, lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE) }
        assertEquals(AutoBackupStatus.FAILED, viewModel.uiState.value.status)
    }

    @Test
    fun choosingASchedule_savesIt() {
        val viewModel = viewModel()
        viewModel.onScheduleChange(BackupSchedule.EVERY_N_INVOICES)
        assertEquals(BackupSchedule.EVERY_N_INVOICES, viewModel.uiState.value.settings.schedule)
    }

    @Test
    fun aValidNumber_isSaved_asItIsTyped() {
        val viewModel = viewModel()
        viewModel.onIntervalChange("50")
        viewModel.onKeepChange("12")
        assertEquals(50, store.settings.value.invoiceInterval)
        assertEquals(12, store.settings.value.keep)
        assertTrue(viewModel.uiState.value.intervalValid)
        assertTrue(viewModel.uiState.value.keepValid)
    }

    @Test
    fun anUnusableNumber_isNotSaved_andTheBoxSaysSo() {
        val viewModel = viewModel()
        viewModel.onIntervalChange("")
        viewModel.onIntervalChange("0")
        viewModel.onIntervalChange("5000")
        viewModel.onKeepChange("")
        viewModel.onKeepChange("0")
        viewModel.onKeepChange("500")
        assertTrue(service.calls.none { it.startsWith("interval") || it.startsWith("keep") })
        assertEquals(AutoBackupSettings.DEFAULT_INVOICE_INTERVAL, store.settings.value.invoiceInterval)
        assertEquals(AutoBackupSettings.DEFAULT_KEEP, store.settings.value.keep)
        assertFalse(viewModel.uiState.value.intervalValid)
        assertFalse(viewModel.uiState.value.keepValid)
    }

    @Test
    fun lettersAndSymbols_areDroppedFromTheNumberBoxes() {
        val viewModel = viewModel()
        viewModel.onIntervalChange("1a2-3")
        assertEquals("123", viewModel.uiState.value.intervalText)
        assertEquals(123, store.settings.value.invoiceInterval)
    }

    @Test
    fun aRefusedChange_changesNothing_andDoesNotCrash() {
        service.refuse = true
        val viewModel = viewModel()
        val before = store.settings.value
        viewModel.onScheduleChange(BackupSchedule.DAILY)
        viewModel.onIntervalChange("30")
        viewModel.onKeepChange("3")
        assertEquals(before, store.settings.value)
    }
}