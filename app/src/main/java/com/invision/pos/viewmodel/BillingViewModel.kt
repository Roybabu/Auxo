package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.barcode.BarcodeCheck
import com.invision.pos.domain.barcode.BarcodeRules
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.BillTotals
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartCheck
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CartValidator
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.Discount
import com.invision.pos.domain.billing.DiscountParse
import com.invision.pos.domain.billing.PaymentCheck
import com.invision.pos.domain.billing.PaymentDraft
import com.invision.pos.domain.billing.PaymentError
import com.invision.pos.domain.billing.PaymentValidator
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.inventory.InventoryFilter
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.usecase.CheckoutResult
import com.invision.pos.domain.usecase.CheckoutUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Products in inventory; zero means there is nothing to sell yet. */
    val productCount: Int = 0,
    val loadFailed: Boolean = false,
    val cart: Cart = Cart(),
    val totals: BillTotals = BillTotals.EMPTY,
    val check: CartCheck = CartCheck(isEmpty = true),
    /** Problems are shown after the first attempt to charge. */
    val showErrors: Boolean = false,
    val searchText: String = "",
    val searchResults: List<ProductSearchResult> = emptyList(),
    val lineDiscounts: Map<Long, DiscountInput> = emptyMap(),
    /** Line whose discount editor is open. */
    val editingDiscountFor: Long? = null,
    val billDiscount: DiscountInput = DiscountInput(),
    val customerExpanded: Boolean = false,
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
}

sealed interface BillingEvent {
    data class Completed(val invoiceId: Long, val number: String, val change: Money) : BillingEvent
    data class OutOfStock(val name: String) : BillingEvent
    data class StockLimit(val name: String, val available: Int) : BillingEvent
    data class BarcodeNotFound(val barcode: String) : BillingEvent
    data object ScannerUnavailable : BillingEvent
    data object StockChanged : BillingEvent
    data object SetupRequired : BillingEvent
    data object FixCart : BillingEvent
    data object SaveFailed : BillingEvent
}

