package com.invision.pos.data.repository

import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.PurchaseReceipt
import com.invision.pos.domain.model.Supplier
import kotlinx.coroutines.flow.Flow

sealed interface ReceiveResult {
    data class Recorded(val receiptId: Long, val number: String) : ReceiveResult

    /** Nothing was saved: more would arrive on these order lines than are left to receive. */
    data class OverReceived(val orderLineIds: Set<Long>) : ReceiveResult

    /** Nothing was saved: this supplier bill number was already received. */
    data object DuplicateSupplierInvoice : ReceiveResult

    /** Nothing was saved: the purchase order is received in full or cancelled. */
    data object OrderClosed : ReceiveResult

    /** Nothing was saved: these items were deleted in the meantime. */
    data class MissingItems(val variantIds: Set<Long>) : ReceiveResult
}

sealed interface OrderUpdateResult {
    data class Saved(val orderId: Long, val number: String) : OrderUpdateResult

    /** The order is no longer a draft, so its lines can't change. */
    data object NotEditable : OrderUpdateResult
}

interface PurchasingRepository {
    fun observeSuppliers(): Flow<List<Supplier>>

    fun observeSupplier(supplierId: Long): Flow<Supplier?>

    suspend fun getSupplier(supplierId: Long): Supplier?

    /** Inserts or updates a supplier and returns its id. */
    suspend fun saveSupplier(supplier: Supplier): Long

    fun observeOrders(): Flow<List<PurchaseOrder>>

    fun observeOrder(orderId: Long): Flow<PurchaseOrder?>

    suspend fun getOrder(orderId: Long): PurchaseOrder?

    /** Saves a draft order, numbering it the first time. Lines are replaced. */
    suspend fun saveDraftOrder(order: PurchaseOrder): OrderUpdateResult

    /** Saves several new draft orders together, for example one per supplier from low-stock alerts. */
    suspend fun createDraftOrders(orders: List<PurchaseOrder>): List<Long>

    /**
     * Moves an order on: DRAFT to ORDERED, or any open order to CANCELLED.
     * Returns false if the change isn't allowed from the current status.
     */
    suspend fun setOrderStatus(orderId: Long, status: PurchaseOrderStatus): Boolean

    fun observeReceipts(fromMillis: Long, toMillis: Long): Flow<List<PurchaseReceipt>>

    fun observeReceipt(receiptId: Long): Flow<PurchaseReceipt?>

    fun observeReceiptsForOrder(orderId: Long): Flow<List<PurchaseReceipt>>

    /**
     * Receives goods in one transaction: numbers the receipt, adds stock (into
     * batches for food and ingredients), records movements, updates the order's
     * received quantities and status, and optionally the items' cost prices.
     * A [requestId] that was already used returns the first result.
     */
    suspend fun receive(receipt: PurchaseReceipt, requestId: String, updateCostPrices: Boolean = true): ReceiveResult
}
