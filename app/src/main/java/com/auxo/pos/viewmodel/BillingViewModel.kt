// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.core.debuglog.DebugLog
import com.auxo.pos.domain.billing.BusinessTime
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.SaveCustomerResult
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.barcode.BarcodeCheck
import com.auxo.pos.domain.barcode.BarcodeRules
import com.auxo.pos.domain.billing.AmountInput
import com.auxo.pos.data.repository.LoyaltyRepository
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.loyalty.LoyaltyMath
import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.loyalty.LoyaltyRules
import com.auxo.pos.domain.billing.BillTotals
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartCheck
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomItem
import com.auxo.pos.domain.billing.CustomItemResult
import com.auxo.pos.domain.billing.CartValidator
import com.auxo.pos.domain.billing.CreditAccount
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.billing.BillSplit
import com.auxo.pos.domain.billing.HeldBills
import com.auxo.pos.domain.billing.SplitOutcome
import com.auxo.pos.data.repository.QuotationRepository
import com.auxo.pos.domain.billing.QuotationRepricing
import com.auxo.pos.domain.billing.QuotationStatus
import com.auxo.pos.domain.billing.QuotationSummary
import com.auxo.pos.domain.billing.Quotations
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.data.repository.HeldBillRepository
import com.auxo.pos.domain.billing.DiscountPolicy
import com.auxo.pos.domain.billing.DiscountParse
import com.auxo.pos.domain.billing.PaymentCheck
import com.auxo.pos.domain.billing.PaymentDraft
import com.auxo.pos.domain.billing.PaymentError
import com.auxo.pos.domain.billing.PaymentValidator
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.contacts.ContactField
import com.auxo.pos.domain.contacts.CustomerForm
import com.auxo.pos.domain.contacts.CustomerValidation
import com.auxo.pos.domain.contacts.CustomerValidator
import com.auxo.pos.domain.inventory.InventoryFilter
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.domain.usecase.CheckoutResult
import com.auxo.pos.domain.usecase.CheckoutUseCase
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
data class ProductSearchResult(val product: Product, val variant: ProductVariant)

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
    /** Available restaurant items, for quick adding in restaurant mode. */
    val quickItems: List<ProductSearchResult> = emptyList(),
    /** Subcategory shown in the quick-add card; null shows the first one. */
    val quickSection: String? = null,
    /** Everything that is not a restaurant dish, by name, for browsing on the new bill page. */
    val browseProducts: List<Product> = emptyList(),
    /** The kind of goods the browse card shows; null shows all of them. */
    val browseCategory: ProductCategory? = null,
    val lineDiscounts: Map<Long, DiscountInput> = emptyMap(),
    /** Line whose discount editor is open. */
    val editingDiscountFor: Long? = null,
    val billDiscount: DiscountInput = DiscountInput(),
    /** The most the signed-in person may discount, in basis points of the value; null when there is no limit. */
    val discountLimitBp: Int? = null,
    /** Items for sale that still have expired stock on the shelf. Expired units are never billed. */
    val expiredItemCount: Int = 0,
    /** The shop's loyalty rules (off unless the Super Admin switched them on). */
    val loyaltyRules: LoyaltyRules = LoyaltyRules.OFF,
    /** Points being used on this bill, already kept within the balance and the bill. */
    val loyaltyPoints: Int = 0,
    /** Most points that can be used on this bill right now. */
    val loyaltyMax: Int = 0,
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
    /** The number this bill will probably get, for the UPI QR. Null until known (or without a billing store). */
    val upiInvoiceNumber: String? = null,
    /** Bills parked at the counter, newest first. */
    val heldBills: List<HeldBillSummary> = emptyList(),
    /** True when the shop can park bills at all. */
    val canHold: Boolean = false,
    /** The parked bill now on the counter, if the cart came from one. It is removed when the sale is saved. */
    val resumedHeldId: Long? = null,
    /** How many tables the restaurant has; the table picker offers 1 to this. */
    val tableCount: Int = HeldBills.DEFAULT_TABLES,
    /** Quotations, newest first. */
    val quotations: List<QuotationSummary> = emptyList(),
    /** True when the shop can make quotations at all. */
    val canQuote: Boolean = false,
    /** The quotation now on the counter, if the cart came from one. It is marked converted when the sale is saved. */
    val convertingQuotationId: Long? = null,
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

    /** True when the loyalty row shows: loyalty is on and the bill is for a saved customer. */
    val loyaltyAvailable: Boolean get() = loyaltyRules.enabled && account != null

    /** What the points being used take off. */
    val loyaltyDiscount: Money get() = totals.loyaltyDiscount

    /** Subcategories that have available restaurant items; an empty name means no subcategory. */
    val quickSections: List<String> get() = quickItems.map { it.product.subcategory.orEmpty() }.distinct()

    /** The kinds of goods the browse card has chips for: those with at least one product. */
    val browseCategories: List<ProductCategory> get() = browseProducts.map { it.category }.distinct().sortedBy { it.ordinal }
}

