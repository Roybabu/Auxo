package com.invision.pos.core.receipt

import com.invision.pos.domain.billing.AmountInWords
import com.invision.pos.domain.billing.IndianNumberFormat
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import java.time.ZoneId

/** A column of the item table on A5/A4 invoices. */
data class SheetColumn(
    val title: String,
    /** Share of the table width. */
    val weight: Float,
    val alignRight: Boolean,
    val value: (index: Int, line: InvoiceLine) -> String,
)

/** Everything printed on an A5/A4 invoice, as text, ready to be drawn. */
data class SheetInvoice(
    val title: String,
    val sellerLines: List<String>,
    val sellerName: String,
    val detailLines: List<Pair<String, String>>,
    val buyerHeading: String,
    val buyerLines: List<String>,
    val columns: List<SheetColumn>,
    val rows: List<List<String>>,
    /** Discount note shown under an item name, if any. */
    val rowNotes: List<String?>,
    val totals: List<Pair<String, String>>,
    val grandTotal: Pair<String, String>,
    val amountInWords: String,
    val taxSummaryHeader: List<String>,
    val taxSummaryRows: List<List<String>>,
    val paymentLines: List<String>,
    val notes: List<String>,
    val footer: String,
    val signatureLabel: String,
    val barcode: String?,
)

object SheetInvoiceLayout {

