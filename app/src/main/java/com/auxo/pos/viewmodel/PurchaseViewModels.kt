// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.OrderUpdateResult
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.ReceiveResult
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.inventory.PickableItem
import com.auxo.pos.domain.inventory.ReorderPlanner
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.purchasing.OrderValidation
import com.auxo.pos.domain.purchasing.PurchaseFormError
import com.auxo.pos.domain.purchasing.PurchaseLineError
import com.auxo.pos.domain.purchasing.PurchaseLineForm
import com.auxo.pos.domain.purchasing.PurchaseOrderText
import com.auxo.pos.domain.purchasing.PurchaseValidator
import com.auxo.pos.domain.purchasing.ReceiptValidation
import com.auxo.pos.domain.usecase.ReceiveGoodsResult
import com.auxo.pos.domain.usecase.ReceiveGoodsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** Estimated value of purchase lines being typed, skipping lines not filled in yet. */
fun estimatedTotal(lines: List<PurchaseLineForm>): Money = lines.fold(Money.ZERO) { acc, line ->
    val quantity = line.stockUnit.parse(line.quantity) ?: return@fold acc
    val cost = Money.parseOrNull(line.unitCost) ?: return@fold acc
    acc + line.stockUnit.costOf(quantity, cost)
}

// Purchase orders and goods receipts list

enum class PurchaseTab { ORDERS, RECEIPTS }

enum class OrderFilter(val statuses: Set<PurchaseOrderStatus>) {
    OPEN(setOf(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTLY_RECEIVED)),
    CLOSED(setOf(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CANCELLED)),
    ALL(PurchaseOrderStatus.entries.toSet()),
}

data class PurchaseListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val tab: PurchaseTab = PurchaseTab.ORDERS,
    val orderFilter: OrderFilter = OrderFilter.OPEN,
    val receiptRange: HistoryRange = HistoryRange.THIS_MONTH,
    val query: String = "",
    val orders: List<PurchaseOrder> = emptyList(),
    val receipts: List<PurchaseReceipt> = emptyList(),
    /** Value still to arrive on open orders. */
    val openOrderValue: Money = Money.ZERO,
    val receiptsTotal: Money = Money.ZERO,
)