enum class CustomerSaveError { NAME_REQUIRED, INVALID_PHONE, INVALID_DETAILS, PHONE_IN_USE, FAILED }

sealed interface BillingEvent {
    data class Completed(val invoiceId: Long, val number: String, val change: Money) : BillingEvent
    data class OutOfStock(val name: String) : BillingEvent
    data class BarcodeNotFound(val barcode: String) : BillingEvent
    data object ScannerUnavailable : BillingEvent
    data object StockChanged : BillingEvent
    data object CreditLimitExceeded : BillingEvent
    data object CustomerMissing : BillingEvent
    data object LoyaltyUnavailable : BillingEvent
    data object SetupRequired : BillingEvent
    data object FixCart : BillingEvent
    data object SaveFailed : BillingEvent
    data class CustomerSaved(val name: String) : BillingEvent
    data object BillHeld : BillingEvent

    /** The chosen items were parked as a separate bill and the rest stays on the counter. */
    data object BillSplitOff : BillingEvent

    /** The parked bill was finished or discarded meanwhile, or cannot be read. */
    data object HeldBillUnavailable : BillingEvent

    /** A quotation was saved under [number]. */
    data class QuotationSaved(val number: String) : BillingEvent

    /** A quotation was put on the counter at today's prices; [changed] names items whose price or tax rate moved. */
    data class QuotationLoaded(val number: String, val changed: List<String>) : BillingEvent

    /** The quotation was deleted, converted meanwhile, or cannot be read. */
    data object QuotationUnavailable : BillingEvent

    /** The quotation as a document, ready to share as a PDF. */
    data class QuotationDocument(val invoice: Invoice) : BillingEvent
}

