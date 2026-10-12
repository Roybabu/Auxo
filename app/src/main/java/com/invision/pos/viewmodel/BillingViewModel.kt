package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.BusinessTime
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.MenuRepository
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.barcode.BarcodeCheck
import com.invision.pos.domain.barcode.BarcodeRules
import com.invision.pos.domain.billing.AmountInput
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.BillTotals
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartCheck
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CustomItem
import com.invision.pos.domain.billing.CustomItemResult
import com.invision.pos.domain.billing.CartValidator
import com.invision.pos.domain.billing.CreditAccount
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.Discount
import com.invision.pos.domain.billing.DiscountParse
import com.invision.pos.domain.billing.PaymentCheck
import com.invision.pos.domain.billing.PaymentDraft
import com.invision.pos.domain.billing.PaymentError
import com.invision.pos.domain.billing.PaymentValidator
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.contacts.ContactField
import com.invision.pos.domain.contacts.CustomerForm
import com.invision.pos.domain.contacts.CustomerValidation
import com.invision.pos.domain.contacts.CustomerValidator
import com.invision.pos.domain.inventory.InventoryFilter
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.usecase.CheckoutResult
import com.invision.pos.domain.usecase.CheckoutUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** One variant offered while searching. */
data class ProductSearchResult(val product: Product, val variant: ProductVariant) {
    val inCartKey: Long get() = variant.id
}

enum class BillingStage { CART, PAYMENT }

/** Text being typed for a line or bill discount. */
data class DiscountInput(val text: String = "", val isPercent: Boolean = false, val invalid: Boolean = false)

data class BillingUiState(
    /** Null while settings load. */
    val settings: StoreSettings? = null,
    val productsLoaded: Boolean = false,
    /** Products for sale in inventory; zero means there is nothing to sell yet. */
    val productCount: Int = 0,
    val loadFailed: Boolean = false,
    val cart: Cart = Cart(),
    val totals: BillTotals = BillTotals.EMPTY,
    val check: CartCheck = CartCheck(isEmpty = true),
    /** Problems are shown after the first attempt to charge. */
    val showErrors: Boolean = false,
    val searchText: String = "",
    val searchResults: List<ProductSearchResult> = emptyList(),
    /** Items that sold the most over the last 30 days, for quick adding when nothing is typed. */
    /** Dishes matching the search. */
    val menuResults: List<MenuItem> = emptyList(),
    /** Available dishes, for quick adding when the store has a menu. */
    val menu: List<MenuItem> = emptyList(),
    /** Menu section shown in the quick-add card; null shows the first section. */
    val menuSection: String? = null,
    val lineDiscounts: Map<Long, DiscountInput> = emptyMap(),
    /** Line whose discount editor is open. */
    val editingDiscountFor: Long? = null,
    val billDiscount: DiscountInput = DiscountInput(),
    val customerExpanded: Boolean = false,
    /** Search text for saved customers. */
    val customerQuery: String = "",
    val customerResults: List<CustomerAccount> = emptyList(),
    /** Saved customer the bill is for, with their balance. */
    val account: CustomerAccount? = null,
    /** Set when saving the typed details as a customer failed. */
    val customerSaveError: CustomerSaveError? = null,
    val stage: BillingStage = BillingStage.CART,
    val payments: List<PaymentDraft> = emptyList(),
    val paymentErrors: Map<Int, PaymentError> = emptyMap(),
    val paymentRemaining: Money = Money.ZERO,
    val paymentShowErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val needsSetup: Boolean
        get() = settings != null && (settings.needsGstinSetup || (settings.gstRegistered && settings.stateCode.isEmpty()))

    val change: Money
        get() = payments.filter { it.method == PaymentMethod.CASH }.fold(Money.ZERO) { acc, draft ->
            val amount = Money.parseOrNull(draft.amount) ?: return@fold acc
            val tendered = Money.parseOrNull(draft.tendered) ?: return@fold acc
            if (tendered > amount) acc + (tendered - amount) else acc
        }

    /** What goes on the customer's account with these payments (valid amounts only). */
    val onAccountAmount: Money
        get() = payments.filter { it.method == PaymentMethod.CREDIT }
            .fold(Money.ZERO) { acc, draft -> acc + (Money.parseOrNull(draft.amount) ?: Money.ZERO) }

    /** What is paid now, in cash, UPI or card: the bill minus what goes on account. */
    val paidNowAmount: Money
        get() = payments.filter { it.method != PaymentMethod.CREDIT }
            .fold(Money.ZERO) { acc, draft -> acc + (Money.parseOrNull(draft.amount) ?: Money.ZERO) }

    /** The customer's balance once this bill is on their account; null without a customer. */
    val balanceAfterBill: Money? get() = account?.let { it.balance + onAccountAmount }

    /** Payment methods offered: "On account" only for a saved customer. */
    val paymentMethods: List<PaymentMethod>
        get() = if (account != null) PaymentMethod.entries else PaymentMethod.MONEY

    /** Menu sections with available dishes, in menu order. */
    val menuSections: List<String> get() = menu.map { it.section }.distinct()
}

