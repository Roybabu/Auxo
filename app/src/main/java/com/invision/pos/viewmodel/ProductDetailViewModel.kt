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
import com.invision.pos.domain.model.BatchDetails
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.domain.usecase.AdjustStockResult
import com.invision.pos.domain.usecase.AdjustStockUseCase
import com.invision.pos.domain.usecase.DeleteProductResult
import com.invision.pos.domain.usecase.DeleteProductUseCase
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
import java.util.UUID

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
    /** How the item's stock is counted; ingredients are typed in kg or L. */
    val unit: StockUnit = StockUnit.UNIT,
    /** Food, beverages and ingredients: stock added goes into a batch. */
    val tracksBatches: Boolean = false,
    val batchNumber: String = "",
    val expiryDate: LocalDate? = null,
    /** One id per dialog, so a repeated save is applied once. */
    val requestId: String = UUID.randomUUID().toString(),
    val reasons: List<StockChangeReason> = StockAdjustmentCalculator.manualReasons,
) {
    private val adjustment: StockAdjustment.Valid?
        get() = StockAdjustmentCalculator.calculate(reason, quantity, variant.stockQuantity, unit) as? StockAdjustment.Valid

    /** Stock after the change, or null while the input is incomplete or invalid. */
    val newStock: Int? get() = adjustment?.let { variant.stockQuantity + it.quantityChange }

    /** Batch fields apply when stock is being added to a batch-tracked item. */
    val asksForBatch: Boolean
        get() = tracksBatches && (adjustment?.quantityChange?.let { it > 0 } ?: (reason == StockChangeReason.RECEIVED || reason == StockChangeReason.RETURNED))
}

sealed interface ProductDetailEvent {
    data object StockUpdated : ProductDetailEvent
    data object Deleted : ProductDetailEvent
    data object ActionFailed : ProductDetailEvent

    /** The product was not deleted because this many units are still in stock. */
    data class StockOnHand(val units: Int) : ProductDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModel(
    private val productId: Long,
    private val repository: InventoryRepository,
    private val adjustStock: AdjustStockUseCase,
    private val clock: Clock,
    private val deleteProduct: DeleteProductUseCase = DeleteProductUseCase(repository),
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
        val product = (uiState.value as? ProductDetailUiState.Ready)?.product
        _adjustDialog.value = AdjustStockDialogState(
            variant = variant,
            unit = product?.stockUnit ?: StockUnit.UNIT,
            tracksBatches = product?.category?.tracksExpiry == true,
            reasons = product?.category?.let(StockAdjustmentCalculator::manualReasonsFor) ?: StockAdjustmentCalculator.manualReasons,
        )
    }

    fun onAdjustReasonChange(reason: StockChangeReason) =
        _adjustDialog.update { it?.copy(reason = reason, error = null) }

    fun onAdjustQuantityChange(quantity: String) = _adjustDialog.update {
        val allowed = if (it?.unit == StockUnit.UNIT) quantity.filter(Char::isDigit) else quantity.filter { c -> c.isDigit() || c == '.' }
        it?.copy(quantity = allowed.take(10), error = null)
    }

    fun onAdjustBatchChange(batchNumber: String) = _adjustDialog.update { it?.copy(batchNumber = batchNumber.take(40), error = null) }

    fun onAdjustExpiryChange(date: LocalDate?) = _adjustDialog.update { it?.copy(expiryDate = date, error = null) }

    fun onAdjustNoteChange(note: String) = _adjustDialog.update { it?.copy(note = note.take(120)) }

    fun dismissAdjust() {
        _adjustDialog.value = null
    }

    fun confirmAdjust() {
        val dialog = _adjustDialog.value ?: return
        if (dialog.isSaving) return
        val batch = if (dialog.asksForBatch) {
            val number = dialog.batchNumber.trim()
            if (number.length > BATCH_MAX || dialog.expiryDate?.isBefore(LocalDate.now(clock)) == true) {
                _adjustDialog.value = dialog.copy(error = StockAdjustmentError.BATCH_INVALID)
                return
            }
            BatchDetails(batchNumber = number.ifEmpty { null }, expiryDate = dialog.expiryDate)
        } else {
            BatchDetails()
        }
        _adjustDialog.value = dialog.copy(isSaving = true)
        viewModelScope.launch {
            val result = runCatching {
                adjustStock(dialog.variant, dialog.reason, dialog.quantity, dialog.note, dialog.unit, batch, dialog.requestId)
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
            val result = runCatching { deleteProduct(productId) }.getOrNull()
            eventChannel.send(
                when (result) {
                    DeleteProductResult.Deleted -> ProductDetailEvent.Deleted
                    is DeleteProductResult.StockOnHand -> ProductDetailEvent.StockOnHand(result.units)
                    DeleteProductResult.NotFound, null -> ProductDetailEvent.ActionFailed
                },
            )
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val BATCH_MAX = 32

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val productId = createSavedStateHandle().get<Long>(ARG_PRODUCT_ID) ?: 0L
                ProductDetailViewModel(
                    productId = productId,
                    repository = container.inventoryRepository,
                    adjustStock = container.adjustStock,
                    clock = container.clock,
                    deleteProduct = DeleteProductUseCase(container.inventoryRepository) { product ->
                        product.photoFile?.let { container.productPhotos.delete(it) }
                    },
                )
            }
        }
    }
}
