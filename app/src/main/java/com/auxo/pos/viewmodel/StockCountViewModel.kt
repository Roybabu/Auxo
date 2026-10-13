package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.StockCountRepository
import com.auxo.pos.domain.barcode.BarcodeCheck
import com.auxo.pos.domain.barcode.BarcodeRules
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.inventory.CountReason
import com.auxo.pos.domain.inventory.CountReviewRow
import com.auxo.pos.domain.inventory.CountSummary
import com.auxo.pos.domain.inventory.InventoryFilter
import com.auxo.pos.domain.inventory.StockCount
import com.auxo.pos.domain.inventory.StockCounts
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.usecase.PostCountResult
import com.auxo.pos.domain.usecase.PostStockCountUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CountStage { COUNTING, REVIEW }

/** An item that can be counted. */
data class CountCandidate(val product: Product, val variant: ProductVariant)

/** The box where the counted quantity of one item is typed. */
data class CountEditor(
    val product: Product,
    val variant: ProductVariant,
    /** In the entry unit (pcs, kg or L). */
    val text: String,
    val error: Boolean = false,
)

data class StockCountUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The count in progress; null before one is started. */
    val count: StockCount? = null,
    val stage: CountStage = CountStage.COUNTING,
    val searchText: String = "",
    val searchResults: List<CountCandidate> = emptyList(),
    /** Everything counted so far, most recent first. */
    val rows: List<CountReviewRow> = emptyList(),
    val summary: CountSummary = StockCounts.summary(emptyList()),
    val reason: CountReason = CountReason.ROUTINE,
    val note: String = "",
    val editor: CountEditor? = null,
    val confirmOpen: Boolean = false,
    val cancelConfirmOpen: Boolean = false,
    val isPosting: Boolean = false,
) {
    /** Counted items whose count differs from the expected stock. */
    val differences: List<CountReviewRow> get() = StockCounts.differences(rows)

    /** Net value of the differences at cost price, for items that have one (negative: stock was lost). */
    val netValue: Money get() = summary.extraValue - summary.shortageValue
}

sealed interface StockCountEvent {
    /** The count was posted: [adjusted] items changed, [matched] matched what was expected. */
    data class Posted(val adjusted: Int, val matched: Int) : StockCountEvent

    /** Some adjustments were made; [failed] items still need to be checked. */
    data class Partial(val adjusted: Int, val failed: Int) : StockCountEvent

    data object Cancelled : StockCountEvent

    /** The count was already closed (posted or cancelled elsewhere). */
    data object CountClosed : StockCountEvent

    data class BarcodeNotFound(val barcode: String) : StockCountEvent

    data object ScannerUnavailable : StockCountEvent

    data object Failed : StockCountEvent
}

private data class CountLocal(
    val stage: CountStage = CountStage.COUNTING,
    val searchText: String = "",
    val reason: CountReason = CountReason.ROUTINE,
    val note: String = "",
    val editor: CountEditor? = null,
    val confirmOpen: Boolean = false,
    val cancelConfirmOpen: Boolean = false,
    val isPosting: Boolean = false,
)

/**
 * Counting the shelves. Start a count, scan or search items and type what is really there, review the
 * differences, then post them as stock adjustments. Until the person confirms the post, no stock changes.
 */
