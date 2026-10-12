package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auxo.pos.data.local.entity.PurchaseOrderEntity
import com.auxo.pos.data.local.entity.PurchaseOrderLineEntity
import com.auxo.pos.data.local.entity.PurchaseOrderWithLines
import com.auxo.pos.data.local.entity.PurchaseReceiptEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptWithLines
import com.auxo.pos.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

/** Thrown inside a receipt transaction when more arrives than an order line has left. */
class OverReceiptException(val orderLineIds: Set<Long>) : RuntimeException("More received than ordered")

/** Thrown when the same supplier bill is received a second time. */
class DuplicateSupplierInvoiceException : RuntimeException("Supplier bill already received")

@Dao
abstract class PurchasingDao {

    // Suppliers

    @Query("SELECT * FROM suppliers ORDER BY active DESC, name COLLATE NOCASE")
    abstract fun observeSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId")
    abstract fun observeSupplier(supplierId: Long): Flow<SupplierEntity?>

    @Query("SELECT * FROM suppliers WHERE id = :supplierId")
    abstract suspend fun getSupplier(supplierId: Long): SupplierEntity?

    @Insert
    abstract suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    abstract suspend fun updateSupplier(supplier: SupplierEntity)

    // Purchase orders

    @Transaction
    @Query("SELECT * FROM purchase_orders ORDER BY created_at DESC, id DESC")
    abstract fun observeOrders(): Flow<List<PurchaseOrderWithLines>>

    @Transaction
    @Query("SELECT * FROM purchase_orders WHERE id = :orderId")
    abstract fun observeOrder(orderId: Long): Flow<PurchaseOrderWithLines?>

    @Transaction
    @Query("SELECT * FROM purchase_orders WHERE id = :orderId")
    abstract suspend fun getOrder(orderId: Long): PurchaseOrderWithLines?

    @Insert
    abstract suspend fun insertOrder(order: PurchaseOrderEntity): Long

    @Update
    abstract suspend fun updateOrder(order: PurchaseOrderEntity)

    @Insert
    abstract suspend fun insertOrderLines(lines: List<PurchaseOrderLineEntity>)

    @Query("DELETE FROM purchase_order_lines WHERE order_id = :orderId")
    abstract suspend fun deleteOrderLines(orderId: Long)

    @Query("SELECT * FROM purchase_order_lines WHERE order_id = :orderId ORDER BY id")
    abstract suspend fun orderLines(orderId: Long): List<PurchaseOrderLineEntity>

    @Query("UPDATE purchase_orders SET status = :status, updated_at = :nowMillis WHERE id = :orderId")
    abstract suspend fun setOrderStatus(orderId: Long, status: String, nowMillis: Long)

    /** Records units received on an order line, refusing more than are left. */
    @Query(
        "UPDATE purchase_order_lines SET quantity_received = quantity_received + :quantity " +
            "WHERE id = :lineId AND order_id = :orderId AND quantity_received + :quantity <= quantity_ordered",
    )
    abstract suspend fun receiveOnOrderLine(orderId: Long, lineId: Long, quantity: Int): Int

    // Goods receipts

    @Transaction
    @Query("SELECT * FROM purchase_receipts WHERE received_at >= :fromMillis AND received_at < :toMillis ORDER BY received_at DESC, id DESC")
    abstract fun observeReceipts(fromMillis: Long, toMillis: Long): Flow<List<PurchaseReceiptWithLines>>

    @Transaction
    @Query("SELECT * FROM purchase_receipts WHERE id = :receiptId")
    abstract fun observeReceipt(receiptId: Long): Flow<PurchaseReceiptWithLines?>

    @Transaction
    @Query("SELECT * FROM purchase_receipts WHERE order_id = :orderId ORDER BY received_at, id")
    abstract fun observeReceiptsForOrder(orderId: Long): Flow<List<PurchaseReceiptWithLines>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM purchase_receipts WHERE supplier_id = :supplierId " +
            "AND supplier_invoice_number = :supplierInvoiceNumber COLLATE NOCASE)",
    )
    abstract suspend fun supplierInvoiceExists(supplierId: Long, supplierInvoiceNumber: String): Boolean

    @Query("SELECT number FROM purchase_receipts WHERE id = :receiptId")
    abstract suspend fun receiptNumber(receiptId: Long): String?

    @Insert
    abstract suspend fun insertReceipt(receipt: PurchaseReceiptEntity): Long

    @Insert
    abstract suspend fun insertReceiptLine(line: PurchaseReceiptLineEntity): Long
}
