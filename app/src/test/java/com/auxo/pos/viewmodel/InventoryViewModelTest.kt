// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.usecase.FindByBarcodeUseCase
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.juice()))

    private fun createViewModel(filter: StockFilter = StockFilter.ALL) =
        InventoryViewModel(repository, FindByBarcodeUseCase(repository), TestData.clock, filter)

    @Test
    fun loadsAllProducts() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.totalProductCount)
        assertEquals(listOf("Butter Biscuits", "Cotton T-Shirt", "Mango Juice"), state.products.map { it.name })
    }

    @Test
    fun searchAndFiltersNarrowTheList_andClearResets() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.onSearchTextChange("juice")
        assertEquals(listOf("Mango Juice"), viewModel.uiState.value.products.map { it.name })

        viewModel.onSearchTextChange("")
        viewModel.onCategorySelected(ProductCategory.GARMENT)
        assertEquals(listOf("Cotton T-Shirt"), viewModel.uiState.value.products.map { it.name })

        viewModel.onCategorySelected(null)
        viewModel.onStockFilterSelected(StockFilter.OUT_OF_STOCK)
        assertEquals(listOf("Mango Juice"), viewModel.uiState.value.products.map { it.name })

        viewModel.clearFilters()
        assertEquals(3, viewModel.uiState.value.products.size)
        assertFalse(viewModel.uiState.value.query.isFiltered)
    }

    @Test
    fun initialStockFilter_isApplied() = runTest {
        val viewModel = createViewModel(StockFilter.LOW_STOCK)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(listOf("Cotton T-Shirt"), viewModel.uiState.value.products.map { it.name })
    }

    @Test
    fun emptyInventory_isReportedAsEmpty() = runTest {
        val viewModel = InventoryViewModel(FakeInventoryRepository(), FindByBarcodeUseCase(FakeInventoryRepository()), TestData.clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(0, viewModel.uiState.value.totalProductCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun readFailure_showsError_andRetryRecovers() = runTest {
        repository.failReads = true
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertTrue(viewModel.uiState.value.loadFailed)

        repository.failReads = false
        viewModel.retry()
        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(3, viewModel.uiState.value.totalProductCount)
    }

    @Test
    fun scannedKnownBarcode_opensProduct() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned(TestData.EAN[2])
        assertEquals(InventoryEvent.OpenProduct(2), viewModel.events.first())
    }

    @Test
    fun scannedUnknownBarcode_offersToAddIt() = runTest {
        val viewModel = createViewModel()
        viewModel.onBarcodeScanned("8901000000088")
        assertEquals(InventoryEvent.BarcodeNotFound("8901000000088"), viewModel.events.first())
    }

    @Test
    fun searchSubmittedWithBarcode_opensProduct() = runTest {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange(TestData.EAN[0])
        viewModel.onSearchSubmitted()
        assertEquals(InventoryEvent.OpenProduct(1), viewModel.events.first())
    }
}