enum class CustomerSaveError { NAME_REQUIRED, INVALID_PHONE, INVALID_DETAILS, PHONE_IN_USE, FAILED }

sealed interface BillingEvent {
    data class Completed(val invoiceId: Long, val number: String, val change: Money) : BillingEvent
    data class OutOfStock(val name: String) : BillingEvent
    data class BarcodeNotFound(val barcode: String) : BillingEvent
    data object ScannerUnavailable : BillingEvent
    data object StockChanged : BillingEvent
    data class IngredientsShort(val names: List<String>) : BillingEvent
    data object CreditLimitExceeded : BillingEvent
    data object CustomerMissing : BillingEvent
    data object SetupRequired : BillingEvent
    data object FixCart : BillingEvent
    data object SaveFailed : BillingEvent
    data class CustomerSaved(val name: String) : BillingEvent
}

class BillingViewModel(
    private val inventory: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    private val checkout: CheckoutUseCase,
    private val customers: CustomerRepository? = null,
    private val menuRepository: MenuRepository? = null,
    private val clock: Clock = BusinessTime.clock(),
    private val billing: BillingRepository? = null,
    private val shop: com.invision.pos.core.shop.ShopPreferences? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BillingEvent>(Channel.BUFFERED)
    val events: Flow<BillingEvent> = eventChannel.receiveAsFlow()

    private var products: List<Product> = emptyList()

    private var allMenu: List<MenuItem> = emptyList()
    private var menuLoaded = false
    private var nextCustomKey = 0L

    /** False when the shop has the restaurant menu switched off: no dishes are offered or found. */
    private var menuEnabled = shop?.restaurantMenu?.value ?: true
    private var accounts: List<CustomerAccount> = emptyList()
    private var nextPaymentKey = 0

    /** One id per bill, so retrying "Complete sale" can't save it twice. */
    private var saleRequestId: String = UUID.randomUUID().toString()

    private var loadJobs: List<Job> = emptyList()

    private val today: LocalDate get() = LocalDate.now(clock)

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(loadFailed = false) }
        load()
    }

    private fun load() {
        loadJobs.forEach { it.cancel() }
        val settingsJob = viewModelScope.launch {
            settingsRepository.observeSettings()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { settings -> recalculate { it.copy(settings = settings) } }
        }
        val productsJob = viewModelScope.launch {
            inventory.observeProducts()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { list ->
                    products = list
                    recalculate { state ->
                        state.copy(
                            productsLoaded = true,
                            productCount = list.count { it.category.isSold },
                            cart = syncStock(state.cart),
                            searchResults = search(state.searchText),
                        )
                    }
                }
        }
        val menuJob = menuRepository?.let { repository ->
            viewModelScope.launch {
                repository.observeMenu()
                    .catch { _uiState.update { it.copy(loadFailed = true) } }
                    .collect { list ->
                        allMenu = list
                        menuLoaded = true
                        recalculate { state ->
                            state.copy(
                                menu = if (menuEnabled) list.filter { it.available } else emptyList(),
                                menuResults = searchMenu(state.searchText),
                                cart = syncStock(state.cart),
                            )
                        }
                    }
            }
        }
        val customersJob = customers?.let { repository ->
            viewModelScope.launch {
                repository.observeAccounts()
                    .catch { _uiState.update { it.copy(loadFailed = true) } }
                    .collect { list ->
                        accounts = list
                        _uiState.update { state ->
                            val linkedId = state.account?.customer?.id
                            val linked = linkedId?.let { id -> list.firstOrNull { it.customer.id == id } }
                            state.copy(customerResults = searchCustomers(state.customerQuery), account = linked ?: state.account)
                        }
                    }
            }
        }
        val shopJob = shop?.let { prefs ->
            viewModelScope.launch {
                prefs.restaurantMenu.collect { enabled ->
                    menuEnabled = enabled
                    recalculate { state ->
                        state.copy(
                            menu = if (enabled) allMenu.filter { it.available } else emptyList(),
                            menuResults = searchMenu(state.searchText),
                        )
                    }
                }
            }
        }
        loadJobs = listOfNotNull(settingsJob, productsJob, menuJob, shopJob, customersJob)
    }

    // Search and adding items

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text, searchResults = search(text), menuResults = searchMenu(text)) }
    }

    /**
     * Enter in the search box, which hardware barcode scanners also send. Adds the
     * item when the text is an exact barcode or matches a single item.
     */
    fun onSearchSubmitted() {
        val text = _uiState.value.searchText.trim()
        if (text.isEmpty()) return
        val exact = findVariantByBarcode(text)
        val state = _uiState.value
        when {
            exact != null -> add(exact.first, exact.second, clearSearch = true)
            state.searchResults.size == 1 && state.menuResults.isEmpty() -> {
                val only = state.searchResults.single()
                add(only.product, only.variant, clearSearch = true)
            }
            state.menuResults.size == 1 && state.searchResults.isEmpty() -> addMenuItem(state.menuResults.single())
            BarcodeRules.check(text) is BarcodeCheck.Valid && state.searchResults.isEmpty() && state.menuResults.isEmpty() ->
                eventChannel.trySend(BillingEvent.BarcodeNotFound(text))
        }
    }

    fun onBarcodeScanned(value: String) {
        val code = value.trim()
        val match = findVariantByBarcode(code)
        if (match == null) {
            eventChannel.trySend(BillingEvent.BarcodeNotFound(code))
        } else {
            add(match.first, match.second, clearSearch = false)
        }
    }

    fun onScannerUnavailable() {
        eventChannel.trySend(BillingEvent.ScannerUnavailable)
    }

    fun addResult(result: ProductSearchResult) = add(result.product, result.variant, clearSearch = true)

    private fun add(product: Product, variant: ProductVariant, clearSearch: Boolean) {
        val inCart = _uiState.value.cart.lines.firstOrNull { it.variantId == variant.id }?.quantity ?: 0
        val label = listOf(product.name, variant.attributesLabel).filter { it.isNotBlank() }.joinToString(" ")
        val available = variant.sellableQuantity(today)
        when {
            available <= 0 -> {
                eventChannel.trySend(BillingEvent.OutOfStock(label))
                return
            }
            // All the stock is already on the bill: nothing more to add, and no message.
            inCart + 1 > available -> return
        }
        recalculate { state ->
            state.copy(
                cart = state.cart.add(CartLine.from(product, variant, today = today)),
                searchText = if (clearSearch) "" else state.searchText,
                searchResults = if (clearSearch) emptyList() else state.searchResults,
                menuResults = if (clearSearch) emptyList() else state.menuResults,
            )
        }
    }

    /** Adds one serving of a dish. */
    fun addMenuItem(item: MenuItem) {
        if (!item.available) {
            eventChannel.trySend(BillingEvent.OutOfStock(item.name))
            return
        }
        recalculate { state ->
            state.copy(
                cart = state.cart.add(CartLine.forMenuItem(item)),
                searchText = "",
                searchResults = emptyList(),
                menuResults = emptyList(),
            )
        }
    }

    /** Puts an item that is not in the shop's list on the bill. Returns false if the details are not valid. */
    fun addCustomItem(name: String, price: String, quantity: String, gstBasisPoints: Int): Boolean {
        val draft = (CustomItem.validate(name, price, quantity, gstBasisPoints) as? CustomItemResult.Valid)?.draft ?: return false
        val key = CartLine.CUSTOM_KEY_BASE - nextCustomKey++
        recalculate { state -> state.copy(cart = state.cart.add(CartLine.custom(key, draft))) }
        return true
    }

    fun onMenuSectionChange(section: String?) = _uiState.update { it.copy(menuSection = section) }

    // Cart lines, keyed by CartLine.variantId (dishes use minus the menu item id)

    fun increment(lineKey: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == lineKey } ?: return
        // The "+" button is off at the limit; this keeps the rule in the billing logic too.
        if (!line.canIncrease) return
        setQuantity(lineKey, line.quantity + 1)
    }

    fun decrement(lineKey: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == lineKey } ?: return
        if (line.quantity > 1) setQuantity(lineKey, line.quantity - 1)
    }

    fun setQuantity(lineKey: Long, quantity: Int) = recalculate { it.copy(cart = it.cart.setQuantity(lineKey, quantity)) }

    fun removeLine(lineKey: Long) = recalculate {
        it.copy(
            cart = it.cart.remove(lineKey),
            lineDiscounts = it.lineDiscounts - lineKey,
            editingDiscountFor = it.editingDiscountFor.takeIf { id -> id != lineKey },
        )
    }

    fun toggleLineDiscountEditor(lineKey: Long) = _uiState.update {
        it.copy(editingDiscountFor = if (it.editingDiscountFor == lineKey) null else lineKey)
    }

    fun onLineDiscountChange(lineKey: Long, text: String, isPercent: Boolean) {
        val parsed = Discount.parse(text, isPercent)
        recalculate { state ->
            val input = DiscountInput(text = text, isPercent = isPercent, invalid = parsed is DiscountParse.Invalid)
            val discount = (parsed as? DiscountParse.Parsed)?.discount
            state.copy(
                lineDiscounts = state.lineDiscounts + (lineKey to input),
                cart = state.cart.setLineDiscount(lineKey, discount),
            )
        }
    }

    fun onBillDiscountChange(text: String, isPercent: Boolean) {
        val parsed = Discount.parse(text, isPercent)
        recalculate { state ->
            state.copy(
                billDiscount = DiscountInput(text, isPercent, parsed is DiscountParse.Invalid),
                cart = state.cart.copy(billDiscount = (parsed as? DiscountParse.Parsed)?.discount),
            )
        }
    }

    // Customer and place of supply

    fun toggleCustomer() = _uiState.update { it.copy(customerExpanded = !it.customerExpanded) }

    /** Typed buyer details. Editing them unlinks a saved customer. */
    fun onCustomerChange(customer: CustomerDetails) = recalculate { state ->
        val unlinked = state.account != null
        state.copy(
            cart = state.cart.copy(customer = customer.copy(customerId = null)),
            account = null,
            payments = if (unlinked) state.payments.filterNot { it.method == PaymentMethod.CREDIT } else state.payments,
            customerSaveError = null,
        )
    }

    fun onCustomerQueryChange(query: String) = _uiState.update { it.copy(customerQuery = query, customerResults = searchCustomers(query)) }

    /** Puts the bill on a saved customer's name, so it can go on their account. */
    fun linkCustomer(account: CustomerAccount) {
        val customer = account.customer
        recalculate { state ->
            state.copy(
                cart = state.cart.copy(
                    customer = CustomerDetails(
                        name = customer.name,
                        phone = customer.phone.orEmpty(),
                        gstin = customer.gstin.orEmpty(),
                        customerId = customer.id,
                    ),
                ),
                account = account,
                customerQuery = "",
                customerResults = emptyList(),
                customerSaveError = null,
            )
        }
    }

    fun unlinkCustomer() = recalculate { state ->
        state.copy(
            cart = state.cart.copy(customer = CustomerDetails()),
            account = null,
            payments = state.payments.filterNot { it.method == PaymentMethod.CREDIT },
        )
    }

    /** Saves the name and mobile typed on the bill as a new customer and links the bill to them. */
    fun saveTypedCustomer() {
        val repository = customers ?: return
        val typed = _uiState.value.cart.customer
        val validation = CustomerValidator.validate(CustomerForm(name = typed.name, phone = typed.phone, gstin = typed.gstin))
        if (validation is CustomerValidation.Invalid) {
            val error = when {
                typed.name.isBlank() -> CustomerSaveError.NAME_REQUIRED
                validation.errors.containsKey(ContactField.PHONE) -> CustomerSaveError.INVALID_PHONE
                else -> CustomerSaveError.INVALID_DETAILS
            }
            _uiState.update { it.copy(customerSaveError = error) }
            return
        }
        val customer = (validation as CustomerValidation.Valid).customer
        viewModelScope.launch {
            when (val result = runCatching { repository.saveCustomer(customer) }.getOrNull()) {
                is SaveCustomerResult.Saved -> {
                    linkCustomer(CustomerAccount(customer.copy(id = result.customerId), Money.ZERO))
                    eventChannel.send(BillingEvent.CustomerSaved(customer.name))
                }
                SaveCustomerResult.PhoneInUse -> _uiState.update { it.copy(customerSaveError = CustomerSaveError.PHONE_IN_USE) }
                null -> _uiState.update { it.copy(customerSaveError = CustomerSaveError.FAILED) }
            }
        }
    }

    fun onPlaceOfSupplyChange(stateCode: String?) = recalculate {
        val store = it.settings?.stateCode
        it.copy(cart = it.cart.copy(placeOfSupplyStateCode = stateCode.takeIf { code -> code != store }))
    }

    fun clearCart() {
        saleRequestId = UUID.randomUUID().toString()
        _uiState.update {
            BillingUiState(
                settings = it.settings,
                productsLoaded = it.productsLoaded,
                productCount = it.productCount,
                menu = it.menu,
                menuSection = it.menuSection,
            )
        }
    }

    // Payment

    fun proceedToPayment() {
        val state = _uiState.value
        if (state.needsSetup) {
            eventChannel.trySend(BillingEvent.SetupRequired)
            return
        }
        if (!state.check.canCheckout || state.billDiscount.invalid || state.lineDiscounts.values.any { it.invalid }) {
            _uiState.update { it.copy(showErrors = true, customerExpanded = it.customerExpanded || it.check.customerIssues.isNotEmpty()) }
            eventChannel.trySend(BillingEvent.FixCart)
            return
        }
        // The amount starts as the whole bill, and can be changed for a part payment.
        val first = PaymentDraft(key = nextPaymentKey++, method = PaymentMethod.CASH, amount = state.totals.grandTotal.toDecimalString())
        updatePayments(listOf(first), stage = BillingStage.PAYMENT)
    }

    fun backToCart() = _uiState.update { it.copy(stage = BillingStage.CART, paymentShowErrors = false) }

    fun onPaymentMethodChange(key: Int, method: PaymentMethod) =
        updatePayment(key) { it.copy(method = method, tendered = if (method == PaymentMethod.CASH) it.tendered else "", reference = "") }

    fun onPaymentAmountChange(key: Int, amount: String) = updatePayment(key) { it.copy(amount = AmountInput.sanitize(amount)) }

    fun onTenderedChange(key: Int, tendered: String) = updatePayment(key) { it.copy(tendered = AmountInput.sanitize(tendered)) }

    /** Called when the amount field is left: shows it with two decimals ("500" -> "500.00"). */
    fun onPaymentAmountDone(key: Int) = updatePayment(key) { it.copy(amount = AmountInput.normalize(it.amount).ifEmpty { it.amount }) }

    /** Called when the cash received field is left: shows it with two decimals ("99.5" -> "99.50"). */
    fun onTenderedDone(key: Int) = updatePayment(key) { it.copy(tendered = AmountInput.normalize(it.tendered).ifEmpty { it.tendered }) }

    fun onReferenceChange(key: Int, reference: String) = updatePayment(key) { it.copy(reference = reference) }

    /** Adds another payment for whatever is still unpaid (split payment). */
    fun addSplitPayment() {
        val state = _uiState.value
        val usedMethods = state.payments.map { it.method }.toSet()
        val method = state.paymentMethods.firstOrNull { it !in usedMethods } ?: PaymentMethod.CASH
        val remaining = state.paymentRemaining.takeIf { it > Money.ZERO } ?: Money.ZERO
        val draft = PaymentDraft(key = nextPaymentKey++, method = method, amount = if (remaining.isZero) "" else remaining.toDecimalString())
        updatePayments(state.payments + draft)
    }

    /**
     * Puts whatever is still unpaid on the customer's account (part payment). Adds to the "on account"
     * row if there already is one. Does nothing without a saved customer or when nothing is left to pay.
     */
    fun putRemainingOnAccount() {
        val state = _uiState.value
        val remaining = state.paymentRemaining
        if (state.account == null || remaining <= Money.ZERO) return
        val existing = state.payments.firstOrNull { it.method == PaymentMethod.CREDIT }
        if (existing != null) {
            val current = Money.parseOrNull(existing.amount) ?: Money.ZERO
            updatePayment(existing.key) { it.copy(amount = (current + remaining).toDecimalString()) }
        } else {
            val draft = PaymentDraft(key = nextPaymentKey++, method = PaymentMethod.CREDIT, amount = remaining.toDecimalString())
            updatePayments(state.payments + draft)
        }
    }

    fun removePayment(key: Int) {
        val payments = _uiState.value.payments
        if (payments.size > 1) updatePayments(payments.filterNot { it.key == key })
    }

    fun completeSale() {
        // Tidy the amounts before they are checked, so "500" and "99.5" are used as 500.00 and 99.50.
        updatePayments(_uiState.value.payments.map { it.copy(amount = AmountInput.normalize(it.amount).ifEmpty { it.amount }, tendered = AmountInput.normalize(it.tendered).ifEmpty { it.tendered }) })
        val state = _uiState.value
        if (state.isSaving) return
        val check = PaymentValidator.check(state.payments, state.totals.grandTotal, creditAccount(state))
        if (check is PaymentCheck.Invalid) {
            _uiState.update { it.copy(paymentShowErrors = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        val requestId = saleRequestId
        viewModelScope.launch {
            val result = runCatching { checkout(state.cart, state.payments, requestId) }.getOrNull()
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is CheckoutResult.Completed -> {
                    clearCart()
                    eventChannel.send(BillingEvent.Completed(result.invoiceId, result.number, result.change))
                }
                is CheckoutResult.StockChanged -> {
                    _uiState.update { it.copy(stage = BillingStage.CART, showErrors = true) }
                    eventChannel.send(BillingEvent.StockChanged)
                }
                is CheckoutResult.IngredientsShort -> {
                    val names = products.flatMap { product -> product.variants.map { product to it } }
                        .filter { (_, variant) -> variant.id in result.variantIds }
                        .map { (product, variant) -> listOf(product.name, variant.attributesLabel).filter { it.isNotBlank() }.joinToString(" ") }
                    eventChannel.send(BillingEvent.IngredientsShort(names))
                }
                is CheckoutResult.CartInvalid -> {
                    _uiState.update { it.copy(stage = BillingStage.CART, showErrors = true) }
                    eventChannel.send(BillingEvent.FixCart)
                }
                is CheckoutResult.PaymentsInvalid -> _uiState.update { it.copy(paymentShowErrors = true) }
                CheckoutResult.CreditLimitExceeded -> {
                    _uiState.update { it.copy(paymentShowErrors = true) }
                    eventChannel.send(BillingEvent.CreditLimitExceeded)
                }
                CheckoutResult.CustomerMissing -> {
                    unlinkCustomer()
                    _uiState.update { it.copy(stage = BillingStage.CART) }
                    eventChannel.send(BillingEvent.CustomerMissing)
                }
                CheckoutResult.SetupRequired -> eventChannel.send(BillingEvent.SetupRequired)
                null -> eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    private fun creditAccount(state: BillingUiState): CreditAccount? =
        state.account?.let { CreditAccount(it.balance, it.customer.creditLimit) }

    private fun updatePayment(key: Int, transform: (PaymentDraft) -> PaymentDraft) =
        updatePayments(_uiState.value.payments.map { if (it.key == key) transform(it) else it })

    private fun updatePayments(payments: List<PaymentDraft>, stage: BillingStage? = null) {
        _uiState.update { state ->
            val check = PaymentValidator.check(payments, state.totals.grandTotal, creditAccount(state))
            val (errors, remaining) = when (check) {
                is PaymentCheck.Invalid -> check.errors to check.remaining
                is PaymentCheck.Valid -> emptyMap<Int, PaymentError>() to Money.ZERO
            }
            state.copy(
                payments = payments,
                paymentErrors = errors,
                paymentRemaining = remaining,
                stage = stage ?: state.stage,
                paymentShowErrors = if (stage != null) false else state.paymentShowErrors,
            )
        }
    }

    // Helpers

    private fun recalculate(transform: (BillingUiState) -> BillingUiState) {
        _uiState.update { current ->
            val next = transform(current)
            val settings = next.settings ?: StoreSettings()
            val totals = BillCalculator.calculate(next.cart, settings.taxModeFor(next.cart))
            next.copy(totals = totals, check = CartValidator.check(next.cart, totals, settings.gstRegistered))
        }
    }

    /** Keeps cart stock figures current: deleted items and dishes taken off the menu become unavailable. */
    private fun syncStock(cart: Cart): Cart {
        val day = today
        val stock = products.flatMap { it.variants }.associate { it.id to it.sellableQuantity(day) }
        val dishes = allMenu.associateBy { it.id }
        return cart.copy(
            lines = cart.lines.map { line ->
                val menuItemId = line.menuItemId
                when {
                    line.isCustom -> line
                    menuItemId == null -> line.copy(availableStock = stock[line.variantId])
                    !menuLoaded -> line
                    else -> line.copy(availableStock = if (dishes[menuItemId]?.available == true) CartLine.UNLIMITED else null)
                }
            },
        )
    }

    private fun findVariantByBarcode(code: String): Pair<Product, ProductVariant>? {
        val codes = BarcodeRules.equivalents(code)
        return products.filter { it.category.isSold }.firstNotNullOfOrNull { product ->
            product.variants.firstOrNull { it.barcode in codes }?.let { product to it }
        }
    }

    private fun search(text: String): List<ProductSearchResult> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val day = today
        return products
            .filter { it.category.isSold && InventoryFilter.matchesText(it, query) }
            .flatMap { product ->
                val variants = product.variants.filter { variant ->
                    // When the query names a variant (size, colour, code), show just that variant.
                    val specific = variant.barcode == query ||
                        variant.sku.equals(query, ignoreCase = true) ||
                        variant.size.equals(query, ignoreCase = true) ||
                        variant.colour?.contains(query, ignoreCase = true) == true
                    specific || product.name.contains(query, ignoreCase = true) || product.brand?.contains(query, ignoreCase = true) == true
                }
                variants.map { ProductSearchResult(product, it) }
            }
            .sortedWith(compareBy({ it.variant.sellableQuantity(day) <= 0 }, { it.product.name.lowercase() }))
            .take(MAX_RESULTS)
    }

    private fun searchMenu(text: String): List<MenuItem> {
        val query = text.trim()
        if (query.isEmpty() || !menuEnabled) return emptyList()
        return allMenu
            .filter { it.name.contains(query, ignoreCase = true) || it.section.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ !it.available }, { it.name.lowercase() }))
            .take(MAX_RESULTS)
    }

    private fun searchCustomers(text: String): List<CustomerAccount> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val digits = query.filter { it.isDigit() }
        return accounts.filter { account ->
            account.customer.name.contains(query, ignoreCase = true) ||
                (digits.length >= 3 && account.customer.phone?.contains(digits) == true)
        }.take(MAX_CUSTOMER_RESULTS)
    }

    companion object {
        const val MAX_RESULTS = 25
        const val MAX_CUSTOMER_RESULTS = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                BillingViewModel(
                    inventory = container.inventoryRepository,
                    settingsRepository = container.settingsRepository,
                    checkout = container.checkout,
                    customers = container.customerRepository,
                    menuRepository = container.menuRepository,
                    clock = container.clock,
                    billing = container.billingRepository,
                    shop = container.shopPreferences,
                )
            }
        }
    }
}
