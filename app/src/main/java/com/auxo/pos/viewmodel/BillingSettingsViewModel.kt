package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.billing.Fssai
import com.auxo.pos.domain.billing.Gstin
import com.auxo.pos.domain.billing.SettingsError
import com.auxo.pos.domain.billing.SettingsField
import com.auxo.pos.domain.billing.SettingsValidator
import com.auxo.pos.domain.model.StoreSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingSettingsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val settings: StoreSettings = StoreSettings(),
    val errors: Map<SettingsField, SettingsError> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val visibleErrors: Map<SettingsField, SettingsError> get() = if (showErrors) errors else emptyMap()
}

sealed interface SettingsEvent {
    data object Saved : SettingsEvent
    data class Invalid(val count: Int) : SettingsEvent
    data object SaveFailed : SettingsEvent
}

class BillingSettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingSettingsUiState())
    val uiState: StateFlow<BillingSettingsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val settings = runCatching { repository.getSettings() }.getOrNull()
            if (settings == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, settings = settings, errors = SettingsValidator.check(settings)) }
            }
        }
    }

    fun update(transform: (StoreSettings) -> StoreSettings) {
        _uiState.update { state ->
            val settings = transform(state.settings)
            state.copy(settings = settings, errors = SettingsValidator.check(settings))
        }
    }

    /** Fills the state from the GSTIN's first two digits when the state is not chosen yet. */
    fun onGstinChange(value: String) = update { settings ->
        val gstin = Gstin.normalize(value).take(15)
        val inferred = if (settings.stateCode.isEmpty() && Gstin.isValid(gstin)) Gstin.stateCode(gstin) else settings.stateCode
        settings.copy(gstin = gstin, stateCode = inferred)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val trimmed = state.settings.trimmed()
        val errors = SettingsValidator.check(trimmed)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors, showErrors = true) }
            eventChannel.trySend(SettingsEvent.Invalid(errors.size))
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = runCatching { repository.saveSettings(trimmed) }.isSuccess
            _uiState.update { it.copy(isSaving = false, settings = if (saved) trimmed else it.settings) }
            eventChannel.send(if (saved) SettingsEvent.Saved else SettingsEvent.SaveFailed)
        }
    }

    private fun StoreSettings.trimmed() = copy(
        storeName = storeName.trim(),
        address = address.trim(),
        phone = phone.trim(),
        gstin = Gstin.normalize(gstin),
        fssai = Fssai.normalize(fssai),
        invoicePrefix = invoicePrefix.trim().uppercase(),
        creditNotePrefix = creditNotePrefix.trim().uppercase(),
        upiId = upiId.trim(),
        upiPayeeName = upiPayeeName.trim(),
        receipt = receipt.copy(footer = receipt.footer.trim()),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BillingSettingsViewModel(appContainer().settingsRepository) }
        }
    }
}
