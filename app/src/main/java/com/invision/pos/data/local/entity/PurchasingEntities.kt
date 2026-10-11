package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Added in version 3. Suppliers are deactivated rather than deleted. */
@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "contact_person") val contactPerson: String?,
    val phone: String?,
    val email: String?,
    val gstin: String?,
    @ColumnInfo(name = "state_code") val stateCode: String?,
    val address: String?,
    val notes: String?,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Added in version 3. */
@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplier_id"]),
    ],
    indices = [Index(value = ["number"], unique = true), Index("supplier_id"), Index("status")],
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    @ColumnInfo(name = "supplier_id") val supplierId: Long,
    /** [com.invision.pos.domain.model.PurchaseOrderStatus] name. */
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "expected_epoch_day") val expectedEpochDay: Long?,
    val note: String?,
)

/** Added in version 3. Quantities are in the product's base unit. */
@Entity(
    tableName = "purchase_order_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("order_id"), Index("variant_id")],
)
data class PurchaseOrderLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "variant_id") val variantId: Long?,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "variant_label") val variantLabel: String,
    @ColumnInfo(name = "stock_unit") val stockUnit: String,
    @ColumnInfo(name = "quantity_ordered") val quantityOrdered: Int,
    /** Never more than [quantityOrdered]. */
    @ColumnInfo(name = "quantity_received") val quantityReceived: Int,
    /** Per piece, kg or litre. */
    @ColumnInfo(name = "unit_cost_minor") val unitCostMinor: Long,
)

/**
 * Goods received. Added in version 3. A supplier's bill number can only be
 * received once per supplier.
 */
@Entity(
    tableName = "purchase_receipts",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["number"], unique = true),
        Index(value = ["supplier_id", "supplier_invoice_number"], unique = true),
        Index("order_id"),
        Index("received_at"),
    ],
)
data class PurchaseReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    @ColumnInfo(name = "supplier_id") val supplierId: Long?,
    @ColumnInfo(name = "order_id") val orderId: Long?,
    @ColumnInfo(name = "supplier_invoice_number") val supplierInvoiceNumber: String?,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    val note: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** Added in version 3. */
@Entity(
    tableName = "purchase_receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("receipt_id"), Index("variant_id")],
)
data class PurchaseReceiptLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "receipt_id") val receiptId: Long,
    @ColumnInfo(name = "variant_id") val variantId: Long?,
    @ColumnInfo(name = "order_line_id") val orderLineId: Long?,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "variant_label") val variantLabel: String,
    @ColumnInfo(name = "stock_unit") val stockUnit: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_cost_minor") val unitCostMinor: Long,
    @ColumnInfo(name = "batch_number") val batchNumber: String?,
    @ColumnInfo(name = "expiry_epoch_day") val expiryEpochDay: Long?,
    @ColumnInfo(name = "batch_id") val batchId: Long?,
)

data class PurchaseOrderWithLines(
    @Embedded val order: PurchaseOrderEntity,
    @Relation(parentColumn = "id", entityColumn = "order_id")
    val lines: List<PurchaseOrderLineEntity>,
    @Relation(parentColumn = "supplier_id", entityColumn = "id")
    val supplier: SupplierEntity?,
)

data class PurchaseReceiptWithLines(
    @Embedded val receipt: PurchaseReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receipt_id")
    val lines: List<PurchaseReceiptLineEntity>,
    @Relation(parentColumn = "supplier_id", entityColumn = "id")
    val supplier: SupplierEntity?,
    @Relation(parentColumn = "order_id", entityColumn = "id")
    val order: PurchaseOrderEntity?,
)
