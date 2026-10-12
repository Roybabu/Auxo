package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.auth.Permission
import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.ReturnError
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.usecase.ProcessReturnResult
import com.invision.pos.domain.usecase.ProcessReturnUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// Invoice detail

/** Message shown once when an invoice opens right after it was saved. */
enum class InvoiceNotice { SALE_SAVED, RETURN_SAVED }

data class InvoiceNoticeMessage(val notice: InvoiceNotice, val change: Money)

sealed interface InvoiceDetailUiState {
    data object Loading : InvoiceDetailUiState
    data object NotFound : InvoiceDetailUiState
    data object Failed : InvoiceDetailUiState

    data class Ready(
        val invoice: Invoice,
        val creditNotes: List<InvoiceSummary>,
        val settings: StoreSettings,
        /** Paper chosen for this preview; starts with the store's default. */
        val paper: ReceiptPaper,
    ) : InvoiceDetailUiState {
        val receiptOptions: ReceiptOptions get() = settings.receipt.copy(paper = paper)
        val canReturn: Boolean get() = invoice.type == InvoiceType.SALE && !invoice.isFullyReturned
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceDetailViewModel(
    private val invoiceId: Long,
    private val billing: BillingRepository,
    private val settingsRepository: SettingsRepository,
    notice: InvoiceNoticeMessage? = null,
) : ViewModel() {

    private val _notice = MutableStateFlow(notice)

    /** Shown once by the screen, then cleared with [noticeShown]. */
    val notice: StateFlow<InvoiceNoticeMessage?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    private val reload = MutableStateFlow(0)
    private val paper = MutableStateFlow<ReceiptPaper?>(null)

    val uiState: StateFlow<InvoiceDetailUiState> = reload
        .flatMapLatest {
            combine(
                billing.observeInvoice(invoiceId),
                billing.observeCreditNotesFor(invoiceId),
                settingsRepository.observeSettings(),
                paper,
            ) { invoice, creditNotes, settings, chosenPaper ->
                if (invoice == null) {
                    InvoiceDetailUiState.NotFound
                } else {
                    InvoiceDetailUiState.Ready(invoice, creditNotes, settings, chosenPaper ?: settings.receipt.paper)
                }
            }.catch<InvoiceDetailUiState> { emit(InvoiceDetailUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), InvoiceDetailUiState.Loading)

    fun onPaperChange(value: ReceiptPaper) {
        paper.value = value
    }

    fun retry() = reload.update { it + 1 }

    companion object {
        const val ARG_INVOICE_ID = "invoiceId"
        const val ARG_NOTICE = "notice"
        const val ARG_CHANGE = "change"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val handle = createSavedStateHandle()
                val notice = handle.get<String>(ARG_NOTICE)?.let { name -> InvoiceNotice.entries.firstOrNull { it.name == name } }
                // Shown only once, even if the screen is recreated.
                handle[ARG_NOTICE] = null
                InvoiceDetailViewModel(
                    invoiceId = handle.get<Long>(ARG_INVOICE_ID) ?: 0L,
                    billing = container.billingRepository,
                    settingsRepository = container.settingsRepository,
                    notice = notice?.let { InvoiceNoticeMessage(it, Money(handle.get<Long>(ARG_CHANGE) ?: 0L)) },
                )
            }
        }
    }
}

// Invoice history and payments

enum class HistoryRange { TODAY, LAST_7_DAYS, THIS_MONTH, ALL }

enum class HistoryTab { INVOICES, PAYMENTS }

/**
 * Money taken and given back. Cash, UPI and card include payments received
 * against customer accounts; sales put on account are counted separately as
 * they bring in no money yet, and refunds credited to an account are not money out.
 */
data class PaymentTotals(
    val cash: Money = Money.ZERO,
    val upi: Money = Money.ZERO,
    val card: Money = Money.ZERO,
    val refunds: Money = Money.ZERO,
    /** Sales put on customer accounts. */
    val onAccount: Money = Money.ZERO,
    /** Part of cash, UPI and card received against accounts. */
    val accountPayments: Money = Money.ZERO,
) {
    val received: Money get() = cash + upi + card
    val net: Money get() = received - refunds

    companion object {
        fun of(payments: List<PaymentRecord>): PaymentTotals = payments.fold(PaymentTotals()) { totals, p ->
            when {
                p.kind == PaymentKind.REFUND && p.method == PaymentMethod.CREDIT -> totals
                p.kind == PaymentKind.REFUND -> totals.copy(refunds = totals.refunds + p.amount)
                else -> {
                    val fromAccount = if (p.customerName != null) p.amount else Money.ZERO
                    when (p.method) {
                        PaymentMethod.CASH -> totals.copy(cash = totals.cash + p.amount, accountPayments = totals.accountPayments + fromAccount)
                        PaymentMethod.UPI -> totals.copy(upi = totals.upi + p.amount, accountPayments = totals.accountPayments + fromAccount)
                        PaymentMethod.CARD -> totals.copy(card = totals.card + p.amount, accountPayments = totals.accountPayments + fromAccount)
                        PaymentMethod.CREDIT -> totals.copy(onAccount = totals.onAccount + p.amount)
                    }
                }
            }
        }
    }
}

data class InvoiceHistoryUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val tab: HistoryTab = HistoryTab.INVOICES,
    val range: HistoryRange = HistoryRange.TODAY,
    val query: String = "",
    val invoices: List<InvoiceSummary> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val paymentTotals: PaymentTotals = PaymentTotals(),
    val salesTotal: Money = Money.ZERO,
    val returnsTotal: Money = Money.ZERO,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceHistoryViewModel(
    private val billing: BillingRepository,
    private val clock: Clock,
    private val customers: CustomerRepository? = null,
    /** False for a person who may open invoices but not see payment records or business totals (an Employee). */
    private val showFinancials: Boolean = true,
) : ViewModel() {

    private val filters = MutableStateFlow(Triple(HistoryTab.INVOICES, HistoryRange.TODAY, ""))
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<InvoiceHistoryUiState> = combine(filters, reload) { f, _ -> f }
        .flatMapLatest { (tab, range, query) ->
            val (from, to) = bounds(range)
            if (!showFinancials) {
                // Only the list of invoices is read: no payment records, no totals.
                return@flatMapLatest billing.observeInvoices(from, to).map { invoices ->
                    InvoiceHistoryUiState(isLoading = false, tab = HistoryTab.INVOICES, range = range, query = query, invoices = filterInvoices(invoices, query))
                }.catch { emit(InvoiceHistoryUiState(isLoading = false, loadFailed = true, tab = HistoryTab.INVOICES, range = range, query = query)) }
            }
            val accountPayments = customers?.observeAccountPayments(from, to) ?: flowOf(emptyList())
            combine(billing.observeInvoices(from, to), billing.observePayments(from, to), accountPayments) { invoices, invoicePayments, fromAccounts ->
                val filtered = filterInvoices(invoices, query)
                val payments = (invoicePayments + fromAccounts).sortedWith(compareByDescending<PaymentRecord> { it.createdAtMillis }.thenByDescending { it.id })
                val filteredPayments = if (query.isBlank()) {
                    payments
                } else {
                    payments.filter {
                        it.invoiceNumber?.contains(query.trim(), ignoreCase = true) == true ||
                            it.customerName?.contains(query.trim(), ignoreCase = true) == true
                    }
                }
                InvoiceHistoryUiState(
                    isLoading = false,
                    tab = tab,
                    range = range,
                    query = query,
                    invoices = filtered,
                    payments = filteredPayments,
                    paymentTotals = PaymentTotals.of(filteredPayments),
                    salesTotal = filtered.filter { it.type == InvoiceType.SALE }.fold(Money.ZERO) { a, i -> a + i.grandTotal },
                    returnsTotal = filtered.filter { it.type == InvoiceType.RETURN }.fold(Money.ZERO) { a, i -> a + i.grandTotal },
                )
            }.catch { emit(InvoiceHistoryUiState(isLoading = false, loadFailed = true, tab = tab, range = range, query = query)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), InvoiceHistoryUiState())

    fun onTabChange(tab: HistoryTab) = filters.update { it.copy(first = tab) }

    fun onRangeChange(range: HistoryRange) = filters.update { it.copy(second = range) }

    fun onQueryChange(query: String) = filters.update { it.copy(third = query) }

    fun retry() = reload.update { it + 1 }

    private fun bounds(range: HistoryRange): Pair<Long, Long> {
        val today = LocalDate.now(clock)
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val startDate = when (range) {
            HistoryRange.TODAY -> today
            HistoryRange.LAST_7_DAYS -> today.minusDays(6)
            HistoryRange.THIS_MONTH -> today.withDayOfMonth(1)
            HistoryRange.ALL -> return 0L to Long.MAX_VALUE
        }
        return startDate.atStartOfDay(clock.zone).toInstant().toEpochMilli() to end
    }

    companion object {
        fun filterInvoices(invoices: List<InvoiceSummary>, query: String): List<InvoiceSummary> {
            val text = query.trim()
            if (text.isEmpty()) return invoices
            val digits = text.filter { it.isDigit() }
            return invoices.filter { invoice ->
                invoice.number.contains(text, ignoreCase = true) ||
                    invoice.buyerName?.contains(text, ignoreCase = true) == true ||
                    (digits.length >= 4 && invoice.buyerPhone?.contains(digits) == true)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                InvoiceHistoryViewModel(
                    container.billingRepository,
                    container.clock,
                    container.customerRepository,
                    showFinancials = container.authorizer.can(Permission.VIEW_FINANCIALS),
                )
            }
        }
    }
}

// Returns

data class ReturnUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val invoice: Invoice? = null,
    /** Units to return per sale line id. */
    val quantities: Map<Long, Int> = emptyMap(),
    val refundMethod: PaymentMethod = PaymentMethod.CASH,
    val reason: String = "",
    /** Refund for the current selection, or null when nothing is selected. */
    val refund: Money? = null,
    val error: ReturnError? = null,
    val isSaving: Boolean = false,
) {
    val selectedUnits: Int get() = quantities.values.sum()
}

sealed interface ReturnEvent {
    data class Completed(val creditNoteId: Long, val number: String) : ReturnEvent
    data object AlreadyReturned : ReturnEvent
    data object Failed : ReturnEvent
}

class ReturnViewModel(
    private val invoiceId: Long,
    private val billing: BillingRepository,
    private val processReturn: ProcessReturnUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReturnUiState())
    val uiState: StateFlow<ReturnUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ReturnEvent>(Channel.BUFFERED)
    val events: Flow<ReturnEvent> = eventChannel.receiveAsFlow()

