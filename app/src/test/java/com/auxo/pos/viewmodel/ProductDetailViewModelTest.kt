// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.inventory.StockAdjustmentError
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.usecase.AdjustStockUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInventoryRepository(listOf(TestData.tShirt()))

    private fun createViewModel(productId: Long = 1) =
        ProductDetailViewModel(productId, repository, AdjustStockUseCase(repository, TestData.clock), TestData.clock)

    @Test
    fun showsProduct() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val state = viewModel.uiState.value as ProductDetailUiState.Ready
        assertEquals("Cotton T-Shirt", state.product.name)
        assertTrue(state.movements.isEmpty())
    }

    @Test
    fun unknownProduct_isNotFound() = runTest {
        val viewModel = createViewModel(productId = 42)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(ProductDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun receivingStock_updatesQuantityAndHistory() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val variant = (viewModel.uiState.value as ProductDetailUiState.Ready).product.variants.first()

        viewModel.startAdjust(variant)
        viewModel.onAdjustReasonChange(StockChangeReason.RECEIVED)
        viewModel.onAdjustQuantityChange("12")
        assertEquals(20, viewModel.adjustDialog.value?.newStock)
        viewModel.onAdjustNoteChange("Supplier delivery")
        viewModel.confirmAdjust()

        assertEquals(ProductDetailEvent.StockUpdated, viewModel.events.first())
        assertNull(viewModel.adjustDialog.value)
        val state = viewModel.uiState.value as ProductDetailUiState.Ready
        assertEquals(20, state.product.variants.first().stockQuantity)
        val movement = state.movements.single()
        assertEquals(12, movement.quantityChange)
        assertEquals("Supplier delivery", movement.note)
    }

    @Test
    fun sellingMoreThanInStock_isRejectedInTheDialog() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val variant = (viewModel.uiState.value as ProductDetailUiState.Ready).product.variants.first() // 8 in stock

        viewModel.startAdjust(variant)
        viewModel.onAdjustReasonChange(StockChangeReason.SOLD)
        viewModel.onAdjustQuantityChange("9")
        viewModel.confirmAdjust()

        assertEquals(StockAdjustmentError.WOULD_GO_NEGATIVE, viewModel.adjustDialog.value?.error)
        assertEquals(8, repository.products.value.single().variants.first().stockQuantity)
    }

    @Test
    fun quantityInput_keepsDigitsOnly() {
        val viewModel = createViewModel()
        viewModel.startAdjust(TestData.tShirt().variants.first())
        viewModel.onAdjustQuantityChange("1a2.3")
        assertEquals("123", viewModel.adjustDialog.value?.quantity)
    }

    @Test
    fun deleting_removesProductAndNotifies_whenNoStockIsLeft() = runTest {
        val empty = TestData.tShirt().let { shirt -> shirt.copy(variants = shirt.variants.map { it.copy(stockQuantity = 0) }) }
        val emptyRepository = FakeInventoryRepository(listOf(empty))
        val viewModel = ProductDetailViewModel(1, emptyRepository, AdjustStockUseCase(emptyRepository, TestData.clock), TestData.clock)
        viewModel.requestDelete()
        assertTrue(viewModel.confirmDelete.value)
        viewModel.confirmDeleteProduct()

        assertEquals(ProductDetailEvent.Deleted, viewModel.events.first())
        assertTrue(emptyRepository.products.value.isEmpty())
    }

    @Test
    fun deleting_isRefused_whileStockIsOnHand_andNothingChanges() = runTest {
        val viewModel = createViewModel()
        viewModel.requestDelete()
        viewModel.confirmDeleteProduct()

        val units = TestData.tShirt().variants.sumOf { it.stockQuantity }
        assertEquals(ProductDetailEvent.StockOnHand(units), viewModel.events.first())
        assertEquals(1, repository.products.value.size)
    }
}
