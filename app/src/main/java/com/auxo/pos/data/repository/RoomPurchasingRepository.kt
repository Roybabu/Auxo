// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.repository

import androidx.room.withTransaction
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.MissingItemException
import com.auxo.pos.data.local.MovementInfo
import com.auxo.pos.data.local.RequestClaim
import com.auxo.pos.data.local.StockWriter
import com.auxo.pos.data.local.claim
import com.auxo.pos.data.local.dao.DuplicateSupplierInvoiceException
import com.auxo.pos.data.local.dao.OverReceiptException
import com.auxo.pos.data.local.entity.PurchaseReceiptEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.toDomain
import com.auxo.pos.data.local.toEntity
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.purchasing.PurchaseNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.ZoneId

/** Thrown inside a receipt transaction when its order can no longer receive goods. */
private class OrderClosedException : RuntimeException("Order closed")

class RoomPurchasingRepository(
    private val database: AuxoDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId = { clock.zone },
) : PurchasingRepository {

    private val dao = database.purchasingDao()
    private val inventory = database.inventoryDao()
    private val operations = database.operationDao()
    private val stock = StockWriter(inventory)

    override fun observeSuppliers(): Flow<List<Supplier>> = dao.observeSuppliers().map { rows -> rows.map { it.toDomain() } }

    override fun observeSupplier(supplierId: Long): Flow<Supplier?> = dao.observeSupplier(supplierId).map { it?.toDomain() }

    override suspend fun getSupplier(supplierId: Long): Supplier? = dao.getSupplier(supplierId)?.toDomain()

    override suspend fun saveSupplier(supplier: Supplier): Long {
        val entity = supplier.toEntity(clock.millis())
        return if (supplier.id == 0L) {
            dao.insertSupplier(entity)
        } else {
            dao.updateSupplier(entity)
            supplier.id
        }
    }

    override fun observeOrders(): Flow<List<PurchaseOrder>> = dao.observeOrders().map { rows -> rows.map { it.toDomain() } }

    override fun observeOrder(orderId: Long): Flow<PurchaseOrder?> = dao.observeOrder(orderId).map { it?.toDomain() }

    override suspend fun getOrder(orderId: Long): PurchaseOrder? = dao.getOrder(orderId)?.toDomain()

    override suspend fun saveDraftOrder(order: PurchaseOrder): OrderUpdateResult = database.withTransaction {
        val now = clock.millis()
        if (order.id == 0L) {
            val id = insertDraft(order, now)
            OrderUpdateResult.Saved(id, dao.getOrder(id)?.order?.number.orEmpty())
        } else {
            val saved = dao.getOrder(order.id)
            if (saved == null || saved.order.status != PurchaseOrderStatus.DRAFT.name) {
                OrderUpdateResult.NotEditable
            } else {
                dao.updateOrder(order.copy(status = PurchaseOrderStatus.DRAFT, createdAtMillis = saved.order.createdAt).toEntity(saved.order.number, now))
                dao.deleteOrderLines(order.id)
                dao.insertOrderLines(order.lines.map { it.copy(quantityReceived = 0).toEntity(order.id) })
                OrderUpdateResult.Saved(order.id, saved.order.number)
            }
        }
    }

    override suspend fun createDraftOrders(orders: List<PurchaseOrder>): List<Long> = database.withTransaction {
        val now = clock.millis()
        orders.map { insertDraft(it, now) }
    }

    private suspend fun insertDraft(order: PurchaseOrder, nowMillis: Long): Long {
        val year = PurchaseNumbers.financialYear(nowMillis, zone())
        val number = operations.nextNumber(PurchaseNumbers.orderSequence(year)) { PurchaseNumbers.order(year, it) }
        val id = dao.insertOrder(order.copy(id = 0, status = PurchaseOrderStatus.DRAFT, createdAtMillis = nowMillis).toEntity(number, nowMillis))
        dao.insertOrderLines(order.lines.map { it.copy(quantityReceived = 0).toEntity(id) })
        return id
    }

    override suspend fun setOrderStatus(orderId: Long, status: PurchaseOrderStatus): Boolean = database.withTransaction {
        val current = dao.getOrder(orderId)?.order?.status?.let { name -> PurchaseOrderStatus.entries.firstOrNull { it.name == name } }
        val allowed = when (status) {
            PurchaseOrderStatus.ORDERED -> current == PurchaseOrderStatus.DRAFT
            PurchaseOrderStatus.CANCELLED -> current?.isOpen == true
            else -> false
        }
        if (allowed) dao.setOrderStatus(orderId, status.name, clock.millis())
        allowed
    }

    override fun observeReceipts(fromMillis: Long, toMillis: Long): Flow<List<PurchaseReceipt>> =
        dao.observeReceipts(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }

    override fun observeReceipt(receiptId: Long): Flow<PurchaseReceipt?> = dao.observeReceipt(receiptId).map { it?.toDomain() }

    override fun observeReceiptsForOrder(orderId: Long): Flow<List<PurchaseReceipt>> =
        dao.observeReceiptsForOrder(orderId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun receive(receipt: PurchaseReceipt, requestId: String, updateCostPrices: Boolean): ReceiveResult {
        require(receipt.id == 0L && receipt.lines.isNotEmpty())
        require(receipt.lines.all { it.quantity > 0 && it.variantId != null }) { "Every line needs an item and a quantity" }
        return try {
            database.withTransaction {
                val now = receipt.receivedAtMillis.takeIf { it > 0 } ?: clock.millis()
                when (val claim = operations.claim(requestId, "RECEIPT", now)) {
                    // A repeated request never receives twice; it gets the first receipt back.
                    is RequestClaim.AlreadyDone -> {
                        val id = claim.resultId ?: 0L
                        return@withTransaction ReceiveResult.Recorded(id, dao.receiptNumber(id).orEmpty())
                    }
                    RequestClaim.New -> Unit
                }

                val order = receipt.orderId?.let { dao.getOrder(it) }
                if (receipt.orderId != null) {
                    val status = order?.order?.status?.let { name -> PurchaseOrderStatus.entries.firstOrNull { it.name == name } }
                    if (status?.isOpen != true) throw OrderClosedException()
                }
                // Goods against an order come from the order's supplier.
                val supplierId = order?.order?.supplierId ?: receipt.supplierId
                val supplierInvoice = receipt.supplierInvoiceNumber?.trim()?.uppercase()?.ifEmpty { null }
                if (supplierId != null && supplierInvoice != null && dao.supplierInvoiceExists(supplierId, supplierInvoice)) {
                    throw DuplicateSupplierInvoiceException()
                }

                val year = PurchaseNumbers.financialYear(now, zone())
                val number = operations.nextNumber(PurchaseNumbers.receiptSequence(year)) { PurchaseNumbers.receipt(year, it) }
                val receiptId = dao.insertReceipt(
                    PurchaseReceiptEntity(
                        number = number,
                        supplierId = supplierId,
                        orderId = receipt.orderId,
                        supplierInvoiceNumber = supplierInvoice,
                        receivedAt = now,
                        note = receipt.note?.trim()?.ifEmpty { null },
                        totalMinor = receipt.total.minorUnits,
                    ),
                )

                val overReceived = mutableSetOf<Long>()
                val info = MovementInfo(StockChangeReason.RECEIVED, now, note = number, receiptId = receiptId)
                for (line in receipt.lines) {
                    val variantId = line.variantId!!
                    if (line.orderLineId != null && receipt.orderId != null) {
                        if (dao.receiveOnOrderLine(receipt.orderId, line.orderLineId, line.quantity) == 0) {
                            overReceived += line.orderLineId
                            continue
                        }
                    }
                    val batchId = stock.add(
                        variantId = variantId,
                        quantity = line.quantity,
                        info = info,
                        batch = BatchDetails(line.batchNumber?.trim()?.ifEmpty { null }, line.expiryDate, line.unitCost),
                    )
                    dao.insertReceiptLine(
                        PurchaseReceiptLineEntity(
                            receiptId = receiptId,
                            variantId = variantId,
                            orderLineId = line.orderLineId,
                            productName = line.productName,
                            variantLabel = line.variantLabel,
                            stockUnit = line.stockUnit.name,
                            quantity = line.quantity,
                            unitCostMinor = line.unitCost.minorUnits,
                            batchNumber = line.batchNumber?.trim()?.ifEmpty { null },
                            expiryEpochDay = line.expiryDate?.toEpochDay(),
                            batchId = batchId,
                        ),
                    )
                    if (updateCostPrices && line.unitCost.minorUnits > 0) inventory.setCostPrice(variantId, line.unitCost.minorUnits)
                }
                if (overReceived.isNotEmpty()) throw OverReceiptException(overReceived)

                if (receipt.orderId != null) {
                    val lines = dao.orderLines(receipt.orderId)
                    val status = if (lines.all { it.quantityReceived >= it.quantityOrdered }) {
                        PurchaseOrderStatus.RECEIVED
                    } else {
                        PurchaseOrderStatus.PARTLY_RECEIVED
                    }
                    dao.setOrderStatus(receipt.orderId, status.name, now)
                }
                operations.setRequestResult(requestId, receiptId)
                ReceiveResult.Recorded(receiptId, number)
            }
        } catch (e: OverReceiptException) {
            ReceiveResult.OverReceived(e.orderLineIds)
        } catch (e: DuplicateSupplierInvoiceException) {
            ReceiveResult.DuplicateSupplierInvoice
        } catch (e: OrderClosedException) {
            ReceiveResult.OrderClosed
        } catch (e: MissingItemException) {
            ReceiveResult.MissingItems(e.variantIds)
        }
    }
}
