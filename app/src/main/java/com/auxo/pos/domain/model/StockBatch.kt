// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

import java.time.LocalDate

/**
 * Stock of one variant received together, with its own batch number and expiry.
 * Food, beverages and ingredients are kept in batches; sales take the batch that
 * expires first and never take expired stock.
 */
data class StockBatch(
    val id: Long = 0,
    val variantId: Long,
    val batchNumber: String? = null,
    val expiryDate: LocalDate? = null,
    /** Units left in this batch. */
    val quantity: Int,
    val receivedAtMillis: Long = 0,
    /** Purchase cost per piece, kg or litre when known. */
    val unitCost: Money? = null,
) {
    fun isExpired(today: LocalDate): Boolean = expiryDate?.isBefore(today) == true

    /** True if the expiry date falls between today and [days] days from now (inclusive). */
    fun expiresWithin(today: LocalDate, days: Long): Boolean {
        val expiry = expiryDate ?: return false
        return !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(days))
    }
}

/** A batch with the product it belongs to, for expiry and wastage lists. */
data class BatchWithProduct(
    val batch: StockBatch,
    val productId: Long,
    val productName: String,
    val variantLabel: String,
    val category: ProductCategory,
    val stockUnit: StockUnit,
    /** Current cost price of the variant. */
    val unitCost: Money?,
) {
    /** Value of what is left in the batch at cost; null when the cost is unknown. */
    val valueAtCost: Money? get() = unitCost?.let { stockUnit.costOf(batch.quantity, it) }
}

/** Batch details for stock coming in. Items not kept in batches ignore them. */
data class BatchDetails(
    val batchNumber: String? = null,
    val expiryDate: LocalDate? = null,
    /** Cost per piece, kg or litre. */
    val unitCost: Money? = null,
)
