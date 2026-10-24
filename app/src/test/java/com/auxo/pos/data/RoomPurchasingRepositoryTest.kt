// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.OrderUpdateResult
import com.auxo.pos.data.repository.ReceiveResult
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomPurchasingRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Suppliers, purchase orders and goods receipts against the real Room database. */
@RunWith(AndroidJUnit4::class)
class RoomPurchasingRepositoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var purchasing: RoomPurchasingRepository
    private val now = TestData.clock.millis()
    private val today = TestData.today

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        purchasing = RoomPurchasingRepository(database, TestData.clock) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    private suspend fun supplier(name: String = "Fresh Mills"): Long = purchasing.saveSupplier(Supplier(name = name))

    private suspend fun stockOf(product: Product): Int = inventory.getProduct(product.id)!!.variants[0].stockQuantity

    private fun orderLine(product: Product, quantity: Int, cost: Long) = PurchaseOrderLine(
        variantId = product.variants[0].id,
        productName = product.name,
        variantLabel = product.variants[0].attributesLabel,
        stockUnit = product.stockUnit,
        quantityOrdered = quantity,
        unitCost = Money(cost),
    )

    private suspend fun orderedOrder(supplierId: Long, vararg lines: PurchaseOrderLine): PurchaseOrder {
        val saved = purchasing.saveDraftOrder(PurchaseOrder(supplierId = supplierId, lines = lines.toList())) as OrderUpdateResult.Saved
        assertTrue(purchasing.setOrderStatus(saved.orderId, PurchaseOrderStatus.ORDERED))
        return purchasing.getOrder(saved.orderId)!!
    }

    private fun receiptLine(product: Product, quantity: Int, cost: Long, orderLineId: Long? = null, batch: String? = null, expiryInDays: Long? = null) =
        PurchaseReceiptLine(
            variantId = product.variants[0].id,
            orderLineId = orderLineId,
            productName = product.name,
            variantLabel = product.variants[0].attributesLabel,
            stockUnit = product.stockUnit,
            quantity = quantity,
            unitCost = Money(cost),
            batchNumber = batch,
            expiryDate = expiryInDays?.let { today.plusDays(it) },
        )

    @Test
    fun suppliers_saveAndHide() = runTest {
        val id = supplier()
        purchasing.saveSupplier(purchasing.getSupplier(id)!!.copy(phone = "033 2229 1234", active = false))
        val stored = purchasing.observeSuppliers().first().single()
        assertEquals("033 2229 1234", stored.phone)
        assertFalse(stored.active)
        assertEquals(now, stored.createdAtMillis)
    }

    @Test
    fun drafts_areNumberedAndEditableUntilOrdered() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val supplierId = supplier()
        val first = purchasing.saveDraftOrder(PurchaseOrder(supplierId = supplierId, lines = listOf(orderLine(shirt, 10, 25_000)))) as OrderUpdateResult.Saved
        assertEquals("PO/26-27/00001", first.number)

        val draft = purchasing.getOrder(first.orderId)!!
        assertEquals("Fresh Mills", draft.supplierName)
        assertEquals(Money(250_000), draft.total)
        val edited = purchasing.saveDraftOrder(draft.copy(lines = listOf(orderLine(shirt, 12, 24_000)))) as OrderUpdateResult.Saved
        assertEquals("PO/26-27/00001", edited.number)
        assertEquals(12, purchasing.getOrder(first.orderId)!!.lines.single().quantityOrdered)

        assertTrue(purchasing.setOrderStatus(first.orderId, PurchaseOrderStatus.ORDERED))
        assertFalse(purchasing.setOrderStatus(first.orderId, PurchaseOrderStatus.ORDERED))
        assertEquals(OrderUpdateResult.NotEditable, purchasing.saveDraftOrder(purchasing.getOrder(first.orderId)!!))
        assertFalse(purchasing.setOrderStatus(first.orderId, PurchaseOrderStatus.RECEIVED)) // only receipts do that

        val ids = purchasing.createDraftOrders(listOf(PurchaseOrder(supplierId = supplierId, lines = listOf(orderLine(shirt, 1, 1))), PurchaseOrder(supplierId = supplierId, lines = listOf(orderLine(shirt, 2, 1)))))
        assertEquals(listOf("PO/26-27/00002", "PO/26-27/00003"), ids.map { purchasing.getOrder(it)!!.number })
        // Stock never changes for orders.
        assertEquals(8, stockOf(shirt))
    }

    @Test
    fun receivingAnOrder_inParts_addsStockBatchesAndClosesIt() = runTest {
        val biscuits = saveNew(TestData.biscuits()) // 40 in stock, cost ₹22
        val flour = saveNew(TestData.flour(grams = 1_000)) // 1 kg, cost ₹45/kg
        val supplierId = supplier()
        val order = orderedOrder(supplierId, orderLine(biscuits, 24, 2_000), orderLine(flour, 10_000, 4_000))
        val biscuitLine = order.lines.first { it.variantId == biscuits.variants[0].id }
        val flourLine = order.lines.first { it.variantId == flour.variants[0].id }

        // First delivery: all the biscuits, part of the flour.
        val first = purchasing.receive(
            PurchaseReceipt(
                orderId = order.id,
                supplierInvoiceNumber = "fm-101",
                lines = listOf(
                    receiptLine(biscuits, 24, 2_000, biscuitLine.id, batch = "GB-7", expiryInDays = 120),
                    receiptLine(flour, 4_000, 4_000, flourLine.id, batch = "W1", expiryInDays = 45),
                ),
            ),
            requestId = "r1",
        ) as ReceiveResult.Recorded
        assertEquals("GRN/26-27/00001", first.number)
        assertEquals(64, stockOf(biscuits))
        assertEquals(5_000, stockOf(flour))
        val partly = purchasing.getOrder(order.id)!!
        assertEquals(PurchaseOrderStatus.PARTLY_RECEIVED, partly.status)
        assertEquals(6_000, partly.lines.first { it.id == flourLine.id }.remaining)

        // Cost prices follow the latest receipt; batches carry their own numbers and expiry.
        val storedBiscuits = inventory.getProduct(biscuits.id)!!.variants[0]
        assertEquals(Money(2_000), storedBiscuits.costPrice)
        assertEquals(setOf(null, "GB-7"), storedBiscuits.batches.map { it.batchNumber }.toSet())
        val receipt = purchasing.observeReceipt(first.receiptId).first()!!
        assertEquals("FM-101", receipt.supplierInvoiceNumber)
        assertEquals("Fresh Mills", receipt.supplierName)
        assertEquals(order.number, receipt.orderNumber)
        assertEquals(Money(48_000 + 16_000), receipt.total)

        // Receiving more than is still due is refused as a whole.
        val over = purchasing.receive(
            PurchaseReceipt(orderId = order.id, lines = listOf(receiptLine(flour, 6_001, 4_000, flourLine.id))),
            requestId = "r2",
        )
        assertEquals(ReceiveResult.OverReceived(setOf(flourLine.id)), over)
        assertEquals(5_000, stockOf(flour))

        // The rest arrives: the order is received in full.
        val second = purchasing.receive(
            PurchaseReceipt(orderId = order.id, supplierInvoiceNumber = "FM-102", lines = listOf(receiptLine(flour, 6_000, 4_000, flourLine.id, batch = "W2", expiryInDays = 50))),
            requestId = "r3",
        ) as ReceiveResult.Recorded
        assertEquals("GRN/26-27/00002", second.number) // the refused receipt used no number
        assertEquals(11_000, stockOf(flour))
        assertEquals(PurchaseOrderStatus.RECEIVED, purchasing.getOrder(order.id)!!.status)
        assertEquals(2, purchasing.observeReceiptsForOrder(order.id).first().size)

        // A closed order takes no more goods.
        val closed = purchasing.receive(PurchaseReceipt(orderId = order.id, lines = listOf(receiptLine(flour, 1, 4_000, flourLine.id))), requestId = "r4")
        assertEquals(ReceiveResult.OrderClosed, closed)

        // Every unit received is a RECEIVED movement linked to its receipt.
        val received = inventory.observeMovementHistory(0, Long.MAX_VALUE).first().filter { it.movement.reason == StockChangeReason.RECEIVED }
        assertEquals(3, received.size)
        assertEquals(setOf(first.number, second.number), received.mapNotNull { it.reference }.toSet())
        assertEquals(setOf("GB-7", "W1", "W2"), received.mapNotNull { it.batchNumber }.toSet())
    }

    @Test
    fun theSameRequest_receivesOnlyOnce() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val supplierId = supplier()
        val receipt = PurchaseReceipt(supplierId = supplierId, lines = listOf(receiptLine(shirt, 5, 24_000)))
        val first = purchasing.receive(receipt, requestId = "tap") as ReceiveResult.Recorded
        val again = purchasing.receive(receipt, requestId = "tap") as ReceiveResult.Recorded
        assertEquals(first, again)
        assertEquals(13, stockOf(shirt))
        assertEquals(1, purchasing.observeReceipts(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun theSameSupplierBill_isReceivedOnce_withOrWithoutAnOrder() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val supplierId = supplier()
        val otherSupplier = supplier("Sample Textiles")
        purchasing.receive(PurchaseReceipt(supplierId = supplierId, supplierInvoiceNumber = "B-77", lines = listOf(receiptLine(shirt, 2, 24_000))), "a")

        val repeat = purchasing.receive(PurchaseReceipt(supplierId = supplierId, supplierInvoiceNumber = "b-77", lines = listOf(receiptLine(shirt, 2, 24_000))), "b")
        assertEquals(ReceiveResult.DuplicateSupplierInvoice, repeat)

        // Against an order the supplier comes from the order.
        val order = orderedOrder(supplierId, orderLine(shirt, 4, 24_000))
        val viaOrder = purchasing.receive(
            PurchaseReceipt(orderId = order.id, supplierInvoiceNumber = "B-77", lines = listOf(receiptLine(shirt, 4, 24_000, order.lines.single().id))),
            "c",
        )
        assertEquals(ReceiveResult.DuplicateSupplierInvoice, viaOrder)
        assertEquals(PurchaseOrderStatus.ORDERED, purchasing.getOrder(order.id)!!.status)

        // Another supplier may use the same bill number.
        assertTrue(purchasing.receive(PurchaseReceipt(supplierId = otherSupplier, supplierInvoiceNumber = "B-77", lines = listOf(receiptLine(shirt, 1, 24_000))), "d") is ReceiveResult.Recorded)
        assertEquals(11, stockOf(shirt))
    }

    @Test
    fun deletedItems_failTheWholeReceipt() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val biscuits = saveNew(TestData.biscuits())
        val supplierId = supplier()
        val lines = listOf(receiptLine(shirt, 2, 24_000), receiptLine(biscuits, 10, 2_000).copy(variantId = 9_999))
        val result = purchasing.receive(PurchaseReceipt(supplierId = supplierId, lines = lines), "x")
        assertEquals(ReceiveResult.MissingItems(setOf(9_999L)), result)
        assertEquals(8, stockOf(shirt))
        assertTrue(purchasing.observeReceipts(0, Long.MAX_VALUE).first().isEmpty())
        // The request id is free again after the rollback.
        assertTrue(purchasing.receive(PurchaseReceipt(supplierId = supplierId, lines = lines.take(1)), "x") is ReceiveResult.Recorded)
    }

    @Test
    fun cancelledOrder_keepsWhatArrived() = runTest {
        val flour = saveNew(TestData.flour(grams = 0))
        val supplierId = supplier()
        val order = orderedOrder(supplierId, orderLine(flour, 5_000, 4_000))
        purchasing.receive(PurchaseReceipt(orderId = order.id, lines = listOf(receiptLine(flour, 2_000, 4_000, order.lines.single().id))), "p")
        assertTrue(purchasing.setOrderStatus(order.id, PurchaseOrderStatus.CANCELLED))
        assertEquals(PurchaseOrderStatus.CANCELLED, purchasing.getOrder(order.id)!!.status)
        assertEquals(2_000, stockOf(flour))
        assertEquals(StockUnit.GRAM, inventory.getProduct(flour.id)!!.stockUnit)
    }
}