class StockCountViewModel(
    private val inventory: InventoryRepository,
    private val counts: StockCountRepository,
    private val postCount: PostStockCountUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(CountLocal())
    private val eventChannel = Channel<StockCountEvent>(Channel.BUFFERED)
    val events: Flow<StockCountEvent> = eventChannel.receiveAsFlow()

    private var products: List<Product> = emptyList()

    private sealed interface Loaded {
        data class Data(val count: StockCount?, val products: List<Product>) : Loaded
        data object Failed : Loaded
    }

    val uiState: StateFlow<StockCountUiState> = combine(
        combine(counts.observeOpen(), inventory.observeProducts()) { count, all ->
            // Restaurant items sold without a stock count have nothing to count.
            val list = all.filter { it.tracksStock }
            products = list
            Loaded.Data(count, list) as Loaded
        }.catch { emit(Loaded.Failed) },
        local,
    ) { loaded, state ->
        when (loaded) {
            Loaded.Failed -> StockCountUiState(isLoading = false, loadFailed = true)
            is Loaded.Data -> {
                val rows = loaded.count?.let { StockCounts.rows(it, loaded.products) }.orEmpty().asReversed()
                StockCountUiState(
                    isLoading = false,
                    count = loaded.count,
                    // Without a count in progress there is nothing to review.
                    stage = if (loaded.count == null) CountStage.COUNTING else state.stage,
                    searchText = state.searchText,
                    searchResults = search(loaded.products, state.searchText),
                    rows = rows,
                    summary = StockCounts.summary(rows),
                    reason = state.reason,
                    note = state.note,
                    editor = state.editor,
                    confirmOpen = state.confirmOpen && loaded.count != null,
                    cancelConfirmOpen = state.cancelConfirmOpen,
                    isPosting = state.isPosting,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StockCountUiState())

    private fun search(list: List<Product>, text: String): List<CountCandidate> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        return list.asSequence()
            .filter { InventoryFilter.matchesText(it, query) }
            .flatMap { product -> product.variants.asSequence().map { CountCandidate(product, it) } }
            .take(MAX_RESULTS)
            .toList()
    }

    fun start() {
        viewModelScope.launch {
            runCatching { counts.start() }.onFailure { eventChannel.send(StockCountEvent.Failed) }
        }
    }

    fun onSearchTextChange(text: String) = local.update { it.copy(searchText = text) }

    /** Search submitted with Enter, which is what USB and Bluetooth scanners send after the code. */
    fun onSearchSubmitted() {
        val text = local.value.searchText.trim()
        if (text.isEmpty() || text.contains(' ')) return
        if (BarcodeRules.check(text) !is BarcodeCheck.Valid) return
        lookUp(text, reportMissing = false)
    }

    fun onBarcodeScanned(value: String) = lookUp(value.trim(), reportMissing = true)

    fun onScannerUnavailable() {
        eventChannel.trySend(StockCountEvent.ScannerUnavailable)
    }

    private fun lookUp(barcode: String, reportMissing: Boolean) {
        viewModelScope.launch {
            val match = runCatching { inventory.findByBarcode(barcode) }.getOrNull()
            if (match != null) {
                openEditor(match.product, match.variant)
            } else if (reportMissing) {
                eventChannel.send(StockCountEvent.BarcodeNotFound(barcode))
            }
        }
    }

    fun openEditor(candidate: CountCandidate) = openEditor(candidate.product, candidate.variant)

    fun openEditor(row: CountReviewRow) = openEditor(row.product, row.variant)

    private fun openEditor(product: Product, variant: ProductVariant) {
        if (uiState.value.count == null) return
        val existing = uiState.value.count?.line(variant.id)
        val text = existing?.let { product.stockUnit.toEntryText(it.counted) }.orEmpty()
        local.update { it.copy(editor = CountEditor(product, variant, text)) }
    }

    fun onCountTextChange(text: String) = local.update { s -> s.copy(editor = s.editor?.copy(text = text, error = false)) }

    fun dismissEditor() = local.update { it.copy(editor = null) }

    /** Saves the typed quantity. The expected stock is read now, so sales made while counting are not lost. */
    fun saveCount() {
        val editor = local.value.editor ?: return
        val count = uiState.value.count ?: return
        val counted = StockCounts.parseCounted(editor.text, editor.product)
        if (counted == null) {
            local.update { it.copy(editor = editor.copy(error = true)) }
            return
        }
        val expected = products.flatMap { it.variants }.firstOrNull { it.id == editor.variant.id }?.stockQuantity ?: editor.variant.stockQuantity
        viewModelScope.launch {
            val saved = runCatching { counts.setLine(count.id, CountLine(editor.variant.id, expected, counted)) }.getOrNull()
            when (saved) {
                true -> local.update { it.copy(editor = null, searchText = "") }
                false -> {
                    local.update { it.copy(editor = null) }
                    eventChannel.send(StockCountEvent.CountClosed)
                }
                null -> eventChannel.send(StockCountEvent.Failed)
            }
        }
    }

    fun removeLine(variantId: Long) {
        val count = uiState.value.count ?: return
        viewModelScope.launch {
            val removed = runCatching { counts.removeLine(count.id, variantId) }.getOrNull()
            if (removed == null) eventChannel.send(StockCountEvent.Failed)
        }
    }

    fun goToReview() = local.update { it.copy(stage = CountStage.REVIEW, editor = null, searchText = "") }

    fun backToCounting() = local.update { it.copy(stage = CountStage.COUNTING, confirmOpen = false) }

    fun onReasonChange(reason: CountReason) = local.update { it.copy(reason = reason) }

    fun onNoteChange(note: String) = local.update { it.copy(note = note.take(StockCounts.NOTE_MAX)) }

    fun requestPost() = local.update { it.copy(confirmOpen = true) }

    fun dismissConfirm() = local.update { it.copy(confirmOpen = false) }

    /** Posts the differences as adjustments. Only called from the confirmation dialog. */
    fun confirmPost() {
        val state = uiState.value
        val count = state.count ?: return
        if (local.value.isPosting) return
        local.update { it.copy(isPosting = true, confirmOpen = false) }
        viewModelScope.launch {
            val result = runCatching { postCount(count, state.reason, state.note) }.getOrNull()
            local.update { it.copy(isPosting = false) }
            when (result) {
                is PostCountResult.Posted -> {
                    local.update { CountLocal() }
                    eventChannel.send(StockCountEvent.Posted(result.adjusted, result.matched))
                }
                is PostCountResult.Partial -> {
                    local.update { it.copy(stage = CountStage.COUNTING) }
                    eventChannel.send(StockCountEvent.Partial(result.adjusted, result.failed.size))
                }
                PostCountResult.NotOpen -> eventChannel.send(StockCountEvent.CountClosed)
                PostCountResult.Failed, null -> eventChannel.send(StockCountEvent.Failed)
            }
        }
    }

    fun requestCancel() = local.update { it.copy(cancelConfirmOpen = true) }

    fun dismissCancel() = local.update { it.copy(cancelConfirmOpen = false) }

    /** Throws the count away. No stock changes. */
    fun confirmCancel() {
        val count = uiState.value.count ?: return
        local.update { CountLocal() }
        viewModelScope.launch {
            val done = runCatching { counts.cancel(count.id) }
            eventChannel.send(if (done.isSuccess) StockCountEvent.Cancelled else StockCountEvent.Failed)
        }
    }

    companion object {
        const val MAX_RESULTS = 30

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                StockCountViewModel(
                    inventory = container.inventoryRepository,
                    counts = container.stockCountRepository,
                    postCount = container.postStockCount,
                )
            }
        }
    }
}
