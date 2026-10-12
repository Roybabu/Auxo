package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.export.ExportFile
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.inventory.ReorderGroup
import com.auxo.pos.domain.inventory.ReorderPlanner
import com.auxo.pos.domain.inventory.ReorderSuggestion
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovementRecord
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.reports.ExpiryWindow
import com.auxo.pos.domain.reports.ReportCsv
import com.auxo.pos.domain.reports.Reports
import com.auxo.pos.domain.usecase.CreateReorderDraftsUseCase
import com.auxo.pos.domain.usecase.RecordWastageUseCase
import com.auxo.pos.domain.usecase.WastageError
import com.auxo.pos.domain.usecase.warningDays
import com.auxo.pos.domain.usecase.WastageResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// Stock movement history

/** Groups of stock change reasons offered as filters. */
enum class MovementGroup(val reasons: Set<StockChangeReason>) {
    ALL(emptySet()),
    STOCK_IN(setOf(StockChangeReason.INITIAL, StockChangeReason.RECEIVED, StockChangeReason.RETURNED)),
    SOLD(setOf(StockChangeReason.SOLD)),
    USED(setOf(StockChangeReason.USED)),
    WASTAGE(setOf(StockChangeReason.DAMAGED, StockChangeReason.EXPIRED, StockChangeReason.SPOILED)),
    CORRECTIONS(setOf(StockChangeReason.CORRECTION)),
}

data class StockMovementsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val range: HistoryRange = HistoryRange.LAST_7_DAYS,
    val group: MovementGroup = MovementGroup.ALL,
    val category: ProductCategory? = null,
    val query: String = "",
    val movements: List<StockMovementRecord> = emptyList(),
    /** Stock written off in the movements shown, at cost. */
    val wastageValue: Money = Money.ZERO,
    val receivedCount: Int = 0,
    val outCount: Int = 0,
)

sealed interface StockMovementsEvent {
    data class Export(val file: ExportFile) : StockMovementsEvent
}

private data class MovementFilters(
    val range: HistoryRange = HistoryRange.LAST_7_DAYS,
    val group: MovementGroup = MovementGroup.ALL,
    val category: ProductCategory? = null,
    val query: String = "",
)

/** Every stock change across products: sales, receipts, returns, kitchen use, wastage and corrections. */
@OptIn(ExperimentalCoroutinesApi::class)
class StockMovementsViewModel(
    private val inventory: InventoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filters = MutableStateFlow(MovementFilters())
    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<StockMovementsEvent>(Channel.BUFFERED)
    val events: Flow<StockMovementsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<StockMovementsUiState> = combine(filters, reload) { f, _ -> f }
        .flatMapLatest { f ->
            val (from, to) = f.range.bounds(clock)
            inventory.observeMovementHistory(from, to)
                .map { records ->
                    val shown = filterMovements(records, f.group, f.category, f.query)
                    StockMovementsUiState(
                        isLoading = false,
                        range = f.range,
                        group = f.group,
                        category = f.category,
                        query = f.query,
                        movements = shown,
                        wastageValue = wastageValue(shown),
                        receivedCount = shown.count { it.movement.quantityChange > 0 },
                        outCount = shown.count { it.movement.quantityChange < 0 },
                    )
                }
                .catch { emit(StockMovementsUiState(isLoading = false, loadFailed = true, range = f.range, group = f.group, category = f.category, query = f.query)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StockMovementsUiState())

    fun onRangeChange(range: HistoryRange) = filters.update { it.copy(range = range) }

    fun onGroupChange(group: MovementGroup) = filters.update { it.copy(group = group) }

    fun onCategoryChange(category: ProductCategory?) = filters.update { it.copy(category = category) }

    fun onQueryChange(query: String) = filters.update { it.copy(query = query.take(40)) }

    fun retry() = reload.update { it + 1 }

    /** The movements shown, as a CSV file. */
    fun export() {
        val state = uiState.value
        if (state.isLoading || state.loadFailed) return
        val table = Reports.stockMovements(state.movements)
        val name = "stock-movements-${state.range.name.lowercase().replace('_', '-')}-${LocalDate.now(clock)}.csv"
        eventChannel.trySend(StockMovementsEvent.Export(ExportFile(name, ReportCsv.write(table, clock.zone))))
    }

    companion object {
        fun filterMovements(
            records: List<StockMovementRecord>,
            group: MovementGroup,
            category: ProductCategory?,
            query: String,
        ): List<StockMovementRecord> {
            val text = query.trim()
            return records.filter { record ->
                (group == MovementGroup.ALL || record.movement.reason in group.reasons) &&
                    (category == null || record.category == category) &&
                    (
                        text.isEmpty() ||
                            record.productName.contains(text, ignoreCase = true) ||
                            record.variantLabel.contains(text, ignoreCase = true) ||
                            record.reference?.contains(text, ignoreCase = true) == true ||
                            record.batchNumber?.contains(text, ignoreCase = true) == true ||
                            record.movement.note?.contains(text, ignoreCase = true) == true
                        )
            }
        }

        /** Value at cost of stock written off among [records]; items without a cost price count as zero. */
        fun wastageValue(records: List<StockMovementRecord>): Money = records
            .filter { it.movement.reason.isWastage }
            .fold(Money.ZERO) { acc, record -> acc + (record.valueAtCost?.let { Money(-it.minorUnits) } ?: Money.ZERO) }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                StockMovementsViewModel(container.inventoryRepository, container.clock)
            }
        }
    }
}

