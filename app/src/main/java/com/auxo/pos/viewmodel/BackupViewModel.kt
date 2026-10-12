package com.auxo.pos.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.SavedBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    /** A backup or restore is running. */
    val isWorking: Boolean = false,
    val lastBackupMillis: Long? = null,
    /** The backup just made, so it can be shared. */
    val saved: SavedBackup? = null,
    val problem: BackupProblem? = null,
    /** A file the person picked, waiting for them to confirm replacing their data. */
    val pendingRestore: Uri? = null,
    /** The restore finished; the screen restarts the app. */
    val restored: Boolean = false,
)

/** Backing up and restoring from the More page. */
class BackupViewModel(private val service: BackupService) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState(lastBackupMillis = service.lastBackupMillis()))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun onBackUp() {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, problem = null, saved = null) }
        viewModelScope.launch {
            try {
                val saved = service.backUp()
                _uiState.update { it.copy(isWorking = false, saved = saved, lastBackupMillis = service.lastBackupMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, problem = (e as? BackupException)?.problem ?: BackupProblem.SAVE_FAILED) }
            }
        }
    }

    /** A file was chosen. Nothing changes until the person confirms. */
    fun onRestoreFilePicked(uri: Uri) = _uiState.update { it.copy(pendingRestore = uri, problem = null) }

    fun onRestoreCancelled() = _uiState.update { it.copy(pendingRestore = null) }

    fun onRestoreConfirmed() {
        val uri = _uiState.value.pendingRestore ?: return
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, pendingRestore = null, problem = null, saved = null) }
        viewModelScope.launch {
            try {
                service.restore(uri)
                _uiState.update { it.copy(isWorking = false, restored = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, problem = (e as? BackupException)?.problem ?: BackupProblem.RESTORE_FAILED) }
            }
        }
    }

    fun onProblemDismissed() = _uiState.update { it.copy(problem = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(appContainer().backupService) }
        }
    }
}
