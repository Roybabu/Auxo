// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.export.ExportFile
import com.auxo.pos.data.repository.AccountPaymentResult
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.SaveCustomerResult
import com.auxo.pos.domain.contacts.ContactError
import com.auxo.pos.domain.contacts.ContactField
import com.auxo.pos.domain.contacts.CustomerForm
import com.auxo.pos.domain.contacts.CustomerValidation
import com.auxo.pos.domain.contacts.CustomerValidator
import com.auxo.pos.domain.customers.AccountPaymentCheck
import com.auxo.pos.domain.customers.AccountPaymentError
import com.auxo.pos.domain.customers.AccountPaymentValidator
import com.auxo.pos.domain.customers.CustomerHistory
import com.auxo.pos.domain.customers.DuesReminder
import com.auxo.pos.domain.customers.OwingSort
import com.auxo.pos.domain.customers.ReminderShop
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.billing.BusinessTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.auxo.pos.domain.customers.Ledger
import com.auxo.pos.domain.customers.LedgerLine
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.reports.ReportCsv
import com.auxo.pos.domain.reports.Reports
import com.auxo.pos.domain.usecase.ReceiveAccountPaymentUseCase
import com.auxo.pos.domain.usecase.ReceivePaymentResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// Customer list

enum class CustomerFilter { ALL, OWING }

