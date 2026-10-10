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
    val batchNumber: String? = null,
    val expiryDate: LocalDate? = null,
) {
    val stockStatus: StockStatus
        get() = when {
            stockQuantity <= 0 -> StockStatus.OUT_OF_STOCK
            stockQuantity <= lowStockThreshold -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

    /** Size, colour and pack size joined for display, e.g. "M · Navy" or "500 g". Empty if none. */
    val attributesLabel: String
        get() = listOfNotNull(size, colour, netQuantity?.format()).joinToString(" · ")

    fun isExpired(today: LocalDate): Boolean = expiryDate?.isBefore(today) == true

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
    val variants: List<ProductVariant> = emptyList(),
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
