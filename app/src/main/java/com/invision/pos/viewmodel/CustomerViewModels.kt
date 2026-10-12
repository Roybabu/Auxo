package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.export.ExportFile
import com.invision.pos.data.repository.AccountPaymentResult
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.domain.contacts.ContactError
import com.invision.pos.domain.contacts.ContactField
import com.invision.pos.domain.contacts.CustomerForm
import com.invision.pos.domain.contacts.CustomerValidation
import com.invision.pos.domain.contacts.CustomerValidator
import com.invision.pos.domain.customers.AccountPaymentCheck
import com.invision.pos.domain.customers.AccountPaymentError
import com.invision.pos.domain.customers.AccountPaymentValidator
import com.invision.pos.domain.customers.CustomerHistory
import com.invision.pos.domain.customers.Ledger
import com.invision.pos.domain.customers.LedgerLine
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.reports.ReportCsv
import com.invision.pos.domain.reports.Reports
import com.invision.pos.domain.usecase.ReceiveAccountPaymentUseCase
import com.invision.pos.domain.usecase.ReceivePaymentResult
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
)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModel(private val customers: CustomerRepository) : ViewModel() {

    private val filters = MutableStateFlow(CustomerFilter.ALL to "")
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<CustomerListUiState> = reload
        .flatMapLatest {
            combine(customers.observeAccounts(), filters) { accounts, (filter, query) ->
                val owing = accounts.filter { it.balance > Money.ZERO }
                CustomerListUiState(
                    isLoading = false,
                    query = query,
                    filter = filter,
                    accounts = filterAccounts(accounts, filter, query),
                    customerCount = accounts.size,
                    totalDue = owing.fold(Money.ZERO) { acc, a -> acc + a.balance },
                    owingCount = owing.size,
                )
            }.catch { emit(CustomerListUiState(isLoading = false, loadFailed = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CustomerListUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(second = query.take(40)) }

    fun onFilterChange(filter: CustomerFilter) = filters.update { it.copy(first = filter) }

    fun retry() = reload.update { it + 1 }

    companion object {
        /** Name, phone or GSTIN matches; with [CustomerFilter.OWING] only accounts with money due, largest first. */
        fun filterAccounts(accounts: List<CustomerAccount>, filter: CustomerFilter, query: String): List<CustomerAccount> {
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
                CustomerFilter.OWING -> matching.filter { it.balance > Money.ZERO }.sortedByDescending { it.balance }
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CustomerListViewModel(appContainer().customerRepository) }
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
    ) : CustomerDetailUiState
}

sealed interface CustomerDetailEvent {
    data class Export(val file: ExportFile) : CustomerDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    private val customerId: Long,
    private val customers: CustomerRepository,
    private val clock: Clock,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<CustomerDetailEvent>(Channel.BUFFERED)
    val events: Flow<CustomerDetailEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CustomerDetailUiState> = reload
        .flatMapLatest {
            combine(
                customers.observeAccount(customerId),
                customers.observeLedger(customerId),
                customers.observeInvoices(customerId),
            ) { account, ledger, invoices ->
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