data class CustomerListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val query: String = "",
    val filter: CustomerFilter = CustomerFilter.ALL,
    val accounts: List<CustomerAccount> = emptyList(),
    val customerCount: Int = 0,
    /** Total owed to the store across all accounts. */
    val totalDue: Money = Money.ZERO,
    val owingCount: Int = 0,
    /** How the Owing list is ordered. */
    val sort: OwingSort = OwingSort.AMOUNT,
    /** Whole days each owing customer has owed money, by customer id. */
    val ageDays: Map<Long, Int> = emptyMap(),
    /** What reminders say about the shop; null until the settings are read. */
    val reminderShop: ReminderShop? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModel(
    private val customers: CustomerRepository,
    private val settings: SettingsRepository? = null,
    private val clock: Clock = BusinessTime.clock(),
) : ViewModel() {

    private data class Query(val filter: CustomerFilter = CustomerFilter.ALL, val text: String = "", val sort: OwingSort = OwingSort.AMOUNT)

    private val filters = MutableStateFlow(Query())
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<CustomerListUiState> = reload
        .flatMapLatest {
            val shop = (settings?.observeSettings() ?: flowOf(null)).map { s ->
                s?.let { ReminderShop.of(it.storeName, it.upiId, it.receipt.language) }
            }.catch { emit(null) }
            combine(customers.observeAccounts(), filters, shop) { accounts, query, reminderShop ->
                val owing = accounts.filter { it.balance > Money.ZERO }
                val today = LocalDate.now(clock)
                CustomerListUiState(
                    isLoading = false,
                    query = query.text,
                    filter = query.filter,
                    accounts = filterAccounts(accounts, query.filter, query.text, query.sort),
                    customerCount = accounts.size,
                    totalDue = owing.fold(Money.ZERO) { acc, a -> acc + a.balance },
                    owingCount = owing.size,
                    sort = query.sort,
                    ageDays = owing.mapNotNull { a -> DuesReminder.ageInDays(a.owingSinceMillis, today, clock.zone)?.let { a.customer.id to it } }.toMap(),
                    reminderShop = reminderShop,
                )
            }.catch { emit(CustomerListUiState(isLoading = false, loadFailed = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CustomerListUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(text = query.take(40)) }

    fun onFilterChange(filter: CustomerFilter) = filters.update { it.copy(filter = filter) }

    fun onSortChange(sort: OwingSort) = filters.update { it.copy(sort = sort) }

    fun retry() = reload.update { it + 1 }

    companion object {
        /**
         * Name, phone or GSTIN matches; with [CustomerFilter.OWING] only accounts with money due, largest first or
         * (with [OwingSort.AGE]) the longest overdue first.
         */
        fun filterAccounts(accounts: List<CustomerAccount>, filter: CustomerFilter, query: String, sort: OwingSort = OwingSort.AMOUNT): List<CustomerAccount> {
            val text = query.trim()
            val digits = text.filter { it.isDigit() }
            val matching = accounts.filter { account ->
                val customer = account.customer
                text.isEmpty() ||
                    customer.name.contains(text, ignoreCase = true) ||
                    (digits.length >= 3 && customer.phone?.contains(digits) == true) ||
                    customer.gstin?.contains(text, ignoreCase = true) == true
            }
            return when (filter) {
                CustomerFilter.ALL -> matching
                CustomerFilter.OWING -> DuesReminder.sortOwing(matching, sort)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CustomerListViewModel(container.customerRepository, container.settingsRepository, container.clock)
            }
        }
    }
}

// Customer detail

sealed interface CustomerDetailUiState {
    data object Loading : CustomerDetailUiState
    data object NotFound : CustomerDetailUiState
    data object Failed : CustomerDetailUiState

    data class Ready(
        val account: CustomerAccount,
        /** Ledger with running balance, newest first for display. */
        val statement: List<LedgerLine>,
        val invoices: List<InvoiceSummary>,
        /** Sales put on the account and payments received, all time. */
        val charged: Money,
        val paid: Money,
        /** Loyalty point entries, newest first. Empty if the customer never had any. */
        val pointsHistory: List<LedgerEntry> = emptyList(),
        /** What a dues reminder says about the shop; null until the settings are read. */
        val reminderShop: ReminderShop? = null,
    ) : CustomerDetailUiState {
        val hasPoints: Boolean get() = pointsHistory.isNotEmpty()
    }
}

sealed interface CustomerDetailEvent {
    data class Export(val file: ExportFile) : CustomerDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    private val customerId: Long,
    private val customers: CustomerRepository,
    private val clock: Clock,
    private val settings: SettingsRepository? = null,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<CustomerDetailEvent>(Channel.BUFFERED)
    val events: Flow<CustomerDetailEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CustomerDetailUiState> = reload
        .flatMapLatest {
            val shop = (settings?.observeSettings() ?: flowOf(null)).map { s ->
                s?.let { ReminderShop.of(it.storeName, it.upiId, it.receipt.language) }
            }.catch { emit(null) }
            combine(
                customers.observeAccount(customerId),
                customers.observeLedger(customerId),
                customers.observeInvoices(customerId),
                shop,
            ) { account, ledger, invoices, reminderShop ->
                if (account == null) {
                    CustomerDetailUiState.NotFound
                } else {
                    val (charged, paid) = Ledger.totals(ledger)
                    CustomerDetailUiState.Ready(
                        account = account,
                        statement = Ledger.statement(ledger).asReversed(),
                        invoices = invoices,
                        charged = charged,
                        paid = paid,
                        pointsHistory = Ledger.pointsHistory(ledger),
                        reminderShop = reminderShop,
                    )
                }
            }.catch<CustomerDetailUiState> { emit(CustomerDetailUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CustomerDetailUiState.Loading)

    fun retry() = reload.update { it + 1 }

    /** Statement as a CSV file, oldest entry first. */
    fun exportStatement() {
        val state = uiState.value as? CustomerDetailUiState.Ready ?: return
        val table = Reports.customerStatement(state.account.customer, state.statement.asReversed())
        val name = "statement-${state.account.customer.name}-${LocalDate.now(clock)}.csv"
        eventChannel.trySend(CustomerDetailEvent.Export(ExportFile(name, ReportCsv.write(table, clock.zone))))
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CustomerDetailViewModel(
                    customerId = createSavedStateHandle().get<Long>(ARG_CUSTOMER_ID) ?: 0L,
                    customers = container.customerRepository,
                    clock = container.clock,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}

// Customer editor

data class CustomerEditorUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isNew: Boolean = true,
    val form: CustomerForm = CustomerForm(),
    val errors: Map<ContactField, ContactError> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val visibleErrors: Map<ContactField, ContactError> get() = if (showErrors) errors else emptyMap()
}

sealed interface CustomerEditorEvent {
    data class Saved(val customerId: Long) : CustomerEditorEvent
    data object Invalid : CustomerEditorEvent
    data object Failed : CustomerEditorEvent
}

class CustomerEditorViewModel(
    private val customerId: Long,
    private val customers: CustomerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerEditorUiState(isLoading = customerId != 0L, isNew = customerId == 0L))
    val uiState: StateFlow<CustomerEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<CustomerEditorEvent>(Channel.BUFFERED)
    val events: Flow<CustomerEditorEvent> = eventChannel.receiveAsFlow()

    private var existing: Customer? = null

    init {
        if (customerId != 0L) load() else setForm(CustomerForm())
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val customer = runCatching { customers.getCustomer(customerId) }.getOrNull()
            existing = customer
            if (customer == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
                setForm(CustomerForm.from(customer))
            }
        }
    }

    private fun setForm(form: CustomerForm) {
        val errors = when (val result = CustomerValidator.validate(form, existing)) {
            is CustomerValidation.Invalid -> result.errors
            is CustomerValidation.Valid -> emptyMap()
        }
        _uiState.update { it.copy(form = form, errors = errors) }
    }

    fun onFormChange(form: CustomerForm) = setForm(form)

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val valid = when (val result = CustomerValidator.validate(state.form, existing)) {
            is CustomerValidation.Invalid -> {
                _uiState.update { it.copy(errors = result.errors, showErrors = true) }
                eventChannel.trySend(CustomerEditorEvent.Invalid)
                return
            }
            is CustomerValidation.Valid -> result
        }
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            when (val result = runCatching { customers.saveCustomer(valid.customer, valid.openingBalance) }.getOrNull()) {
                is SaveCustomerResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(CustomerEditorEvent.Saved(result.customerId))
                }
                SaveCustomerResult.PhoneInUse -> _uiState.update {
                    it.copy(isSaving = false, errors = it.errors + (ContactField.PHONE to ContactError.PHONE_IN_USE))
                }
                null -> {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(CustomerEditorEvent.Failed)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CustomerEditorViewModel(
                    customerId = createSavedStateHandle().get<Long>(CustomerDetailViewModel.ARG_CUSTOMER_ID) ?: 0L,
                    customers = appContainer().customerRepository,
                )
            }
        }
    }
}

// Receive payment on account

data class AccountPaymentUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val account: CustomerAccount? = null,
    val amount: String = "",
    val method: PaymentMethod = PaymentMethod.CASH,
    val reference: String = "",
    val note: String = "",
    val errors: Set<AccountPaymentError> = emptySet(),
    val isSaving: Boolean = false,
    /** One id per screen, so a repeated save records the payment once. */
    val requestId: String = UUID.randomUUID().toString(),
) {
    val balance: Money get() = account?.balance ?: Money.ZERO

    /** Balance after this payment, while the amount is valid. */
    val balanceAfter: Money?
        get() = Money.parseOrNull(amount)?.takeIf { it > Money.ZERO && it <= balance }?.let { balance - it }
}

sealed interface AccountPaymentEvent {
    data class Saved(val amount: Money) : AccountPaymentEvent
    data object CustomerMissing : AccountPaymentEvent
    data object Failed : AccountPaymentEvent
}

class AccountPaymentViewModel(
    private val customerId: Long,
    private val customers: CustomerRepository,
    private val receivePayment: ReceiveAccountPaymentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountPaymentUiState())
    val uiState: StateFlow<AccountPaymentUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AccountPaymentEvent>(Channel.BUFFERED)
    val events: Flow<AccountPaymentEvent> = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val account = runCatching { customers.observeAccount(customerId).first() }.getOrNull()
            _uiState.update {
                if (account == null) {
                    it.copy(isLoading = false, loadFailed = true)
                } else {
                    it.copy(
                        isLoading = false,
                        account = account,
                        amount = it.amount.ifEmpty { account.balance.takeIf { b -> b > Money.ZERO }?.toDecimalString().orEmpty() },
                    )
                }
            }
        }
    }

    fun onAmountChange(text: String) = _uiState.update {
        it.copy(amount = text.filter { c -> c.isDigit() || c == '.' }.take(12), errors = emptySet())
    }

    fun onMethodChange(method: PaymentMethod) {
        if (!method.movesMoney) return
        _uiState.update { it.copy(method = method, reference = "", errors = emptySet()) }
    }

    fun onReferenceChange(text: String) = _uiState.update { it.copy(reference = text.trim().take(35), errors = emptySet()) }

    fun onNoteChange(text: String) = _uiState.update { it.copy(note = text.take(AccountPaymentValidator.NOTE_MAX + 20), errors = emptySet()) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.account == null) return
        val check = AccountPaymentValidator.check(state.amount, state.method, state.reference, state.note, state.balance)
        if (check is AccountPaymentCheck.Invalid) {
            _uiState.update { it.copy(errors = check.errors) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = runCatching {
                receivePayment(customerId, state.amount, state.method, state.reference, state.note, state.requestId)
            }.getOrNull()
            when (result) {
                is ReceivePaymentResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.check.errors) }
                is ReceivePaymentResult.Saved -> when (val saved = result.result) {
                    is AccountPaymentResult.Recorded -> {
                        _uiState.update { it.copy(isSaving = false) }
                        eventChannel.send(AccountPaymentEvent.Saved((check as AccountPaymentCheck.Valid).amount))
                    }
                    is AccountPaymentResult.MoreThanDue -> _uiState.update {
                        it.copy(
                            isSaving = false,
                            account = it.account?.copy(balance = saved.balance),
                            errors = setOf(AccountPaymentError.MORE_THAN_DUE),
                        )
                    }
                    AccountPaymentResult.CustomerMissing -> {
                        _uiState.update { it.copy(isSaving = false) }
                        eventChannel.send(AccountPaymentEvent.CustomerMissing)
                    }
                }
                null -> {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(AccountPaymentEvent.Failed)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AccountPaymentViewModel(
                    customerId = createSavedStateHandle().get<Long>(CustomerDetailViewModel.ARG_CUSTOMER_ID) ?: 0L,
                    customers = container.customerRepository,
                    receivePayment = container.receiveAccountPayment,
                )
            }
        }
    }
}