// Stock alerts: reordering and expiry

enum class AlertsTab { LOW_STOCK, EXPIRY }

/** Writing off part or all of a batch. */
data class WastageDialogState(
    val batch: BatchWithProduct,
    val reason: StockChangeReason,
    /** In the entry unit (pcs, kg or L). */
    val quantity: String,
    val note: String = "",
    val error: WastageError? = null,
    val isSaving: Boolean = false,
    /** One id per write-off, so a repeated tap can't take the stock out twice. */
    val requestId: String = UUID.randomUUID().toString(),
)

data class StockAlertsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val tab: AlertsTab = AlertsTab.LOW_STOCK,
    val today: LocalDate = LocalDate.MIN,
    /** Out-of-stock and low items with a suggested order, out of stock first. */
    val suggestions: List<ReorderSuggestion> = emptyList(),
    /** Variants left out of the next set of draft orders. Everything else is included. */
    val deselected: Set<Long> = emptySet(),
    /** Active suppliers. */
    val suppliers: List<Supplier> = emptyList(),
    /** Supplier for chosen items that have no preferred supplier. */
    val fallbackSupplierId: Long? = null,
    val isCreating: Boolean = false,
    /** Order quantities typed by the person, by variant id, in the entry unit (pcs, kg or L). */
    val quantityTexts: Map<Long, String> = emptyMap(),
    /** Variants whose typed quantity is not usable. */
    val invalidQuantities: Set<Long> = emptySet(),
    val expiryWindow: ExpiryWindow = ExpiryWindow.WITHIN_30_DAYS,
    /** Batches in the window, soonest expiry first. */
    val batches: List<BatchWithProduct> = emptyList(),
    /** Expired stock still on the shelf, at cost. */
    val expiredValue: Money = Money.ZERO,
    val expiredBatchCount: Int = 0,
) {
    val selected: List<ReorderSuggestion> get() = suggestions.filter { it.variant.id !in deselected }

    /** Chosen items that would be left out because they have no supplier. */
    val selectedWithoutSupplier: Int
        get() = if (fallbackSupplierId != null) 0 else selected.count { it.supplierId == null }

    fun supplierName(id: Long?): String? = suppliers.firstOrNull { it.id == id }?.name

    /** The suggestions grouped by supplier, for the list. */
    val groups: List<ReorderGroup> get() = ReorderPlanner.groups(suggestions, suppliers)

    /** Draft orders can be made when something is chosen and no chosen quantity is unusable. */
    val canCreate: Boolean get() = selected.isNotEmpty() && selected.none { it.variant.id in invalidQuantities } && !isCreating
}

