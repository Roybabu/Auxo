package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.audit.AuditRecord
import com.invision.pos.core.audit.AuditTrail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ActivityLogUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val entries: List<AuditRecord> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityLogViewModel(private val trail: AuditTrail) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<ActivityLogUiState> = reload
        .flatMapLatest {
            trail.observeRecent()
                .map { ActivityLogUiState(isLoading = false, entries = it) }
                .catch { emit(ActivityLogUiState(isLoading = false, loadFailed = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActivityLogUiState())

    fun retry() = reload.update { it + 1 }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ActivityLogViewModel(appContainer().auditTrail) }
        }
    }
}