class BillingViewModel(
    private val inventory: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    private val checkout: CheckoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BillingEvent>(Channel.BUFFERED)
    val events: Flow<BillingEvent> = eventChannel.receiveAsFlow()

    private var products: List<Product> = emptyList()
    private var nextPaymentKey = 0

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { settings -> recalculate { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            inventory.observeProducts()
                .catch { _uiState.update { it.copy(loadFailed = true) } }
                .collect { list ->
                    products = list
                    recalculate { state ->
                        state.copy(
                            productsLoaded = true,
                            productCount = list.size,
                            cart = syncStock(state.cart, list),
                            searchResults = search(state.searchText),
                        )
                    }
                }
        }
    }

    // Search and adding items

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text, searchResults = search(text)) }
    }

    /**
     * Enter in the search box, which hardware barcode scanners also send. Adds the
     * item when the text is an exact barcode or matches a single variant.
     */
    fun onSearchSubmitted() {
        val text = _uiState.value.searchText.trim()
        if (text.isEmpty()) return
        val exact = findVariantByBarcode(text)
        when {
            exact != null -> add(exact.first, exact.second, clearSearch = true)
            _uiState.value.searchResults.size == 1 -> {
                val only = _uiState.value.searchResults.single()
                add(only.product, only.variant, clearSearch = true)
            }
            BarcodeRules.check(text) is BarcodeCheck.Valid && _uiState.value.searchResults.isEmpty() ->
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
        when {
            variant.stockQuantity <= 0 -> {
                eventChannel.trySend(BillingEvent.OutOfStock(label))
                return
            }
            inCart + 1 > variant.stockQuantity -> {
                eventChannel.trySend(BillingEvent.StockLimit(label, variant.stockQuantity))
                return
            }
        }
        recalculate { state ->
            state.copy(
                cart = state.cart.add(CartLine.from(product, variant)),
                searchText = if (clearSearch) "" else state.searchText,
                searchResults = if (clearSearch) emptyList() else state.searchResults,
            )
        }
    }

    // Cart lines

    fun increment(variantId: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == variantId } ?: return
        val available = line.availableStock ?: 0
        if (line.quantity + 1 > available) {
            eventChannel.trySend(BillingEvent.StockLimit(line.productName, available))
            return
        }
        setQuantity(variantId, line.quantity + 1)
    }

    fun decrement(variantId: Long) {
        val line = _uiState.value.cart.lines.firstOrNull { it.variantId == variantId } ?: return
        if (line.quantity > 1) setQuantity(variantId, line.quantity - 1)
    }

    fun setQuantity(variantId: Long, quantity: Int) = recalculate { it.copy(cart = it.cart.setQuantity(variantId, quantity)) }

    fun removeLine(variantId: Long) = recalculate {
        it.copy(
            cart = it.cart.remove(variantId),
            lineDiscounts = it.lineDiscounts - variantId,
            editingDiscountFor = it.editingDiscountFor.takeIf { id -> id != variantId },
        )
    }

    fun toggleLineDiscountEditor(variantId: Long) = _uiState.update {
        it.copy(editingDiscountFor = if (it.editingDiscountFor == variantId) null else variantId)
    }

    fun onLineDiscountChange(variantId: Long, text: String, isPercent: Boolean) {
        val parsed = Discount.parse(text, isPercent)
        recalculate { state ->
            val input = DiscountInput(text = text, isPercent = isPercent, invalid = parsed is DiscountParse.Invalid)
            val discount = (parsed as? DiscountParse.Parsed)?.discount
            state.copy(
                lineDiscounts = state.lineDiscounts + (variantId to input),
                cart = state.cart.setLineDiscount(variantId, discount),
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

    fun onCustomerChange(customer: CustomerDetails) = recalculate { it.copy(cart = it.cart.copy(customer = customer)) }

    fun onPlaceOfSupplyChange(stateCode: String?) = recalculate {
        val store = it.settings?.stateCode
        it.copy(cart = it.cart.copy(placeOfSupplyStateCode = stateCode.takeIf { code -> code != store }))
    }

    fun clearCart() = _uiState.update {
        BillingUiState(settings = it.settings, productsLoaded = it.productsLoaded, productCount = it.productCount)
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
        val total = state.totals.grandTotal
        val first = PaymentDraft(key = nextPaymentKey++, method = PaymentMethod.CASH, amount = total.toDecimalString())
        updatePayments(listOf(first), stage = BillingStage.PAYMENT)
    }

    fun backToCart() = _uiState.update { it.copy(stage = BillingStage.CART, paymentShowErrors = false) }

    fun onPaymentMethodChange(key: Int, method: PaymentMethod) =
        updatePayment(key) { it.copy(method = method, tendered = if (method == PaymentMethod.CASH) it.tendered else "", reference = "") }

    fun onPaymentAmountChange(key: Int, amount: String) = updatePayment(key) { it.copy(amount = amount) }

    fun onTenderedChange(key: Int, tendered: String) = updatePayment(key) { it.copy(tendered = tendered) }

    fun onReferenceChange(key: Int, reference: String) = updatePayment(key) { it.copy(reference = reference) }

    /** Adds another payment for whatever is still unpaid (split payment). */
    fun addSplitPayment() {
        val state = _uiState.value
        val remaining = state.paymentRemaining.takeIf { it > Money.ZERO } ?: Money.ZERO
        val usedMethods = state.payments.map { it.method }.toSet()
        val method = PaymentMethod.entries.firstOrNull { it !in usedMethods } ?: PaymentMethod.CASH
        val draft = PaymentDraft(key = nextPaymentKey++, method = method, amount = if (remaining.isZero) "" else remaining.toDecimalString())
        updatePayments(state.payments + draft)
    }

    fun removePayment(key: Int) {
        val payments = _uiState.value.payments
        if (payments.size > 1) updatePayments(payments.filterNot { it.key == key })
    }

    fun completeSale() {
        val state = _uiState.value
        if (state.isSaving) return
        val check = PaymentValidator.check(state.payments, state.totals.grandTotal)
        if (check is PaymentCheck.Invalid) {
            _uiState.update { it.copy(paymentShowErrors = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = runCatching { checkout(state.cart, state.payments) }.getOrNull()
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
                is CheckoutResult.CartInvalid -> {
                    _uiState.update { it.copy(stage = BillingStage.CART, showErrors = true) }
                    eventChannel.send(BillingEvent.FixCart)
                }
                is CheckoutResult.PaymentsInvalid -> _uiState.update { it.copy(paymentShowErrors = true) }
                CheckoutResult.SetupRequired -> eventChannel.send(BillingEvent.SetupRequired)
                null -> eventChannel.send(BillingEvent.SaveFailed)
            }
        }
    }

    private fun updatePayment(key: Int, transform: (PaymentDraft) -> PaymentDraft) =
        updatePayments(_uiState.value.payments.map { if (it.key == key) transform(it) else it })

    private fun updatePayments(payments: List<PaymentDraft>, stage: BillingStage? = null) {
        _uiState.update { state ->
            val check = PaymentValidator.check(payments, state.totals.grandTotal)
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

    /** Keeps cart stock figures current; deleted variants become unavailable. */
    private fun syncStock(cart: Cart, list: List<Product>): Cart {
        val stock = list.flatMap { it.variants }.associate { it.id to it.stockQuantity }
        return cart.copy(lines = cart.lines.map { it.copy(availableStock = stock[it.variantId]) })
    }

    private fun findVariantByBarcode(code: String): Pair<Product, ProductVariant>? =
        products.firstNotNullOfOrNull { product ->
            product.variants.firstOrNull { it.barcode == code }?.let { product to it }
        }

    private fun search(text: String): List<ProductSearchResult> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
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
            .sortedWith(compareBy({ it.variant.stockQuantity <= 0 }, { it.product.name.lowercase() }))
            .take(MAX_RESULTS)
    }

    companion object {
        const val MAX_RESULTS = 25

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                BillingViewModel(container.inventoryRepository, container.settingsRepository, container.checkout)
            }
        }
    }
}
