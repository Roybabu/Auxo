package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.InvisionApplication
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.domain.usecase.ObserveStoreOverviewUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val storeName: String,
        val salesToday: String,
        val billsToday: Int,
    ) : HomeUiState
}

class HomeViewModel(
    observeStoreOverview: ObserveStoreOverviewUseCase,
    moneyFormatter: MoneyFormatter,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = observeStoreOverview()
        .map { overview ->
            HomeUiState.Ready(
                storeName = overview.storeName,
                salesToday = moneyFormatter.format(overview.salesToday),
                billsToday = overview.billsToday,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as InvisionApplication).container
                HomeViewModel(
                    observeStoreOverview = container.observeStoreOverview,
                    moneyFormatter = container.moneyFormatter,
                )
            }
        }
    }
}
