package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.LoyaltyRepository
import com.auxo.pos.domain.loyalty.LoyaltyRuleCheck
import com.auxo.pos.domain.loyalty.LoyaltyRuleError
import com.auxo.pos.domain.loyalty.LoyaltyRuleValidator
import com.auxo.pos.domain.loyalty.LoyaltyRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoyaltySettingsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val enabled: Boolean = false,
    val spendText: String = "",
    val valueText: String = "",
    /** Problems with what is typed now. Shown for a field once it has text, or for all once Save was tried. */
    val errors: Set<LoyaltyRuleError> = emptySet(),
    val showErrors: Boolean = false,
    /** True when something was changed since the rules were loaded or last saved. */
    val dirty: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
    /** The rules as typed, when they are valid; used for the example line. */
    val preview: LoyaltyRules? = null,
)

/** The Super Admin's loyalty page: switch points on or off and set what a point earns and is worth. */
class LoyaltySettingsViewModel(private val repository: LoyaltyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoyaltySettingsUiState())
    val uiState: StateFlow<LoyaltySettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val rules = try {
                repository.getRules()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            _uiState.update {
                evaluate(
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        enabled = rules.enabled,
                        spendText = rules.spendPerPoint.toDecimalString(),
                        valueText = rules.pointValue.toDecimalString(),
                        dirty = false,
                    ),
                )
            }
        }
    }

    fun onEnabledChange(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun onSpendChange(text: String) = edit { it.copy(spendText = text.filter { c -> c.isDigit() || c == '.' }.take(10)) }

    fun onValueChange(text: String) = edit { it.copy(valueText = text.filter { c -> c.isDigit() || c == '.' }.take(10)) }

    private fun edit(change: (LoyaltySettingsUiState) -> LoyaltySettingsUiState) {
        _uiState.update { evaluate(change(it).copy(dirty = true, saved = false, saveFailed = false)) }
    }

    private fun evaluate(state: LoyaltySettingsUiState): LoyaltySettingsUiState =
        when (val check = LoyaltyRuleValidator.check(state.enabled, state.spendText, state.valueText)) {
            is LoyaltyRuleCheck.Valid -> state.copy(errors = emptySet(), preview = check.rules)
            is LoyaltyRuleCheck.Invalid -> state.copy(errors = check.errors, preview = null)
        }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val rules = state.preview
        if (rules == null) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveFailed = false, saved = false) }
        viewModelScope.launch {
            try {
                repository.saveRules(rules)
                _uiState.update { it.copy(isSaving = false, saved = true, dirty = false, showErrors = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoyaltySettingsViewModel(appContainer().loyaltyRepository) }
        }
    }
}
