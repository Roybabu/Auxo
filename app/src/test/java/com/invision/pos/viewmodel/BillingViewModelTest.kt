package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.CustomerIssue
import com.invision.pos.domain.billing.LineIssue
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.domain.usecase.ProcessReturnUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.juice(stock = 0)))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()

    private fun createViewModel() = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock))

    private fun stock(variantId: Long) = inventory.products.value.flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    @Test
    fun loads_settingsAndProducts() {
        val state = createViewModel().uiState.value
        assertEquals("Roy Mart", state.settings?.storeName)
        assertTrue(state.productsLoaded)
        assertTrue(state.cart.isEmpty)
        assertFalse(state.needsSetup)
    }

    @Test
    fun searchAndAdd() {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange("navy")
        assertEquals(listOf("M · Navy", "L · Navy"), viewModel.uiState.value.searchResults.map { it.variant.attributesLabel })
        viewModel.addResult(viewModel.uiState.value.searchResults.first())
        val state = viewModel.uiState.value
        assertEquals(1, state.cart.itemCount)
        assertEquals("", state.searchText)
        assertEquals(Money(49_900), state.totals.grandTotal)
    }

    @Test
    fun barcodeEntry_addsAndMerges() {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange(TestData.EAN[2])
        viewModel.onSearchSubmitted()
        viewModel.onBarcodeScanned(TestData.EAN[2])
        val line = viewModel.uiState.value.cart.lines.single()
        assertEquals(2, line.quantity)
        assertEquals(Money(6_000), viewModel.uiState.value.totals.grandTotal)
    }

    @Test
    fun unknownBarcode_andOutOfStock_areReported() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned("8901000000088")
        assertEquals(BillingEvent.BarcodeNotFound("8901000000088"), viewModel.events.first())
        viewModel.onBarcodeScanned(TestData.EAN[3]) // juice, none in stock
        assertTrue(viewModel.events.first() is BillingEvent.OutOfStock)
        assertTrue(viewModel.uiState.value.cart.isEmpty)
    }

    @Test
    fun quantityIsLimitedByStock() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[1]) // L Navy, 1 in stock
        viewModel.increment(12)
        assertEquals(BillingEvent.StockLimit("Cotton T-Shirt", 1), viewModel.events.first())
        assertEquals(1, viewModel.uiState.value.cart.itemCount)
    }

    @Test
    fun discounts_lineAndBill() {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.onLineDiscountChange(11, "10", isPercent = true)
        assertEquals(Money(9_980), viewModel.uiState.value.totals.lineDiscountTotal)
        viewModel.onBillDiscountChange("20", isPercent = false)
        assertEquals(Money(2_000), viewModel.uiState.value.totals.billDiscountTotal)
        viewModel.onBillDiscountChange("abc", isPercent = false)
        assertTrue(viewModel.uiState.value.billDiscount.invalid)
        assertEquals(Money.ZERO, viewModel.uiState.value.totals.billDiscountTotal)
    }

    @Test
    fun liveStockChanges_flagTheCartLine() {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[2])
        inventory.products.value = inventory.products.value.map { p ->
            p.copy(variants = p.variants.map { if (it.id == 21L) it.copy(stockQuantity = 0) else it })
        }
        assertEquals(LineIssue.NotEnoughStock(0), viewModel.uiState.value.check.lineIssues[21])
    }

    @Test
    fun customerDetails_validatedBeforePayment() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[2])
        viewModel.onCustomerChange(CustomerDetails(phone = "12345"))
        viewModel.proceedToPayment()
        assertEquals(BillingEvent.FixCart, viewModel.events.first())
        assertEquals(BillingStage.CART, viewModel.uiState.value.stage)
        assertTrue(CustomerIssue.INVALID_PHONE in viewModel.uiState.value.check.customerIssues)
        assertTrue(viewModel.uiState.value.customerExpanded)
    }

    @Test
    fun setupRequired_whenGstinMissing() = runTest {
        settings.settings.value = StoreSettings(gstRegistered = true, stateCode = "19")
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.needsSetup)
        viewModel.onBarcodeScanned(TestData.EAN[2])
        viewModel.proceedToPayment()
        assertEquals(BillingEvent.SetupRequired, viewModel.events.first())
    }

    @Test
    fun cashSale_completesAndDeductsStock() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.proceedToPayment()
        val state = viewModel.uiState.value
        assertEquals(BillingStage.PAYMENT, state.stage)
        assertEquals("499.00", state.payments.single().amount)
        viewModel.onTenderedChange(state.payments.single().key, "500")
        assertEquals(Money(100), viewModel.uiState.value.change)
        viewModel.completeSale()

        val event = viewModel.events.first() as BillingEvent.Completed
        assertEquals("INV/26-27/00001", event.number)
        assertEquals(Money(100), event.change)
        assertEquals(7, stock(11))
        assertTrue(viewModel.uiState.value.cart.isEmpty)
        assertEquals(BillingStage.CART, viewModel.uiState.value.stage)
        assertEquals(InvoiceType.SALE, billing.invoices.value.single().type)
    }

    @Test
    fun splitPayment_mustAddUp() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[0]) // 499
        viewModel.proceedToPayment()
        val first = viewModel.uiState.value.payments.single().key
        viewModel.onPaymentAmountChange(first, "300")
        assertEquals(Money(19_900), viewModel.uiState.value.paymentRemaining)
        viewModel.addSplitPayment()
        val second = viewModel.uiState.value.payments.last()
        assertEquals(PaymentMethod.UPI, second.method)
        assertEquals("199.00", second.amount)
        assertTrue(viewModel.uiState.value.paymentRemaining.isZero)
        viewModel.completeSale()
        assertTrue(viewModel.events.first() is BillingEvent.Completed)
        assertEquals(2, billing.invoices.value.single().payments.size)
    }

    @Test
    fun underpaid_doesNotSave() {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.proceedToPayment()
        viewModel.onPaymentAmountChange(viewModel.uiState.value.payments.single().key, "100")
        viewModel.completeSale()
        assertTrue(viewModel.uiState.value.paymentShowErrors)
        assertTrue(billing.invoices.value.isEmpty())
        assertEquals(8, stock(11))
    }

    @Test
    fun stockSoldElsewhere_beforeCheckout_isCaught() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[1]) // the only L
        viewModel.proceedToPayment()
        // Another sale takes the last unit before this one completes.
        billing.recordSale(
            com.invision.pos.domain.billing.SampleSales.shirtsAndBiscuits().copy(
                id = 0,
                lines = listOf(com.invision.pos.domain.billing.SampleSales.shirtsAndBiscuits().lines[0].copy(variantId = 12, quantity = 1)),
            ),
            "INV",
        )
        viewModel.completeSale()
        assertEquals(BillingEvent.FixCart, viewModel.events.first())
        assertEquals(BillingStage.CART, viewModel.uiState.value.stage)
        assertEquals(1, billing.invoices.value.size)
    }

    @Test
    fun placeOfSupply_otherState_switchesToIgst() {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[2])
        viewModel.onPlaceOfSupplyChange("27")
        assertEquals("27", viewModel.uiState.value.cart.placeOfSupplyStateCode)
        assertTrue(viewModel.uiState.value.totals.igstTotal > Money.ZERO)
        viewModel.onPlaceOfSupplyChange("19")
        assertNull(viewModel.uiState.value.cart.placeOfSupplyStateCode)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnAndHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()

    private suspend fun sellThreeShirts(): Long {
        val viewModel = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock))
        repeat(3) { viewModel.onBarcodeScanned(TestData.EAN[0]) }
        viewModel.onCustomerChange(CustomerDetails(name = "Anita Das", phone = "9831000000"))
        viewModel.proceedToPayment()
        viewModel.completeSale()
        return (viewModel.events.first() as BillingEvent.Completed).invoiceId
    }

    private fun returnViewModel(invoiceId: Long) =
        ReturnViewModel(invoiceId, billing, settings, ProcessReturnUseCase(billing, settings, TestData.clock), TestData.clock)

    @Test
    fun partialReturn_restocksAndIssuesCreditNote() = runTest {
        val invoiceId = sellThreeShirts()
        assertEquals(5, inventory.products.value.first().variants.first().stockQuantity)
        val viewModel = returnViewModel(invoiceId)
        val lineId = viewModel.uiState.value.invoice!!.lines.single().id
        assertEquals(null, viewModel.uiState.value.refund)

        viewModel.setQuantity(lineId, 5) // clamped to 3 sold
        assertEquals(3, viewModel.uiState.value.quantities[lineId])
        viewModel.setQuantity(lineId, 1)
        assertTrue(viewModel.uiState.value.refund!! > Money.ZERO)
        viewModel.onRefundMethodChange(PaymentMethod.UPI)
        viewModel.onReasonChange("Wrong size")
        viewModel.confirm()

        val event = viewModel.events.first() as ReturnEvent.Completed
        assertEquals("CN/26-27/00001", event.number)
        assertEquals(6, inventory.products.value.first().variants.first().stockQuantity)
        val creditNote = billing.invoices.value.first { it.id == event.creditNoteId }
        assertEquals(PaymentMethod.UPI, creditNote.payments.single().method)
        assertEquals("Wrong size", creditNote.note)
    }

    @Test
    fun returnScreen_forCreditNote_fails() = runTest {
        val invoiceId = sellThreeShirts()
        val first = returnViewModel(invoiceId)
        first.returnAll()
        first.confirm()
        val creditNoteId = (first.events.first() as ReturnEvent.Completed).creditNoteId
        assertTrue(returnViewModel(creditNoteId).uiState.value.loadFailed)
        assertTrue(returnViewModel(invoiceId).uiState.value.invoice!!.isFullyReturned)
    }

    @Test
    fun history_listsSearchesAndTotalsPayments() = runTest {
        val invoiceId = sellThreeShirts()
        val ret = returnViewModel(invoiceId)
        ret.setQuantity(ret.uiState.value.invoice!!.lines.single().id, 1)
        ret.confirm()
        ret.events.first()

        val viewModel = InvoiceHistoryViewModel(billing, TestData.clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertEquals(2, state.invoices.size)
        assertEquals(Money(1_497_00), state.salesTotal)
        assertTrue(state.returnsTotal > Money.ZERO)
        assertEquals(Money(1_497_00), state.paymentTotals.cash)
        assertEquals(state.returnsTotal, state.paymentTotals.refunds)
        assertEquals(state.salesTotal - state.returnsTotal, state.paymentTotals.net)

        viewModel.onQueryChange("anita")
        assertEquals(2, viewModel.uiState.value.invoices.size)
        viewModel.onQueryChange("CN/")
        assertEquals(1, viewModel.uiState.value.invoices.size)
        viewModel.onQueryChange("98310")
        assertEquals(2, viewModel.uiState.value.invoices.size)
        viewModel.onQueryChange("nobody")
        assertTrue(viewModel.uiState.value.invoices.isEmpty())
    }

    @Test
    fun historyRanges_excludeOlderInvoices() = runTest {
        sellThreeShirts()
        val tomorrow = java.time.Clock.offset(TestData.clock, java.time.Duration.ofDays(1))
        val viewModel = InvoiceHistoryViewModel(billing, tomorrow)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertTrue(viewModel.uiState.value.invoices.isEmpty()) // today = tomorrow
        viewModel.onRangeChange(HistoryRange.LAST_7_DAYS)
        assertEquals(1, viewModel.uiState.value.invoices.size)
        viewModel.onRangeChange(HistoryRange.ALL)
        assertEquals(1, viewModel.uiState.value.invoices.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillingSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun gstin_fillsTheState_andSaves() = runTest {
        val repository = FakeSettingsRepository(StoreSettings())
        val viewModel = BillingSettingsViewModel(repository)
        viewModel.update { it.copy(storeName = " Roy Mart ") }
        viewModel.onGstinChange("19aabcu9603r1zk")
        assertEquals("19", viewModel.uiState.value.settings.stateCode)
        viewModel.save()
        assertEquals(SettingsEvent.Saved, viewModel.events.first())
        assertEquals("Roy Mart", repository.settings.value.storeName)
        assertEquals("19AABCU9603R1ZK", repository.settings.value.gstin)
    }

    @Test
    fun invalidSettings_areNotSaved() = runTest {
        val repository = FakeSettingsRepository(StoreSettings())
        val viewModel = BillingSettingsViewModel(repository)
        viewModel.update { it.copy(upiId = "bad") }
        viewModel.save()
        assertTrue(viewModel.events.first() is SettingsEvent.Invalid)
        assertTrue(viewModel.uiState.value.showErrors)
        assertEquals(StoreSettings(), repository.settings.value)
    }

    @Test
    fun saveFailure_isReported() = runTest {
        val repository = FakeSettingsRepository().apply { failSaves = true }
        val viewModel = BillingSettingsViewModel(repository)
        viewModel.save()
        assertEquals(SettingsEvent.SaveFailed, viewModel.events.first())
    }
}
