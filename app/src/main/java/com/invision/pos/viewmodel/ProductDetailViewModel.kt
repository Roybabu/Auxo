package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.domain.inventory.StockAdjustment
import com.invision.pos.domain.inventory.StockAdjustmentCalculator
import com.invision.pos.domain.inventory.StockAdjustmentError
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.usecase.AdjustStockResult
import com.invision.pos.domain.usecase.AdjustStockUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState
    data object NotFound : ProductDetailUiState
    data object Failed : ProductDetailUiState

    data class Ready(
        val product: Product,
        val movements: List<StockMovement>,
        val today: LocalDate,
    ) : ProductDetailUiState
}

/** State of the "Adjust stock" dialog while it is open. */
data class AdjustStockDialogState(
    val variant: ProductVariant,
    val reason: StockChangeReason = StockChangeReason.RECEIVED,
    val quantity: String = "",
    val note: String = "",
    val error: StockAdjustmentError? = null,
    val isSaving: Boolean = false,
) {
    /** Stock after the change, or null while the input is incomplete or invalid. */
    val newStock: Int?
        get() = (StockAdjustmentCalculator.calculate(reason, quantity, variant.stockQuantity) as? StockAdjustment.Valid)
            ?.let { variant.stockQuantity + it.quantityChange }
}

sealed interface ProductDetailEvent {
    data object StockUpdated : ProductDetailEvent
    data object Deleted : ProductDetailEvent
    data object ActionFailed : ProductDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModel(
    private val productId: Long,
    private val repository: InventoryRepository,
    private val adjustStock: AdjustStockUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<ProductDetailEvent>(Channel.BUFFERED)
    val events: Flow<ProductDetailEvent> = eventChannel.receiveAsFlow()

    private val _adjustDialog = MutableStateFlow<AdjustStockDialogState?>(null)
    val adjustDialog: StateFlow<AdjustStockDialogState?> = _adjustDialog.asStateFlow()

    private val _confirmDelete = MutableStateFlow(false)
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    val uiState: StateFlow<ProductDetailUiState> = reload
        .flatMapLatest {
            combine<Product?, List<StockMovement>, ProductDetailUiState>(
                repository.observeProduct(productId),
                repository.observeStockMovements(productId),
            ) { product, movements ->
                if (product == null) {
                    ProductDetailUiState.NotFound
                } else {
                    ProductDetailUiState.Ready(product, movements, LocalDate.now(clock))
                }
            }.catch { emit(ProductDetailUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProductDetailUiState.Loading)

    fun retry() = reload.update { it + 1 }

    fun startAdjust(variant: ProductVariant) {
        _adjustDialog.value = AdjustStockDialogState(variant = variant)
    }

    fun onAdjustReasonChange(reason: StockChangeReason) =
        _adjustDialog.update { it?.copy(reason = reason, error = null) }

    fun onAdjustQuantityChange(quantity: String) =
        _adjustDialog.update { it?.copy(quantity = quantity.filter(Char::isDigit).take(7), error = null) }

    fun onAdjustNoteChange(note: String) = _adjustDialog.update { it?.copy(note = note.take(120)) }

    fun dismissAdjust() {
        _adjustDialog.value = null
    }

    fun confirmAdjust() {
        val dialog = _adjustDialog.value ?: return
        if (dialog.isSaving) return
        _adjustDialog.value = dialog.copy(isSaving = true)
        viewModelScope.launch {
            val result = runCatching {
                adjustStock(dialog.variant, dialog.reason, dialog.quantity, dialog.note)
            }.getOrNull()
            when (result) {
                AdjustStockResult.Done -> {
                    _adjustDialog.value = null
                    eventChannel.send(ProductDetailEvent.StockUpdated)
                }
                is AdjustStockResult.Rejected -> _adjustDialog.update { it?.copy(isSaving = false, error = result.error) }
                null -> {
                    _adjustDialog.update { it?.copy(isSaving = false) }
                    eventChannel.send(ProductDetailEvent.ActionFailed)
                }
            }
        }
    }

    fun requestDelete() {
        _confirmDelete.value = true
    }

    fun dismissDelete() {
        _confirmDelete.value = false
    }

    fun confirmDeleteProduct() {
        _confirmDelete.value = false
        viewModelScope.launch {
            val deleted = runCatching { repository.deleteProduct(productId) }.isSuccess
            eventChannel.send(if (deleted) ProductDetailEvent.Deleted else ProductDetailEvent.ActionFailed)
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val productId = createSavedStateHandle().get<Long>(ARG_PRODUCT_ID) ?: 0L
                ProductDetailViewModel(
                    productId = productId,
                    repository = container.inventoryRepository,
                    adjustStock = container.adjustStock,
                    clock = container.clock,
                )
            }
        }
    }
}
