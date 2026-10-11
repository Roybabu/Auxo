package com.invision.pos.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.backup.BackupException
import com.invision.pos.core.backup.BackupProblem
import com.invision.pos.core.backup.BackupService
import com.invision.pos.core.backup.SavedBackup
import com.invision.pos.testutil.MainDispatcherRule
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

        override suspend fun backUp(): SavedBackup {
            failBackup?.let { throw it }
            last = 1_000L
            return SavedBackup("b.zip", "Documents/Invision", Uri.parse("content://test/b.zip"))
        }

        override suspend fun restore(source: Uri) {
            failRestore?.let { throw it }
            restoredFrom = source
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
}
