// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.SavedBackup
import com.auxo.pos.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeService : BackupService {
        var last: Long? = null
        var failBackup: BackupException? = null
        var failRestore: BackupException? = null
        var restoredFrom: Uri? = null
        var restoredWith: String? = null
        var storedPassphrase: String? = null
        /** The passphrase the pretend locked file needs; null means the file is not locked. */
        var lockedWith: String? = null

        override suspend fun backUp(): SavedBackup {
            failBackup?.let { throw it }
            last = 1_000L
            return SavedBackup("b.zip", "Documents/Auxo", Uri.parse("content://test/b.zip"))
        }

        override suspend fun restore(source: Uri, passphrase: String?) {
            failRestore?.let { throw it }
            lockedWith?.let { needed ->
                if (passphrase == null) throw BackupException(BackupProblem.PASSPHRASE_NEEDED)
                if (passphrase != needed) throw BackupException(BackupProblem.WRONG_PASSPHRASE)
            }
            restoredFrom = source
            restoredWith = passphrase
        }

        override fun isProtected() = storedPassphrase != null

        override fun setPassphrase(passphrase: String) {
            storedPassphrase = passphrase
        }

        override fun clearPassphrase() {
            storedPassphrase = null
        }

        override fun lastBackupMillis() = last
    }

    private val service = FakeService()
    private val file = Uri.parse("content://test/old.zip")

    @Test
    fun backUp_showsWhereTheFileWent_andWhen() {
        val viewModel = BackupViewModel(service)
        assertNull(viewModel.uiState.value.lastBackupMillis)

        viewModel.onBackUp()

        val state = viewModel.uiState.value
        assertFalse(state.isWorking)
        assertEquals("b.zip", state.saved?.fileName)
        assertEquals(1_000L, state.lastBackupMillis)
        assertNull(state.problem)
    }

    @Test
    fun aFailedBackup_reportsWhy() {
        service.failBackup = BackupException(BackupProblem.SAVE_FAILED)
        val viewModel = BackupViewModel(service)
        viewModel.onBackUp()
        assertEquals(BackupProblem.SAVE_FAILED, viewModel.uiState.value.problem)
        assertNull(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun pickingAFile_changesNothing_untilConfirmed() {
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        assertEquals(file, viewModel.uiState.value.pendingRestore)
        assertNull(service.restoredFrom)

        viewModel.onRestoreCancelled()
        assertNull(viewModel.uiState.value.pendingRestore)
        assertNull(service.restoredFrom)
    }

    @Test
    fun confirming_restores_andAsksForARestart() {
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        viewModel.onRestoreConfirmed()

        assertEquals(file, service.restoredFrom)
        assertTrue(viewModel.uiState.value.restored)
        assertNull(viewModel.uiState.value.pendingRestore)
    }

    @Test
    fun aBadBackup_isReported_andNothingRestarts() {
        service.failRestore = BackupException(BackupProblem.NOT_A_BACKUP)
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        viewModel.onRestoreConfirmed()

        assertEquals(BackupProblem.NOT_A_BACKUP, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.restored)

        viewModel.onProblemDismissed()
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun confirmingWithNoFile_doesNothing() {
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreConfirmed()
        assertNull(service.restoredFrom)
        assertFalse(viewModel.uiState.value.restored)
    }

    @Test
    fun aLockedBackup_asksForItsPassphrase_thenRestores() {
        service.lockedWith = "correct horse"
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        viewModel.onRestoreConfirmed()

        assertEquals(file, viewModel.uiState.value.unlocking)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.restored)

        viewModel.onPassphraseForRestore("correct horse")
        assertNull(viewModel.uiState.value.unlocking)
        assertTrue(viewModel.uiState.value.restored)
        assertEquals("correct horse", service.restoredWith)
    }

    @Test
    fun aWrongPassphrase_saysSo_andAsksAgain() {
        service.lockedWith = "correct horse"
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        viewModel.onRestoreConfirmed()
        viewModel.onPassphraseForRestore("wrong one")

        assertEquals(BackupProblem.WRONG_PASSPHRASE, viewModel.uiState.value.problem)
        assertEquals(file, viewModel.uiState.value.unlocking)
        assertFalse(viewModel.uiState.value.restored)
        assertNull(service.restoredFrom)
    }

    @Test
    fun cancellingThePassphraseBox_restoresNothing() {
        service.lockedWith = "correct horse"
        val viewModel = BackupViewModel(service)
        viewModel.onRestoreFilePicked(file)
        viewModel.onRestoreConfirmed()
        viewModel.onUnlockCancelled()

        assertNull(viewModel.uiState.value.unlocking)
        assertNull(service.restoredFrom)
    }

    @Test
    fun settingAndClearingThePassphrase_switchesLockingOnAndOff() {
        val viewModel = BackupViewModel(service)
        assertFalse(viewModel.uiState.value.isProtected)

        viewModel.onChoosePassphrase()
        assertTrue(viewModel.uiState.value.choosingPassphrase)
        viewModel.onPassphraseSet("a long passphrase")
        assertTrue(viewModel.uiState.value.isProtected)
        assertFalse(viewModel.uiState.value.choosingPassphrase)
        assertEquals("a long passphrase", service.storedPassphrase)

        viewModel.onAskTurnOff()
        assertTrue(viewModel.uiState.value.confirmingTurnOff)
        viewModel.onTurnOffConfirmed()
        assertFalse(viewModel.uiState.value.isProtected)
        assertFalse(viewModel.uiState.value.confirmingTurnOff)
        assertNull(service.storedPassphrase)
    }
}