class BillingViewModel(
    private val inventory: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    private val checkout: CheckoutUseCase,
    private val customers: CustomerRepository? = null,
    private val clock: Clock = BusinessTime.clock(),
    private val billing: BillingRepository? = null,
    private val shop: com.auxo.pos.core.shop.ShopPreferences? = null,
    private val loyalty: LoyaltyRepository? = null,
    /** True for someone who may discount without the Employee limit (the Super Admin). */
    private val canExceedDiscountLimit: () -> Boolean = { true },
    /** Where parked bills are kept; without it the Hold button is not offered. */
    private val heldBills: HeldBillRepository? = null,
    /** Where quotations are kept; without it the quotation buttons are not offered. */
    private val quotations: QuotationRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BillingEvent>(Channel.BUFFERED)
    val events: Flow<BillingEvent> = eventChannel.receiveAsFlow()

    private var products: List<Product> = emptyList()

    private var nextCustomKey = 0L

    /** False when restaurant mode is switched off: the quick-add card is not offered. */
    private var restaurantEnabled = shop?.restaurantMenu?.value ?: true
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
                            productCount = list.size,
                            expiredItemCount = list.count { p -> p.variants.any { it.expiredQuantity(today) > 0 } },
                            cart = syncStock(state.cart),
                            searchResults = search(state.searchText),
                            quickItems = quickItems(),
                            browseProducts = browseProducts(),
                        )
                    }
                }
        }
        val customersJob = customers?.let { repository ->
            viewModelScope.launch {
                repository.observeAccounts()
                    .catch { _uiState.update { it.copy(loadFailed = true) } }
                    .collect { list ->
                        accounts = list
                        recalculate { state ->
                            val linkedId = state.account?.customer?.id
                            val linked = linkedId?.let { id -> list.firstOrNull { it.customer.id == id } }
                            state.copy(customerResults = searchCustomers(state.customerQuery), account = linked ?: state.account)
                        }
                    }
            }
        }
        val loyaltyJob = loyalty?.let { repository ->
            viewModelScope.launch {
                repository.observeRules()
                    .catch { recalculate { it.copy(loyaltyRules = LoyaltyRules.OFF, loyaltyPoints = 0) } }
                    .collect { rules -> recalculate { it.copy(loyaltyRules = rules) } }
            }
        }
        val shopJob = shop?.let { prefs ->
            viewModelScope.launch {
                prefs.restaurantMenu.collect { enabled ->
                    restaurantEnabled = enabled
                    recalculate { state -> state.copy(quickItems = quickItems()) }
                }
            }
        }
        val tablesJob = shop?.let { prefs ->
            viewModelScope.launch { prefs.tableCount.collect { count -> _uiState.update { it.copy(tableCount = count) } } }
        }
        val heldJob = heldBills?.let { repository ->
            viewModelScope.launch {
                repository.observeHeld()
                    .catch { recalculate { it.copy(heldBills = emptyList()) } }
                    .collect { list -> _uiState.update { it.copy(heldBills = list, canHold = true) } }
            }
        }
        val quotationJob = quotations?.let { repository ->
            viewModelScope.launch {
                repository.observeAll()
                    .catch { _uiState.update { it.copy(quotations = emptyList()) } }
                    .collect { list -> _uiState.update { it.copy(quotations = list, canQuote = true) } }
            }
        }
        loadJobs = listOfNotNull(settingsJob, productsJob, shopJob, tablesJob, customersJob, loyaltyJob, heldJob, quotationJob)
    }

    // Search and adding items

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text, searchResults = search(text)) }
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
            state.searchResults.size == 1 -> {
                val only = state.searchResults.single()
                add(only.product, only.variant, clearSearch = true)
            }
            BarcodeRules.check(text) is BarcodeCheck.Valid && state.searchResults.isEmpty() ->
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
        val available = product.sellableUnits(variant, today)
        when {
            available <= 0 -> {
                eventChannel.trySend(BillingEvent.OutOfStock(label))
                return
            }
            // All the stock is already on the bill: nothing more to add, and no message.
            inCart >= available -> return
        }
        recalculate { state ->
            state.copy(
                cart = state.cart.add(
                    CartLine.from(product, variant, today = today).let { it.copy(quantity = it.withinStock(it.quantity)) },
                ),
                searchText = if (clearSearch) "" else state.searchText,
                searchResults = if (clearSearch) emptyList() else state.searchResults,
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

    fun onQuickSectionChange(section: String?) = _uiState.update { it.copy(quickSection = section) }

    fun onBrowseCategoryChange(category: ProductCategory?) = _uiState.update { it.copy(browseCategory = category) }

    // Cart lines, keyed by CartLine.variantId

    fun increment(lineKey: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == lineKey } ?: return
        // The "+" button is off at the limit; this keeps the rule in the billing logic too.
        if (!line.canIncrease) return
        setQuantity(lineKey, line.quantity + line.step)
    }

    fun decrement(lineKey: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == lineKey } ?: return
        if (line.quantity > line.step) setQuantity(lineKey, line.quantity - line.step)
    }

    /** A weight or volume typed in for a loose item, such as "1.25" kilograms. Ignored when it is not a valid amount. */
    fun setTypedQuantity(lineKey: Long, text: String) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == lineKey } ?: return
        val base = line.stockUnit.parse(text) ?: return
        setQuantity(lineKey, base)
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

    fun onLineDiscountReasonChange(lineKey: Long, reason: String) =
        recalculate { it.copy(cart = it.cart.setLineDiscountReason(lineKey, reason.take(DiscountPolicy.REASON_MAX))) }

    fun onBillDiscountReasonChange(reason: String) =
        recalculate { it.copy(cart = it.cart.copy(billDiscountReason = reason.take(DiscountPolicy.REASON_MAX))) }

    fun onBillDiscountChange(text: String, isPercent: Boolean) {
        val parsed = Discount.parse(text, isPercent)
        recalculate { state ->
            state.copy(
                billDiscount = DiscountInput(text, isPercent, parsed is DiscountParse.Invalid),
                cart = state.cart.copy(billDiscount = (parsed as? DiscountParse.Parsed)?.discount),
            )
        }
    }

    // Loyalty points

    /** Points typed by the cashier. Kept within the customer's balance and the bill. */
    fun onLoyaltyPointsChange(text: String) {
        val wanted = text.filter { it.isDigit() }.take(9).toIntOrNull() ?: 0
        recalculate { it.copy(loyaltyPoints = wanted) }
    }

    fun useAllLoyaltyPoints() = recalculate { it.copy(loyaltyPoints = Int.MAX_VALUE) }

    fun clearLoyaltyPoints() = recalculate { it.copy(loyaltyPoints = 0) }

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
                loyaltyRules = it.loyaltyRules,
                productsLoaded = it.productsLoaded,
                productCount = it.productCount,
                quickItems = it.quickItems,
                quickSection = it.quickSection,
                browseProducts = it.browseProducts,
                browseCategory = it.browseCategory,
                heldBills = it.heldBills,
                canHold = it.canHold,
                tableCount = it.tableCount,
                quotations = it.quotations,
                canQuote = it.canQuote,
            )
        }
    }

    // Quotations

    /**
     * Saves the bill on the counter as a quotation valid for [validityDays], then clears the counter. A quotation
     * takes nothing out of stock and uses no invoice number. A customer name is not required.
     */
    fun saveQuotation(validityDays: Int) {
        val store = quotations ?: return
        val state = _uiState.value
        val settings = state.settings
        if (state.cart.isEmpty || state.isSaving || settings == null) return
        // Loyalty points are not kept on a quotation, so its total is worked out without them.
        val cart = state.cart.copy(loyalty = null)
        val total = BillCalculator.calculate(cart, settings.taxModeFor(cart)).grandTotal
        viewModelScope.launch {
            val saved = runCatching { store.save(cart, total, Quotations.validUntil(today, validityDays)) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
            if (saved != null) {
                clearCart()
                eventChannel.send(BillingEvent.QuotationSaved(saved.number))
                quotationInvoice(saved, cart, settings)?.let { eventChannel.send(BillingEvent.QuotationDocument(it)) }
            } else {
                eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    private fun quotationInvoice(summary: QuotationSummary, cart: Cart, settings: StoreSettings?): Invoice? {
        settings ?: return null
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, settings, emptyList(), summary.createdAtMillis)
            .copy(number = summary.number, quotationValidUntil = summary.validUntil)
    }

    /** Sends a saved quotation to the share sheet as a PDF document. */
    fun shareQuotation(id: Long) {
        val store = quotations ?: return
        val settings = _uiState.value.settings ?: return
        viewModelScope.launch {
            val quotation = runCatching { store.get(id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
            val invoice = quotation?.let { quotationInvoice(it.summary, it.cart, settings) }
            eventChannel.send(if (invoice != null) BillingEvent.QuotationDocument(invoice) else BillingEvent.QuotationUnavailable)
        }
    }

    /**
     * Puts a quotation on the counter as a normal bill, in one step. Prices, tax rates and stock are looked up
     * again now, so the bill follows today's shop prices and a line that is no longer in stock is flagged. Nothing
     * is saved until the sale is paid; only then does the quotation count as converted. Only when the counter is
     * empty, so nothing being typed is lost.
     */
    fun convertQuotation(id: Long) {
        val store = quotations ?: return
        val current = _uiState.value
        if (!current.cart.isEmpty || current.convertingQuotationId == id) return
        viewModelScope.launch {
            val quotation = runCatching { store.get(id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
            if (quotation == null || quotation.summary.status != QuotationStatus.OPEN) {
                eventChannel.send(BillingEvent.QuotationUnavailable)
                return@launch
            }
            val repriced = QuotationRepricing.reprice(quotation.cart, products)
            val account = repriced.cart.customer.customerId?.let { cid -> accounts.firstOrNull { it.customer.id == cid } }
            val customer = if (account == null) repriced.cart.customer.copy(customerId = null) else repriced.cart.customer
            val cart = repriced.cart.copy(customer = customer)
            cart.lines.filter { it.isCustom }.maxOfOrNull { CartLine.CUSTOM_KEY_BASE - it.variantId }?.let {
                nextCustomKey = maxOf(nextCustomKey, it + 1)
            }
            saleRequestId = UUID.randomUUID().toString()
            recalculate { state ->
                state.copy(
                    cart = syncStock(cart),
                    lineDiscounts = cart.lines.mapNotNull { line -> line.discount?.let { line.variantId to inputFor(it) } }.toMap(),
                    billDiscount = inputFor(cart.billDiscount),
                    account = account,
                    convertingQuotationId = id,
                    customerExpanded = customer.name.isNotBlank() || customer.phone.isNotBlank(),
                )
            }
            eventChannel.send(BillingEvent.QuotationLoaded(quotation.summary.number, repriced.changed))
        }
    }

    /** Deletes a quotation. The screen asks first. */
    fun deleteQuotation(id: Long) {
        val store = quotations ?: return
        viewModelScope.launch {
            runCatching { store.delete(id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _uiState.update { if (it.convertingQuotationId == id) it.copy(convertingQuotationId = null) else it }
        }
    }

    // Held bills

    /**
     * Parks the bill on the counter under an optional [label] (a customer name) and starts a new one. A bill with a
     * restaurant dish needs a [tableNumber] that no other parked bill is using; any other bill is given a number by the
     * app. A [phone] typed for the customer is kept on the bill.
     */
    fun holdBill(label: String, tableNumber: Int? = null, phone: String = "") {
        val store = heldBills ?: return
        val state = _uiState.value
        if (state.cart.isEmpty || state.isSaving) return
        if (state.cart.needsTable) {
            val free = tableNumber != null && tableNumber in 1..state.tableCount &&
                tableNumber !in HeldBills.busyTables(state.heldBills, state.resumedHeldId)
            if (!free) return
        }
        val table = tableNumber.takeIf { state.cart.needsTable }
        val digits = phone.filter(Char::isDigit).takeLast(10)
        val cart = if (digits.isNotEmpty() && state.cart.customer.phone.isBlank()) {
            state.cart.copy(customer = state.cart.customer.copy(phone = digits))
        } else {
            state.cart
        }
        viewModelScope.launch {
            val saved = runCatching { store.hold(cart, state.totals.grandTotal, label, state.resumedHeldId, table) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .isSuccess
            if (saved) {
                clearCart()
                eventChannel.send(BillingEvent.BillHeld)
            } else {
                eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    /**
     * Splits the bill on the counter by item: the chosen [quantities] (units per line) are parked as their own bill
     * under [label], and the rest stays here. If the bill on the counter came from a parked bill, that parked copy is
     * updated too, so nothing is left in two places. Does nothing when the split is not allowed (see [BillSplit]).
     */
    fun splitOffBill(quantities: Map<Long, Int>, label: String) {
        val store = heldBills ?: return
        val state = _uiState.value
        if (state.isSaving) return
        val outcome = BillSplit.splitOff(state.cart, quantities) as? SplitOutcome.Ok ?: return
        val settings = state.settings ?: StoreSettings()
        val movedTotal = BillCalculator.calculate(outcome.moved, settings.taxModeFor(outcome.moved)).grandTotal
        val restTotal = BillCalculator.calculate(outcome.remaining, settings.taxModeFor(outcome.remaining)).grandTotal
        val resumed = state.heldBills.firstOrNull { it.id == state.resumedHeldId }
        viewModelScope.launch {
            val saved = runCatching {
                store.hold(outcome.moved, movedTotal, HeldBills.cleanLabel(label), null, null)
                if (resumed != null) store.hold(outcome.remaining, restTotal, resumed.label, resumed.id, resumed.tableNumber)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.isSuccess
            if (saved) {
                saleRequestId = UUID.randomUUID().toString()
                recalculate { current ->
                    current.copy(
                        cart = outcome.remaining,
                        lineDiscounts = outcome.remaining.lines.mapNotNull { line -> line.discount?.let { line.variantId to inputFor(it) } }.toMap(),
                    )
                }
                eventChannel.send(BillingEvent.BillSplitOff)
            } else {
                eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    /**
     * Parks a resumed restaurant bill again, against the table it already has and under the name it already had, so
     * nothing is asked a second time. Does nothing for a bill that did not come from a table.
     */
    fun holdAgain() {
        val state = _uiState.value
        val held = state.heldBills.firstOrNull { it.id == state.resumedHeldId } ?: return
        val table = held.tableNumber ?: return
        holdBill(held.label, table)
    }

    /**
     * Puts a parked bill back on the counter. Only when the counter is empty, so nothing being typed is lost.
     * Stock is looked up again now (nothing was reserved), so a line that is no longer in stock is flagged.
     */
    fun resumeHeld(id: Long) {
        val store = heldBills ?: return
        val current = _uiState.value
        if (!current.cart.isEmpty || current.resumedHeldId == id) return
        viewModelScope.launch {
            val held = runCatching { store.get(id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
            if (held == null) {
                eventChannel.send(BillingEvent.HeldBillUnavailable)
                return@launch
            }
            val account = held.cart.customer.customerId?.let { cid -> accounts.firstOrNull { it.customer.id == cid } }
            val customer = if (account == null) held.cart.customer.copy(customerId = null) else held.cart.customer
            val cart = held.cart.copy(customer = customer)
            cart.lines.filter { it.isCustom }.maxOfOrNull { CartLine.CUSTOM_KEY_BASE - it.variantId }?.let {
                nextCustomKey = maxOf(nextCustomKey, it + 1)
            }
            saleRequestId = UUID.randomUUID().toString()
            recalculate { state ->
                state.copy(
                    cart = syncStock(cart),
                    lineDiscounts = cart.lines.mapNotNull { line -> line.discount?.let { line.variantId to inputFor(it) } }.toMap(),
                    billDiscount = inputFor(cart.billDiscount),
                    account = account,
                    resumedHeldId = id,
                    customerExpanded = customer.name.isNotBlank() || customer.phone.isNotBlank(),
                )
            }
        }
    }

    /** Reads a parked bill to look at it, without putting it on the counter. Null when it is gone or cannot be read. */
    suspend fun loadHeld(id: Long): com.auxo.pos.domain.billing.HeldBill? =
        runCatching { heldBills?.get(id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    /** Throws a parked bill away. The screen asks first. */
    fun discardHeld(id: Long) {
        val store = heldBills ?: return
        viewModelScope.launch {
            runCatching { store.discard(id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _uiState.update { if (it.resumedHeldId == id) it.copy(resumedHeldId = null) else it }
        }
    }

    private fun inputFor(discount: Discount?): DiscountInput = when (discount) {
        null -> DiscountInput()
        is Discount.Amount -> DiscountInput(text = discount.value.toDecimalString(), isPercent = false)
        is Discount.Percent -> DiscountInput(
            text = java.math.BigDecimal.valueOf(discount.basisPoints.toLong(), 2).stripTrailingZeros().toPlainString(),
            isPercent = true,
        )
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
        loadUpiInvoiceNumber(state.settings?.invoicePrefix)
    }

    /** Looks up the number the QR will carry. The sale keeps whatever number it is really given. */
    private fun loadUpiInvoiceNumber(prefix: String?) {
        val store = billing ?: return
        if (prefix == null) return
        viewModelScope.launch {
            val number = runCatching { store.previewInvoiceNumber(prefix, today) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
            _uiState.update { it.copy(upiInvoiceNumber = number) }
        }
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
                    // The bill is finished, so its parked copy (if it was resumed) goes.
                    state.resumedHeldId?.let { held -> runCatching { heldBills?.discard(held) } }
                    // A quotation that became this sale is marked used; saving the sale already settled everything else.
                    state.convertingQuotationId?.let { id -> runCatching { quotations?.markConverted(id, result.invoiceId) } }
                    clearCart()
                    eventChannel.send(BillingEvent.Completed(result.invoiceId, result.number, result.change))
                }
                is CheckoutResult.StockChanged -> {
                    _uiState.update { it.copy(stage = BillingStage.CART, showErrors = true) }
                    eventChannel.send(BillingEvent.StockChanged)
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
                CheckoutResult.LoyaltyUnavailable -> {
                    // Rules or balance changed under the bill: drop the points and let the cashier review the new total.
                    recalculate { it.copy(loyaltyPoints = 0, stage = BillingStage.CART) }
                    eventChannel.send(BillingEvent.LoyaltyUnavailable)
                }
                CheckoutResult.SetupRequired -> eventChannel.send(BillingEvent.SetupRequired)
                null -> eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    private fun creditAccount(state: BillingUiState): CreditAccount? =
        state.account?.let { CreditAccount(it.balance, it.customer.creditLimit, onHold = isOnHold(it)) }

    /** True when the shop holds credit after a number of days and this customer has owed money for longer than that. */
    private fun isOnHold(account: com.auxo.pos.domain.model.CustomerAccount): Boolean {
        val holdDays = shop?.creditHoldDays?.value ?: 0
        if (holdDays <= 0 || account.balance <= Money.ZERO) return false
        val age = com.auxo.pos.domain.customers.DuesReminder.ageInDays(account.owingSinceMillis, java.time.LocalDate.now(clock), clock.zone)
        return age != null && age > holdDays
    }

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
            val changed = transform(current)
            val settings = changed.settings ?: StoreSettings()
            val mode = settings.taxModeFor(changed.cart)
            // Loyalty points come off after every other discount, so work out the bill without them first.
            val withoutPoints = changed.cart.copy(loyalty = null)
            val before = BillCalculator.calculate(withoutPoints, mode)
            val billBeforePoints = before.subtotal - before.lineDiscountTotal - before.billDiscountTotal
            val max = changed.account?.let { LoyaltyMath.maxRedeemable(changed.loyaltyRules, it.points, billBeforePoints) } ?: 0
            val points = changed.loyaltyPoints.coerceIn(0, max)
            val next = changed.copy(
                cart = if (points > 0) {
                    withoutPoints.copy(loyalty = LoyaltyRedemption(points, LoyaltyMath.valueOf(changed.loyaltyRules, points)))
                } else {
                    withoutPoints
                },
                loyaltyPoints = points,
                loyaltyMax = max,
            )
            val totals = BillCalculator.calculate(next.cart, mode)
            val limit = if (canExceedDiscountLimit()) null else settings.employeeDiscountLimitBp
            next.copy(totals = totals, discountLimitBp = limit, check = CartValidator.check(next.cart, totals, settings.gstRegistered, limit))
        }
    }

    /** Keeps cart stock figures current: deleted items and restaurant items switched off become unavailable. */
    private fun syncStock(cart: Cart): Cart {
        val day = today
        val stock: Map<Long, Int?> = products.flatMap { product ->
            product.variants.map { variant ->
                variant.id to when {
                    !product.available -> null
                    !product.tracksStock -> CartLine.UNLIMITED
                    else -> variant.sellableQuantity(day)
                }
            }
        }.toMap()
        return cart.copy(lines = cart.lines.map { line -> if (line.isCustom) line else line.copy(availableStock = stock[line.variantId]) })
    }

    /** Available restaurant items grouped by subcategory (items without one last), for the quick-add card. */
    private fun quickItems(): List<ProductSearchResult> {
        if (!restaurantEnabled) return emptyList()
        return products
            .filter { it.category == ProductCategory.RESTAURANT && it.available }
            .sortedWith(compareBy({ it.subcategory.isNullOrBlank() }, { it.subcategory.orEmpty().lowercase() }, { it.name.lowercase() }))
            .flatMap { product -> product.variants.map { ProductSearchResult(product, it) } }
    }

    /** Every product that is not a restaurant dish (dishes have their own menu card), by name. */
    private fun browseProducts(): List<Product> =
        products.filter { it.category != ProductCategory.RESTAURANT && it.variants.isNotEmpty() }.sortedBy { it.name.lowercase() }

    private fun findVariantByBarcode(code: String): Pair<Product, ProductVariant>? {
        // Items saved without a barcode have an empty one, so an empty scan must never match them.
        if (code.isBlank()) return null
        val codes = BarcodeRules.equivalents(code)
        val found = products.firstNotNullOfOrNull { product ->
            product.variants.firstOrNull { it.barcode in codes }?.let { product to it }
        }
        DebugLog.event("Scan", "Barcode $code: ${if (found != null) "found variant ${found.second.id}" else "no match"}")
        return found
    }

    private fun search(text: String): List<ProductSearchResult> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val day = today
        return products
            .filter { InventoryFilter.matchesText(it, query) }
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
            .sortedWith(compareBy({ it.product.sellableUnits(it.variant, day) <= 0 }, { it.product.name.lowercase() }))
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
                    clock = container.clock,
                    billing = container.billingRepository,
                    shop = container.shopPreferences,
                    loyalty = container.loyaltyRepository,
                    canExceedDiscountLimit = { container.authorizer.can(com.auxo.pos.core.auth.Permission.UNLIMITED_DISCOUNT) },
                    heldBills = container.heldBillRepository,
                    quotations = container.quotationRepository,
                )
            }
        }
    }
}
