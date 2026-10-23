// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.billing.HeldBills
import com.auxo.pos.domain.model.Customer
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

/** Parking bills against restaurant tables, and numbering the other bills. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeldTablesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.dish()))
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

    private fun BillingViewModel.addDish() = onBarcodeScanned("DISH-40")
    private fun BillingViewModel.addShirt() = onBarcodeScanned(TestData.EAN[0])

    @Test
    fun aRestaurantBill_cannotBeParkedWithoutATable() = runTest {
        val vm = viewModel()
        vm.addDish()
        assertTrue(vm.uiState.value.cart.needsTable)
        vm.holdBill("Asha")
        assertTrue(held.all.isEmpty())
        assertFalse(vm.uiState.value.cart.isEmpty)
    }

    @Test
    fun aRestaurantBill_isParkedAgainstTheChosenTable() = runTest {
        val vm = viewModel()
        vm.addDish()
        vm.holdBill("", tableNumber = 5)
        assertEquals(BillingEvent.BillHeld, vm.events.first())
        val bill = vm.uiState.value.heldBills.single()
        assertEquals(5, bill.tableNumber)
        assertNull(bill.tokenNumber)
    }

    @Test
    fun aTableOutsideTheCount_isRefused() = runTest {
        val vm = viewModel()
        vm.addDish()
        vm.holdBill("", tableNumber = HeldBills.DEFAULT_TABLES + 1)
        assertTrue(held.all.isEmpty())
    }

    @Test
    fun aTableThatIsInUse_isRefused_untilItsBillIsGone() = runTest {
        val vm = viewModel()
        vm.addDish()
        vm.holdBill("", tableNumber = 2)
        vm.addDish()
        vm.holdBill("", tableNumber = 2)
        assertEquals(1, held.all.size)
        vm.holdBill("", tableNumber = 3)
        assertEquals(setOf(2, 3), held.all.map { it.summary.tableNumber }.toSet())
    }

    @Test
    fun resumingATableBill_andParkingAgain_keepsTheTableWithoutAsking() = runTest {
        val vm = viewModel()
        vm.addDish()
        vm.holdBill("Asha", tableNumber = 4)
        val id = vm.uiState.value.heldBills.single().id

        vm.resumeHeld(id)
        vm.addDish()
        vm.holdAgain()

        val bill = vm.uiState.value.heldBills.single()
        assertEquals(id, bill.id)
        assertEquals(4, bill.tableNumber)
        assertEquals("Asha", bill.label)
        assertEquals(2, bill.itemCount)
        assertTrue(vm.uiState.value.cart.isEmpty)
    }

    @Test
    fun otherBills_getTheNextFreeNumber_andReuseFreedOnes() = runTest {
        val vm = viewModel()
        vm.addShirt()
        vm.holdBill("")
        vm.addShirt()
        vm.holdBill("", phone = "98300 12345")
        assertEquals(listOf(1, 2), held.all.map { it.summary.tokenNumber }.sortedBy { it })
        assertEquals("9830012345", held.all.first { it.summary.tokenNumber == 2 }.cart.customer.phone)

        held.discard(held.all.first { it.summary.tokenNumber == 1 }.summary.id)
        vm.addShirt()
        vm.holdBill("")
        assertEquals(listOf(1, 2), held.all.map { it.summary.tokenNumber }.sortedBy { it })
    }

    @Test
    fun aResumedNumberedBill_keepsItsNumber() = runTest {
        val vm = viewModel()
        vm.addShirt()
        vm.holdBill("")
        vm.addShirt()
        vm.holdBill("")
        val first = held.all.first { it.summary.tokenNumber == 1 }.summary.id
        vm.resumeHeld(first)
        vm.addShirt()
        vm.holdBill("")
        assertEquals(listOf(1, 2), held.all.map { it.summary.tokenNumber }.sortedBy { it })
        assertEquals(first, held.all.first { it.summary.tokenNumber == 1 }.summary.id)
    }
}
