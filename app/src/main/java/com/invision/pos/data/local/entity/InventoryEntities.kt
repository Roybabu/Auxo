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
    @ColumnInfo(name = "batch_number") val batchNumber: String?,
    /** Expiry as days since 1970-01-01 (LocalDate.toEpochDay). */
    @ColumnInfo(name = "expiry_epoch_day") val expiryEpochDay: Long?,
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
    indices = [Index("variant_id"), Index("created_at"), Index("invoice_id")],
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
)

data class ProductWithVariants(
    @Embedded val product: ProductEntity,
    @Relation(parentColumn = "id", entityColumn = "product_id")
    val variants: List<ProductVariantEntity>,
)
