// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.backup.auto.AutoBackupPolicy
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupStatus
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.data.security.AutoBackupService
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutoBackupUiState(
    val settings: AutoBackupSettings = AutoBackupSettings(),
    val status: AutoBackupStatus = AutoBackupStatus.OFF,
    /** What is typed in the two number boxes. A box that holds nothing usable is not saved. */
    val intervalText: String = AutoBackupSettings.DEFAULT_INVOICE_INTERVAL.toString(),
    val keepText: String = AutoBackupSettings.DEFAULT_KEEP.toString(),
    /** The box for choosing the backup passphrase is open: a schedule was picked before any passphrase existed. */
    val choosingPassphrase: Boolean = false,
) {
    val intervalValid: Boolean get() = intervalText.toIntOrNull()?.let { it in AutoBackupSettings.MIN_INVOICE_INTERVAL..AutoBackupSettings.MAX_INVOICE_INTERVAL } == true
    val keepValid: Boolean get() = keepText.toIntOrNull()?.let { it in AutoBackupSettings.MIN_KEEP..AutoBackupSettings.MAX_KEEP } == true
}

/** Automatic backup on the Backup page, and its status on Home. Only the Super Admin reaches either. */
class AutoBackupViewModel(
    private val service: AutoBackupService,
    private val clock: Clock,
    /** Whether a backup passphrase exists. Automatic backups are always locked, so a schedule needs one. */
    private val hasPassphrase: () -> Boolean = { true },
    private val setPassphrase: (String) -> Unit = {},
) : ViewModel() {

    /** The schedule picked while the passphrase box was open; it is applied once a passphrase is saved. */
    private var pendingSchedule: BackupSchedule? = null

    private val first = service.settings.value
    private val _uiState = MutableStateFlow(
        AutoBackupUiState(
            settings = first,
            status = AutoBackupPolicy.status(first, clock.millis()),
            intervalText = first.invoiceInterval.toString(),
            keepText = first.keep.toString(),
        ),
    )
    val uiState: StateFlow<AutoBackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings, status = AutoBackupPolicy.status(settings, clock.millis())) }
            }
        }
    }

    fun onScheduleChange(schedule: BackupSchedule) {
        // Turning a schedule on asks for the passphrase first, when there is none yet; the schedule starts once it is saved.
        if (schedule != BackupSchedule.OFF && !hasPassphrase()) {
            pendingSchedule = schedule
            _uiState.update { it.copy(choosingPassphrase = true) }
            return
        }
        launchChange { service.setSchedule(schedule) }
    }

    fun onPassphraseSet(passphrase: String) {
        // The box already refuses weak passphrases; this keeps one from getting through any other way.
        if (com.auxo.pos.core.backup.PassphraseStrength.problem(passphrase) != null) return
        val schedule = pendingSchedule
        pendingSchedule = null
        _uiState.update { it.copy(choosingPassphrase = false) }
        try {
            setPassphrase(passphrase)
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("AutoBackupViewModel", "Failed and showed an error", e)
            return
        }
        if (schedule != null) launchChange { service.setSchedule(schedule) }
    }

    fun onPassphraseCancelled() {
        pendingSchedule = null
        _uiState.update { it.copy(choosingPassphrase = false) }
    }

    fun onIntervalChange(text: String) {
        val digits = text.filter(Char::isDigit).take(4)
        _uiState.update { it.copy(intervalText = digits) }
        digits.toIntOrNull()?.takeIf { it in AutoBackupSettings.MIN_INVOICE_INTERVAL..AutoBackupSettings.MAX_INVOICE_INTERVAL }
            ?.let { n -> launchChange { service.setInvoiceInterval(n) } }
    }

    fun onKeepChange(text: String) {
        val digits = text.filter(Char::isDigit).take(3)
        _uiState.update { it.copy(keepText = digits) }
        digits.toIntOrNull()?.takeIf { it in AutoBackupSettings.MIN_KEEP..AutoBackupSettings.MAX_KEEP }
            ?.let { n -> launchChange { service.setKeep(n) } }
    }

    private fun launchChange(change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("AutoBackupViewModel", "Failed and showed an error", e)
                // A refused change leaves the saved settings as they were, which the screen already shows.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AutoBackupViewModel(container.autoBackupService, container.clock, container.backupService::isProtected, container.backupService::setPassphrase)
            }
        }
    }
}
