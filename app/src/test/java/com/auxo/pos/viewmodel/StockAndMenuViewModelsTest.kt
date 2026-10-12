package com.auxo.pos.viewmodel

import com.auxo.pos.domain.model.MenuItem
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.RecipeLine
import com.auxo.pos.domain.model.StockBatch
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.reports.ExpiryWindow
import com.auxo.pos.domain.usecase.CreateReorderDraftsUseCase
import com.auxo.pos.domain.usecase.RecordWastageUseCase
import com.auxo.pos.domain.usecase.WastageError
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeMenuRepository
import com.auxo.pos.testutil.FakePurchasingRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.keepActive
import com.auxo.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockAlertsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = TestData.today
    private val biscuits = TestData.biscuits().let { product ->
        val variant = product.variants[0]
        product.copy(
            variants = listOf(
                variant.copy(
                    batches = listOf(
                        StockBatch(id = 1, variantId = variant.id, batchNumber = "OLD", expiryDate = today.minusDays(1), quantity = 10),
                        StockBatch(id = 2, variantId = variant.id, batchNumber = "NEW", expiryDate = today.plusDays(5), quantity = 30),
                    ),
                ),
            ),
        )
    }
    private val inventory = FakeInventoryRepository(
        listOf(
            TestData.tShirt().copy(preferredSupplierId = 1), // L is low
            TestData.juice(stock = 0).copy(preferredSupplierId = 3), // out of stock; supplier 3 is hidden
            biscuits,
        ),
    )
    private val purchasing = FakePurchasingRepository(
        inventory,
        listOf(Supplier(id = 1, name = "Roy Textiles"), Supplier(id = 2, name = "Drinks Co"), Supplier(id = 3, name = "Closed Co", active = false)),
    )

    private fun viewModel(tab: AlertsTab = AlertsTab.LOW_STOCK) = StockAlertsViewModel(
        initialTab = tab,
        inventory = inventory,
        purchasing = purchasing,
        createReorderDrafts = CreateReorderDraftsUseCase(purchasing),
        recordWastage = RecordWastageUseCase(inventory),
        clock = TestData.clock,
    )

    @Test
    fun lowStock_becomesDraftOrdersPerSupplier() = runTest {
        val viewModel = viewModel()
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)
        val state = viewModel.uiState.value
        assertEquals(listOf("Mango Juice", "Cotton T-Shirt"), state.suggestions.map { it.product.name })
        assertNull(state.suggestions[0].supplierId) // the hidden supplier doesn't count
        assertEquals(listOf("Roy Textiles", "Drinks Co"), state.suppliers.map { it.name })
        assertEquals(1, state.selectedWithoutSupplier)

        viewModel.createDraftOrders()
        assertEquals(StockAlertsEvent.DraftsCreated(listOf(1L), skipped = 1), events.last())
        assertEquals(1L, purchasing.orders.value.single().supplierId)

        viewModel.onFallbackSupplierChange(2)
        assertEquals(0, viewModel.uiState.value.selectedWithoutSupplier)
        viewModel.createDraftOrders()
        val created = (events.last() as StockAlertsEvent.DraftsCreated).orderIds
        assertEquals(2, created.size)
        assertEquals(setOf(1L, 2L), purchasing.orders.value.filter { it.id in created }.map { it.supplierId }.toSet())
    }

    @Test
    fun choosingNothing_ordersNothing() = runTest {
        val viewModel = viewModel()
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)
        viewModel.selectAll(false)
        assertTrue(viewModel.uiState.value.selected.isEmpty())
        viewModel.createDraftOrders()
        assertEquals(StockAlertsEvent.NothingToOrder, events.last())
        viewModel.toggleSelected(inventory.products.value.first { it.name == "Cotton T-Shirt" }.variants[1].id)
        assertEquals(1, viewModel.uiState.value.selected.size)
        assertTrue(purchasing.orders.value.isEmpty())
    }

    @Test
    fun expiry_listsBatches_andWritesThemOff() = runTest {
        val viewModel = viewModel(AlertsTab.EXPIRY)
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)
        val state = viewModel.uiState.value
        assertEquals(listOf("OLD", "NEW"), state.batches.map { it.batch.batchNumber })
        assertEquals(1, state.expiredBatchCount)
        assertEquals(Money(22_000), state.expiredValue) // 10 packs at ₹22
        viewModel.onExpiryWindowChange(ExpiryWindow.EXPIRED)
        assertEquals(listOf("OLD"), viewModel.uiState.value.batches.map { it.batch.batchNumber })

        viewModel.startWriteOff(viewModel.uiState.value.batches.single())
        assertEquals(StockChangeReason.EXPIRED, viewModel.wastageDialog.value!!.reason)
        assertEquals("10", viewModel.wastageDialog.value!!.quantity)
        viewModel.onWastageQuantityChange("11")
        viewModel.confirmWriteOff()
        assertEquals(WastageError.MORE_THAN_IN_BATCH, viewModel.wastageDialog.value!!.error)

        viewModel.onWastageQuantityChange("10")
        viewModel.onWastageReasonChange(StockChangeReason.SPOILED)
        viewModel.onWastageNoteChange("Mould")
        viewModel.confirmWriteOff()
        assertNull(viewModel.wastageDialog.value)
        assertEquals(StockAlertsEvent.WrittenOff("Butter Biscuits"), events.last())
        val stored = inventory.products.value.first { it.name == "Butter Biscuits" }.variants[0]
        assertEquals(30, stored.stockQuantity)
        val movement = inventory.movements.value.last()
        assertEquals(StockChangeReason.SPOILED, movement.reason)
        assertEquals("Mould", movement.note)
        assertTrue(viewModel.uiState.value.batches.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StockMovementsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))

    private suspend fun move(variantId: Long, change: Int, reason: StockChangeReason) =
        inventory.recordStockChange(StockMovement(variantId = variantId, quantityChange = change, reason = reason, createdAtMillis = TestData.clock.millis()))

    @Test
    fun filters_andExport() = runTest {
        move(11, 5, StockChangeReason.RECEIVED)
        move(11, -1, StockChangeReason.DAMAGED)
        move(21, -2, StockChangeReason.SOLD)
        val viewModel = StockMovementsViewModel(inventory, TestData.clock)
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)

        assertEquals(3, viewModel.uiState.value.movements.size)
        assertEquals(1, viewModel.uiState.value.receivedCount)
        assertEquals(2, viewModel.uiState.value.outCount)
        assertEquals(Money(25_000), viewModel.uiState.value.wastageValue) // one damaged shirt at cost

        viewModel.onGroupChange(MovementGroup.WASTAGE)
        assertEquals(listOf(StockChangeReason.DAMAGED), viewModel.uiState.value.movements.map { it.movement.reason })
        viewModel.onGroupChange(MovementGroup.ALL)
        viewModel.onCategoryChange(ProductCategory.PACKAGED_FOOD)
        assertEquals(listOf("Butter Biscuits"), viewModel.uiState.value.movements.map { it.productName })
        viewModel.onCategoryChange(null)
        viewModel.onQueryChange("navy")
        assertEquals(2, viewModel.uiState.value.movements.size)

        viewModel.export()
        val file = (events.last() as StockMovementsEvent.Export).file
        assertEquals("stock-movements-last-7-days-2026-10-10.csv", file.fileName)
        val lines = file.content.trimEnd().split("\r\n")
        assertEquals("When,Item,Variant,Reason,Change,Batch,Reference,Note", lines.first())
        assertEquals(3, lines.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flour = TestData.flour(grams = 500)
    private val milk = TestData.milk(millilitres = 2_000)
    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), flour, milk))
    private val paratha = MenuItem(
        id = 1, name = "Paratha", section = "Breakfast", price = Money(6_000), deductIngredients = true,
        recipe = listOf(RecipeLine(flour.variants[0].id, 120), RecipeLine(milk.variants[0].id, 30)),
    )
    private val tea = MenuItem(id = 2, name = "Masala Tea", section = "Drinks", price = Money(2_000))
    private val menu = FakeMenuRepository(listOf(paratha, tea))

    @Test
    fun menuList_showsSectionsAndServingsLeft() = runTest {
        val viewModel = MenuListViewModel(menu, inventory, TestData.clock)
        keepActive(viewModel.uiState)
        val state = viewModel.uiState.value
        assertEquals(listOf("Breakfast", "Drinks"), state.sections.map { it.name })
        assertEquals(4, state.sections[0].dishes.single().servingsLeft) // 500 g flour / 120 g
        assertNull(state.sections[1].dishes.single().servingsLeft)
        assertEquals(0, state.shortCount)

        viewModel.setAvailable(tea.id, false)
        assertEquals(1, viewModel.uiState.value.unavailableCount)
        viewModel.onQueryChange("tea")
        assertEquals(listOf("Masala Tea"), viewModel.uiState.value.sections.flatMap { it.dishes }.map { it.item.name })

        inventory.products.value = inventory.products.value.map { product ->
            if (product.id == flour.id) product.copy(variants = listOf(product.variants[0].copy(stockQuantity = 100))) else product
        }
        viewModel.onQueryChange("")
        assertEquals(1, viewModel.uiState.value.shortCount)
    }

    @Test
    fun editor_savesADishWithoutARecipe() = runTest {
        val viewModel = MenuEditorViewModel(0, menu, inventory)
        val events = record(viewModel.events)
        assertEquals(listOf("Breakfast", "Drinks"), viewModel.uiState.value.sections)

        viewModel.onFormChange(viewModel.uiState.value.form.copy(name = "Puri", section = "Breakfast", price = "45"))
        viewModel.save()
        val id = (events.last() as MenuEditorEvent.Saved).menuItemId
        val saved = menu.items.value.first { it.id == id }
        assertTrue(saved.recipe.isEmpty())
        assertEquals(Money(4_500), saved.price)
    }

    @Test
    fun editor_loadsAnExistingDish_andDeletesIt() = runTest {
        val viewModel = MenuEditorViewModel(paratha.id, menu, inventory)
        val events = record(viewModel.events)
        val form = viewModel.uiState.value.form
        assertEquals("Paratha", form.name)
        // Recipes are gone from the editor, even for a dish that had one.
        assertTrue(form.recipe.isEmpty())
        assertFalse(form.deductIngredients)

        viewModel.onFormChange(form.copy(price = ""))
        viewModel.save()
        assertTrue(events.last() is MenuEditorEvent.Invalid)
        assertEquals(Money(6_000), menu.items.value.first { it.id == paratha.id }.price)

        viewModel.delete()
        assertEquals(MenuEditorEvent.Deleted, events.last())
        assertEquals(listOf(tea.id), menu.items.value.map { it.id })
    }
}
