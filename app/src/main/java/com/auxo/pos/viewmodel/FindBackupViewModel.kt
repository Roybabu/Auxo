// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.backup.BackupException
import com.auxo.pos.core.backup.BackupManifest
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
    /**
     * Whether this phone may be restored from here. A restore replaces the shop's data and clears the sign-in, so it is
     * only allowed while no account exists; a phone that already has one is changed from Backup, after signing in.
     */
    private val mayRestore: () -> Boolean = { true },
    /** Reads the manifest of a locked backup with the typed passphrase; throws [BackupException] if it does not open. */
    private val peek: (Uri, String) -> BackupManifest = { _, _ -> throw BackupException(BackupProblem.UNREADABLE) },
) : ViewModel() {

    /** The passphrase that opened the selected locked backup; kept only in memory until the restore or until the person backs out. */
    private var openedWith: String? = null
    private var peeking = false

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
    fun onChoose(backup: FoundBackup) {
        openedWith = null
        _uiState.update { it.copy(chosen = backup, problem = null) }
    }

    /**
     * Restore shop was tapped. A plain backup goes straight to the confirmation. A locked one first asks for its
     * passphrase, so the confirmation can show which shop it holds.
     */
    fun onRestoreRequested() {
        val backup = _uiState.value.chosen ?: return
        if (backup.locked) _uiState.update { it.copy(unlocking = true, problem = null) } else _uiState.update { it.copy(selected = backup, problem = null) }
    }

    /** The person backed out of the confirmation or of the passphrase question. */
    fun onSelectionCancelled() {
        openedWith = null
        _uiState.update { it.copy(selected = null, unlocking = false, problem = null) }
    }

    /** The person confirmed the selected backup (after the last warning). */
    fun onConfirmed() {
        val backup = _uiState.value.selected ?: return
        restore(backup.uri, if (backup.locked) openedWith else null)
    }

    /** A passphrase was typed for the ticked locked backup: if it opens, the confirmation shows the shop's details. */
    fun onPassphrase(passphrase: String) {
        val backup = _uiState.value.chosen ?: return
        if (peeking) return
        peeking = true
        _uiState.update { it.copy(problem = null) }
        viewModelScope.launch {
            try {
                val manifest = withContext(io) { peek(backup.uri, passphrase) }
                openedWith = passphrase
                _uiState.update { it.copy(unlocking = false, selected = backup.copy(manifest = manifest), problem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = (e as? BackupException)?.problem ?: BackupProblem.UNREADABLE
                _uiState.update { it.copy(unlocking = true, problem = if (problem == BackupProblem.PASSPHRASE_NEEDED) BackupProblem.WRONG_PASSPHRASE else problem) }
            } finally {
                peeking = false
            }
        }
    }

    fun onProblemDismissed() = _uiState.update { it.copy(problem = null) }

    /** Back to choosing a folder. */
    fun onChooseAnother() {
        openedWith = null
        _uiState.update { FindBackupUiState() }
    }

    private fun restore(uri: Uri, passphrase: String?) {
        if (_uiState.value.restoring) return
        if (!mayRestore()) {
            com.auxo.pos.core.debuglog.DebugLog.warn("FindBackupViewModel", "Restore refused: this phone already has an account")
            _uiState.update { it.copy(selected = null, unlocking = false, problem = BackupProblem.RESTORE_FAILED) }
            return
        }
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
                        BackupProblem.WRONG_PASSPHRASE -> {
                            openedWith = null
                            it.copy(restoring = false, selected = null, unlocking = true, problem = problem)
                        }
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
                FindBackupViewModel(
                    container.backupScanner::scan,
                    container.firstRunRestore,
                    afterRestore = container::afterFirstRunRestore,
                    mayRestore = { container.authRepository.state.value == AuthState.NeedsSetup },
                    peek = container.backupScanner::peekLocked,
                )
            }
        }
    }
}
