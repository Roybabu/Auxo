package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.backup.auto.AutoBackupPolicy
import com.invision.pos.core.backup.auto.AutoBackupResult
import com.invision.pos.core.backup.auto.AutoBackupSettings
import com.invision.pos.core.backup.auto.AutoBackupStatus
import com.invision.pos.core.backup.auto.BackupSchedule
import com.invision.pos.data.security.AutoBackupService
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
    /** A backup to the chosen folder is running. */
    val isWorking: Boolean = false,
    /** What the last "Back up now" came to, shown until the next one. */
    val lastResult: AutoBackupResult? = null,
    /** The picked folder could not be kept: the phone would not give lasting access to it. */
    val folderAccessProblem: Boolean = false,
) {
    val intervalValid: Boolean get() = intervalText.toIntOrNull()?.let { it in AutoBackupSettings.MIN_INVOICE_INTERVAL..AutoBackupSettings.MAX_INVOICE_INTERVAL } == true
    val keepValid: Boolean get() = keepText.toIntOrNull()?.let { it in AutoBackupSettings.MIN_KEEP..AutoBackupSettings.MAX_KEEP } == true
}

/** Automatic backup on the Backup page, and its status on Home. Only the Super Admin reaches either. */
class AutoBackupViewModel(private val service: AutoBackupService, private val clock: Clock) : ViewModel() {

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

    fun onFolderPicked(folderUri: String, folderName: String) {
        _uiState.update { it.copy(folderAccessProblem = false, lastResult = null) }
        launchChange { service.chooseFolder(folderUri, folderName) }
    }

    fun onFolderAccessProblem() = _uiState.update { it.copy(folderAccessProblem = true) }

    fun onScheduleChange(schedule: BackupSchedule) = launchChange { service.setSchedule(schedule) }

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

    fun onBackUpNow() {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, lastResult = null) }
        viewModelScope.launch {
            val result = try {
                service.backUpNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.update { it.copy(isWorking = false, lastResult = result) }
        }
    }

    private fun launchChange(change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A refused change leaves the saved settings as they were, which the screen already shows.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AutoBackupViewModel(appContainer().autoBackupService, appContainer().clock) }
        }
    }
}
