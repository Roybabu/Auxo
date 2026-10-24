// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import org.json.JSONArray
import org.json.JSONObject

/**
 * One counted item. [expected] is what the app said was in stock at the moment it was counted and [counted] is
 * what the person found, both in the item's base unit (pieces, grams or millilitres).
 */
data class CountLine(val variantId: Long, val expected: Int, val counted: Int) {
    /** Positive: more found than expected (extra stock). Negative: less found (shortage). */
    val difference: Int get() = counted - expected
}

/** A stock count that is still being done. Nothing in it has changed any stock yet. */
data class StockCount(val id: Long, val startedAtMillis: Long, val lines: List<CountLine>) {
    fun line(variantId: Long): CountLine? = lines.firstOrNull { it.variantId == variantId }
}

/** Why the count was done. Chosen when the differences are posted. */
enum class CountReason(val label: String) {
    ROUTINE("Routine count"),
    YEAR_END("Year-end count"),
    SPOT_CHECK("Spot check"),
    OTHER("Other"),
}

/** A counted line with the product it belongs to. */
data class CountReviewRow(val product: Product, val variant: ProductVariant, val line: CountLine) {
    val difference: Int get() = line.difference

    /** What the difference is worth at cost price; null when the item has no cost price. */
    val valueAtCost: Money?
        get() = variant.costPrice?.let { product.stockUnit.costOf(line.difference, it) }
}

data class CountSummary(
    /** Items counted. */
    val counted: Int,
    /** Items whose count differs from the expected stock. */
    val withDifference: Int,
    /** Items with less than expected. */
    val short: Int,
    /** Items with more than expected. */
    val extra: Int,
    /** Cost of the shortages (a positive amount). Items without a cost price are left out. */
    val shortageValue: Money,
    /** Cost of the extra stock. */
    val extraValue: Money,
)

object StockCounts {

    const val NOTE_MAX = 80

    /** The text saved on every stock movement the count creates. */
    fun movementNote(reason: CountReason, note: String): String {
        val extra = note.trim().take(NOTE_MAX)
        return if (extra.isEmpty()) "Stock count · ${reason.label}" else "Stock count · ${reason.label} · $extra"
    }

    /** One id per count and item, so posting the same count twice can't change stock twice. */
    fun requestId(countId: Long, variantId: Long): String = "stock-count-$countId-$variantId"

    /** What was typed, in the item's entry unit (pcs, kg or L), as a base-unit count; null if it can't be used. */
    fun parseCounted(text: String, product: Product): Int? = product.stockUnit.parse(text, allowZero = true)

    /** Counted lines joined with their products. Lines whose product was deleted since are dropped. */
    fun rows(count: StockCount, products: List<Product>): List<CountReviewRow> {
        val byVariant = products.flatMap { p -> p.variants.map { it.id to (p to it) } }.toMap()
        return count.lines.mapNotNull { line -> byVariant[line.variantId]?.let { (p, v) -> CountReviewRow(p, v, line) } }
    }

    fun differences(rows: List<CountReviewRow>): List<CountReviewRow> = rows.filter { it.difference != 0 }

    fun summary(rows: List<CountReviewRow>): CountSummary {
        val different = differences(rows)
        val short = different.filter { it.difference < 0 }
        val extra = different.filter { it.difference > 0 }
        return CountSummary(
            counted = rows.size,
            withDifference = different.size,
            short = short.size,
            extra = extra.size,
            shortageValue = short.mapNotNull { it.valueAtCost }.fold(Money.ZERO) { acc, v -> acc - v },
            extraValue = extra.mapNotNull { it.valueAtCost }.fold(Money.ZERO) { acc, v -> acc + v },
        )
    }

    /** Adds or replaces [line] (one line per item). */
    fun withLine(lines: List<CountLine>, line: CountLine): List<CountLine> =
        lines.filterNot { it.variantId == line.variantId } + line

    fun encode(lines: List<CountLine>): String {
        val array = JSONArray()
        lines.forEach { array.put(JSONObject().put("v", it.variantId).put("e", it.expected).put("c", it.counted)) }
        return array.toString()
    }

    /** Lines saved by [encode]; an unreadable payload gives no lines instead of a crash. */
    fun decode(payload: String): List<CountLine> = try {
        val array = JSONArray(payload)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            CountLine(o.getLong("v"), o.getInt("e"), o.getInt("c"))
        }
    } catch (e: org.json.JSONException) {
        emptyList()
    }
}
