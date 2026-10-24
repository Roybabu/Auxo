// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeCustomerRepository
import com.auxo.pos.testutil.FakeHeldBillRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseItemsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.dish(id = 3)))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val customers = FakeCustomerRepository(listOf(Customer(id = 1, name = "Anita Das", phone = "9830012345")))

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        clock = TestData.clock,
        heldBills = FakeHeldBillRepository(),
    )

    @Test
    fun browse_listsEveryProductThatIsNotARestaurantDish_byName() = runTest {
        val state = viewModel().uiState.value
        assertEquals(listOf(ProductCategory.GARMENT, ProductCategory.PACKAGED_FOOD).toSet(), state.browseCategories.toSet())
        assertEquals(state.browseProducts.map { it.name.lowercase() }.sorted(), state.browseProducts.map { it.name.lowercase() })
        assertEquals(false, state.browseProducts.any { it.category == ProductCategory.RESTAURANT })
    }

    @Test
    fun browseCategory_survivesStartingANewBill() = runTest {
        val vm = viewModel()
        assertNull(vm.uiState.value.browseCategory)
        vm.onBrowseCategoryChange(ProductCategory.GARMENT)
        vm.clearCart()
        assertEquals(ProductCategory.GARMENT, vm.uiState.value.browseCategory)
        assertEquals(2, vm.uiState.value.browseProducts.size)
    }
}
