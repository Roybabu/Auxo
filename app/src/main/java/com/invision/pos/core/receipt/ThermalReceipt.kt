package com.invision.pos.core.receipt

import com.invision.pos.domain.billing.IndianNumberFormat
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.upiLinkFor
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
        /**
         * Set on a line that is a label with an amount at the right edge. [text] is the same line laid out on the
         * character grid (for plain-text uses); the renderer draws [label] at the left and [value] at the right
         * with real text widths, because letters of Hindi or Bengali are not all the same width.
         */
        val label: String? = null,
        val value: String? = null,
    ) : ThermalElement

    data class Divider(val char: Char = '-') : ThermalElement

    /** Code 128 barcode of the invoice number. */
    data class Barcode(val value: String) : ThermalElement

    /** UPI payment QR for the bill, with [caption] (the amount to pay) printed under it. */
    data class Qr(val content: String, val caption: String) : ThermalElement

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
            out += ThermalElement.Line(twoColumns(label, value, width), Align.LEFT, bold, label = label, value = value)
        }

        // Seller
        val seller = invoice.seller
        center(seller.name, bold = true, large = true)
        if (seller.address.isNotBlank()) center(seller.address)
        if (seller.phone.isNotBlank()) center(fillIn(labels.phone, seller.phone))
        if (seller.gstin.isNotBlank()) center(fillIn(labels.gstin, seller.gstin))
        out += ThermalElement.Divider()

        // Invoice details
        center(ReceiptText.title(invoice, labels), bold = true)
        left(fillIn(if (invoice.type == InvoiceType.RETURN) labels.cnNo else labels.invNo, invoice.number))
        left(fillIn(labels.date, ReceiptText.dateTime(invoice.createdAtMillis, zone)))
        invoice.originalInvoiceNumber?.let { left(fillIn(labels.against, it)) }
        if (options.showCustomer) {
            left(fillIn(labels.customer, invoice.buyer.name ?: labels.walkInCustomer))
            invoice.buyer.phone?.let { left(fillIn(labels.phone, ReceiptText.maskPhone(it))) }
            invoice.buyer.gstin?.let { left(fillIn(labels.gstin, it)) }
        }
        out += ThermalElement.Divider()

        // Items
        out += ThermalElement.Line(twoColumns(labels.item, labels.amount, width), bold = true, label = labels.item, value = labels.amount)
        out += ThermalElement.Divider()
        for (line in invoice.lines) {
            val name = listOf(line.productName, line.variantLabel).filter { it.isNotBlank() }.joinToString(" ")
            left(name)
            val gross = line.unitPrice * line.quantity
            pair("  " + fillIn(labels.qtyTimesPrice, line.quantity, IndianNumberFormat.amount(line.unitPrice)), IndianNumberFormat.amount(gross))
            ReceiptText.mrpDiscount(line)?.let { pair("  " + fillIn(labels.mrpDiscountLine, IndianNumberFormat.amount(line.mrp!!)), "-" + IndianNumberFormat.amount(it)) }
            if (!line.discount.isZero) {
                pair("  " + labels.discount + (line.discountReason?.let { " ($it)" } ?: ""), "-" + IndianNumberFormat.amount(line.discount))
            }
            if (invoice.gstRegistered && options.showHsn) {
                val parts = listOfNotNull(
                    line.hsnCode?.let { fillIn(labels.hsn, it) },
                    fillIn(labels.gstRate, ReceiptText.rate(line.gstRateBasisPoints)),
                )
                left("  " + parts.joinToString("  "))
            }
        }
        out += ThermalElement.Divider()

        // Totals
        pair(fillIn(labels.itemsCount, invoice.lines.size), fillIn(labels.qtyCount, invoice.itemCount))
        pair(labels.subtotal, IndianNumberFormat.amount(invoice.subtotal))
        if (!invoice.discountTotal.isZero) pair(labels.discount, "-" + IndianNumberFormat.amount(invoice.discountTotal))
        invoice.discountReason?.let { left(fillIn(labels.discountReason, it)) }
        if (invoice.gstRegistered) {
            pair(labels.taxableValue, IndianNumberFormat.amount(invoice.taxableTotal))
            if (invoice.interState) {
                pair(labels.igst, IndianNumberFormat.amount(invoice.igstTotal))
            } else {
                pair(labels.cgst, IndianNumberFormat.amount(invoice.cgstTotal))
                pair(labels.sgst, IndianNumberFormat.amount(invoice.sgstTotal))
            }
        }
        if (!invoice.roundOff.isZero) pair(labels.roundOff, signed(invoice.roundOff))
        out += ThermalElement.Divider('=')
        val totalLabel = if (invoice.type == InvoiceType.RETURN) labels.refund else labels.total
        val totalValue = IndianNumberFormat.amount(invoice.grandTotal)
        if (totalLabel.length + 1 + totalValue.length <= width / 2) {
            out += ThermalElement.Line(twoColumns(totalLabel, totalValue, width / 2), bold = true, large = true, label = totalLabel, value = totalValue)
        } else {
            pair(fillIn(labels.rupeeLabel, totalLabel), totalValue, bold = true)
        }
        out += ThermalElement.Divider('=')

        // Payments
        for (payment in invoice.payments) {
            pair(ReceiptText.paymentLabel(payment, labels), IndianNumberFormat.amount(payment.amount))
            payment.reference?.let { left("  " + fillIn(labels.ref, it)) }
            payment.tendered?.let { pair("  " + labels.cashReceived, IndianNumberFormat.amount(it)) }
            payment.change?.let { pair("  " + labels.change, IndianNumberFormat.amount(it)) }
        }

        if (invoice.note != null) {
            out += ThermalElement.Divider()
            left(fillIn(labels.reason, invoice.note))
        }

        out += ThermalElement.Divider()
        if (options.showSavings && invoice.type == InvoiceType.SALE && invoice.savings > Money.ZERO) {
            center(fillIn(labels.youSavedRs, IndianNumberFormat.amount(invoice.savings)), bold = true)
        }
        if (options.footer.isNotBlank()) center(options.footer)
        if (labels.feedbackNote.isNotBlank()) center(labels.feedbackNote)
        options.upiLinkFor(invoice)?.let { link ->
            out += ThermalElement.Gap
            out += ThermalElement.Qr(link, fillIn(labels.scanToPay, IndianNumberFormat.amount(invoice.grandTotal)))
        }
        if (options.showInvoiceBarcode && invoice.number.isNotEmpty()) {
            out += ThermalElement.Gap
            out += ThermalElement.Barcode(invoice.number)
        }
        return ThermalReceipt(width, out)
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
