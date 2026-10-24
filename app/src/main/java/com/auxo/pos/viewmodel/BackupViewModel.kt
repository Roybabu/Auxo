// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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
    /** New backups are locked with a passphrase. */
    val isProtected: Boolean = false,
    /** The box for choosing a passphrase is open. */
    val choosingPassphrase: Boolean = false,
    /** The question "stop locking backups?" is open. */
    val confirmingTurnOff: Boolean = false,
    /** The chosen file is locked, so its passphrase is being asked for. */
    val unlocking: Uri? = null,
)

/** Backing up and restoring from the More page. */
class BackupViewModel(private val service: BackupService) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState(lastBackupMillis = service.lastBackupMillis(), isProtected = service.isProtected()))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** Set when a backup was asked for before any passphrase existed: it is made as soon as one is chosen. */
    private var backUpAfterPassphrase = false

    fun onBackUp() {
        if (_uiState.value.isWorking) return
        // Backups are always locked. With no passphrase yet, ask for one first and make the backup right after.
        if (!service.isProtected()) {
            backUpAfterPassphrase = true
            _uiState.update { it.copy(choosingPassphrase = true, problem = null) }
            return
        }
        startBackUp()
    }

    private fun startBackUp() {
        _uiState.update { it.copy(isWorking = true, problem = null, saved = null) }
        viewModelScope.launch {
            try {
                val saved = service.backUp()
                _uiState.update { it.copy(isWorking = false, saved = saved, lastBackupMillis = service.lastBackupMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("BackupViewModel", "Failed and showed an error", e)
                _uiState.update { it.copy(isWorking = false, problem = (e as? BackupException)?.problem ?: BackupProblem.SAVE_FAILED) }
            }
        }
    }

    /** A file was chosen. Nothing changes until the person confirms. */
    fun onRestoreFilePicked(uri: Uri) = _uiState.update { it.copy(pendingRestore = uri, problem = null) }

    fun onRestoreCancelled() = _uiState.update { it.copy(pendingRestore = null) }

    fun onRestoreConfirmed() {
        val uri = _uiState.value.pendingRestore ?: return
        _uiState.update { it.copy(pendingRestore = null) }
        restore(uri, passphrase = null)
    }

    /** The passphrase for a locked backup was typed. */
    fun onPassphraseForRestore(passphrase: String) {
        val uri = _uiState.value.unlocking ?: return
        _uiState.update { it.copy(unlocking = null) }
        restore(uri, passphrase)
    }

    fun onUnlockCancelled() = _uiState.update { it.copy(unlocking = null) }

    private fun restore(uri: Uri, passphrase: String?) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, problem = null, saved = null) }
        viewModelScope.launch {
            try {
                service.restore(uri, passphrase)
                _uiState.update { it.copy(isWorking = false, restored = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("BackupViewModel", "Failed and showed an error", e)
                val problem = (e as? BackupException)?.problem ?: BackupProblem.RESTORE_FAILED
                when (problem) {
                    // A locked file: ask for the passphrase (again, after a wrong one) instead of failing.
                    BackupProblem.PASSPHRASE_NEEDED -> _uiState.update { it.copy(isWorking = false, unlocking = uri) }
                    BackupProblem.WRONG_PASSPHRASE -> _uiState.update { it.copy(isWorking = false, unlocking = uri, problem = problem) }
                    else -> _uiState.update { it.copy(isWorking = false, problem = problem) }
                }
            }
        }
    }

    fun onChoosePassphrase() = _uiState.update { it.copy(choosingPassphrase = true) }

    fun onChoosePassphraseCancelled() {
        backUpAfterPassphrase = false
        _uiState.update { it.copy(choosingPassphrase = false) }
    }

    fun onPassphraseSet(passphrase: String) {
        // The box already refuses weak passphrases; this keeps one from getting through any other way.
        if (com.auxo.pos.core.backup.PassphraseStrength.problem(passphrase) != null) return
        try {
            service.setPassphrase(passphrase)
            _uiState.update { it.copy(choosingPassphrase = false, isProtected = service.isProtected(), problem = null) }
            if (backUpAfterPassphrase) {
                backUpAfterPassphrase = false
                startBackUp()
            }
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("BackupViewModel", "Failed and showed an error", e)
            backUpAfterPassphrase = false
            _uiState.update { it.copy(choosingPassphrase = false, problem = (e as? BackupException)?.problem ?: BackupProblem.SAVE_FAILED) }
        }
    }

    fun onAskTurnOff() = _uiState.update { it.copy(confirmingTurnOff = true) }

    fun onTurnOffCancelled() = _uiState.update { it.copy(confirmingTurnOff = false) }

    fun onTurnOffConfirmed() {
        service.clearPassphrase()
        _uiState.update { it.copy(confirmingTurnOff = false, isProtected = service.isProtected()) }
    }

    fun onProblemDismissed() = _uiState.update { it.copy(problem = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(appContainer().backupService) }
        }
    }
}
