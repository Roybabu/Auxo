package com.invision.pos.core.receipt

import com.invision.pos.domain.billing.IndianNumberFormat
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.ReceiptOptions
import java.time.ZoneId

enum class Align { LEFT, CENTER, RIGHT }

/** One element of a thermal receipt, laid out on a fixed character grid. */
sealed interface ThermalElement {
    /**
     * A line of text. [large] lines print at double width and height, so they
     * hold half as many characters.
     */
    data class Line(
        val text: String,
        val align: Align = Align.LEFT,
        val bold: Boolean = false,
        val large: Boolean = false,
    ) : ThermalElement

    data class Divider(val char: Char = '-') : ThermalElement

    /** Code 128 barcode of the invoice number. */
    data class Barcode(val value: String) : ThermalElement

    data object Gap : ThermalElement
}

data class ThermalReceipt(val width: Int, val elements: List<ThermalElement>)

/**
 * Lays out an invoice for a thermal roll as fixed-width text, the way receipt
 * printers print. Amounts have no currency symbol so columns stay aligned.
 */
object ThermalReceiptBuilder {

    fun build(
        invoice: Invoice,
        options: ReceiptOptions,
        width: Int,
        zone: ZoneId,
        labels: ReceiptLabels = ReceiptLabels(),
    ): ThermalReceipt {
        val out = mutableListOf<ThermalElement>()
        fun center(text: String, bold: Boolean = false, large: Boolean = false) {
            val lineWidth = if (large) width / 2 else width
            ReceiptText.wrap(text, lineWidth).forEach { out += ThermalElement.Line(it, Align.CENTER, bold, large) }
        }
        fun left(text: String, bold: Boolean = false) {
            // Lines that fit keep their spacing; only longer ones are wrapped.
            if (text.length <= width && '\n' !in text) {
                out += ThermalElement.Line(text, Align.LEFT, bold)
                return
            }
            val indent = text.takeWhile { it == ' ' }
            ReceiptText.wrap(text.trimStart(), width - indent.length).forEach {
                out += ThermalElement.Line(indent + it, Align.LEFT, bold)
            }
        }
        fun pair(label: String, value: String, bold: Boolean = false) {
            out += ThermalElement.Line(twoColumns(label, value, width), Align.LEFT, bold)
        }

        // Seller
        val seller = invoice.seller
        center(seller.name, bold = true, large = true)
        if (seller.address.isNotBlank()) center(seller.address)
        if (seller.phone.isNotBlank()) center("Ph: ${seller.phone}")
        if (seller.gstin.isNotBlank()) center("GSTIN: ${seller.gstin}")
        out += ThermalElement.Divider()

        // Invoice details
        center(ReceiptText.title(invoice, labels), bold = true)
        left("${if (invoice.type == InvoiceType.RETURN) "CN No" else "Inv No"}: ${invoice.number}")
        left("Date: ${ReceiptText.dateTime(invoice.createdAtMillis, zone)}")
        invoice.originalInvoiceNumber?.let { left("Against: $it") }
        if (options.showCustomer) {
            left("Customer: ${invoice.buyer.name ?: labels.walkInCustomer}")
            invoice.buyer.phone?.let { left("Phone: $it") }
            invoice.buyer.gstin?.let { left("GSTIN: $it") }
        }
        if (invoice.gstRegistered && invoice.placeOfSupplyStateCode.isNotEmpty()) {
            left("Place of supply: ${ReceiptText.placeOfSupply(invoice.placeOfSupplyStateCode)}")
        }
        out += ThermalElement.Divider()

        // Items
        out += ThermalElement.Line(twoColumns("Item", "Amount", width), bold = true)
        out += ThermalElement.Divider()
        for (line in invoice.lines) {
            val name = listOf(line.productName, line.variantLabel).filter { it.isNotBlank() }.joinToString(" ")
            left(name)
            val gross = line.unitPrice * line.quantity
            pair("  ${line.quantity} x ${IndianNumberFormat.amount(line.unitPrice)}", IndianNumberFormat.amount(gross))
            if (!line.discount.isZero) pair("  Discount", "-" + IndianNumberFormat.amount(line.discount))
            if (invoice.gstRegistered && options.showHsn) {
                val parts = listOfNotNull(
                    line.hsnCode?.let { "HSN $it" },
                    "GST ${ReceiptText.rate(line.gstRateBasisPoints)}",
                )
                left("  " + parts.joinToString("  "))
            }
        }
        out += ThermalElement.Divider()

        // Totals
        pair("Items: ${invoice.lines.size}", "Qty: ${invoice.itemCount}")
        pair("Subtotal", IndianNumberFormat.amount(invoice.subtotal))
        if (!invoice.discountTotal.isZero) pair("Discount", "-" + IndianNumberFormat.amount(invoice.discountTotal))
        if (invoice.gstRegistered) {
            pair("Taxable value", IndianNumberFormat.amount(invoice.taxableTotal))
            if (invoice.interState) {
                pair("IGST", IndianNumberFormat.amount(invoice.igstTotal))
            } else {
                pair("CGST", IndianNumberFormat.amount(invoice.cgstTotal))
                pair("SGST", IndianNumberFormat.amount(invoice.sgstTotal))
            }
        }
        if (!invoice.roundOff.isZero) pair("Round off", signed(invoice.roundOff))
        out += ThermalElement.Divider('=')
        val totalLabel = if (invoice.type == InvoiceType.RETURN) "REFUND" else "TOTAL"
        val totalValue = IndianNumberFormat.amount(invoice.grandTotal)
        if (totalLabel.length + 1 + totalValue.length <= width / 2) {
            out += ThermalElement.Line(twoColumns(totalLabel, totalValue, width / 2), bold = true, large = true)
        } else {
            pair("$totalLabel (Rs.)", totalValue, bold = true)
        }
        out += ThermalElement.Divider('=')

        // Payments
        for (payment in invoice.payments) {
            val method = ReceiptText.method(payment.method)
            val label = if (payment.kind == PaymentKind.REFUND) "Refund ($method)" else "Paid by $method"
            pair(label, IndianNumberFormat.amount(payment.amount))
            payment.reference?.let { left("  Ref: $it") }
            payment.tendered?.let { pair("  Cash received", IndianNumberFormat.amount(it)) }
            payment.change?.let { pair("  Change", IndianNumberFormat.amount(it)) }
        }

        // GST summary
        if (invoice.gstRegistered && options.showTaxBreakup && invoice.lines.isNotEmpty()) {
            out += ThermalElement.Divider()
            out += ThermalElement.Line("GST summary", bold = true)
            gstSummaryRows(invoice, width).forEach { out += ThermalElement.Line(it) }
        }

        if (invoice.note != null) {
            out += ThermalElement.Divider()
            left("Reason: ${invoice.note}")
        }

        out += ThermalElement.Divider()
        if (options.showSavings && invoice.type == InvoiceType.SALE && invoice.savings > Money.ZERO) {
            center("You saved Rs. ${IndianNumberFormat.amount(invoice.savings)}", bold = true)
        }
        if (options.footer.isNotBlank()) center(options.footer)
        if (options.showInvoiceBarcode && invoice.number.isNotEmpty()) {
            out += ThermalElement.Gap
            out += ThermalElement.Barcode(invoice.number)
        }
        return ThermalReceipt(width, out)
    }

