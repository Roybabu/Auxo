package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.QuotationStatus
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeCustomerRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeQuotationRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class QuotationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val customers = FakeCustomerRepository(emptyList())
    private val quotes = FakeQuotationRepository()

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        clock = TestData.clock,
        quotations = quotes,
    )

    private fun stock(variantId: Long) = inventory.products.value.flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    private fun setShirtPrice(minor: Long) {
        inventory.products.value = inventory.products.value.map { p ->
            if (p.id == 1L) p.copy(variants = p.variants.map { v -> v.copy(sellingPrice = Money(minor)) }) else p
        }
    }

    @Test
    fun saving_keepsTheBillAsAQuotation_clearsTheCounter_andTouchesNeitherStockNorInvoices() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.increment(11)
        val before = stock(11)

        vm.saveQuotation(15)

        assertTrue(vm.events.first() is BillingEvent.QuotationSaved)
        val document = vm.events.first() as BillingEvent.QuotationDocument
        assertTrue(vm.uiState.value.cart.isEmpty)
        val saved = vm.uiState.value.quotations.single()
        assertEquals(2, saved.itemCount)
        assertEquals(TestData.today.plusDays(15), saved.validUntil)
        assertEquals(before, stock(11))
        assertTrue(billing.invoices.value.isEmpty())
        // The document is a quotation with the quotation's number, not an invoice.
        assertEquals(saved.number, document.invoice.number)
        assertEquals(saved.validUntil, document.invoice.quotationValidUntil)
        assertTrue(document.invoice.payments.isEmpty())
        assertEquals(saved.total, document.invoice.grandTotal)
        assertTrue(vm.uiState.value.canQuote)
    }

    @Test
    fun anEmptyBill_cannotBeQuoted() = runTest {
        val vm = viewModel()
        vm.saveQuotation(15)
        assertTrue(quotes.all.isEmpty())
    }

    @Test
    fun aFailedSave_isReported_andTheBillStaysOnTheCounter() = runTest {
        quotes.failSaves = true
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(7)
        assertEquals(BillingEvent.SaveFailed, vm.events.first())
        assertEquals(1, vm.uiState.value.cart.itemCount)
    }

    @Test
    fun converting_putsTheQuotationOnTheCounter_atTodaysPrice_andSaysWhatMoved() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(15)
        vm.events.first()
        vm.events.first()
        val id = vm.uiState.value.quotations.single().id
        setShirtPrice(550_00)

        vm.convertQuotation(id)

        val loaded = vm.events.first() as BillingEvent.QuotationLoaded
        assertEquals(listOf("Cotton T-Shirt"), loaded.changed)
        assertEquals(Money(550_00), vm.uiState.value.cart.lines.single().unitPrice)
        assertEquals(id, vm.uiState.value.convertingQuotationId)
        assertTrue(billing.invoices.value.isEmpty()) // nothing is saved until it is paid
        assertEquals(QuotationStatus.OPEN, quotes.all.single().summary.status)
    }

    @Test
    fun converting_neverReplacesABillBeingTyped() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(15)
        vm.events.first()
        vm.events.first()
        vm.onBarcodeScanned(TestData.EAN[1])
        vm.convertQuotation(vm.uiState.value.quotations.single().id)
        assertNull(vm.uiState.value.convertingQuotationId)
        assertEquals(1, vm.uiState.value.cart.lines.size)
    }

    @Test
    fun theConvertedQuotation_becomesARealInvoice_andIsMarkedConvertedOnlyOnceSaved() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(15)
        vm.events.first()
        vm.events.first()
        val id = vm.uiState.value.quotations.single().id
        vm.convertQuotation(id)
        vm.events.first()
        val stockBefore = stock(11)

        vm.proceedToPayment()
        vm.onTenderedChange(vm.uiState.value.payments.single().key, "1000")
        vm.completeSale()
        assertTrue(vm.events.first() is BillingEvent.Completed)

        val invoice = billing.invoices.value.single()
        assertTrue(invoice.number.isNotBlank())
        assertEquals(null, invoice.quotationValidUntil)
        assertEquals(stockBefore - 1, stock(11))
        val row = quotes.all.single().summary
        assertEquals(QuotationStatus.CONVERTED, row.status)
        assertEquals(invoice.id, row.convertedInvoiceId)
        assertNull(vm.uiState.value.convertingQuotationId)
        // A converted quotation cannot be converted twice.
        vm.convertQuotation(id)
        assertEquals(BillingEvent.QuotationUnavailable, vm.events.first())
    }

    @Test
    fun converting_flagsALineThatIsNoLongerInStock() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.increment(11)
        vm.saveQuotation(15)
        vm.events.first()
        vm.events.first()
        inventory.products.value = inventory.products.value.map { p ->
            if (p.id == 1L) p.copy(variants = p.variants.map { v -> v.copy(stockQuantity = 1) }) else p
        }
        vm.convertQuotation(vm.uiState.value.quotations.single().id)
        vm.events.first()
        assertTrue(!vm.uiState.value.check.canCheckout)
    }

    @Test
    fun aDeletedQuotation_isGone_andACleanCounterIsKept() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(15)
        vm.events.first()
        vm.events.first()
        val id = vm.uiState.value.quotations.single().id
        vm.deleteQuotation(id)
        assertTrue(quotes.all.isEmpty())
        vm.convertQuotation(id)
        assertEquals(BillingEvent.QuotationUnavailable, vm.events.first())
    }

    @Test
    fun sharing_buildsTheQuotationDocument() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.saveQuotation(30)
        vm.events.first()
        vm.events.first()
        val saved = vm.uiState.value.quotations.single()
        vm.shareQuotation(saved.id)
        val document = (vm.events.first() as BillingEvent.QuotationDocument).invoice
        assertEquals(saved.number, document.number)
        assertEquals(LocalDate.of(2026, 11, 9), document.quotationValidUntil)
    }
}