private data class PurchaseFilters(
    val tab: PurchaseTab,
    val orderFilter: OrderFilter = OrderFilter.OPEN,
    val receiptRange: HistoryRange = HistoryRange.THIS_MONTH,
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseListViewModel(
    initialTab: PurchaseTab,
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filters = MutableStateFlow(PurchaseFilters(initialTab))
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<PurchaseListUiState> = combine(filters, reload) { f, _ -> f }
        .flatMapLatest { f ->
            val (from, to) = f.receiptRange.bounds(clock)
            combine(purchasing.observeOrders(), purchasing.observeReceipts(from, to)) { orders, receipts ->
                val open = orders.filter { it.status.isOpen }
                val shownReceipts = filterReceipts(receipts, f.query)
                PurchaseListUiState(
                    isLoading = false,
                    tab = f.tab,
                    orderFilter = f.orderFilter,
                    receiptRange = f.receiptRange,
                    query = f.query,
                    orders = filterOrders(orders, f.orderFilter, f.query),
                    receipts = shownReceipts,
                    openOrderValue = open.fold(Money.ZERO) { acc, order -> acc + remainingValue(order) },
                    receiptsTotal = shownReceipts.fold(Money.ZERO) { acc, receipt -> acc + receipt.total },
                )
            }.catch {
                emit(PurchaseListUiState(isLoading = false, loadFailed = true, tab = f.tab, orderFilter = f.orderFilter, receiptRange = f.receiptRange, query = f.query))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PurchaseListUiState(tab = initialTab))

    fun onTabChange(tab: PurchaseTab) = filters.update { it.copy(tab = tab) }

    fun onOrderFilterChange(filter: OrderFilter) = filters.update { it.copy(orderFilter = filter) }

    fun onReceiptRangeChange(range: HistoryRange) = filters.update { it.copy(receiptRange = range) }

    fun onQueryChange(query: String) = filters.update { it.copy(query = query.take(40)) }

    fun retry() = reload.update { it + 1 }

    companion object {
        const val ARG_TAB = "tab"

        /** Value of what is still due on [order], at the ordered cost. */
        fun remainingValue(order: PurchaseOrder): Money =
            order.lines.fold(Money.ZERO) { acc, line -> acc + line.stockUnit.costOf(line.remaining, line.unitCost) }

        fun filterOrders(orders: List<PurchaseOrder>, filter: OrderFilter, query: String): List<PurchaseOrder> {
            val text = query.trim()
            return orders.filter { order ->
                order.status in filter.statuses && (
                    text.isEmpty() ||
                        order.number.contains(text, ignoreCase = true) ||
                        order.supplierName.contains(text, ignoreCase = true) ||
                        order.lines.any { it.productName.contains(text, ignoreCase = true) }
                    )
            }
        }

        fun filterReceipts(receipts: List<PurchaseReceipt>, query: String): List<PurchaseReceipt> {
            val text = query.trim()
            if (text.isEmpty()) return receipts
            return receipts.filter { receipt ->
                receipt.number.contains(text, ignoreCase = true) ||
                    receipt.supplierName?.contains(text, ignoreCase = true) == true ||
                    receipt.supplierInvoiceNumber?.contains(text, ignoreCase = true) == true ||
                    receipt.orderNumber?.contains(text, ignoreCase = true) == true ||
                    receipt.lines.any { it.productName.contains(text, ignoreCase = true) }
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val tab = createSavedStateHandle().get<String>(ARG_TAB)
                    ?.let { name -> PurchaseTab.entries.firstOrNull { it.name == name } } ?: PurchaseTab.ORDERS
                PurchaseListViewModel(tab, container.purchasingRepository, container.clock)
            }
        }
    }
}

// Purchase order editor

data class PurchaseOrderEditorUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The order was sent or closed, so it can no longer be edited. */
    val notEditable: Boolean = false,
    val isNew: Boolean = true,
    val number: String = "",
    /** Active suppliers, plus the order's own supplier if it was hidden since. */
    val suppliers: List<Supplier> = emptyList(),
    val supplierId: Long? = null,
    val expectedDate: LocalDate? = null,
    val note: String = "",
    val lines: List<PurchaseLineForm> = emptyList(),
    val picker: ItemPickerState = ItemPickerState(),
    val formErrors: Set<PurchaseFormError> = emptySet(),
    val lineErrors: Map<String, Set<PurchaseLineError>> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val total: Money get() = estimatedTotal(lines)
    val visibleFormErrors: Set<PurchaseFormError> get() = if (showErrors) formErrors else emptySet()
    val visibleLineErrors: Map<String, Set<PurchaseLineError>> get() = if (showErrors) lineErrors else emptyMap()
}

sealed interface PurchaseOrderEditorEvent {
    data class Saved(val orderId: Long, val number: String, val ordered: Boolean) : PurchaseOrderEditorEvent
    data class Invalid(val count: Int) : PurchaseOrderEditorEvent
    data class AlreadyAdded(val label: String) : PurchaseOrderEditorEvent
    data object NotEditable : PurchaseOrderEditorEvent
    data object Failed : PurchaseOrderEditorEvent
}

class PurchaseOrderEditorViewModel(
    private val orderId: Long,
    private val purchasing: PurchasingRepository,
    private val inventory: InventoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseOrderEditorUiState(isNew = orderId == 0L))
    val uiState: StateFlow<PurchaseOrderEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<PurchaseOrderEditorEvent>(Channel.BUFFERED)
    val events: Flow<PurchaseOrderEditorEvent> = eventChannel.receiveAsFlow()

    private var existing: PurchaseOrder? = null
    private var products: List<Product> = emptyList()
    private var nextKey = 0
    private var jobs: List<Job> = emptyList()

    private val today: LocalDate get() = LocalDate.now(clock)

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        jobs.forEach { it.cancel() }
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val productsJob = viewModelScope.launch {
            inventory.observeProducts()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { all ->
                    // Restaurant items sold without a stock count are not bought or received.
                    val list = all.filter { it.tracksStock }
                    products = list
                    _uiState.update { it.copy(picker = it.picker.copy(results = pickItems(list, it.picker.query) { true })) }
                }
        }
        val loadJob = viewModelScope.launch {
            val loaded = runCatching {
                val suppliers = purchasing.observeSuppliers().first()
                val order = if (orderId != 0L) purchasing.getOrder(orderId) else null
                suppliers to order
            }.getOrNull()
            if (loaded == null || (orderId != 0L && loaded.second == null)) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            val (suppliers, order) = loaded
            existing = order
            val shown = suppliers.filter { it.active || it.id == order?.supplierId }
            _uiState.update { state ->
                if (order == null) {
                    state.copy(
                        isLoading = false,
                        suppliers = shown,
                        // With a single supplier there is nothing to choose.
                        supplierId = state.supplierId ?: shown.singleOrNull()?.id,
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        notEditable = !order.status.canEdit,
                        number = order.number,
                        suppliers = shown,
                        supplierId = order.supplierId,
                        expectedDate = order.expectedDate,
                        note = order.note.orEmpty(),
                        lines = order.lines.map { PurchaseLineForm.fromOrderLine(newKey(), it) },
                    )
                }
            }
            revalidate()
            followSuppliers(suppliers.map { it.id }.toSet())
        }
        jobs = listOf(productsJob, loadJob)
    }

    /**
     * Keeps the supplier list current, so a supplier added through "Add supplier"
     * shows up on return, and is chosen if none was.
     */
    private suspend fun followSuppliers(knownIds: Set<Long>) {
        var known = knownIds
        purchasing.observeSuppliers().catch { }.collect { list ->
            val added = list.filter { it.active && it.id !in known }
            known = list.map { it.id }.toSet()
            _uiState.update { state ->
                val shown = list.filter { it.active || it.id == state.supplierId }
                state.copy(suppliers = shown, supplierId = state.supplierId ?: added.singleOrNull()?.id ?: shown.singleOrNull()?.id)
            }
            revalidate()
        }
    }

    private fun newKey(): String = "line-${nextKey++}"

    fun onSupplierChange(supplierId: Long) = edit { it.copy(supplierId = supplierId) }

    fun onExpectedDateChange(date: LocalDate?) = edit { it.copy(expectedDate = date) }

    fun onNoteChange(note: String) = edit { it.copy(note = note.take(PurchaseValidator.NOTE_MAX + 20)) }

    fun onItemQueryChange(query: String) = _uiState.update {
        it.copy(picker = ItemPickerState(query.take(40), pickItems(products, query) { true }))
    }

    /** Adds [item] to the order, suggesting a quantity when it is low on stock. */
    fun addItem(item: PickableItem) {
        val state = _uiState.value
        if (state.lines.any { it.variantId == item.variant.id }) {
            eventChannel.trySend(PurchaseOrderEditorEvent.AlreadyAdded(item.label))
            _uiState.update { it.copy(picker = ItemPickerState()) }
            return
        }
        val variant = item.variant
        val low = variant.needsRestock
        val suggested = if (low) ReorderPlanner.suggestedQuantity(variant) else null
        val line = PurchaseLineForm.forVariant(newKey(), item.product, variant, suggested)
        edit { it.copy(lines = it.lines + line, picker = ItemPickerState()) }
    }

    fun onLineQuantityChange(key: String, text: String) = editLine(key) { it.copy(quantity = text.filter { c -> c.isDigit() || c == '.' }.take(10)) }

    fun onLineCostChange(key: String, text: String) = editLine(key) { it.copy(unitCost = text.filter { c -> c.isDigit() || c == '.' }.take(12)) }

    fun removeLine(key: String) = edit { it.copy(lines = it.lines.filterNot { line -> line.key == key }) }

    private fun editLine(key: String, transform: (PurchaseLineForm) -> PurchaseLineForm) =
        edit { state -> state.copy(lines = state.lines.map { if (it.key == key) transform(it) else it }) }

    private fun edit(transform: (PurchaseOrderEditorUiState) -> PurchaseOrderEditorUiState) {
        _uiState.update(transform)
        revalidate()
    }

    private fun validate(state: PurchaseOrderEditorUiState): OrderValidation =
        PurchaseValidator.validateOrder(state.supplierId, state.lines, state.expectedDate, state.note, today, existing)

    private fun revalidate() = _uiState.update { state ->
        when (val result = validate(state)) {
            is OrderValidation.Invalid -> state.copy(formErrors = result.formErrors, lineErrors = result.lineErrors)
            is OrderValidation.Valid -> state.copy(formErrors = emptySet(), lineErrors = emptyMap())
        }
    }

    /** Saves the draft; with [markOrdered] it is also marked as sent to the supplier. */
    fun save(markOrdered: Boolean) {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed || state.notEditable) return
        val order = when (val result = validate(state)) {
            is OrderValidation.Invalid -> {
                _uiState.update { it.copy(formErrors = result.formErrors, lineErrors = result.lineErrors, showErrors = true) }
                eventChannel.trySend(PurchaseOrderEditorEvent.Invalid(result.formErrors.size + result.lineErrors.size))
                return
            }
            is OrderValidation.Valid -> result.order
        }
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val saved = runCatching { purchasing.saveDraftOrder(order) }.getOrNull()
            when (saved) {
                is OrderUpdateResult.Saved -> {
                    val ordered = markOrdered && runCatching { purchasing.setOrderStatus(saved.orderId, PurchaseOrderStatus.ORDERED) }.getOrDefault(false)
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(PurchaseOrderEditorEvent.Saved(saved.orderId, saved.number, ordered))
                }
                OrderUpdateResult.NotEditable -> {
                    _uiState.update { it.copy(isSaving = false, notEditable = true) }
                    eventChannel.send(PurchaseOrderEditorEvent.NotEditable)
                }
                null -> {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(PurchaseOrderEditorEvent.Failed)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PurchaseOrderEditorViewModel(
                    orderId = createSavedStateHandle().get<Long>(PurchaseOrderDetailViewModel.ARG_ORDER_ID) ?: 0L,
                    purchasing = container.purchasingRepository,
                    inventory = container.inventoryRepository,
                    clock = container.clock,
                )
            }
        }
    }
}

