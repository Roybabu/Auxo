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
import com.auxo.pos.core.backup.BackupScanner
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.FoundBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FindBackupUiState(
    /** A folder is being searched. */
    val scanning: Boolean = false,
    /** The backups found in the chosen folder, newest first; null until a folder has been searched. */
    val found: List<FoundBackup>? = null,
    /** The backup the person has ticked in the list; Restore shop acts on this one only. */
    val chosen: FoundBackup? = null,
    /** The backup whose restore is being confirmed (the step after Restore shop). */
    val selected: FoundBackup? = null,
    /** The selected backup is locked, so its passphrase is being asked for. */
    val unlocking: Boolean = false,
    val restoring: Boolean = false,
    val restored: Boolean = false,
    val problem: BackupProblem? = null,
    /** The folder picker was closed without a choice, so the person is offered a new shop instead. */
    val skipped: Boolean = false,
)

/** The first screen on a new phone: find backups in a folder and restore one, before any account exists. */
class FindBackupViewModel(
    private val scanner: (Uri) -> List<FoundBackup>,
    private val service: BackupService,
    /** Where the folder is searched; tests pass an immediate one. */
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    /** Runs once a restore has worked, before the app restarts. */
    private val afterRestore: () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindBackupUiState())
    val uiState: StateFlow<FindBackupUiState> = _uiState.asStateFlow()

    /** A folder was chosen: look for backups in it. */
    fun onFolderChosen(tree: Uri) {
        if (_uiState.value.scanning) return
        _uiState.update { FindBackupUiState(scanning = true) }
        viewModelScope.launch {
            val result = try {
                withContext(io) { scanner(tree) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("FindBackupViewModel", "Failed and showed an error", e)
                emptyList()
            }
            _uiState.update { it.copy(scanning = false, found = result) }
        }
    }

    /** The folder picker was closed without choosing anything. */
    fun onPickerClosed() = _uiState.update { it.copy(skipped = true) }

    /** A card was tapped: it is ticked, and nothing is restored yet. */
    fun onChoose(backup: FoundBackup) = _uiState.update { it.copy(chosen = backup, problem = null) }

    /** Restore shop was tapped: the ticked backup is shown for confirmation. */
    fun onRestoreRequested() = _uiState.update { state -> state.chosen?.let { state.copy(selected = it, problem = null) } ?: state }

    fun onSelectionCancelled() = _uiState.update { it.copy(selected = null, unlocking = false) }

    /** The person confirmed the selected backup. A locked one asks for its passphrase first. */
    fun onConfirmed() {
        val backup = _uiState.value.selected ?: return
        if (backup.locked) _uiState.update { it.copy(unlocking = true) } else restore(backup.uri, null)
    }

    fun onPassphrase(passphrase: String) {
        val backup = _uiState.value.selected ?: return
        _uiState.update { it.copy(unlocking = false) }
        restore(backup.uri, passphrase)
    }

    fun onProblemDismissed() = _uiState.update { it.copy(problem = null) }

    /** Back to choosing a folder. */
    fun onChooseAnother() = _uiState.update { FindBackupUiState() }

    private fun restore(uri: Uri, passphrase: String?) {
        if (_uiState.value.restoring) return
        _uiState.update { it.copy(restoring = true, problem = null) }
        viewModelScope.launch {
            try {
                service.restore(uri, passphrase)
                afterRestore()
                _uiState.update { it.copy(restoring = false, restored = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("FindBackupViewModel", "Failed and showed an error", e)
                val problem = (e as? BackupException)?.problem ?: BackupProblem.RESTORE_FAILED
                _uiState.update {
                    when (problem) {
                        BackupProblem.WRONG_PASSPHRASE -> it.copy(restoring = false, unlocking = true, problem = problem)
                        else -> it.copy(restoring = false, selected = null, problem = problem)
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                FindBackupViewModel(container.backupScanner::scan, container.firstRunRestore, afterRestore = container::afterFirstRunRestore)
            }
        }
    }
}