    /** Rate, taxable value and tax columns; falls back to two lines when a row is too wide. */
    private fun gstSummaryRows(invoice: Invoice, width: Int): List<String> {
        val groups = invoice.lines.groupBy { it.gstRateBasisPoints }.toSortedMap()
        val header = if (invoice.interState) listOf("Rate", "Taxable", "IGST") else listOf("Rate", "Taxable", "CGST", "SGST")
        val rows = groups.map { (rate, lines) ->
            val taxable = lines.fold(Money.ZERO) { acc, l -> acc + l.taxable }
            val cgst = lines.fold(Money.ZERO) { acc, l -> acc + l.cgst }
            val sgst = lines.fold(Money.ZERO) { acc, l -> acc + l.sgst }
            val igst = lines.fold(Money.ZERO) { acc, l -> acc + l.igst }
            val values = if (invoice.interState) {
                listOf(IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(igst))
            } else {
                listOf(IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(cgst), IndianNumberFormat.amount(sgst))
            }
            listOf(ReceiptText.rate(rate)) + values
        }
        val all = listOf(header) + rows
        val rateWidth = all.maxOf { it[0].length }
        val valueWidth = (width - rateWidth) / (header.size - 1)
        val fits = all.all { row -> row.drop(1).all { it.length < valueWidth } } && valueWidth > 0
        return if (fits) {
            all.map { row -> row[0].padEnd(rateWidth) + row.drop(1).joinToString("") { it.padStart(valueWidth) } }
        } else {
            all.flatMap { row ->
                listOf(twoColumns(row[0], row[1], width)) +
                    row.drop(2).mapIndexed { i, value -> twoColumns("  " + header[i + 2], value, width) }
            }
        }
    }

    private fun signed(amount: Money): String =
        if (amount > Money.ZERO) "+" + IndianNumberFormat.amount(amount) else IndianNumberFormat.amount(amount)

    /** Label on the left, value on the right; the label is cut short if needed. */
    fun twoColumns(label: String, value: String, width: Int): String {
        val space = width - value.length - 1
        if (space <= 0) return value.takeLast(width)
        val shown = if (label.length > space) label.take(space) else label
        return shown + " ".repeat(width - shown.length - value.length) + value
    }
}
