// SPDX-License-Identifier: GPL-3.0-or-later
package com.auxo.pos.domain.insights

import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProfitRow(val name: String, val amount: Money)
data class ProfitCategory(val category: ProductCategory, val amount: Money)
data class ProfitDay(val date: LocalDate, val amount: Money)
data class ProfitEstimate(
    val sales: Money,
    val costs: Money,
    val profit: Money,
    val missingLines: Int,
    val missingSales: Money,
    val products: List<ProfitRow>,
    val categories: List<ProfitCategory>,
    val days: List<ProfitDay>,
    val knownLines: Int,
)

/** Estimate using current variant costs; invoice lines do not store cost at the date of sale. */
object ProfitBuilder {
    fun build(invoices: List<Invoice>, products: List<Product>, zone: ZoneId): ProfitEstimate {
        val variants = products.flatMap { p -> p.variants.map { it.id to (p to it) } }.toMap()
        val byProduct = mutableMapOf<String, Long>()
        val byCategory = mutableMapOf<ProductCategory, Long>()
        val byDay = sortedMapOf<LocalDate, Long>()
        var sales = 0L
        var costs = 0L
        var missing = 0
        var missingSales = 0L
        var known = 0
        for (invoice in invoices) {
            val sign = if (invoice.type == InvoiceType.RETURN) -1L else 1L
            val date = Instant.ofEpochMilli(invoice.createdAtMillis).atZone(zone).toLocalDate()
            for (line in invoice.lines) {
                val item = line.variantId?.let(variants::get)
                val cost = item?.second?.costPrice
                if (item == null || cost == null) {
                    missing++
                    missingSales += sign * line.taxable.minorUnits
                    continue
                }
                known++
                val revenue = sign * line.taxable.minorUnits
                val expense = sign * line.stockUnit.costOf(line.quantity, cost).minorUnits
                val profit = revenue - expense
                sales += revenue
                costs += expense
                byProduct[line.productName] = (byProduct[line.productName] ?: 0) + profit
                val category = item.first.category
                byCategory[category] = (byCategory[category] ?: 0) + profit
                byDay[date] = (byDay[date] ?: 0) + profit
            }
        }
        return ProfitEstimate(Money(sales), Money(costs), Money(sales - costs), missing, Money(missingSales),
            byProduct.entries.sortedByDescending { it.value }.map { ProfitRow(it.key, Money(it.value)) },
            byCategory.entries.sortedByDescending { it.value }.map { ProfitCategory(it.key, Money(it.value)) },
            byDay.map { ProfitDay(it.key, Money(it.value)) }, known)
    }
}