    fun build(
        invoice: Invoice,
        options: ReceiptOptions,
        paper: ReceiptPaper,
        zone: ZoneId,
        labels: ReceiptLabels = ReceiptLabels(),
    ): SheetInvoice {
        val seller = invoice.seller
        val gst = invoice.gstRegistered
        val isReturn = invoice.type == InvoiceType.RETURN

        val sellerLines = buildList {
            if (seller.address.isNotBlank()) addAll(seller.address.lines().filter { it.isNotBlank() })
            if (seller.phone.isNotBlank()) add("Phone: ${seller.phone}")
            if (seller.gstin.isNotBlank()) add("GSTIN: ${seller.gstin}")
        }
        val details = buildList {
            add((if (isReturn) "Credit note no." else "Invoice no.") to invoice.number)
            add("Date" to ReceiptText.dateTime(invoice.createdAtMillis, zone))
            invoice.originalInvoiceNumber?.let { add("Against invoice" to it) }
            if (gst && invoice.placeOfSupplyStateCode.isNotEmpty()) {
                add("Place of supply" to ReceiptText.placeOfSupply(invoice.placeOfSupplyStateCode))
            }
        }
        val buyerLines = if (options.showCustomer) {
            buildList {
                add(invoice.buyer.name ?: labels.walkInCustomer)
                invoice.buyer.phone?.let { add("Phone: $it") }
                invoice.buyer.gstin?.let { add("GSTIN: $it") }
            }
        } else {
            emptyList()
        }

        val showHsn = gst && options.showHsn
        val wide = paper == ReceiptPaper.A4
        val columns = buildList {
            add(SheetColumn("#", 0.05f, alignRight = false) { i, _ -> (i + 1).toString() })
            add(SheetColumn("Item", if (wide) 0.30f else 0.34f, alignRight = false) { _, l ->
                listOf(l.productName, l.variantLabel).filter { it.isNotBlank() }.joinToString(" · ")
            })
            if (showHsn) add(SheetColumn("HSN", 0.10f, alignRight = false) { _, l -> l.hsnCode.orEmpty() })
            add(SheetColumn("Qty", 0.07f, alignRight = true) { _, l -> l.quantity.toString() })
            add(SheetColumn("Rate", 0.12f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.unitPrice) })
            if (wide) add(SheetColumn("Disc.", 0.10f, alignRight = true) { _, l -> if (l.discount.isZero) "" else IndianNumberFormat.amount(l.discount) })
            if (gst && wide) add(SheetColumn("Taxable", 0.12f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.taxable) })
            if (gst) add(SheetColumn("GST", 0.07f, alignRight = true) { _, l -> ReceiptText.rate(l.gstRateBasisPoints) })
            add(SheetColumn("Amount", 0.14f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.total) })
        }
        val rows = invoice.lines.mapIndexed { i, line -> columns.map { it.value(i, line) } }
        val rowNotes = invoice.lines.map { line ->
            if (!wide && !line.discount.isZero) "Discount ${IndianNumberFormat.amount(line.discount)}" else null
        }

        val totals = buildList {
            add("Subtotal" to IndianNumberFormat.amount(invoice.subtotal))
            if (!invoice.discountTotal.isZero) add("Discount" to "-" + IndianNumberFormat.amount(invoice.discountTotal))
            if (gst) {
                add("Taxable value" to IndianNumberFormat.amount(invoice.taxableTotal))
                if (invoice.interState) {
                    add("IGST" to IndianNumberFormat.amount(invoice.igstTotal))
                } else {
                    add("CGST" to IndianNumberFormat.amount(invoice.cgstTotal))
                    add("SGST" to IndianNumberFormat.amount(invoice.sgstTotal))
                }
            }
            if (!invoice.roundOff.isZero) {
                val sign = if (invoice.roundOff > Money.ZERO) "+" else ""
                add("Round off" to sign + IndianNumberFormat.amount(invoice.roundOff))
            }
        }
        val grandLabel = if (isReturn) "Total refund" else "Grand total"

        val (summaryHeader, summaryRows) = if (gst && options.showTaxBreakup) {
            val header = if (invoice.interState) {
                listOf("GST rate", "Taxable value", "IGST", "Total tax")
            } else {
                listOf("GST rate", "Taxable value", "CGST", "SGST", "Total tax")
            }
            val rowsByRate = invoice.lines.groupBy { it.gstRateBasisPoints }.toSortedMap().map { (rate, lines) ->
                val taxable = lines.fold(Money.ZERO) { a, l -> a + l.taxable }
                val cgst = lines.fold(Money.ZERO) { a, l -> a + l.cgst }
                val sgst = lines.fold(Money.ZERO) { a, l -> a + l.sgst }
                val igst = lines.fold(Money.ZERO) { a, l -> a + l.igst }
                if (invoice.interState) {
                    listOf(ReceiptText.rate(rate), IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(igst), IndianNumberFormat.amount(igst))
                } else {
                    listOf(
                        ReceiptText.rate(rate), IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(cgst),
                        IndianNumberFormat.amount(sgst), IndianNumberFormat.amount(cgst + sgst),
                    )
                }
            }
            header to rowsByRate
        } else {
            emptyList<String>() to emptyList()
        }

        val payments = invoice.payments.map { p ->
            buildString {
                append(ReceiptText.paymentLabel(p, sheet = true) + ": ")
                append(IndianNumberFormat.rupees(p.amount))
                p.reference?.let { append(" (Ref $it)") }
                if (p.tendered != null && p.change != null) {
                    append(" · Received ${IndianNumberFormat.rupees(p.tendered)}, change ${IndianNumberFormat.rupees(p.change)}")
                }
            }
        }

        val notes = buildList {
            invoice.note?.let { add("Reason for return: $it") }
            if (gst && invoice.pricesIncludeTax) add("Prices include GST.")
            if (!gst) add("Composition or unregistered dealer: tax not charged.")
            if (options.showSavings && !isReturn && invoice.savings > Money.ZERO) {
                add("You saved ${IndianNumberFormat.rupees(invoice.savings)} on MRP.")
            }
        }

        return SheetInvoice(
            title = ReceiptText.title(invoice, labels),
            sellerName = seller.name,
            sellerLines = sellerLines,
            detailLines = details,
            buyerHeading = if (isReturn) "Customer" else "Bill to",
            buyerLines = buyerLines,
            columns = columns,
            rows = rows,
            rowNotes = rowNotes,
            totals = totals,
            grandTotal = grandLabel to IndianNumberFormat.rupees(invoice.grandTotal),
            amountInWords = AmountInWords.rupees(invoice.grandTotal),
            taxSummaryHeader = summaryHeader,
            taxSummaryRows = summaryRows,
            paymentLines = payments,
            notes = notes,
            footer = options.footer,
            signatureLabel = "For ${seller.name}",
            barcode = invoice.number.takeIf { options.showInvoiceBarcode && it.isNotEmpty() },
        )
    }
}
