package com.invision.pos.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.domain.barcode.BarcodeCheck
import com.invision.pos.domain.barcode.BarcodeRules
import com.invision.pos.domain.inventory.InventoryFilter
import com.invision.pos.domain.inventory.InventoryQuery
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.usecase.FindByBarcodeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class InventoryUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Products after search and filters. */
    val products: List<Product> = emptyList(),
    /** All products, before filtering. Zero means the inventory is empty. */
    val totalProductCount: Int = 0,
    val query: InventoryQuery = InventoryQuery(),
    val today: LocalDate = LocalDate.MIN,
)

sealed interface InventoryEvent {
    data class OpenProduct(val productId: Long) : InventoryEvent
    data class BarcodeNotFound(val barcode: String) : InventoryEvent
    data object ScannerUnavailable : InventoryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val repository: InventoryRepository,
    private val findByBarcode: FindByBarcodeUseCase,
    private val clock: Clock,
    initialStockFilter: StockFilter = StockFilter.ALL,
) : ViewModel() {

    private val query = MutableStateFlow(InventoryQuery(stockFilter = initialStockFilter))
    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<InventoryEvent>(Channel.BUFFERED)

    val events: Flow<InventoryEvent> = eventChannel.receiveAsFlow()

    private sealed interface Load {
        data class Loaded(val products: List<Product>) : Load
        data object Failed : Load
    }

    val uiState: StateFlow<InventoryUiState> = reload
        .flatMapLatest {
            repository.observeProducts()
                .map<List<Product>, Load> { Load.Loaded(it) }
                .catch { emit(Load.Failed) }
        }
        .combine(query) { load, currentQuery ->
            val today = LocalDate.now(clock)
            when (load) {
                is Load.Loaded -> InventoryUiState(
                    isLoading = false,
                    products = InventoryFilter.apply(load.products, currentQuery, today),
                    totalProductCount = load.products.size,
                    query = currentQuery,
                    today = today,
                )
                Load.Failed -> InventoryUiState(isLoading = false, loadFailed = true, query = currentQuery, today = today)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = InventoryUiState(query = query.value),
        )

    fun onSearchTextChange(text: String) = query.update { it.copy(text = text) }

    fun onCategorySelected(category: ProductCategory?) = query.update { it.copy(category = category) }

    fun onStockFilterSelected(filter: StockFilter) = query.update { it.copy(stockFilter = filter) }

    fun clearFilters() = query.update { InventoryQuery() }

    fun retry() = reload.update { it + 1 }

    /**
     * Search submitted with Enter, which is also what USB and Bluetooth barcode
     * scanners send after the code. Opens the product if the text is a known barcode.
     */
    fun onSearchSubmitted() {
        val text = query.value.text.trim()
        if (text.isEmpty() || text.contains(' ')) return
        if (BarcodeRules.check(text) !is BarcodeCheck.Valid) return
        lookUp(text, reportMissing = false)
    }

    fun onBarcodeScanned(value: String) = lookUp(value.trim(), reportMissing = true)

    fun onScannerUnavailable() {
        eventChannel.trySend(InventoryEvent.ScannerUnavailable)
    }

    private fun lookUp(barcode: String, reportMissing: Boolean) {
        viewModelScope.launch {
            val match = runCatching { findByBarcode(barcode) }.getOrNull()
            when {
                match != null -> eventChannel.send(InventoryEvent.OpenProduct(match.product.id))
                reportMissing -> eventChannel.send(InventoryEvent.BarcodeNotFound(barcode))
            }
        }
    }

    companion object {
        const val ARG_STOCK_FILTER = "stockFilter"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val handle: SavedStateHandle = createSavedStateHandle()
                val filter = handle.get<String>(ARG_STOCK_FILTER)
                    ?.let { name -> StockFilter.entries.firstOrNull { it.name == name } }
                    ?: StockFilter.ALL
                InventoryViewModel(
                    repository = container.inventoryRepository,
                    findByBarcode = container.findByBarcode,
                    clock = container.clock,
                    initialStockFilter = filter,
                )
            }
        }
    }
}
