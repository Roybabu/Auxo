// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.LineIssue
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeCustomerRepository
import com.auxo.pos.testutil.FakeHeldBillRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeldBillsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val customers = FakeCustomerRepository(listOf(Customer(id = 1, name = "Anita Das", phone = "9830012345")))
    private val held = FakeHeldBillRepository()

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        clock = TestData.clock,
        heldBills = held,
    )

    private fun stock(variantId: Long) = inventory.products.value.flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    @Test
    fun holding_parksTheBill_andStartsANewOne_withoutTouchingStock() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.increment(11)
        val stockBefore = stock(11)

        vm.holdBill("  Table 4 ")

        assertEquals(BillingEvent.BillHeld, vm.events.first())
        assertTrue(vm.uiState.value.cart.isEmpty)
        val list = vm.uiState.value.heldBills
        assertEquals("Table 4", list.single().label)
        assertEquals(2, list.single().itemCount)
        assertEquals(stockBefore, stock(11))
        assertTrue(billing.invoices.value.isEmpty())
        assertTrue(vm.uiState.value.canHold)
    }

    @Test
    fun anEmptyBill_cannotBeHeld() = runTest {
        val vm = viewModel()
        vm.holdBill("x")
        assertTrue(held.all.isEmpty())
    }

    @Test
    fun aFailedHold_keepsTheBillOnTheCounter() = runTest {
        held.failHolds = true
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("Asha")
        assertEquals(BillingEvent.SaveFailed, vm.events.first())
        assertEquals(1, vm.uiState.value.cart.itemCount)
        assertTrue(held.all.isEmpty())
    }

    @Test
    fun resuming_restoresLinesDiscountsReasonsAndCustomer() = runTest {
        val vm = viewModel()
        vm.onCustomerQueryChange("anita")
        vm.linkCustomer(vm.uiState.value.customerResults.single())
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.onLineDiscountChange(11, "10", isPercent = true)
        vm.onLineDiscountReasonChange(11, "Festival")
        vm.onBillDiscountChange("20", isPercent = false)
        vm.onBillDiscountReasonChange("Regular")
        val total = vm.uiState.value.totals.grandTotal
        vm.holdBill("Anita")
        vm.events.first()
        val id = vm.uiState.value.heldBills.single().id

        vm.resumeHeld(id)

        val state = vm.uiState.value
        assertEquals(id, state.resumedHeldId)
        assertEquals(total, state.totals.grandTotal)
        assertEquals(Discount.Percent(1_000), state.cart.lines.single().discount)
        assertEquals("10", state.lineDiscounts[11L]?.text)
        assertTrue(state.lineDiscounts[11L]!!.isPercent)
        assertEquals("20.00", state.billDiscount.text)
        assertEquals("Regular", state.cart.billDiscountReason)
        assertEquals("Anita Das", state.account?.customer?.name)
        assertTrue(state.check.canCheckout)
    }

    @Test
    fun resuming_looksAtStockAgain_soAShortLineIsFlagged() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        repeat(3) { vm.increment(11) } // 4 shirts held
        vm.holdBill("")
        vm.events.first()
        // Meanwhile most of the stock was sold.
        inventory.products.value = inventory.products.value.map { p ->
            p.copy(variants = p.variants.map { if (it.id == 11L) it.copy(stockQuantity = 2) else it })
        }

        vm.resumeHeld(vm.uiState.value.heldBills.single().id)

        assertEquals(LineIssue.NotEnoughStock(2), vm.uiState.value.check.lineIssues[11L])
        assertFalse(vm.uiState.value.check.canCheckout)
    }

    @Test
    fun resuming_isRefusedWhileThereIsAnotherBillOnTheCounter() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("First")
        vm.events.first()
        vm.onBarcodeScanned(TestData.EAN[2])
        val current = vm.uiState.value.cart

        vm.resumeHeld(vm.uiState.value.heldBills.single().id)

        assertEquals(current, vm.uiState.value.cart)
        assertNull(vm.uiState.value.resumedHeldId)
    }

    @Test
    fun aBillThatIsGone_cannotBeResumed() = runTest {
        val vm = viewModel()
        vm.resumeHeld(999)
        assertEquals(BillingEvent.HeldBillUnavailable, vm.events.first())
        assertTrue(vm.uiState.value.cart.isEmpty)
    }

    @Test
    fun customItems_keepWorking_afterAResume() = runTest {
        val vm = viewModel()
        assertTrue(vm.addCustomItem("Alteration", "150", "1", 1_800))
        vm.holdBill("")
        vm.events.first()
        vm.resumeHeld(vm.uiState.value.heldBills.single().id)
        assertEquals(Money(150_00), vm.uiState.value.cart.lines.single().unitPrice)
        assertTrue(vm.uiState.value.check.lineIssues.isEmpty())
        // A second custom item does not take the first one's place.
        assertTrue(vm.addCustomItem("Packing", "20", "1", 1_800))
        assertEquals(2, vm.uiState.value.cart.lines.size)
    }

    @Test
    fun finishingAResumedBill_removesItFromTheHeldList() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("Asha")
        vm.events.first()
        val id = vm.uiState.value.heldBills.single().id
        vm.resumeHeld(id)
        assertEquals(1, held.all.size) // resuming alone does not remove it: it is safe until the sale is saved

        vm.proceedToPayment()
        vm.onTenderedChange(vm.uiState.value.payments.single().key, "500")
        vm.completeSale()
        assertTrue(vm.events.first() is BillingEvent.Completed)

        assertTrue(held.all.isEmpty())
        assertTrue(vm.uiState.value.heldBills.isEmpty())
        assertNull(vm.uiState.value.resumedHeldId)
        assertEquals(1, billing.invoices.value.size)
    }

    @Test
    fun aBillThatIsNotFinished_staysHeld() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("Asha")
        vm.events.first()
        vm.resumeHeld(vm.uiState.value.heldBills.single().id)
        vm.clearCart()
        assertEquals(1, held.all.size)
        assertEquals(1, vm.uiState.value.heldBills.size)
    }

    @Test
    fun holdingAResumedBillAgain_updatesIt_insteadOfMakingACopy() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("Asha")
        vm.events.first()
        vm.resumeHeld(vm.uiState.value.heldBills.single().id)
        vm.increment(11)
        vm.holdBill("Asha K")
        vm.events.first()
        val only = vm.uiState.value.heldBills.single()
        assertEquals("Asha K", only.label)
        assertEquals(2, only.itemCount)
    }

    @Test
    fun discard_removesTheBill() = runTest {
        val vm = viewModel()
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("Asha")
        vm.events.first()
        vm.discardHeld(vm.uiState.value.heldBills.single().id)
        assertTrue(held.all.isEmpty())
        assertTrue(vm.uiState.value.heldBills.isEmpty())
    }

    @Test
    fun withoutAHeldStore_holdIsNotOffered() = runTest {
        val vm = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock))
        assertFalse(vm.uiState.value.canHold)
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.holdBill("x")
        assertEquals(1, vm.uiState.value.cart.itemCount)
    }
}
