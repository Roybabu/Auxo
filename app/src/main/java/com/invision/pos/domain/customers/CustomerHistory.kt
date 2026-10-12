package com.invision.pos.domain.customers

import com.invision.pos.domain.billing.BusinessTime
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Rounding
import java.time.Instant
import java.time.ZoneId

/**
 * What one customer has bought, worked out from their stored invoices and credit notes only.
 *
 * - [purchases] are the sale bills, newest first; [returns] are the credit notes, newest first.
 * - [totalSpend] is what they paid in sales less what was given back on credit notes.
 * - A [visitCount] is a calendar day (in the shop's time zone) with at least one sale bill, so two bills
 *   rung up on the same day count as one visit.
 * - [averageBill] is the average sale bill before returns, and is null until there is a sale.
 */
data class CustomerHistory(
    val purchases: List<InvoiceSummary>,
    val returns: List<InvoiceSummary>,
    val salesTotal: Money,
    val returnsTotal: Money,
    val visitCount: Int,
    val firstVisitMillis: Long?,
    val lastVisitMillis: Long?,
    val averageBill: Money?,
) {
    val billCount: Int get() = purchases.size
    val totalSpend: Money get() = salesTotal - returnsTotal

    companion object {
        val EMPTY = CustomerHistory(emptyList(), emptyList(), Money.ZERO, Money.ZERO, 0, null, null, null)

        fun from(invoices: List<InvoiceSummary>, zone: ZoneId = BusinessTime.ZONE): CustomerHistory {
            val sales = invoices.filter { it.type == InvoiceType.SALE }.sortedWith(newestFirst)
            val returns = invoices.filter { it.type == InvoiceType.RETURN }.sortedWith(newestFirst)
            val salesTotal = sales.fold(Money.ZERO) { acc, invoice -> acc + invoice.grandTotal }
            val returnsTotal = returns.fold(Money.ZERO) { acc, invoice -> acc + invoice.grandTotal }
            val days = sales.map { Instant.ofEpochMilli(it.createdAtMillis).atZone(zone).toLocalDate() }.toSet()
            return CustomerHistory(
                purchases = sales,
                returns = returns,
                salesTotal = salesTotal,
                returnsTotal = returnsTotal,
                visitCount = days.size,
                firstVisitMillis = sales.lastOrNull()?.createdAtMillis,
                lastVisitMillis = sales.firstOrNull()?.createdAtMillis,
                averageBill = sales.takeIf { it.isNotEmpty() }?.let { Money(Rounding.halfUp(salesTotal.minorUnits, it.size.toLong())) },
            )
        }

        private val newestFirst = compareByDescending<InvoiceSummary> { it.createdAtMillis }.thenByDescending { it.id }
    }
}
