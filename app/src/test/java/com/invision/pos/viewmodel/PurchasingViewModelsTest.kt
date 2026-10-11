package com.invision.pos.viewmodel

import com.invision.pos.data.repository.ReceiveResult
import com.invision.pos.domain.inventory.PickableItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderLine
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.PurchaseReceipt
import com.invision.pos.domain.model.PurchaseReceiptLine
import com.invision.pos.domain.model.Supplier
import com.invision.pos.domain.purchasing.PurchaseFormError
import com.invision.pos.domain.purchasing.PurchaseLineError
import com.invision.pos.domain.usecase.ReceiveGoodsUseCase
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakePurchasingRepository
import com.invision.pos.testutil.FakeSettingsRepository
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
class PurchasingViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.flour(grams = 500)))
    private val mills = Supplier(id = 1, name = "Fresh Mills")
    private val textiles = Supplier(id = 2, name = "Roy Textiles")
    private val purchasing = FakePurchasingRepository(inventory, listOf(mills, textiles))

    private fun item(productId: Long, index: Int = 0): PickableItem {
        val product = inventory.products.value.first { it.id == productId }
        return PickableItem(product, product.variants[index])
    }

    private fun stock(variantId: Long) = inventory.products.value.flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    // Purchase order editor

    @Test
    fun newOrder_addsItemsWithSuggestedQuantities_andSaves() = runTest {
        val viewModel = PurchaseOrderEditorViewModel(0, purchasing, inventory, TestData.clock)
        val events = record(viewModel.events)
        viewModel.onSupplierChange(textiles.id)
        viewModel.onItemQueryChange("navy")
        assertEquals(2, viewModel.uiState.value.picker.results.size)
        viewModel.addItem(item(1, index = 1)) // L: 1 in stock, low at 2 -> suggest 3
        viewModel.addItem(item(4)) // flour 500 g, low at 1 kg -> suggest 1.5 kg
        val lines = viewModel.uiState.value.lines
        assertEquals(listOf("3", "1.5"), lines.map { it.quantity })
        assertEquals(listOf("250.00", "45.00"), lines.map { it.unitCost })
        assertEquals("", viewModel.uiState.value.picker.query)
        // 3 x ₹250 + 1.5 kg x ₹45
        assertEquals(Money(75_000 + 6_750), viewModel.uiState.value.total)

        viewModel.addItem(item(1, index = 1))
        assertEquals(PurchaseOrderEditorEvent.AlreadyAdded("Cotton T-Shirt · L · Navy"), events.last())
        assertEquals(2, viewModel.uiState.value.lines.size)

        viewModel.save(markOrdered = true)
        val saved = events.last() as PurchaseOrderEditorEvent.Saved
        assertTrue(saved.ordered)
        val order = purchasing.orders.value.single()
        assertEquals(PurchaseOrderStatus.ORDERED, order.status)
        assertEquals(textiles.id, order.supplierId)
        assertEquals(listOf(3, 1_500), order.lines.map { it.quantityOrdered })
    }

    @Test
    fun invalidOrder_showsErrorsOnlyAfterSaving() = runTest {
        val viewModel = PurchaseOrderEditorViewModel(0, purchasing, inventory, TestData.clock)
        val events = record(viewModel.events)
        viewModel.addItem(item(2))
        val key = viewModel.uiState.value.lines.single().key
        viewModel.onLineQuantityChange(key, "")
        assertTrue(viewModel.uiState.value.visibleLineErrors.isEmpty())

        viewModel.save(markOrdered = false)
        assertTrue(events.last() is PurchaseOrderEditorEvent.Invalid)
        assertEquals(setOf(PurchaseFormError.SUPPLIER_REQUIRED), viewModel.uiState.value.visibleFormErrors)
        assertEquals(setOf(PurchaseLineError.QUANTITY_REQUIRED), viewModel.uiState.value.visibleLineErrors[key])
        assertTrue(purchasing.orders.value.isEmpty())

        viewModel.onSupplierChange(mills.id)
        viewModel.onLineQuantityChange(key, "12x")
        assertEquals("12", viewModel.uiState.value.lines.single().quantity) // only digits and a point are kept
        viewModel.save(markOrdered = false)
        assertEquals(PurchaseOrderStatus.DRAFT, purchasing.orders.value.single().status)
    }

    @Test
    fun sentOrders_cantBeEdited() = runTest {
        val id = purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = mills.id, lines = listOf(orderLine(21, 24))))).single()
        purchasing.setOrderStatus(id, PurchaseOrderStatus.ORDERED)
        val viewModel = PurchaseOrderEditorViewModel(id, purchasing, inventory, TestData.clock)
        assertTrue(viewModel.uiState.value.notEditable)
        assertEquals("PO/26-27/00001", viewModel.uiState.value.number)
    }

    private fun orderLine(variantId: Long, quantity: Int, cost: Long = 2_000) = PurchaseOrderLine(
        variantId = variantId,
        productName = "Butter Biscuits",
        variantLabel = "",
        quantityOrdered = quantity,
        unitCost = Money(cost),
    )

    // Order detail

    @Test
    fun orderDetail_marksOrdered_cancels_andShares() = runTest {
        val id = purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = mills.id, lines = listOf(orderLine(21, 24))))).single()
        val viewModel = PurchaseOrderDetailViewModel(id, purchasing, FakeSettingsRepository())
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)
        val ready = viewModel.uiState.value as PurchaseOrderDetailUiState.Ready
        assertEquals(Money(48_000), ready.remainingValue)

        viewModel.markOrdered()
        assertEquals(PurchaseOrderDetailEvent.StatusChanged(PurchaseOrderStatus.ORDERED), events.last())
        viewModel.markOrdered()
        assertEquals(PurchaseOrderDetailEvent.ChangeRejected, events.last())

        viewModel.share()
        val share = events.last() as PurchaseOrderDetailEvent.Share
        assertTrue(share.text.contains("From: Roy Mart"))
        assertTrue(share.text.contains("To: Fresh Mills"))

        viewModel.cancelOrder()
        assertEquals(PurchaseOrderStatus.CANCELLED, purchasing.orders.value.single().status)
    }

    // Receiving

    private fun receiveViewModel(orderId: Long = 0) =
        ReceiveGoodsViewModel(orderId, purchasing, inventory, ReceiveGoodsUseCase(purchasing, TestData.clock), TestData.clock)

    @Test
    fun receivingAnOrder_fillsWhatIsDue_andRecordsTheReceipt() = runTest {
        val id = purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = mills.id, lines = listOf(orderLine(21, 24), orderLine(11, 5, 25_000))))).single()
        purchasing.setOrderStatus(id, PurchaseOrderStatus.ORDERED)
        val viewModel = receiveViewModel(id)
        val events = record(viewModel.events)
        val state = viewModel.uiState.value
        assertTrue(state.isFromOrder)
        assertEquals(mills.id, state.supplierId)
        assertEquals(listOf("24", "5"), state.lines.map { it.quantity })
        assertEquals(listOf(true, false), state.lines.map { it.tracksExpiry })

        val (biscuits, shirts) = state.lines
        viewModel.onLineQuantityChange(shirts.key, "0") // the shirts didn't come
        viewModel.onLineBatchChange(biscuits.key, "GB-9")
        viewModel.onLineExpiryChange(biscuits.key, TestData.today.plusDays(100))
        viewModel.onSupplierInvoiceChange("FM-1")
        viewModel.save()

        val saved = events.last() as ReceiveGoodsEvent.Saved
        assertEquals("GRN/26-27/00001", saved.number)
        assertEquals(64, stock(21))
        assertEquals(8, stock(11))
        assertEquals(PurchaseOrderStatus.PARTLY_RECEIVED, purchasing.orders.value.single().status)
        val batch = inventory.products.value.first { it.id == 2L }.variants[0].batches.last()
        assertEquals("GB-9", batch.batchNumber)

        // Saving again with the same screen doesn't receive twice.
        viewModel.save()
        assertEquals(64, stock(21))
    }

    @Test
    fun receiving_reportsDuplicateBillsAndOverReceipts() = runTest {
        purchasing.receipts.value = listOf(PurchaseReceipt(id = 9, number = "GRN/26-27/00009", supplierId = mills.id, supplierInvoiceNumber = "FM-1", lines = listOf(receiptLine())))
        val viewModel = receiveViewModel()
        val events = record(viewModel.events)
        viewModel.onSupplierChange(mills.id)
        viewModel.addItem(item(1))
        val key = viewModel.uiState.value.lines.single().key
        viewModel.onLineQuantityChange(key, "2")
        viewModel.onSupplierInvoiceChange("fm-1")
        viewModel.save()
        assertEquals(ReceiveGoodsEvent.DuplicateSupplierInvoice, events.last())
        assertTrue(viewModel.uiState.value.duplicateSupplierInvoice)
        assertEquals(8, stock(11))

        viewModel.onSupplierInvoiceChange("FM-2")
        assertFalse(viewModel.uiState.value.duplicateSupplierInvoice)
        viewModel.save()
        assertTrue(events.last() is ReceiveGoodsEvent.Saved)
        assertEquals(10, stock(11))
    }

    private fun receiptLine() = PurchaseReceiptLine(variantId = 11, productName = "Cotton T-Shirt", variantLabel = "M", quantity = 1, unitCost = Money(1))

    @Test
    fun directReceipt_needsASupplierAndItems() = runTest {
        val viewModel = receiveViewModel()
        val events = record(viewModel.events)
        assertFalse(viewModel.uiState.value.isFromOrder)
        viewModel.save()
        assertTrue(events.last() is ReceiveGoodsEvent.Invalid)
        assertEquals(setOf(PurchaseFormError.SUPPLIER_REQUIRED, PurchaseFormError.NO_LINES), viewModel.uiState.value.formErrors)
        assertEquals(0, purchasing.receiveCalls)
    }

    @Test
    fun overReceipt_marksTheLine() = runTest {
        val id = purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = mills.id, lines = listOf(orderLine(21, 24))))).single()
        purchasing.setOrderStatus(id, PurchaseOrderStatus.ORDERED)
        val viewModel = receiveViewModel(id)
        val events = record(viewModel.events)
        // Another device received part of the order in the meantime.
        purchasing.receive(
            PurchaseReceipt(orderId = id, lines = listOf(PurchaseReceiptLine(variantId = 21, orderLineId = purchasing.orders.value.single().lines.single().id, productName = "B", variantLabel = "", quantity = 20, unitCost = Money(2_000)))),
            "other",
        )
        viewModel.save()
        assertEquals(ReceiveGoodsEvent.OverReceived, events.last())
        val key = viewModel.uiState.value.lines.single().key
        assertEquals(setOf(PurchaseLineError.MORE_THAN_ORDERED), viewModel.uiState.value.lineErrors[key])
    }

    // Purchases list

    @Test
    fun purchaseList_filtersOrdersAndSumsWhatIsDue() = runTest {
        purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = mills.id, lines = listOf(orderLine(21, 10))), PurchaseOrder(supplierId = textiles.id, lines = listOf(orderLine(11, 2, 25_000)))))
        purchasing.setOrderStatus(2, PurchaseOrderStatus.CANCELLED)
        val viewModel = PurchaseListViewModel(PurchaseTab.ORDERS, purchasing, TestData.clock)
        keepActive(viewModel.uiState)
        assertEquals(listOf(1L), viewModel.uiState.value.orders.map { it.id })
        assertEquals(Money(20_000), viewModel.uiState.value.openOrderValue)
        viewModel.onOrderFilterChange(OrderFilter.ALL)
        assertEquals(2, viewModel.uiState.value.orders.size)
        viewModel.onQueryChange("textiles")
        assertEquals(listOf(2L), viewModel.uiState.value.orders.map { it.id })

        assertTrue(purchasing.receive(PurchaseReceipt(supplierId = mills.id, receivedAtMillis = TestData.clock.millis(), lines = listOf(receiptLine())), "r") is ReceiveResult.Recorded)
        viewModel.onTabChange(PurchaseTab.RECEIPTS)
        viewModel.onQueryChange("")
        assertEquals(1, viewModel.uiState.value.receipts.size)
        assertEquals(Money(1), viewModel.uiState.value.receiptsTotal)
    }

    // Suppliers

    @Test
    fun supplierEditor_validatesSavesAndHides() = runTest {
        val viewModel = SupplierEditorViewModel(0, purchasing)
        val events = record(viewModel.events)
        viewModel.save()
        assertTrue(events.last() is SupplierEditorEvent.Invalid)
        viewModel.onFormChange(viewModel.uiState.value.form.copy(name = "Kolkata Spices", phone = "033 2229 1234"))
        viewModel.save()
        val id = (events.last() as SupplierEditorEvent.Saved).supplierId
        assertEquals("Kolkata Spices", purchasing.suppliers.value.first { it.id == id }.name)

        val editor = SupplierEditorViewModel(id, purchasing)
        val editorEvents = record(editor.events)
        editor.setActive(false)
        assertEquals(SupplierEditorEvent.ActiveChanged(false), editorEvents.last())
        assertFalse(purchasing.suppliers.value.first { it.id == id }.active)

        val list = SupplierListViewModel(purchasing)
        keepActive(list.uiState)
        assertEquals(listOf("Fresh Mills", "Roy Textiles"), list.uiState.value.suppliers.map { it.name }.sorted())
        list.onShowHiddenChange(true)
        assertEquals(3, list.uiState.value.suppliers.size)
        list.onQueryChange("2229")
        assertEquals(listOf("Kolkata Spices"), list.uiState.value.suppliers.map { it.name })
    }
}