// Purchase order detail

sealed interface PurchaseOrderDetailUiState {
    data object Loading : PurchaseOrderDetailUiState
    data object NotFound : PurchaseOrderDetailUiState
    data object Failed : PurchaseOrderDetailUiState

    data class Ready(
        val order: PurchaseOrder,
        /** Goods received against this order, oldest first. */
        val receipts: List<PurchaseReceipt>,
        val remainingValue: Money,
    ) : PurchaseOrderDetailUiState
}

sealed interface PurchaseOrderDetailEvent {
    data class Share(val subject: String, val text: String) : PurchaseOrderDetailEvent
    data class StatusChanged(val status: PurchaseOrderStatus) : PurchaseOrderDetailEvent

    /** The order moved on in the meantime, so the change wasn't allowed. */
    data object ChangeRejected : PurchaseOrderDetailEvent
    data object Failed : PurchaseOrderDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseOrderDetailViewModel(
    private val orderId: Long,
    private val purchasing: PurchasingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<PurchaseOrderDetailEvent>(Channel.BUFFERED)
    val events: Flow<PurchaseOrderDetailEvent> = eventChannel.receiveAsFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    val uiState: StateFlow<PurchaseOrderDetailUiState> = reload
        .flatMapLatest {
            combine(purchasing.observeOrder(orderId), purchasing.observeReceiptsForOrder(orderId)) { order, receipts ->
                if (order == null) {
                    PurchaseOrderDetailUiState.NotFound
                } else {
                    PurchaseOrderDetailUiState.Ready(order, receipts, PurchaseListViewModel.remainingValue(order))
                }
            }.catch<PurchaseOrderDetailUiState> { emit(PurchaseOrderDetailUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PurchaseOrderDetailUiState.Loading)

    fun retry() = reload.update { it + 1 }

    fun markOrdered() = changeStatus(PurchaseOrderStatus.ORDERED)

    /** Closes the order; anything not received yet is no longer expected. */
    fun cancelOrder() = changeStatus(PurchaseOrderStatus.CANCELLED)

    private fun changeStatus(status: PurchaseOrderStatus) {
        if (_isWorking.value) return
        _isWorking.value = true
        viewModelScope.launch {
            val result = runCatching { purchasing.setOrderStatus(orderId, status) }.getOrNull()
            _isWorking.value = false
            eventChannel.send(
                when (result) {
                    true -> PurchaseOrderDetailEvent.StatusChanged(status)
                    false -> PurchaseOrderDetailEvent.ChangeRejected
                    null -> PurchaseOrderDetailEvent.Failed
                },
            )
        }
    }

    /** The order as text for WhatsApp, SMS or email. */
    fun share() {
        val order = (uiState.value as? PurchaseOrderDetailUiState.Ready)?.order ?: return
        viewModelScope.launch {
            val store = runCatching { settings.getSettings() }.getOrNull()
            val text = PurchaseOrderText.format(order, store?.storeName.orEmpty(), store?.phone.orEmpty())
            eventChannel.send(PurchaseOrderDetailEvent.Share("Purchase order ${order.number}", text))
        }
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PurchaseOrderDetailViewModel(
                    orderId = createSavedStateHandle().get<Long>(ARG_ORDER_ID) ?: 0L,
                    purchasing = container.purchasingRepository,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}

// Receiving goods

data class ReceiveGoodsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The order this receipt is for, or null for goods bought without an order. */
    val order: PurchaseOrder? = null,
    /** The order is received in full or cancelled. */
    val orderClosed: Boolean = false,
    val suppliers: List<Supplier> = emptyList(),
    val supplierId: Long? = null,
    val supplierInvoice: String = "",
    val note: String = "",
    val lines: List<PurchaseLineForm> = emptyList(),
    val picker: ItemPickerState = ItemPickerState(),
    val formErrors: Set<PurchaseFormError> = emptySet(),
    val lineErrors: Map<String, Set<PurchaseLineError>> = emptyMap(),
    /** The supplier bill number was already received. */
    val duplicateSupplierInvoice: Boolean = false,
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    /** One id per receipt, so a repeated save can't receive the goods twice. */
    val requestId: String = UUID.randomUUID().toString(),
) {
    val total: Money get() = estimatedTotal(lines)
    val isFromOrder: Boolean get() = order != null
}

sealed interface ReceiveGoodsEvent {
    data class Saved(val receiptId: Long, val number: String) : ReceiveGoodsEvent
    data class Invalid(val count: Int) : ReceiveGoodsEvent
    data class AlreadyAdded(val label: String) : ReceiveGoodsEvent
    data object OverReceived : ReceiveGoodsEvent
    data object DuplicateSupplierInvoice : ReceiveGoodsEvent
    data object OrderClosed : ReceiveGoodsEvent
    data object ItemsMissing : ReceiveGoodsEvent
    data object Failed : ReceiveGoodsEvent
}

/**
 * Goods arriving, against a purchase order ([orderId] not 0) or bought directly.
 * Stock, batches, cost prices and the order's progress are saved together.
 */
class ReceiveGoodsViewModel(
    private val orderId: Long,
    private val purchasing: PurchasingRepository,
    private val inventory: InventoryRepository,
    private val receiveGoods: ReceiveGoodsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveGoodsUiState())
    val uiState: StateFlow<ReceiveGoodsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ReceiveGoodsEvent>(Channel.BUFFERED)
    val events: Flow<ReceiveGoodsEvent> = eventChannel.receiveAsFlow()

    private var products: List<Product> = emptyList()
    private var nextKey = 0
    private var jobs: List<Job> = emptyList()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        jobs.forEach { it.cancel() }
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val productsJob = viewModelScope.launch {
            inventory.observeProducts()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { all ->
                    // Restaurant items sold without a stock count are not bought or received.
                    val list = all.filter { it.tracksStock }
                    products = list
                    _uiState.update { it.copy(picker = it.picker.copy(results = pickItems(list, it.picker.query) { true })) }
                }
        }
        val loadJob = viewModelScope.launch {
            val loaded = runCatching {
                val suppliers = purchasing.observeSuppliers().first()
                val order = if (orderId != 0L) purchasing.getOrder(orderId) else null
                val catalogue = inventory.observeProducts().first()
                Triple(suppliers, order, catalogue)
            }.getOrNull()
            if (loaded == null || (orderId != 0L && loaded.second == null)) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            val (suppliers, order, catalogue) = loaded
            products = catalogue
            val tracked = catalogue.flatMap { product -> product.variants.map { it.id to product.category.tracksExpiry } }.toMap()
            _uiState.update { state ->
                if (order == null) {
                    val active = suppliers.filter { it.active }
                    state.copy(isLoading = false, suppliers = active, supplierId = state.supplierId ?: active.singleOrNull()?.id)
                } else {
                    state.copy(
                        isLoading = false,
                        order = order,
                        orderClosed = !order.status.isOpen,
                        suppliers = suppliers.filter { it.id == order.supplierId },
                        supplierId = order.supplierId,
                        // Deleted items can't be received; lines already in full are left out.
                        lines = order.lines.filter { it.variantId != null && it.remaining > 0 }.map { line ->
                            PurchaseLineForm(
                                key = newKey(),
                                variantId = line.variantId,
                                productName = line.productName,
                                variantLabel = line.variantLabel,
                                stockUnit = line.stockUnit,
                                quantity = line.stockUnit.toEntryText(line.remaining),
                                unitCost = line.unitCost.toDecimalString(),
                                orderLineId = line.id,
                                remaining = line.remaining,
                                tracksExpiry = tracked[line.variantId] == true,
                            )
                        },
                    )
                }
            }
            if (order == null) followSuppliers(suppliers.map { it.id }.toSet())
        }
        jobs = listOf(productsJob, loadJob)
    }

    /**
     * Keeps the supplier list of a receipt without an order current, so a supplier
     * added through "Add supplier" shows up on return, and is chosen if none was.
     */
    private suspend fun followSuppliers(knownIds: Set<Long>) {
        var known = knownIds
        purchasing.observeSuppliers().catch { }.collect { list ->
            val added = list.filter { it.active && it.id !in known }
            known = list.map { it.id }.toSet()
            _uiState.update { state ->
                val active = list.filter { it.active }
                state.copy(suppliers = active, supplierId = state.supplierId ?: added.singleOrNull()?.id ?: active.singleOrNull()?.id)
            }
            if (_uiState.value.showErrors) revalidate()
        }
    }

    private fun newKey(): String = "line-${nextKey++}"

    fun onSupplierChange(supplierId: Long) {
        if (_uiState.value.isFromOrder) return
        edit { it.copy(supplierId = supplierId) }
    }

    fun onSupplierInvoiceChange(text: String) = edit {
        it.copy(supplierInvoice = text.take(30), duplicateSupplierInvoice = false)
    }

    fun onNoteChange(note: String) = edit { it.copy(note = note.take(PurchaseValidator.NOTE_MAX + 20)) }

    fun onItemQueryChange(query: String) = _uiState.update {
        it.copy(picker = ItemPickerState(query.take(40), pickItems(products, query) { true }))
    }

    /** Adds an item that arrived without an order. */
    fun addItem(item: PickableItem) {
        val state = _uiState.value
        if (state.isFromOrder) return
        if (state.lines.any { it.variantId == item.variant.id }) {
            eventChannel.trySend(ReceiveGoodsEvent.AlreadyAdded(item.label))
            _uiState.update { it.copy(picker = ItemPickerState()) }
            return
        }
        val line = PurchaseLineForm.forVariant(newKey(), item.product, item.variant)
        edit { it.copy(lines = it.lines + line, picker = ItemPickerState()) }
    }

    fun onLineQuantityChange(key: String, text: String) = editLine(key) { it.copy(quantity = text.filter { c -> c.isDigit() || c == '.' }.take(10)) }

    fun onLineCostChange(key: String, text: String) = editLine(key) { it.copy(unitCost = text.filter { c -> c.isDigit() || c == '.' }.take(12)) }

    fun onLineBatchChange(key: String, text: String) = editLine(key) { it.copy(batchNumber = text.take(PurchaseValidator.BATCH_MAX + 8)) }

    fun onLineExpiryChange(key: String, date: LocalDate?) = editLine(key) { it.copy(expiryDate = date) }

    /** Direct receipts only; on an order, set the quantity to 0 to skip a line. */
    fun removeLine(key: String) {
        if (_uiState.value.isFromOrder) return
        edit { it.copy(lines = it.lines.filterNot { line -> line.key == key }) }
    }

    private fun editLine(key: String, transform: (PurchaseLineForm) -> PurchaseLineForm) =
        edit { state -> state.copy(lines = state.lines.map { if (it.key == key) transform(it) else it }) }

    private fun edit(transform: (ReceiveGoodsUiState) -> ReceiveGoodsUiState) {
        _uiState.update(transform)
        if (_uiState.value.showErrors) revalidate()
    }

    private fun revalidate() {
        val state = _uiState.value
        val result = PurchaseValidator.validateReceipt(
            state.supplierId,
            state.order?.id,
            state.supplierInvoice,
            state.lines,
            state.note,
            LocalDate.now(clock),
        )
        _uiState.update {
            when (result) {
                is ReceiptValidation.Invalid -> it.copy(formErrors = result.formErrors, lineErrors = result.lineErrors)
                is ReceiptValidation.Valid -> it.copy(formErrors = emptySet(), lineErrors = emptyMap())
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed || state.orderClosed) return
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val result = runCatching {
                receiveGoods(state.supplierId, state.order?.id, state.supplierInvoice, state.lines, state.note, state.requestId)
            }.getOrNull()
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is ReceiveGoodsResult.Invalid -> {
                    val invalid = result.validation
                    _uiState.update { it.copy(formErrors = invalid.formErrors, lineErrors = invalid.lineErrors) }
                    eventChannel.send(ReceiveGoodsEvent.Invalid(invalid.formErrors.size + invalid.lineErrors.size))
                }
                is ReceiveGoodsResult.Saved -> handle(result.result)
                null -> eventChannel.send(ReceiveGoodsEvent.Failed)
            }
        }
    }

    private suspend fun handle(result: ReceiveResult) {
        when (result) {
            is ReceiveResult.Recorded -> {
                _uiState.update { it.copy(formErrors = emptySet(), lineErrors = emptyMap()) }
                eventChannel.send(ReceiveGoodsEvent.Saved(result.receiptId, result.number))
            }
            is ReceiveResult.OverReceived -> {
                _uiState.update { state ->
                    val keys = state.lines.filter { it.orderLineId in result.orderLineIds }.map { it.key }
                    state.copy(lineErrors = state.lineErrors + keys.associateWith { setOf(PurchaseLineError.MORE_THAN_ORDERED) })
                }
                eventChannel.send(ReceiveGoodsEvent.OverReceived)
            }
            ReceiveResult.DuplicateSupplierInvoice -> {
                _uiState.update { it.copy(duplicateSupplierInvoice = true) }
                eventChannel.send(ReceiveGoodsEvent.DuplicateSupplierInvoice)
            }
            ReceiveResult.OrderClosed -> {
                _uiState.update { it.copy(orderClosed = true) }
                eventChannel.send(ReceiveGoodsEvent.OrderClosed)
            }
            is ReceiveResult.MissingItems -> {
                _uiState.update { state ->
                    val keys = state.lines.filter { it.variantId in result.variantIds }.map { it.key }
                    state.copy(lineErrors = state.lineErrors + keys.associateWith { setOf(PurchaseLineError.ITEM_DELETED) })
                }
                eventChannel.send(ReceiveGoodsEvent.ItemsMissing)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ReceiveGoodsViewModel(
                    orderId = createSavedStateHandle().get<Long>(PurchaseOrderDetailViewModel.ARG_ORDER_ID) ?: 0L,
                    purchasing = container.purchasingRepository,
                    inventory = container.inventoryRepository,
                    receiveGoods = container.receiveGoods,
                    clock = container.clock,
                )
            }
        }
    }
}

// Goods receipt detail

sealed interface ReceiptDetailUiState {
    data object Loading : ReceiptDetailUiState
    data object NotFound : ReceiptDetailUiState
    data object Failed : ReceiptDetailUiState
    data class Ready(val receipt: PurchaseReceipt) : ReceiptDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptDetailViewModel(
    private val receiptId: Long,
    private val purchasing: PurchasingRepository,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<ReceiptDetailUiState> = reload
        .flatMapLatest {
            purchasing.observeReceipt(receiptId)
                .map { receipt -> if (receipt == null) ReceiptDetailUiState.NotFound else ReceiptDetailUiState.Ready(receipt) }
                .catch<ReceiptDetailUiState> { emit(ReceiptDetailUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReceiptDetailUiState.Loading)

    fun retry() = reload.update { it + 1 }

    companion object {
        const val ARG_RECEIPT_ID = "receiptId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReceiptDetailViewModel(
                    receiptId = createSavedStateHandle().get<Long>(ARG_RECEIPT_ID) ?: 0L,
                    purchasing = appContainer().purchasingRepository,
                )
            }
        }
    }
}
