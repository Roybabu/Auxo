// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockStatus
import java.time.LocalDate

enum class StockFilter { ALL, LOW_STOCK, OUT_OF_STOCK, EXPIRING_SOON }

data class InventoryQuery(
    val text: String = "",
    val category: ProductCategory? = null,
    /** A shop category row; two categories can share a kind, so lists filter on this. */
    val categoryId: Long? = null,
    val stockFilter: StockFilter = StockFilter.ALL,
) {
    val isFiltered: Boolean
        get() = text.isNotBlank() || category != null || categoryId != null || stockFilter != StockFilter.ALL
}

object InventoryFilter {

    /** Default for the shop's expiring-soon window (see StoreSettings.expiryWarningDays). */
    const val EXPIRY_WARNING_DAYS = 30L

    fun apply(
        products: List<Product>,
        query: InventoryQuery,
        today: LocalDate,
        warningDays: Int = EXPIRY_WARNING_DAYS.toInt(),
    ): List<Product> {
        val text = query.text.trim()
        return products
            .asSequence()
            .filter { query.category == null || it.category == query.category }
            .filter { query.categoryId == null || it.categoryId == query.categoryId }
            .filter { text.isEmpty() || matchesText(it, text) }
            .filter { matchesStockFilter(it, query.stockFilter, today, warningDays.toLong()) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    /** Name, brand, subcategory, size or colour contain the text; SKU or barcode match exactly or contain it. */
    fun matchesText(product: Product, text: String): Boolean {
        if (product.name.contains(text, ignoreCase = true)) return true
        if (product.brand?.contains(text, ignoreCase = true) == true) return true
        if (product.subcategory?.contains(text, ignoreCase = true) == true) return true
        return product.variants.any { variant ->
            variant.barcode == text ||
                variant.sku?.contains(text, ignoreCase = true) == true ||
                variant.size?.equals(text, ignoreCase = true) == true ||
                variant.colour?.contains(text, ignoreCase = true) == true
        }
    }

    private fun matchesStockFilter(product: Product, filter: StockFilter, today: LocalDate, warningDays: Long): Boolean =
        when {
            filter == StockFilter.ALL -> true
            // An item sold without a stock count has no stock level or expiry to filter on.
            !product.tracksStock -> false
            filter == StockFilter.LOW_STOCK -> product.variants.any { it.stockStatus == StockStatus.LOW_STOCK }
            filter == StockFilter.OUT_OF_STOCK -> product.variants.any { it.stockStatus == StockStatus.OUT_OF_STOCK }
            else -> product.variants.any {
                it.isExpired(today) || it.expiresWithin(today, warningDays)
            }
        }
}
