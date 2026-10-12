package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.inventory.InventorySummary
import com.auxo.pos.domain.model.StoreOverview
import com.auxo.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.auxo.pos.domain.usecase.ObserveStoreOverviewUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Failed : HomeUiState

    data class Ready(
        val storeName: String,
        val salesToday: String,
        val billsToday: Int,
        val inventory: InventorySummary,
        val stockValueAtCost: String,
        val stockValueAtPrice: String,
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    observeStoreOverview: ObserveStoreOverviewUseCase,
    observeInventorySummary: ObserveInventorySummaryUseCase,
    moneyFormatter: MoneyFormatter,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = reload
        .flatMapLatest {
            combine<StoreOverview, InventorySummary, HomeUiState>(
                observeStoreOverview(),
                observeInventorySummary(),
            ) { overview, inventory ->
                HomeUiState.Ready(
                    storeName = overview.storeName,
                    salesToday = moneyFormatter.format(overview.salesToday),
                    billsToday = overview.billsToday,
                    inventory = inventory,
                    stockValueAtCost = moneyFormatter.format(inventory.stockValueAtCost),
                    stockValueAtPrice = moneyFormatter.format(inventory.stockValueAtPrice),
                )
            }.catch { emit(HomeUiState.Failed) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    fun retry() {
        reload.update { it + 1 }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    observeStoreOverview = container.observeStoreOverview,
                    observeInventorySummary = container.observeInventorySummary,
                    moneyFormatter = container.moneyFormatter,
                )
            }
        }
    }
}
