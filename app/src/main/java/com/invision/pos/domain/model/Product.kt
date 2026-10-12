package com.invision.pos.domain.model

import java.time.LocalDate

enum class StockStatus { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

/**
 * One sellable item: a garment in a particular size and colour, or one pack
 * size of a food or beverage product. Stock, price and barcode live here.
 */
data class ProductVariant(
    val id: Long = 0,
    val productId: Long = 0,
    val sku: String? = null,
    val barcode: String,
    val size: String? = null,
    val colour: String? = null,
    val netQuantity: NetQuantity? = null,
    val sellingPrice: Money,
    val mrp: Money? = null,
    val costPrice: Money? = null,
    val stockQuantity: Int = 0,
    val lowStockThreshold: Int = 0,
    /**
     * For batch-tracked items, the batch that sells next (earliest expiry with stock);
     * kept up to date from [batches].
     */
    val batchNumber: String? = null,
    val expiryDate: LocalDate? = null,
    /** Quantity to order when restocking; 0 means work it out from the low-stock level. */
    val reorderQuantity: Int = 0,
    /** Batches with stock, earliest expiry first. Empty for items not kept in batches. */
    val batches: List<StockBatch> = emptyList(),
) {
    /**
     * Stock at or below this is low. A level set on the item is kept; without one, anything under
     * two units is low ([DEFAULT_LOW_STOCK_LEVEL]).
     */
    val effectiveLowStockLevel: Int get() = if (lowStockThreshold > 0) lowStockThreshold else DEFAULT_LOW_STOCK_LEVEL

    /** Out of stock, or low: worked out from the stock now, never saved as a flag. */
    val needsRestock: Boolean get() = stockQuantity <= effectiveLowStockLevel

    val stockStatus: StockStatus
        get() = when {
            stockQuantity <= 0 -> StockStatus.OUT_OF_STOCK
            stockQuantity <= effectiveLowStockLevel -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

    /** Size, colour and pack size joined for display, e.g. "M · Navy" or "500 g". Empty if none. */
    val attributesLabel: String
        get() = listOfNotNull(size, colour, netQuantity?.format()).joinToString(" · ")

    fun isExpired(today: LocalDate): Boolean = expiryDate?.isBefore(today) == true

    /** Expired units still in stock; they can't be sold and should be written off. */
    fun expiredQuantity(today: LocalDate): Int = batches.filter { it.isExpired(today) }.sumOf { it.quantity }

    /** Units that can be sold on [today]: stock less any expired batches. */
    fun sellableQuantity(today: LocalDate): Int = (stockQuantity - expiredQuantity(today)).coerceAtLeast(0)

    /** True if the expiry date falls between today and [days] days from now (inclusive). */
    fun expiresWithin(today: LocalDate, days: Long): Boolean {
        val expiry = expiryDate ?: return false
        return !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(days))
    }
}

data class Product(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val category: ProductCategory,
    val description: String? = null,
    val material: String? = null,
    val dietType: DietType? = null,
    /** HSN code printed on tax invoices (4, 6 or 8 digits). */
    val hsnCode: String? = null,
    /** Null until set; GST-registered stores need it to bill the product. */
    val gstRate: GstRate? = null,
    val variants: List<ProductVariant> = emptyList(),
    /** How stock is counted; only ingredients use grams or millilitres. */
    val stockUnit: StockUnit = StockUnit.UNIT,
    /** Supplier that low-stock purchase orders go to by default. */
    val preferredSupplierId: Long? = null,
    /**
     * Name of the product's photo file in the app's private photo folder (see ProductPhotoStore), or null when it
     * has no photo. Only the name is kept here; the picture itself is not in the database.
     */
    val photoFile: String? = null,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val totalStock: Int get() = variants.sumOf { it.stockQuantity }

    /** Out of stock only when every variant is; low when any variant needs restocking. */
    val stockStatus: StockStatus
        get() = when {
            variants.all { it.stockStatus == StockStatus.OUT_OF_STOCK } -> StockStatus.OUT_OF_STOCK
            variants.any { it.stockStatus != StockStatus.IN_STOCK } -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

    val lowestPrice: Money? get() = variants.minOfOrNull { it.sellingPrice }

    val highestPrice: Money? get() = variants.maxOfOrNull { it.sellingPrice }
}

/** Low-stock level used when an item has none of its own: 0 or 1 unit is low, 2 or more is not. */
const val DEFAULT_LOW_STOCK_LEVEL = 1