// Customer history

sealed interface CustomerHistoryUiState {
    data object Loading : CustomerHistoryUiState
    data object NotFound : CustomerHistoryUiState
    data object Failed : CustomerHistoryUiState

    data class Ready(
        val customer: Customer,
        val history: CustomerHistory,
    ) : CustomerHistoryUiState
}

class CustomerHistoryViewModel(
    private val customerId: Long,
    private val customers: CustomerRepository,
    private val zone: ZoneId,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CustomerHistoryUiState> = reload
        .flatMapLatest {
            combine(customers.observeAccount(customerId), customers.observeInvoices(customerId)) { account, invoices ->
                if (account == null) {
                    CustomerHistoryUiState.NotFound
                } else {
                    CustomerHistoryUiState.Ready(account.customer, CustomerHistory.from(invoices, zone))
                }
            }.catch<CustomerHistoryUiState> { emit(CustomerHistoryUiState.Failed) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CustomerHistoryUiState.Loading)

    fun retry() = reload.update { it + 1 }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CustomerHistoryViewModel(
                    customerId = createSavedStateHandle().get<Long>(CustomerDetailViewModel.ARG_CUSTOMER_ID) ?: 0L,
                    customers = container.customerRepository,
                    zone = container.clock.zone,
                )
            }
        }
    }
}
