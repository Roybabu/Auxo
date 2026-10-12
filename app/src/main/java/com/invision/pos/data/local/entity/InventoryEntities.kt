package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "products",
    indices = [Index("name"), Index("category")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    /** [com.invision.pos.domain.model.ProductCategory] name. */
    val category: String,
    val description: String?,
    /** Garments only. */
    val material: String?,
    /** Packaged food only: [com.invision.pos.domain.model.DietType] name. */
    @ColumnInfo(name = "diet_type") val dietType: String?,
    @ColumnInfo(name = "hsn_code") val hsnCode: String? = null,
    /** "FIXED" or "APPAREL"; null when no GST rate is set. Added in version 2. */
    @ColumnInfo(name = "gst_rule") val gstRule: String? = null,
    /** Rate in basis points when [gstRule] is "FIXED". */
    @ColumnInfo(name = "gst_rate_bp") val gstRateBasisPoints: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** [com.invision.pos.domain.model.StockUnit] name; null means units. Added in version 3. */
    @ColumnInfo(name = "stock_unit") val stockUnit: String? = null,
    /** Supplier for reorders. Added in version 3. */
    @ColumnInfo(name = "preferred_supplier_id") val preferredSupplierId: Long? = null,
    /** File name of the product photo in the app's private photo folder. Added in version 15. */
    @ColumnInfo(name = "photo_file") val photoFile: String? = null,
)

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("product_id"),
        Index(value = ["barcode"], unique = true),
        Index(value = ["sku"], unique = true),
    ],
)
data class ProductVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val sku: String?,
    val barcode: String,
    val size: String?,
    val colour: String?,
    /** Net quantity in thousandths of [netQuantityUnit]. */
    @ColumnInfo(name = "net_quantity_milli") val netQuantityMilli: Long?,
    @ColumnInfo(name = "net_quantity_unit") val netQuantityUnit: String?,
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "mrp_minor") val mrpMinor: Long?,
    @ColumnInfo(name = "cost_minor") val costMinor: Long?,
    @ColumnInfo(name = "stock_quantity") val stockQuantity: Int,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Int,
    /** For batch-tracked items, the batch that sells next; kept in step with stock_batches. */
    @ColumnInfo(name = "batch_number") val batchNumber: String?,
    /** Expiry as days since 1970-01-01 (LocalDate.toEpochDay). */
    @ColumnInfo(name = "expiry_epoch_day") val expiryEpochDay: Long?,
    /** Added in version 3. */
    @ColumnInfo(name = "reorder_quantity", defaultValue = "0") val reorderQuantity: Int = 0,
)

/**
 * A batch of a food, beverage or ingredient variant. For these items the batch
 * quantities always add up to the variant's stock_quantity. Added in version 3.
 */
@Entity(
    tableName = "stock_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("variant_id"), Index("expiry_epoch_day")],
)
data class StockBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "variant_id") val variantId: Long,
    @ColumnInfo(name = "batch_number") val batchNumber: String?,
    @ColumnInfo(name = "expiry_epoch_day") val expiryEpochDay: Long?,
    /** Units left; never negative. */
    val quantity: Int,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    /** Cost per piece, kg or litre. */
    @ColumnInfo(name = "unit_cost_minor") val unitCostMinor: Long?,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("variant_id"), Index("created_at"), Index("invoice_id"), Index("batch_id"), Index("receipt_id")],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "variant_id") val variantId: Long,
    @ColumnInfo(name = "quantity_change") val quantityChange: Int,
    /** [com.invision.pos.domain.model.StockChangeReason] name. */
    val reason: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Sale or credit note that caused the change. Added in version 2. */
    @ColumnInfo(name = "invoice_id") val invoiceId: Long? = null,
    /** Batch the units came from or went into. Added in version 3. */
    @ColumnInfo(name = "batch_id") val batchId: Long? = null,
    /** Goods receipt that brought the units in. Added in version 3. */
    @ColumnInfo(name = "receipt_id") val receiptId: Long? = null,
)

/**
 * One-time ids of operations already saved, so a repeated tap or retry can't save
 * the same receipt, payment or write-off twice. Added in version 3.
 */
@Entity(tableName = "applied_requests")
data class AppliedRequestEntity(
    @PrimaryKey @ColumnInfo(name = "request_id") val requestId: String,
    val kind: String,
    /** Id of what the operation created, such as the receipt id. */
    @ColumnInfo(name = "result_id") val resultId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class VariantWithBatches(
    @Embedded val variant: ProductVariantEntity,
    @Relation(parentColumn = "id", entityColumn = "variant_id")
    val batches: List<StockBatchEntity>,
)

data class ProductWithVariants(
    @Embedded val product: ProductEntity,
    @Relation(entity = ProductVariantEntity::class, parentColumn = "id", entityColumn = "product_id")
    val variants: List<VariantWithBatches>,
)

/** A variant's category and stock, for deciding how to change its stock. */
data class VariantStockRow(
    val id: Long,
    val category: String,
    @ColumnInfo(name = "stock_quantity") val stockQuantity: Int,
)

/** Batch with its product, for expiry lists. */
data class BatchRow(
    @Embedded val batch: StockBatchEntity,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    val category: String,
    @ColumnInfo(name = "stock_unit") val stockUnit: String?,
    val size: String?,
    val colour: String?,
    @ColumnInfo(name = "net_quantity_milli") val netQuantityMilli: Long?,
    @ColumnInfo(name = "net_quantity_unit") val netQuantityUnit: String?,
    @ColumnInfo(name = "cost_minor") val costMinor: Long?,
)

/** Stock movement with its product and references, for history and reports. */
data class MovementRow(
    @Embedded val movement: StockMovementEntity,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    val category: String,
    @ColumnInfo(name = "stock_unit") val stockUnit: String?,
    val size: String?,
    val colour: String?,
    @ColumnInfo(name = "net_quantity_milli") val netQuantityMilli: Long?,
    @ColumnInfo(name = "net_quantity_unit") val netQuantityUnit: String?,
    @ColumnInfo(name = "cost_minor") val costMinor: Long?,
    @ColumnInfo(name = "batch_number_ref") val batchNumberRef: String?,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String?,
    @ColumnInfo(name = "receipt_number") val receiptNumber: String?,
)

/** Units of a batch taken by a sale, or put back by its returns. */
data class BatchQuantityRow(
    @ColumnInfo(name = "batch_id") val batchId: Long,
    val quantity: Int,
)
