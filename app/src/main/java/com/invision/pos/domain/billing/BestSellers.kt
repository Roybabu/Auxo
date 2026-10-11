package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType

/** Works out which items sell the most, for the quick-add list on the billing page. */
object BestSellers {

    /** How far back sales count. */
    const val DAYS = 30

    /** How many items the quick-add list shows. */
    const val LIMIT = 8

    /**
     * Variant ids ordered by units sold, most first. Returns (credit notes) take units off the
     * item they brought back, and items with nothing sold overall are left out. Lines for dishes
     * or deleted items (no variant) are ignored. Ties keep the lower id first so the order is stable.
     */
    fun rank(invoices: List<Invoice>): List<Long> {
        val sold = linkedMapOf<Long, Int>()
        for (invoice in invoices) {
            val sign = if (invoice.type == InvoiceType.RETURN) -1 else 1
            for (line in invoice.lines) {
                val id = line.variantId ?: continue
                sold[id] = (sold[id] ?: 0) + sign * line.quantity
            }
        }
        return sold.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }
}
