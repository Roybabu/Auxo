package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind

/**
 * Checks that an invoice or credit note adds up before it is saved: each line's
 * total is its taxable value plus tax, the lines add up to the header totals,
 * the grand total is the lines plus round-off, and the payments (or refunds)
 * add up to the grand total exactly. The builders always produce balanced
 * documents; this stops a bug elsewhere from writing one that is not.
 */
object InvoiceIntegrity {

    fun problems(invoice: Invoice): List<String> {
        val found = mutableListOf<String>()
        fun sum(values: List<Money>) = values.fold(Money.ZERO) { acc, m -> acc + m }

        if (invoice.lines.isEmpty()) found += "no lines"
        invoice.lines.forEachIndexed { index, line ->
            val n = index + 1
            if (line.quantity <= 0) found += "line $n has quantity ${line.quantity}"
            val amounts = listOf(line.unitPrice, line.discount, line.taxable, line.cgst, line.sgst, line.igst, line.total)
            if (amounts.any { it.minorUnits < 0 }) found += "line $n has a negative amount"
            if (line.taxable + line.cgst + line.sgst + line.igst != line.total) found += "line $n total is not taxable value plus tax"
        }
        if (sum(invoice.lines.map { it.taxable }) != invoice.taxableTotal) found += "taxable total does not match the lines"
        if (sum(invoice.lines.map { it.cgst }) != invoice.cgstTotal) found += "CGST total does not match the lines"
        if (sum(invoice.lines.map { it.sgst }) != invoice.sgstTotal) found += "SGST total does not match the lines"
        if (sum(invoice.lines.map { it.igst }) != invoice.igstTotal) found += "IGST total does not match the lines"
        if (sum(invoice.lines.map { it.total }) + invoice.roundOff != invoice.grandTotal) found += "grand total is not the lines plus round-off"
        if (invoice.grandTotal.minorUnits < 0) found += "grand total is negative"

        val expectedKind = if (invoice.type == InvoiceType.SALE) PaymentKind.PAYMENT else PaymentKind.REFUND
        if (invoice.payments.any { it.kind != expectedKind }) found += "payments are of the wrong kind for a ${invoice.type.name.lowercase()}"
        if (invoice.payments.any { it.amount.minorUnits <= 0 }) found += "a payment is zero or negative"
        if (sum(invoice.payments.map { it.amount }) != invoice.grandTotal) found += "payments do not add up to the grand total"
        return found
    }

    /** @throws IllegalArgumentException listing what is wrong, if [invoice] does not balance. */
    fun require(invoice: Invoice) {
        val problems = problems(invoice)
        require(problems.isEmpty()) { "Invoice does not balance: ${problems.joinToString("; ")}" }
    }
}
