package com.auxo.pos.viewmodel

import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.payInFull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Taps on "Complete sale" that arrive while a sale is saving, or after a lost reply. */
@OptIn(ExperimentalCoroutinesApi::class)
class DoubleTapSaleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** A billing store whose sale can be held part-way through, and made to lose its reply. */
    private class ControlledBilling(private val inner: FakeBillingRepository) : BillingRepository by inner {
        var saleCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        var loseReplyOnce = false

        override suspend fun recordSale(invoice: Invoice, invoicePrefix: String, requestId: String?): SaleResult {
            saleCalls++
            gate?.await()
            val result = inner.recordSale(invoice, invoicePrefix, requestId)
            if (loseReplyOnce) {
                loseReplyOnce = false
                throw IOException("Reply lost after the sale was saved")
            }
            return result
        }
    }

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt()))
    private val store = FakeBillingRepository(inventory)
    private val billing = ControlledBilling(store)
    private val settings = FakeSettingsRepository()

    private fun stock() = inventory.products.value.flatMap { it.variants }.first { it.id == 11L }.stockQuantity

    private fun readyToPay(): BillingViewModel {
        val viewModel = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock))
        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.proceedToPayment()
        viewModel.payInFull()
        return viewModel
    }

    @Test
    fun tappingTwiceWhileSaving_savesOneSale() = runTest {
        val gate = CompletableDeferred<Unit>().also { billing.gate = it }
        val viewModel = readyToPay()

        viewModel.completeSale()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.completeSale()
        viewModel.completeSale()
        assertEquals(1, billing.saleCalls)

        gate.complete(Unit)
        assertTrue(viewModel.events.first() is BillingEvent.Completed)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(1, store.invoices.value.size)
        assertEquals(7, stock())
    }

    @Test
    fun tappingAgainAfterALostReply_doesNotSellTwice() = runTest {
        billing.loseReplyOnce = true
        val viewModel = readyToPay()

        viewModel.completeSale()
        assertEquals(BillingEvent.SaveFailed, viewModel.events.first())
        // The sale did reach the store, but the app never heard back; the cart is still there.
        assertEquals(1, store.invoices.value.size)
        assertFalse(viewModel.uiState.value.cart.isEmpty)

        viewModel.completeSale()
        val completed = viewModel.events.first() as BillingEvent.Completed
        assertEquals(store.invoices.value.single().number, completed.number)
        assertEquals(1, store.invoices.value.size)
        assertEquals(7, stock())
        assertTrue(viewModel.uiState.value.cart.isEmpty)
    }

    @Test
    fun theNextSale_getsItsOwnRequestId() = runTest {
        val viewModel = readyToPay()
        viewModel.completeSale()
        assertTrue(viewModel.events.first() is BillingEvent.Completed)

        viewModel.onBarcodeScanned(TestData.EAN[0])
        viewModel.proceedToPayment()
        viewModel.payInFull()
        viewModel.completeSale()
        assertTrue(viewModel.events.first() is BillingEvent.Completed)
        assertEquals(2, store.invoices.value.size)
        assertEquals(6, stock())
    }
}
