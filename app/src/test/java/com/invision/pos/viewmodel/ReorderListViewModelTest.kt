package com.invision.pos.viewmodel

import com.invision.pos.domain.model.Supplier
import com.invision.pos.domain.usecase.CreateReorderDraftsUseCase
import com.invision.pos.domain.usecase.RecordWastageUseCase
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakePurchasingRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.keepActive
import com.invision.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReorderListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flourVariant = TestData.flour(grams = 500).variants[0].id
    private val juiceVariant = TestData.juice(stock = 0).variants[0].id

    private val inventory = FakeInventoryRepository(
        listOf(
            TestData.tShirt().copy(preferredSupplierId = 1),
            TestData.juice(stock = 0).copy(preferredSupplierId = 2),
            TestData.flour(grams = 500).copy(preferredSupplierId = 2),
        ),
    )
    private val purchasing = FakePurchasingRepository(inventory, listOf(Supplier(id = 1, name = "Roy Textiles"), Supplier(id = 2, name = "Drinks Co")))

    private fun viewModel() = StockAlertsViewModel(
        initialTab = AlertsTab.LOW_STOCK,
        inventory = inventory,
        purchasing = purchasing,
        createReorderDrafts = CreateReorderDraftsUseCase(purchasing),
        recordWastage = RecordWastageUseCase(inventory),
        clock = TestData.clock,
    )

    @Test
    fun theListIsGroupedBySupplier_withASuggestedQuantityForEachItem() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val groups = vm.uiState.value.groups
        assertEquals(listOf<Long?>(2L, 1L), groups.map { it.supplierId })
        assertEquals(setOf("Mango Juice", "Wheat Flour"), groups[0].items.map { it.product.name }.toSet())
        assertTrue(groups.flatMap { it.items }.all { it.suggestedQuantity > 0 })
    }

    @Test
    fun oneTapMakesOneDraftPerSupplier_withTheQuantitiesThePersonTyped() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.onQuantityChange(flourVariant, "3")
        vm.onQuantityChange(juiceVariant, " 48 ")
        assertTrue(vm.uiState.value.canCreate)

        vm.createDraftOrders()
        assertEquals(StockAlertsEvent.DraftsCreated(listOf(1L, 2L), skipped = 0), events.last())
        val drinks = purchasing.orders.value.first { it.supplierId == 2L }
        assertEquals(3_000, drinks.lines.first { it.variantId == flourVariant }.quantityOrdered)
        assertEquals(48, drinks.lines.first { it.variantId == juiceVariant }.quantityOrdered)
        assertEquals(1L, purchasing.orders.value.single { it.supplierId == 1L }.supplierId)
        // Typed quantities are used once.
        assertTrue(vm.uiState.value.quantityTexts.isEmpty())
    }

    @Test
    fun anUnusableQuantity_blocksTheDrafts_untilItIsFixedOrCleared() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        record(vm.events)
        vm.onQuantityChange(juiceVariant, "abc")
        assertEquals(setOf(juiceVariant), vm.uiState.value.invalidQuantities)
        assertFalse(vm.uiState.value.canCreate)
        vm.createDraftOrders()
        assertTrue(purchasing.orders.value.isEmpty())

        // Leaving that item out of the order makes the rest orderable.
        vm.toggleSelected(juiceVariant)
        assertTrue(vm.uiState.value.canCreate)

        vm.toggleSelected(juiceVariant)
        vm.onQuantityChange(juiceVariant, "")
        assertTrue(vm.uiState.value.invalidQuantities.isEmpty())
        assertTrue(vm.uiState.value.canCreate)
    }

    @Test
    fun existingPurchaseOrderRules_areUnchanged_itemsWithoutASupplierAreSkipped() = runTest {
        val noSupplier = FakeInventoryRepository(listOf(TestData.juice(stock = 0)))
        val repo = FakePurchasingRepository(noSupplier, listOf(Supplier(id = 1, name = "Roy Textiles")))
        val vm = StockAlertsViewModel(
            initialTab = AlertsTab.LOW_STOCK,
            inventory = noSupplier,
            purchasing = repo,
            createReorderDrafts = CreateReorderDraftsUseCase(repo),
            recordWastage = RecordWastageUseCase(noSupplier),
            clock = TestData.clock,
        )
        keepActive(vm.uiState)
        val events = record(vm.events)
        assertEquals(listOf<Long?>(null), vm.uiState.value.groups.map { it.supplierId })
        vm.createDraftOrders()
        assertEquals(StockAlertsEvent.NothingToOrder, events.last())
        assertTrue(repo.orders.value.isEmpty())
    }
}