sealed interface StockAlertsEvent {
    /** [orderIds] drafts were created; [skipped] chosen items had no supplier. */
    data class DraftsCreated(val orderIds: List<Long>, val skipped: Int) : StockAlertsEvent
    data object NothingToOrder : StockAlertsEvent
    data class WrittenOff(val name: String) : StockAlertsEvent
    data object Failed : StockAlertsEvent
}

private data class AlertFilters(
    val tab: AlertsTab,
    val deselected: Set<Long> = emptySet(),
    val fallbackSupplierId: Long? = null,
    val quantityTexts: Map<Long, String> = emptyMap(),
    /** Null until the user picks one; then the shop's expiring-soon setting applies. */
    val expiryWindow: ExpiryWindow? = null,
)

/**
 * Low-stock alerts that turn into draft purchase orders, and expiring or expired
 * batches that can be written off as wastage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockAlertsViewModel(
    initialTab: AlertsTab,
    private val inventory: InventoryRepository,
    private val purchasing: PurchasingRepository,
    private val createReorderDrafts: CreateReorderDraftsUseCase,
    private val recordWastage: RecordWastageUseCase,
    private val clock: Clock,
    private val settings: SettingsRepository? = null,
) : ViewModel() {

    private val filters = MutableStateFlow(AlertFilters(initialTab))
    private val reload = MutableStateFlow(0)
    private val creating = MutableStateFlow(false)

    private val eventChannel = Channel<StockAlertsEvent>(Channel.BUFFERED)
    val events: Flow<StockAlertsEvent> = eventChannel.receiveAsFlow()

    private val _wastageDialog = MutableStateFlow<WastageDialogState?>(null)
    val wastageDialog: StateFlow<WastageDialogState?> = _wastageDialog.asStateFlow()

    val uiState: StateFlow<StockAlertsUiState> = reload
        .flatMapLatest {
            combine(inventory.observeProducts(), inventory.observeBatches(), purchasing.observeSuppliers().catch { emit(emptyList()) }, combine(filters, creating, warningDays(settings), ::Triple)) { products, batches, suppliers, (f, isCreating, days) ->
                val today = LocalDate.now(clock)
                val active = suppliers.filter { it.active }
                val activeIds = active.map { it.id }.toSet()
                // A hidden supplier no longer receives orders.
                val catalogue = products.map { product ->
                    if (product.preferredSupplierId != null && product.preferredSupplierId !in activeIds) product.copy(preferredSupplierId = null) else product
                }
                val planned = ReorderPlanner.suggestions(catalogue)
                val suggestions = ReorderPlanner.withQuantities(planned, f.quantityTexts)
                val expired = batches.filter { it.batch.isExpired(today) }
                StockAlertsUiState(
                    isLoading = false,
                    tab = f.tab,
                    today = today,
                    suggestions = suggestions,
                    deselected = f.deselected,
                    suppliers = active,
                    fallbackSupplierId = f.fallbackSupplierId?.takeIf { it in activeIds },
                    isCreating = isCreating,
                    quantityTexts = f.quantityTexts,
                    invalidQuantities = ReorderPlanner.invalidQuantities(planned, f.quantityTexts),
                    // Until the user picks a window, the list opens on the shop's expiring-soon setting.
                    expiryWindow = f.expiryWindow ?: ExpiryWindow.forWarningDays(days),
                    batches = batches.filter { (f.expiryWindow ?: ExpiryWindow.forWarningDays(days)).includes(it.batch, today) },
                    expiredValue = expired.fold(Money.ZERO) { acc, item -> acc + (item.valueAtCost ?: Money.ZERO) },
                    expiredBatchCount = expired.size,
                )
            }.catch { emit(StockAlertsUiState(isLoading = false, loadFailed = true, tab = filters.value.tab)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StockAlertsUiState(tab = initialTab))

    fun onTabChange(tab: AlertsTab) = filters.update { it.copy(tab = tab) }

    fun onExpiryWindowChange(window: ExpiryWindow) = filters.update { it.copy(expiryWindow = window) }

    fun toggleSelected(variantId: Long) = filters.update {
        it.copy(deselected = if (variantId in it.deselected) it.deselected - variantId else it.deselected + variantId)
    }

    fun selectAll(selected: Boolean) = filters.update { f ->
        f.copy(deselected = if (selected) emptySet() else uiState.value.suggestions.map { it.variant.id }.toSet())
    }

    fun onFallbackSupplierChange(supplierId: Long?) = filters.update { it.copy(fallbackSupplierId = supplierId) }

    /** The person typed an order quantity for one item (entry unit); blank goes back to the suggestion. */
    fun onQuantityChange(variantId: Long, text: String) = filters.update { f ->
        f.copy(quantityTexts = if (text.isBlank()) f.quantityTexts - variantId else f.quantityTexts + (variantId to text.trim()))
    }

    fun retry() = reload.update { it + 1 }

    /** One draft purchase order per supplier for the chosen items. */
    fun createDraftOrders() {
        val state = uiState.value
        if (state.isCreating || creating.value) return
        if (state.selected.any { it.variant.id in state.invalidQuantities }) return
        val chosen = state.selected
        val orderable = chosen.filter { it.supplierId != null || state.fallbackSupplierId != null }
        if (orderable.isEmpty()) {
            eventChannel.trySend(StockAlertsEvent.NothingToOrder)
            return
        }
        creating.value = true
        viewModelScope.launch {
            val ids = runCatching { createReorderDrafts(orderable, state.fallbackSupplierId) }.getOrNull()
            creating.value = false
            if (ids == null) {
                eventChannel.send(StockAlertsEvent.Failed)
            } else {
                filters.update { it.copy(deselected = emptySet(), quantityTexts = emptyMap()) }
                eventChannel.send(StockAlertsEvent.DraftsCreated(ids, chosen.size - orderable.size))
            }
        }
    }

    // Write-offs

    fun startWriteOff(batch: BatchWithProduct) {
        val today = LocalDate.now(clock)
        _wastageDialog.value = WastageDialogState(
            batch = batch,
            reason = if (batch.batch.isExpired(today)) StockChangeReason.EXPIRED else StockChangeReason.DAMAGED,
            quantity = batch.stockUnit.toEntryText(batch.batch.quantity),
        )
    }

    fun onWastageReasonChange(reason: StockChangeReason) = _wastageDialog.update { it?.copy(reason = reason, error = null) }

    fun onWastageQuantityChange(text: String) = _wastageDialog.update {
        it?.copy(quantity = text.filter { c -> c.isDigit() || c == '.' }.take(10), error = null)
    }

    fun onWastageNoteChange(text: String) = _wastageDialog.update { it?.copy(note = text.take(RecordWastageUseCase.NOTE_MAX + 20), error = null) }

    fun dismissWriteOff() {
        if (_wastageDialog.value?.isSaving == true) return
        _wastageDialog.value = null
    }

    fun confirmWriteOff() {
        val dialog = _wastageDialog.value ?: return
        if (dialog.isSaving) return
        _wastageDialog.value = dialog.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                recordWastage(dialog.batch, dialog.quantity, dialog.reason, dialog.note, dialog.requestId)
            }.getOrNull()
            when (result) {
                WastageResult.Done -> {
                    _wastageDialog.value = null
                    eventChannel.send(StockAlertsEvent.WrittenOff(dialog.batch.productName))
                }
                is WastageResult.Rejected -> _wastageDialog.update { it?.copy(isSaving = false, error = result.error) }
                null -> {
                    _wastageDialog.update { it?.copy(isSaving = false) }
                    eventChannel.send(StockAlertsEvent.Failed)
                }
            }
        }
    }

    companion object {
        const val ARG_TAB = "tab"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val tab = createSavedStateHandle().get<String>(ARG_TAB)
                    ?.let { name -> AlertsTab.entries.firstOrNull { it.name == name } } ?: AlertsTab.LOW_STOCK
                StockAlertsViewModel(
                    initialTab = tab,
                    inventory = container.inventoryRepository,
                    purchasing = container.purchasingRepository,
                    createReorderDrafts = container.createReorderDrafts,
                    recordWastage = container.recordWastage,
                    clock = container.clock,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}
