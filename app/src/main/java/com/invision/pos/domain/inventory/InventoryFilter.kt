package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.StockStatus
import java.time.LocalDate

enum class StockFilter { ALL, LOW_STOCK, OUT_OF_STOCK, EXPIRING_SOON }

data class InventoryQuery(
    val text: String = "",
    val category: ProductCategory? = null,
    val stockFilter: StockFilter = StockFilter.ALL,
) {
    val isFiltered: Boolean
        get() = text.isNotBlank() || category != null || stockFilter != StockFilter.ALL
}

object InventoryFilter {

    /** Items expiring within this many days are flagged on Home and in the "Expiring" filter. */
    const val EXPIRY_WARNING_DAYS = 30L

    fun apply(products: List<Product>, query: InventoryQuery, today: LocalDate): List<Product> {
        val text = query.text.trim()
        return products
            .asSequence()
            .filter { query.category == null || it.category == query.category }
            .filter { text.isEmpty() || matchesText(it, text) }
            .filter { matchesStockFilter(it, query.stockFilter, today) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    /** Name, brand, size or colour contain the text; SKU or barcode match exactly or contain it. */
    fun matchesText(product: Product, text: String): Boolean {
        if (product.name.contains(text, ignoreCase = true)) return true
        if (product.brand?.contains(text, ignoreCase = true) == true) return true
        return product.variants.any { variant ->
            variant.barcode == text ||
                variant.sku?.contains(text, ignoreCase = true) == true ||
                variant.size?.equals(text, ignoreCase = true) == true ||
                variant.colour?.contains(text, ignoreCase = true) == true
        }
    }

    private fun matchesStockFilter(product: Product, filter: StockFilter, today: LocalDate): Boolean =
        when (filter) {
            StockFilter.ALL -> true
            StockFilter.LOW_STOCK -> product.variants.any { it.stockStatus == StockStatus.LOW_STOCK }
            StockFilter.OUT_OF_STOCK -> product.variants.any { it.stockStatus == StockStatus.OUT_OF_STOCK }
            StockFilter.EXPIRING_SOON -> product.variants.any {
                it.isExpired(today) || it.expiresWithin(today, EXPIRY_WARNING_DAYS)
            }
        }
}