    /**
     * Stays the same while the same return is submitted again (a second tap, or a retry after
     * the reply was lost), so it is saved once; any change to the return gets a new one.
     */
    private var requestKey: Triple<Map<Long, Int>, PaymentMethod, String>? = null
    private var requestId: String = UUID.randomUUID().toString()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val invoice = runCatching { billing.getInvoice(invoiceId) }.getOrNull()
            if (invoice == null || invoice.type != InvoiceType.SALE) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, invoice = invoice, quantities = invoice.lines.associate { l -> l.id to 0 }) }
                preview()
            }
        }
    }

    fun setQuantity(lineId: Long, quantity: Int) {
        val line = _uiState.value.invoice?.lines?.firstOrNull { it.id == lineId } ?: return
        _uiState.update { it.copy(quantities = it.quantities + (lineId to quantity.coerceIn(0, line.returnableQuantity)), error = null) }
        preview()
    }

    fun returnAll() {
        val invoice = _uiState.value.invoice ?: return
        _uiState.update { it.copy(quantities = invoice.lines.associate { l -> l.id to l.returnableQuantity }, error = null) }
        preview()
    }

    fun onRefundMethodChange(method: PaymentMethod) = _uiState.update { it.copy(refundMethod = method) }

    fun onReasonChange(reason: String) = _uiState.update { it.copy(reason = reason.take(REASON_MAX)) }

    fun confirm() {
        val state = _uiState.value
        if (state.isSaving || state.invoice == null) return
        _uiState.update { it.copy(isSaving = true) }
        val key = Triple(state.quantities.filterValues { it > 0 }, state.refundMethod, state.reason.trim())
        if (key != requestKey) {
            requestKey = key
            requestId = UUID.randomUUID().toString()
        }
        val id = requestId
        viewModelScope.launch {
            val result = runCatching {
                processReturn(invoiceId, state.quantities, state.refundMethod, state.reason, id)
            }.getOrNull()
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is ProcessReturnResult.Completed -> eventChannel.send(ReturnEvent.Completed(result.creditNoteId, result.number))
                is ProcessReturnResult.Rejected -> {
                    _uiState.update { it.copy(error = result.error) }
                    // The units may have been returned a moment ago (for example, the reply was lost): show the sale as it is now.
                    if (result.error == ReturnError.QUANTITY_TOO_LARGE) load()
                }
                ProcessReturnResult.AlreadyReturned -> {
                    eventChannel.send(ReturnEvent.AlreadyReturned)
                    load()
                }
                ProcessReturnResult.InvoiceNotFound, null -> eventChannel.send(ReturnEvent.Failed)
            }
        }
    }

    private fun preview() {
        val state = _uiState.value
        val invoice = state.invoice ?: return
        val build = ReturnBuilder.build(invoice, state.quantities, state.refundMethod, state.reason, clock.millis())
        _uiState.update { it.copy(refund = (build as? ReturnBuild.Ready)?.creditNote?.grandTotal) }
    }

    companion object {
        const val ARG_INVOICE_ID = "invoiceId"
        const val REASON_MAX = 120

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ReturnViewModel(
                    invoiceId = createSavedStateHandle().get<Long>(ARG_INVOICE_ID) ?: 0L,
                    billing = container.billingRepository,
                    processReturn = container.processReturn,
                    clock = container.clock,
                )
            }
        }
    }
}
